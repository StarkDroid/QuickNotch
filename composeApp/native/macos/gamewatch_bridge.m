#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <CoreGraphics/CoreGraphics.h>
#import <unistd.h>
#include <stdio.h>
#include <string.h>

/**
 * Fullscreen-game detection for the gaming quickview.
 *
 * A "game" here is the only signal macOS exposes without Screen Recording or
 * Accessibility permission: some process that is not ours, not a system shell,
 * owns a layer-0 window covering the whole main display. Games (and any other
 * app the user made fullscreen) satisfy that exactly; ordinary windows, the
 * Dock, the menu bar and our own overlay never do. Window *titles* need
 * permission on modern macOS, but owner names and bounds do not, which is why
 * this bridge reads those and nothing else.
 *
 * Returns 1 and writes the owner's app name (UTF-8, truncated to outLen) when a
 * fullscreen window is found, 0 otherwise. Fail-soft by construction: a missing
 * window server connection simply reports "no game".
 */

static BOOL QNIsShellOwner(NSString *name) {
    if (name.length == 0) return YES;
    static NSSet<NSString *> *shells = nil;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        shells = [NSSet setWithArray:@[
            @"Window Server", @"Dock", @"Finder", @"SystemUIServer",
            @"Control Center", @"Notification Center", @"Spotlight",
            @"QuickNotch", @"loginwindow", @"UserNotificationCenter",
            @"TextInputMenuAgent", @"Universal Control"
        ]];
    });
    return [shells containsObject:name];
}

int qn_fullscreen_game(char *out, int outLen) {
    if (out != NULL && outLen > 0) out[0] = '\0';

    const CGRect screen = CGDisplayBounds(CGMainDisplayID());
    if (screen.size.width < 1.0 || screen.size.height < 1.0) return 0;

    CGWindowListOption options =
        kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements;
    CFArrayRef windows = CGWindowListCopyWindowInfo(options, kCGNullWindowID);
    if (windows == NULL) return 0;

    const pid_t self = getpid();
    // Two points of slack: a "maximised" window that still shows the menu bar
    // is not fullscreen, and a real fullscreen window is exact to the pixel.
    const CGFloat tolerance = 2.0;
    int found = 0;

    for (CFIndex i = 0; i < CFArrayGetCount(windows) && !found; i++) {
        CFDictionaryRef window = CFArrayGetValueAtIndex(windows, i);
        if (window == NULL) continue;

        CFNumberRef layerRef = CFDictionaryGetValue(window, kCGWindowLayer);
        int layer = 0;
        if (layerRef == NULL || !CFNumberGetValue(layerRef, kCFNumberIntType, &layer)) continue;
        // Normal app windows only: menu bar (24/25), Dock and overlays live
        // above or below this band and would otherwise read as "fullscreen".
        if (layer != 0) continue;

        CFDictionaryRef boundsRef = CFDictionaryGetValue(window, kCGWindowBounds);
        CGRect bounds = CGRectZero;
        if (boundsRef == NULL ||
            !CGRectMakeWithDictionaryRepresentation(boundsRef, &bounds)) continue;
        if (bounds.size.width < screen.size.width - tolerance ||
            bounds.size.height < screen.size.height - tolerance) continue;

        CFNumberRef pidRef = CFDictionaryGetValue(window, kCGWindowOwnerPID);
        pid_t pid = 0;
        if (pidRef == NULL || !CFNumberGetValue(pidRef, kCFNumberIntType, &pid)) continue;
        if (pid == self) continue;

        CFStringRef ownerRef = CFDictionaryGetValue(window, kCGWindowOwnerName);
        NSString *owner = (ownerRef != NULL)
            ? CFBridgingRelease(CFStringCreateCopy(kCFAllocatorDefault, ownerRef))
            : @"";
        if (QNIsShellOwner(owner)) continue;

        if (out != NULL && outLen > 0) {
            snprintf(out, (size_t)outLen, "%s", owner.UTF8String ?: "");
        }
        found = 1;
    }

    CFRelease(windows);
    return found;
}
