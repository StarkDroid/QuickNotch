#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>

// In-process bridge loaded by `WindowPassthrough` (macOS) plus the overlay's
// window policy and Dock-presence controllers. All AppKit window/application
// state has to be touched on the main queue, which the JVM side cannot
// guarantee (it polls from a worker and the Swing EDT), and going through a
// compiled C symbol instead of JNA->objc_msgSend avoids the unreliable
// void-return / BOOL-arg ABI path on Apple silicon (SIGTRAP). Every entry point
// is plain C so JNA's call marshalling is exactly the normal C-ABI.
//
// `nsWindow` is the raw NSWindow* captured from the AWT peer's
// CFRetainedResource.ptr field (see `resolveMacWindowPtr`).

// Run a block on the main thread, whether or not we are already there. Reads
// (window/screen geometry) use this synchronously; writes use
// dispatch_async(dispatch_get_main_queue()).
static void runOnMainSync(void (^block)(void)) {
    if ([NSThread isMainThread]) {
        block();
    } else {
        dispatch_sync(dispatch_get_main_queue(), block);
    }
}

// Synchronous "is this really an NSWindow that understands the setter" probe;
// `respondsToSelector:` is a pure message send and is safe off the main queue.
int quicknotch_window_responds(void *nsWindow) {
    if (nsWindow == NULL) {
        return 0;
    }
    return [(__bridge NSWindow *)nsWindow respondsToSelector:@selector(setIgnoresMouseEvents:)] ? 1 : 0;
}

// Fire-and-forget toggle of the whole-window mouse transparency, dispatched
// onto AppKit's main queue so the main-thread requirement holds.
void quicknotch_set_ignores_mouse_events(void *nsWindow, int ignore) {
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    BOOL flag = ignore ? YES : NO;
    dispatch_async(dispatch_get_main_queue(), ^{
        window.ignoresMouseEvents = flag;
    });
}

// Synchronous read-back of the live flag (main-thread safe). Debug/diagnostic
// helper for the passthrough's reconcile logic.
int quicknotch_get_ignores_mouse_events(void *nsWindow) {
    if (nsWindow == NULL) return -1;
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    __block BOOL flag = NO;
    if ([NSThread isMainThread]) {
        flag = window.ignoresMouseEvents;
    } else {
        dispatch_sync(dispatch_get_main_queue(), ^{
            flag = window.ignoresMouseEvents;
        });
    }
    return flag ? 1 : 0;
}

// Stacking order of the overlay window. AWT's alwaysOnTop only reaches
// NSFloatingWindowLevel (3), which sits BELOW the menu bar (24) — a window
// parked at screen-y=0 would still read as "below the screen". The notch needs
// NSStatusWindowLevel (25) = the band the menu bar occupies. Setting the level
// here also re-asserts it after AWT's z-order bookkeeping (which resets levels
// whenever it reorders sibling windows).
void quicknotch_set_window_level(void *nsWindow, int level) {
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    dispatch_async(dispatch_get_main_queue(), ^{
        window.level = (NSWindowLevel)level;
    });
}

// Spaces/Mission-Control behaviour, as a bitmask of NSWindowCollectionBehavior.
// The notch joins every Space (it is tied to the physical display, not an app),
// stays put while Spaces animate, survives full-screen apps (Auxiliary), and is
// hidden from Cmd+Tab even when the app momentarily flips to a regular policy.
void quicknotch_set_collection_behavior(void *nsWindow, unsigned long long behavior) {
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    dispatch_async(dispatch_get_main_queue(), ^{
        window.collectionBehavior = (NSWindowCollectionBehavior)behavior;
    });
}

// Pins the window flush to the very top of its own screen (full frame, NOT the
// visible frame — the menu bar / notch area is where the island lives), a few
// points below the physical edge for the floating styles. AWT cannot reach this
// position: macOS AWT clamps window placement to the visible area, so y=0 in
// AWT-space is already 30pt below the top (see main.kt placement notes). This
// moves the NSWindow directly in Cocoa coordinates and keeps it horizontally
// centred on the screen it currently sits on.
void quicknotch_set_frame_top_gap(void *nsWindow, double topGap) {
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    dispatch_async(dispatch_get_main_queue(), ^{
        NSScreen *screen = window.screen ?: NSScreen.mainScreen;
        NSRect s = screen.frame;
        NSRect f = window.frame;
        // Cocoa origin is bottom-left; the top edge of the screen is at
        // (s.origin.y + s.size.height). topGap == 0 parks the top at the very
        // top edge; a positive gap floats the body off it (capsule styles).
        double topCocoaY = s.origin.y + s.size.height - topGap;
        NSRect target = NSMakeRect(
            s.origin.x + (s.size.width - f.size.width) / 2.0,
            topCocoaY - f.size.height,
            f.size.width,
            f.size.height);
        if (!NSEqualRects(f, target)) {
            [window setFrame:target display:NO];
        }
    });
}

// Height (points) of the menu-bar strip on the window's screen: the gap between
// the full frame and the visible frame. Used when the "always on top" toggle is
// OFF to park the island back below the menu bar instead of leaving it shifted
// up by a previous ON state.
double quicknotch_get_menu_bar_height(void *nsWindow) {
    __block double height = 0.0;
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    runOnMainSync(^{
        NSScreen *screen = window.screen ?: NSScreen.mainScreen;
        height = screen.frame.size.height - screen.visibleFrame.size.height;
    });
    return height;
}

// The window's top-left in CG screen coordinates (top-left origin, in points),
// which is what AWT cannot know after the direct Cocoa moves above: AWT reports
// its own cached virtual location (y=0 is "30pt below the top"), not the live
// NSWindow frame. Passthrough's cursor-over math needs the real origin or the
// island's hit target drifts off by exactly the menu-bar height.
int quicknotch_get_window_cg_origin(void *nsWindow, double *x, double *y) {
    if (nsWindow == NULL) {
        return 0;
    }
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    __block NSRect frame = NSZeroRect;
    __block NSRect screenFrame = NSZeroRect;
    runOnMainSync(^{
        frame = window.frame;
        NSScreen *screen = window.screen ?: NSScreen.mainScreen;
        screenFrame = screen.frame;
    });
    if (frame.size.width <= 0.0 || frame.size.height <= 0.0) {
        return 0;
    }
    // CG y counts DOWN from the screen's top edge.
    double cgTop = screenFrame.origin.y + screenFrame.size.height;
    double topLeftY = cgTop - (frame.origin.y + frame.size.height);
    *x = frame.origin.x;
    *y = topLeftY;
    return 1;
}

// Application activation policy: 0 = Regular (Dock icon + app menu, shown while
// the Settings window is open), 1 = Accessory (pure menu-bar agent — no Dock
// icon, cannot take focus — the notch's resting state). Accessory is also what
// lets the overlay layer over full-screen apps. Only meaningful after NSApp
// exists, which AWT guarantees by the time any window is composed.
void quicknotch_set_activation_policy(int policy) {
    dispatch_async(dispatch_get_main_queue(), ^{
        [NSApp setActivationPolicy:(NSApplicationActivationPolicy)policy];
    });
}

// Pull the app to the front when the Settings window opens (an accessory app
// cannot activate on its own; flipping its policy back to Regular while another
// app is active does not surface the Settings window without this nudge).
void quicknotch_activate_app(void) {
    dispatch_async(dispatch_get_main_queue(), ^{
        [NSApp activateIgnoringOtherApps:YES];
    });
}

// Backdrop blur behind the island via the WindowServer compositor directly.
// An NSVisualEffectView never sampled the backdrop behind this JVM/Metal
// canvas in any placement (inside the canvas it covered the controls; as a
// sibling or stripped of its fill it rendered nothing at all), so the blur
// is applied one level lower instead: CGSSetWindowBackgroundBlurRadius blurs
// everything behind the whole window at the given radius, exactly the
// mechanism Warp/Alacritty/iTerm2/WezTerm use for translucent terminals.
// Private SkyLight API, resolved at runtime with dlopen/dlsym so nothing
// links against it at build time; direct distribution only (never the App
// Store). Trade-off vs. the effect view: the radius covers the full native
// window including the transparent click-through margin, which reads as a
// soft blurred halo around the pill rather than a sharply masked capsule.
// Compose still draws its light scrim over the island for legibility.
#import <dlfcn.h>

typedef void *CGSConnectionID;
typedef CGSConnectionID (*CGSMainConnectionIDFn)(void);
typedef int (*CGSSetWindowBackgroundBlurRadiusFn)(CGSConnectionID, long, long long);

static CGSMainConnectionIDFn sCGSMainConnectionID = NULL;
static CGSSetWindowBackgroundBlurRadiusFn sCGSSetWindowBackgroundBlurRadius = NULL;
static BOOL sCGSResolved = NO;

static void resolveBlurSymbols(void) {
    if (sCGSResolved) return;
    sCGSResolved = YES;
    sCGSMainConnectionID = (CGSMainConnectionIDFn)dlsym(RTLD_DEFAULT, "CGSMainConnectionID");
    sCGSSetWindowBackgroundBlurRadius =
        (CGSSetWindowBackgroundBlurRadiusFn)dlsym(RTLD_DEFAULT, "CGSSetWindowBackgroundBlurRadius");
    if (sCGSSetWindowBackgroundBlurRadius == NULL) {
        void *skyLight = dlopen("/System/Library/PrivateFrameworks/SkyLight.framework/SkyLight", RTLD_NOW);
        if (skyLight == NULL) {
            skyLight = dlopen("/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices",
                              RTLD_NOW);
        }
        if (skyLight != NULL) {
            if (sCGSMainConnectionID == NULL) {
                sCGSMainConnectionID = (CGSMainConnectionIDFn)dlsym(skyLight, "CGSMainConnectionID");
            }
            sCGSSetWindowBackgroundBlurRadius =
                (CGSSetWindowBackgroundBlurRadiusFn)dlsym(skyLight, "CGSSetWindowBackgroundBlurRadius");
        }
    }
}

static long long sLastRequestedBlurRadius = -1;

static void applyWindowBlurRadius(NSWindow *window, long long radius) {
    long windowId = (long)[window windowNumber];
    if (windowId == 0) return;
    int result = sCGSSetWindowBackgroundBlurRadius(sCGSMainConnectionID(), windowId, radius);
    fprintf(stderr, "QuickNotch window blur: wid=%ld radius=%lld result=%d\n",
            windowId, radius, result);
}

void quicknotch_set_window_blur_radius(void *nsWindow, long long radius) {
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    dispatch_async(dispatch_get_main_queue(), ^{
        resolveBlurSymbols();
        if (sCGSMainConnectionID == NULL || sCGSSetWindowBackgroundBlurRadius == NULL) {
            fprintf(stderr, "QuickNotch window blur: CGS symbols unavailable\n");
            return;
        }
        sLastRequestedBlurRadius = radius;
        applyWindowBlurRadius(window, radius);
        if (radius == 0) {
            // The radius call is sticky: re-assert the clear shortly after in
            // case the compositor drops a lone zero, unless a re-enable has
            // arrived in the meantime.
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 350 * NSEC_PER_MSEC),
                           dispatch_get_main_queue(), ^{
                if (sLastRequestedBlurRadius == 0) {
                    applyWindowBlurRadius(window, 0);
                }
            });
        }
    });
}