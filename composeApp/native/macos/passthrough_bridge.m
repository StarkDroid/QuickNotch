#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>

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

// Height (points) of the menu-bar strip on the window's screen. Measured as
// the distance between the top of the full frame and the top of the visible
// frame: the visible frame also shrinks for a Dock at the bottom, and the
// total difference would park the island below that reserved band instead of
// below the menu bar.
double quicknotch_get_menu_bar_height(void *nsWindow) {
    __block double height = 0.0;
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    runOnMainSync(^{
        NSScreen *screen = window.screen ?: NSScreen.mainScreen;
        NSRect frame = screen.frame;
        NSRect visible = screen.visibleFrame;
        double frameTop = frame.origin.y + frame.size.height;
        double visibleTop = visible.origin.y + visible.size.height;
        height = MAX(0.0, frameTop - visibleTop);
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

// Backdrop blur behind the island through a dedicated helper window: a
// borderless nonactivating STANDALONE panel at the overlay's level, stacked
// directly below it and re-synced to it (never an addChildWindow: — the
// overlay is an AWT window, which silences the compositor radius, see the
// syncBlurWindow comment), sized to the island itself so the frosted shape
// tracks expand/collapse with no halo. Two mechanisms layer here: the
// window's compositor blur radius (private SkyLight
// CGSSetWindowBackgroundBlurRadius — iTerm2/Terminal.app's recipe) and an
// effect view filling the panel, corner-masked to the notch shape. The
// window background MUST keep a non-zero alpha (~1%): with a fully clear
// background the compositor treats the window as "irregular" and skips the
// blur (documented CGS behaviour). The intensity slider drives both (radius
// + effect opacity). Compose still draws its light scrim over the island
// for legibility.
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

static NSWindow *sBlurWindow = nil;
static NSVisualEffectView *sBlurEffect = nil;
static NSWindow *sBlurParent = nil;
static NSTimer *sBlurSyncTimer = nil;
static double sIslandW = 0;
static double sIslandH = 0;
static double sIslandIntensity = 0;
static long sBlurAppliedWid = -1;
static long long sBlurAppliedRadius = -1;

static void applyHelperBlurRadius(long long radius);

// Per-corner rounded-rect path in view/layer coordinates. The two corners
// are given per coordinate-space edge: `rEdge0` rounds the corner at y=0,
// `rEdgeH` the corner at y+height — the caller decides which is the visual
// top or bottom from the view's flippedness (an unflipped view reads its
// bounds y-up, so the path built "y-down" rounded the WRONG pair: top rounded,
// bottom square — exactly the inversion the window capture showed).
// notchShape() (OverlayGeometry.kt) rounds only the visual bottom pair on
// the flush style and all four on capsule styles, so the corners have to be
// chosen one-by-one — CGPathCreateWithRoundedRect rounds everything it is
// given. Quad curves rather than arcs: at these radii (13–18pt) the parabola
// tracks the true quarter-circle to well under a point, and the path builder
// stays free of angle bookkeeping.
static CGPathRef createIslandPath(CGRect b, double rEdge0, double rEdgeH) {
    CGMutablePathRef p = CGPathCreateMutable();
    double x = b.origin.x, y = b.origin.y, w = b.size.width, h = b.size.height;
    CGPathMoveToPoint(p, NULL, x + rEdge0, y);
    CGPathAddLineToPoint(p, NULL, x + w - rEdge0, y);
    if (rEdge0 > 0) CGPathAddQuadCurveToPoint(p, NULL, x + w, y, x + w, y + rEdge0);
    CGPathAddLineToPoint(p, NULL, x + w, y + h - rEdgeH);
    if (rEdgeH > 0) CGPathAddQuadCurveToPoint(p, NULL, x + w, y + h, x + w - rEdgeH, y + h);
    CGPathAddLineToPoint(p, NULL, x + rEdgeH, y + h);
    if (rEdgeH > 0) CGPathAddQuadCurveToPoint(p, NULL, x, y + h, x, y + h - rEdgeH);
    CGPathAddLineToPoint(p, NULL, x, y + rEdge0);
    if (rEdge0 > 0) CGPathAddQuadCurveToPoint(p, NULL, x, y, x + rEdge0, y);
    CGPathCloseSubpath(p);
    return p;
}

// Shape the frost with an explicit CAShapeLayer mask on BOTH the effect view
// and the window's content view. Setting cornerRadius + maskedCorners on the
// effect layer alone never reshaped the rendered output: a window-server
// capture of the helper showed a fully opaque (alpha 255) square right into
// the bottom-left corner — the backdrop/vibrancy pass draws the effect
// view's full square frame regardless of its layer mask. Clipping the parent
// content view too gives the pass a mask somewhere up its own layer tree;
// verified by capture: the helper's own output now dies to alpha 0 at the
// rounded corners. Each view gets its own path orientation from isFlipped,
// so a flipped effect view and an unflipped content view can't disagree.
static void applyHelperShapeMask(double cornerRadius, int roundTop) {
    if (sBlurWindow == nil) return;
    CGRect b = sBlurWindow.contentView.bounds;
    if (b.size.width <= 0 || b.size.height <= 0) return;
    double rVisualTop = roundTop ? cornerRadius : 0.0;
    double rVisualBot = cornerRadius;
    NSView *views[2] = { sBlurWindow.contentView, (NSView *)sBlurEffect };
    for (int i = 0; i < 2; i++) {
        NSView *v = views[i];
        if (v == nil) continue;
        v.wantsLayer = YES;
        CALayer *layer = v.layer;
        if (layer == nil) continue;
        // y=0 edge is the visual top only in a flipped coordinate system.
        BOOL flipped = v.isFlipped;
        CGPathRef path = createIslandPath(b, flipped ? rVisualTop : rVisualBot,
                                          flipped ? rVisualBot : rVisualTop);
        CAShapeLayer *mask = (CAShapeLayer *)layer.mask;
        if (![mask isKindOfClass:[CAShapeLayer class]]) {
            mask = [CAShapeLayer layer];
            layer.mask = mask;
        }
        mask.path = path;
        CGPathRelease(path);
    }
}

// The helper used to be an addChildWindow: of the overlay — but the overlay
// is an AWT window, and a CGS backdrop radius on a child of an AWT window
// returns success yet never blurs (verified: the identical helper parented
// to a plain Cocoa panel blurs fine, and parent moves / child setFrames /
// level re-asserts don't invalidate it — the AWT parent itself is the
// blocker). Standalone at the parent's level instead, ordered directly
// below it, with geometry/level/order/visibility re-synced while it lives.
// Visibility mirroring replaces what addChildWindow gave for free: hiding
// the overlay (tray "hide notch" hides the AWT window without unmounting
// composition) must hide the frost too, or an orphan slab floats at the top.
static void syncBlurWindow(void) {
    if (sBlurWindow == nil || sBlurParent == nil) return;
    if (![sBlurParent isVisible]) {
        if (sBlurWindow.isVisible) [sBlurWindow orderOut:nil];
        return;
    }
    if (!sBlurWindow.isVisible) {
        // Coming back with the parent: the window server may drop the
        // compositor radius across an orderOut, so re-arm it explicitly
        // (plus a mapped-window retry, mirroring the first-enable path).
        [sBlurWindow orderWindow:NSWindowBelow relativeTo:[sBlurParent windowNumber]];
        sBlurAppliedWid = -1;
        sBlurAppliedRadius = -1;
        applyHelperBlurRadius((long long)sIslandIntensity);
        long long pending = (long long)sIslandIntensity;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 250 * NSEC_PER_MSEC),
                       dispatch_get_main_queue(), ^{
            applyHelperBlurRadius(pending);
        });
        return;
    }
    NSRect parent = sBlurParent.frame;
    NSRect island = NSMakeRect(NSMidX(parent) - sIslandW / 2.0,
                               NSMaxY(parent) - sIslandH, sIslandW, sIslandH);
    if (!NSEqualRects(sBlurWindow.frame, island)) {
        [sBlurWindow setFrame:island display:NO];
    }
    if (sBlurWindow.level != sBlurParent.level) {
        sBlurWindow.level = sBlurParent.level;
    }
    // AWT re-orders the overlay within its level (z-order bookkeeping);
    // without this the standalone helper can drift above it and its frost
    // would cover the drawn island instead of sitting behind it.
    [sBlurWindow orderWindow:NSWindowBelow relativeTo:[sBlurParent windowNumber]];
}

static void applyHelperBlurRadius(long long radius) {
    if (sCGSMainConnectionID == NULL || sCGSSetWindowBackgroundBlurRadius == NULL) return;
    long windowId = (sBlurWindow != nil) ? (long)[sBlurWindow windowNumber] : 0;
    if (windowId == 0) return;
    // Same (window, radius) twice is a no-op for the daemon; skipping it
    // keeps per-frame size updates free of redundant compositor traffic.
    if (windowId == sBlurAppliedWid && radius == sBlurAppliedRadius) return;
    sCGSSetWindowBackgroundBlurRadius(sCGSMainConnectionID(), windowId, radius);
    sBlurAppliedWid = windowId;
    sBlurAppliedRadius = radius;
}

static void closeBlurWindow(void) {
    if (sBlurSyncTimer != nil) {
        [sBlurSyncTimer invalidate];
        sBlurSyncTimer = nil;
    }
    if (sBlurWindow != nil) {
        [sBlurWindow close];
        sBlurWindow = nil;
        sBlurEffect = nil;
    }
    sBlurParent = nil;
    sIslandW = 0;
    sIslandH = 0;
    sBlurAppliedWid = -1;
    sBlurAppliedRadius = -1;
}

void quicknotch_set_content_blur(void *nsWindow, int enabled,
                                 double w, double h,
                                 double cornerRadius, int roundTop,
                                 double intensity) {
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    dispatch_async(dispatch_get_main_queue(), ^{
        resolveBlurSymbols();
        if (!enabled) {
            // Explicit teardown: close (not merely order out). Re-showing an
            // ordered-out panel is what left the frozen slab behind; every
            // enable below starts fresh.
            closeBlurWindow();
            return;
        }
        double clamped = MAX(0.0, intensity);
        if (w <= 0 || h <= 0) return;
        if (sBlurWindow == nil || sBlurParent != window) {
            closeBlurWindow();
        }
        sIslandW = w;
        sIslandH = h;
        sIslandIntensity = clamped;
        if (sBlurWindow == nil) {
            // Island rect in screen coordinates: top-centred over the overlay.
            // Island-sized (not overlay-sized) so the frosted shape is the
            // pill itself, with no halo and no corner-nub problem.
            sBlurParent = window;
            NSRect parent = window.frame;
            NSRect island = NSMakeRect(NSMidX(parent) - w / 2.0,
                                       NSMaxY(parent) - h, w, h);
            sBlurWindow = [[NSPanel alloc]
                initWithContentRect:island
                          styleMask:(NSWindowStyleMaskBorderless | NSWindowStyleMaskNonactivatingPanel)
                            backing:NSBackingStoreBuffered
                              defer:NO];
            // EXPERIMENT: fully clear background — no window-level alpha gate.
            // The 1%-alpha fill made the compositor blur the WHOLE square
            // window (per-pixel or not), spilling a square frost lip past
            // the rounded effect-view mask at the bottom corners. If the
            // effect view alone still frosts, the gate is unnecessary.
            sBlurWindow.backgroundColor = NSColor.clearColor;
            sBlurWindow.opaque = NO;
            sBlurWindow.hasShadow = NO;
            [(NSPanel *)sBlurWindow setHidesOnDeactivate:NO];
            sBlurWindow.collectionBehavior =
                NSWindowCollectionBehaviorCanJoinAllSpaces |
                NSWindowCollectionBehaviorStationary |
                NSWindowCollectionBehaviorFullScreenAuxiliary |
                NSWindowCollectionBehaviorIgnoresCycle;
            sBlurWindow.level = window.level;
            sBlurWindow.ignoresMouseEvents = YES;
            // Effect view as the guaranteed-baseline frost + the corner mask;
            // the compositor radius layers real backdrop blur underneath it.
            sBlurEffect = [[NSVisualEffectView alloc]
                initWithFrame:sBlurWindow.contentView.bounds];
            sBlurEffect.material = NSVisualEffectMaterialPopover;
            sBlurEffect.appearance =
                [NSAppearance appearanceNamed:NSAppearanceNameVibrantDark];
            sBlurEffect.blendingMode = NSVisualEffectBlendingModeBehindWindow;
            sBlurEffect.state = NSVisualEffectStateActive;
            sBlurEffect.wantsLayer = YES;
            sBlurEffect.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
            [sBlurWindow.contentView addSubview:sBlurEffect];
            // Standalone ordering (NOT addChildWindow:, which blocks the CGS
            // radius on an AWT parent): same level as the overlay, stacked
            // directly beneath it, re-asserted by the sync timer below.
            [sBlurWindow orderWindow:NSWindowBelow relativeTo:[window windowNumber]];
            if (sBlurSyncTimer == nil) {
                sBlurSyncTimer = [NSTimer timerWithTimeInterval:0.25 repeats:YES
                    block:^(NSTimer *t) { syncBlurWindow(); }];
                [[NSRunLoop mainRunLoop] addTimer:sBlurSyncTimer
                                          forMode:NSRunLoopCommonModes];
            }
        } else {
            NSRect parent = window.frame;
            NSRect island = NSMakeRect(NSMidX(parent) - w / 2.0,
                                       NSMaxY(parent) - h, w, h);
            if (!NSEqualRects(sBlurWindow.frame, island)) {
                [sBlurWindow setFrame:island display:NO];
            }
        }
        // The panel tracks the island while it animates; keep the frost's
        // rounded mask matched to the notch shape, and its opacity plus the
        // compositor radius on the intensity slider.
        sBlurEffect.alphaValue = clamped / 40.0;
        // notchShape() (OverlayGeometry.kt) rounds only the two corners
        // facing away from the anchored edge — the BOTTOM in screen
        // coordinates — keeping the top square on the flush style so the
        // island reads as cut into the display; capsule styles round all
        // four. The frost mask must follow exactly: an inverted mask left a
        // square lip past the rounded bottom corners (visible spill at the
        // bottom-left/bottom-right of the notch).
        applyHelperShapeMask(cornerRadius, roundTop);
        if (!sBlurWindow.visible) {
            [sBlurWindow orderWindow:NSWindowBelow relativeTo:[window windowNumber]];
        }
        // The radius may only stick once the window is mapped (windowNumber 0
        // is ignored by applyHelperBlurRadius), so always retry once after a
        // beat — the dedupe keeps it free when the immediate apply landed.
        long long pendingRadius = (long long)clamped;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 250 * NSEC_PER_MSEC),
                       dispatch_get_main_queue(), ^{
            applyHelperBlurRadius(pendingRadius);
        });
        applyHelperBlurRadius((long long)clamped);
    });
}
