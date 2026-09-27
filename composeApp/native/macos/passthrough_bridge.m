#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>

// In-process bridge loaded by `WindowPassthrough` (macOS) to make the overlay
// window's transparent margin click-through. All AppKit window state has to be
// touched on the main queue, which the JVM side cannot guarantee (it polls from
// a worker and the Swing EDT), and going through a compiled C symbol instead of
// JNA->objc_msgSend avoids the unreliable void-return / BOOL-arg ABI path on
// Apple silicon (SIGTRAP). Both entry points are plain C so JNA's call
// marshalling is exactly the normal C-ABI.
//
// `nsWindow` is the raw NSWindow* captured from the AWT peer's
// CFRetainedResource.ptr field (see `resolveMacWindowPtr`).

// Synchronous "is this really an NSWindow that understands the setter" probe;
// `respondsToSelector:` is a pure message send and is safe off the main queue.
int quicknotch_window_responds(void *nsWindow) {
    if (nsWindow == NULL) {
        return 0;
    }
    return [(__bridge NSWindow *)nsWindow respondsToSelector:@selector(setIgnoresMouseEvents:)] ? 1 : 0;
}

// Fire-and-forget toggle, dispatched onto AppKit's main queue so the
// main-thread requirement holds no matter which JVM thread called in.
void quicknotch_set_ignores_mouse_events(void *nsWindow, int ignore) {
    NSWindow *window = (__bridge NSWindow *)nsWindow;
    BOOL flag = ignore ? YES : NO;
    dispatch_async(dispatch_get_main_queue(), ^{
        window.ignoresMouseEvents = flag;
    });
}