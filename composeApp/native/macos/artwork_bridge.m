//
//  artwork_bridge.m
//
//  Entitled-context album-art fetcher for macOS. The now-playing poll loop
//  (`nowplaying_bridge.m`) runs MediaRemote through `osascript -l JavaScript`,
//  which answers metadata but never yields the `ArtworkData` bytes — scripted
//  contexts only ever see the metadata half of the daemon's reply. The full
//  C API (`MRMediaRemoteGetNowPlayingInfo`), called from a process whose
//  bundle identity starts with "com.apple.", gets the artwork too.
//
//  This file is therefore not a standalone executable: it is built as a dylib
//  exposing one plain C entry point, `qn_artwork_get`, and it is loaded into
//  `/usr/bin/perl` (bundle identity `com.apple.perl*`) by the tiny
//  `artwork-driver.pl` script via DynaLoader. Running inside the entitled
//  host is the entire trick — the code itself is just one MediaRemote query
//  with a deadline, printing a single JSON line:
//
//    {"id":"title|artist|album","art":"<base64>"}   — cover bytes + whose they are
//    {"id":"title|artist|album","art":null}         — that track publishes no art
//    {"id":null,"art":null}                        — nothing playing / query failed
//
//  Only artwork is fetched here — title/artist/state keep flowing through the
//  existing poll loop, and the Kotlin side merges the bytes only when `id`
//  matches the track that requested them (a lagging daemon photographs the
//  wrong song, and that reply must retry, never merge), so this runs strictly
//  once per track that is missing art.
//

#import <Foundation/Foundation.h>
#import <dispatch/dispatch.h>

// `MRMediaRemoteGetNowPlayingInfo(queue, completion)` — resolved at runtime
// from the private framework (which itself resolves out of the dyld shared
// cache), so nothing here links against MediaRemote at build time.
typedef void (^MRInfoCompletion)(CFDictionaryRef);
typedef void (*MRGetInfoFn)(dispatch_queue_t, MRInfoCompletion);
typedef void (*MRGetPidFn)(dispatch_queue_t, void (^)(int));

// The daemon answers artwork reliably only once the caller's XPC channel is
// warm; a throwaway PID probe first (same as other adapter implementations)
// keeps the first real query from coming back empty.
static void WarmConnection(MRGetPidFn getPid) {
    if (!getPid) return;
    dispatch_semaphore_t sem = dispatch_semaphore_create(0);
    getPid(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^(int pid) {
        (void)pid;
        dispatch_semaphore_signal(sem);
    });
    dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC));
}

static NSString *StringOrEmpty(id value) {
    return [value isKindOfClass:[NSString class]] ? value : @"";
}

// One synchronous daemon round-trip, 2s cap (nil on timeout). The waiter below
// calls this roughly every 300ms, so a slow answer delays one tick rather than
// the whole fetch.
static NSDictionary *QueryInfo(MRGetInfoFn getInfo) {
    __block NSDictionary *info = nil;
    dispatch_semaphore_t sem = dispatch_semaphore_create(0);
    getInfo(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0),
        ^(CFDictionaryRef d) {
            if (d) info = [(__bridge NSDictionary *)d copy];
            dispatch_semaphore_signal(sem);
        });
    if (dispatch_semaphore_wait(sem,
            dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC)) != 0) {
        return nil;
    }
    return info;
}

void qn_artwork_get(void) {
    @autoreleasepool {
        // `id` mirrors the poll loop's content id (`title|artist|album`) so the
        // Kotlin side can tell whether the daemon was actually on the track it
        // asked about — during a switch the daemon lags behind, and merging
        // that reply would paste the old cover (or settle artlessness) onto
        // the new song. Serialized properly: titles can hold quotes.
        //
        // Browsers publish the cover seconds after the metadata, so a single
        // query usually lands before the bytes exist. Instead of answering at
        // once, the helper lingers (bounded): it adopts the first identified
        // track as its baseline and reports the moment art appears for it, or
        // the moment the daemon moves on to another track — whichever comes
        // first. The Kotlin side then either merges immediately or retries on
        // its short cadence, instead of waiting out a fixed retry quantum.
        NSString *trackId = nil;
        NSData *art = nil;
        CFBundleRef bundle = CFBundleCreate(kCFAllocatorDefault,
            (__bridge CFURLRef)[NSURL fileURLWithPath:
                @"/System/Library/PrivateFrameworks/MediaRemote.framework"]);
        if (bundle) {
            MRGetInfoFn getInfo = (MRGetInfoFn)CFBundleGetFunctionPointerForName(
                bundle, CFSTR("MRMediaRemoteGetNowPlayingInfo"));
            MRGetPidFn getPid = (MRGetPidFn)CFBundleGetFunctionPointerForName(
                bundle, CFSTR("MRMediaRemoteGetNowPlayingApplicationPID"));
            if (getInfo) {
                WarmConnection(getPid);
                NSTimeInterval deadline =
                    [NSDate timeIntervalSinceReferenceDate] + 7.0;
                NSString *baseline = nil;
                for (;;) {
                    trackId = nil;
                    art = nil;
                    NSDictionary *info = QueryInfo(getInfo);
                    if (info) {
                        NSString *title = StringOrEmpty(
                            info[@"kMRMediaRemoteNowPlayingInfoTitle"]);
                        if (title.length) {
                            trackId = [@[
                                title,
                                StringOrEmpty(info[@"kMRMediaRemoteNowPlayingInfoArtist"]),
                                StringOrEmpty(info[@"kMRMediaRemoteNowPlayingInfoAlbum"])
                            ] componentsJoinedByString:@"|"];
                            id artData = info[@"kMRMediaRemoteNowPlayingInfoArtworkData"];
                            if ([artData isKindOfClass:[NSData class]] &&
                                [(NSData *)artData length] > 16) {
                                art = artData;
                            }
                        }
                    }
                    if (!baseline && trackId) baseline = trackId;
                    BOOL moved = baseline && trackId &&
                        ![trackId isEqualToString:baseline];
                    if (art || moved) break;
                    if ([NSDate timeIntervalSinceReferenceDate] >= deadline) break;
                    [NSThread sleepForTimeInterval:0.3];
                }
            }
            CFRelease(bundle);
        }
        NSMutableDictionary *payload = [NSMutableDictionary dictionary];
        payload[@"id"] = trackId ? trackId : [NSNull null];
        if (art) {
            NSString *b64 = [art base64EncodedStringWithOptions:0];
            payload[@"art"] = b64.length ? b64 : [NSNull null];
        } else {
            payload[@"art"] = [NSNull null];
        }
        NSData *json = [NSJSONSerialization dataWithJSONObject:payload
                                                       options:0
                                                         error:nil];
        NSString *line = json ?
            [[NSString alloc] initWithData:json encoding:NSUTF8StringEncoding] :
            @"{\"id\":null,\"art\":null}";
        printf("%s\n", line.UTF8String);
        fflush(stdout);
    }
}
