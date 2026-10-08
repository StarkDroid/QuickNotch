//
//  btwatch_bridge.m
//
//  macOS Bluetooth/audio change watcher. The audio-device battery poller on
//  macOS uses `system_profiler SPBluetoothDataType`, which is too heavy to run
//  every second the way Windows polls its WinRT connection state — so it runs
//  on a slow 10 s cadence and the headphone ring currently lags connect /
//  disconnect by up to that interval.
//
//  This helper closes the gap with an event instead of a faster poll:
//
//   • CoreAudio (the reliable signal). Whenever the *default output device*
//     changes — a Bluetooth headset routes in, or the route falls back to the
//     speakers when they drop — CoreAudio fires a property-change listener.
//     That is semantically exactly what the ring's own gate tests, so the
//     event fires the instant the ring should appear or vanish. Device
//     add/remove events are also watched for the early "joined the audio
//     graph, not yet routed" moment.
//
//   • Bluetooth distributed/Darwin notifications (bonus, some releases only).
//     bluetoothd's `com.apple.bluetooth.device-*` names historically carried
//     connect/disconnect; modern macOS doesn't always post them, but the
//     observer is harmless when it does.
//
//  Every fired event coalesces into one marker line on stdout ("AUDIO" /
//  "BT"), which the app treats as "probe the Bluetooth stack right now", so
//  the ring appears moments after the device connects instead of up to ten
//  seconds later, at effectively zero standing cost. If no event ever arrives
//  the slow poll remains the backstop — behaviour just degrades to the
//  previous latency.

#import <Foundation/Foundation.h>
#import <CoreAudio/CoreAudio.h>
#import <notify.h>
#include <unistd.h>

// ── Orphan watchdog (see nowplaying_bridge.m) ───────────────────────────────
// The JVM supervisor can die without cleanup (SIGTERM/kill -9 never dispose
// the composition); launchd then reparents us to pid 1 and a leaked watcher
// would sit on its CoreAudio/Darwin listeners forever. Exit within a second
// of seeing ppid == 1.
static void ArmOrphanWatchdog(void) {
    NSTimer *timer = [NSTimer timerWithTimeInterval:1.0 repeats:YES block:^(NSTimer *__unused t) {
        if (getppid() == 1) exit(0);
    }];
    [[NSRunLoop mainRunLoop] addTimer:timer forMode:NSRunLoopCommonModes];
}

static volatile BOOL changeInFlight;

static void EmitChange(const char *kind) {
    if (changeInFlight) return;
    changeInFlight = YES;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 500 * NSEC_PER_MSEC),
                   dispatch_get_global_queue(QOS_CLASS_BACKGROUND, 0), ^{
        changeInFlight = NO;
    });
    printf("%s\n", kind);
    fflush(stdout);
}

// ── CoreAudio listeners on the system audio object ───────────────────────────

static dispatch_queue_t GetAudioQueue(void) {
    static dispatch_queue_t q;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        q = dispatch_queue_create("quicknotch.audiowatch", NULL);
    });
    return q;
}

static void ObserveAudioProperty(AudioObjectPropertyAddress addr) {
    AudioObjectAddPropertyListenerBlock(
        kAudioObjectSystemObject, &addr, GetAudioQueue(),
        ^(UInt32 n, const AudioObjectPropertyAddress *a) {
            (void)n; (void)a;
            EmitChange("AUDIO");
        });
}

// ── Bluetooth distributed / Darwin notifications (best-effort bonus) ─────────

static void ObserveDistributed(NSString *name) {
    [[NSDistributedNotificationCenter defaultCenter]
        addObserverForName:name
                    object:nil
                        queue:[NSOperationQueue mainQueue]
                usingBlock:^(NSNotification *__unused note) {
                    EmitChange("BT");
                }];
}

static void ObserveDarwin(const char *name) {
    __attribute__((unused)) int token = 0;
    notify_register_dispatch(name, &token,
                             dispatch_get_global_queue(QOS_CLASS_BACKGROUND, 0),
                             ^(int __unused tokenValue) {
        EmitChange("BT");
    });
}

int main(int argc, const char *argv[]) {
    (void)argc; (void)argv;
    @autoreleasepool {
        signal(SIGPIPE, SIG_IGN);
        ArmOrphanWatchdog();

        AudioObjectPropertyAddress outAddr = {
            kAudioHardwarePropertyDefaultOutputDevice,
            kAudioObjectPropertyScopeGlobal,
            kAudioObjectPropertyElementMain
        };
        AudioObjectPropertyAddress sysOutAddr = {
            kAudioHardwarePropertyDefaultSystemOutputDevice,
            kAudioObjectPropertyScopeGlobal,
            kAudioObjectPropertyElementMain
        };
        AudioObjectPropertyAddress devAddr = {
            kAudioHardwarePropertyDevices,
            kAudioObjectPropertyScopeGlobal,
            kAudioObjectPropertyElementMain
        };
        ObserveAudioProperty(outAddr);
        ObserveAudioProperty(sysOutAddr);
        ObserveAudioProperty(devAddr);

        ObserveDistributed(@"com.apple.bluetooth.device-connected");
        ObserveDistributed(@"com.apple.bluetooth.device-disconnected");
        ObserveDistributed(@"com.apple.bluetooth.device-changed");
        ObserveDarwin("com.apple.bluetooth.device-connected");
        ObserveDarwin("com.apple.bluetooth.device-disconnected");
        ObserveDarwin("com.apple.bluetooth.device-changed");

        [[NSRunLoop mainRunLoop] run];
    }
    return 0;
}