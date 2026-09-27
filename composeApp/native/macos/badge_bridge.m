#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>

// In-process bridge loaded by `NotificationCount` (macOS) to move QuickNotch's
// own app-icon badge so the "Send test notification" button can be reflected in
// the quickview badge in real time. On macOS the quickview unread badge is the
// sum of the Notification Center store's per-app `badge` columns (the same
// numbers the Dock shows); setting our own badge through
// `UNUserNotificationCenter setBadgeCount:` makes the test flow deterministic
// without depending on whether an osascript banner happens to get a store row.
//
// Going through a compiled C symbol instead of JNA->objc_msgSend avoids the
// unreliable void-return ABI path on Apple silicon (SIGTRAP). Like all AppKit
// touches, the badge set is dispatched onto the main queue.
//
// Not a bundle context (e.g. dev `:run` through a plain java process) can't
// own an app badge; the call is a harmless no-op there.
void quicknotch_set_badge_count(int count) {
    dispatch_async(dispatch_get_main_queue(), ^{
        // `setBadgeCount:` needs macOS 13; on 12 the badge simply doesn't move.
        if (@available(macOS 13.0, *)) {
            UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
            [center setBadgeCount:(NSInteger)count withCompletionHandler:nil];
        }
    });
}