//
//  nowplaying_bridge.m
//
//  macOS now-playing helper. Since macOS 15.4 the MediaRemote daemon only
//  answers to processes whose bundle identifier starts with "com.apple."
//  (e.g. /usr/bin/osascript == com.apple.osascript, /usr/bin/perl ==
//  com.apple.perl5); a plain third-party binary gets nil for every query.
//
//  So this helper does NOT call MediaRemote directly. It supervises the
//  private-class API (MRNowPlayingRequest / MRNowPlayingController) from an
//  `osascript -l JavaScript` subprocess which runs in the entitled context,
//  parses the JSON it prints, and re-emits one canonical snapshot per change
//  on stdout — the same shape as `smtc.ps1` on Windows:
//
//    {"state":"playing|paused|stopped|none","src":"…","title":"…",
//     "artist":"…","album":"…","pos":12345,"dur":234567,"art":"…","id":"…"}
//
//  `art` is emitted from `kMRMediaRemoteNowPlayingInfoArtworkData` when the
//  entitled context provides it; most sources don't, so when the playing client
//  is the system Music app the script also pulls the cover via Music's own
//  AppleScript interface. Other sources fall back to the placeholder vinyl.
//
//  Usage:
//    nowplaying-bridge            — poll every 2s, stream until killed
//    nowplaying-bridge --cmd X    — send a transport command and exit
//                                   X in { toggleplaypause, nexttrack,
//                                   previoustrack }
//

#import <Foundation/Foundation.h>

// ── The JXA payload that runs inside the entitled osascript process ────────
// argv[0]: "get" → print one JSON snapshot line; or "cmd <id>" → MRController
// command. Rate key: 0 = paused, 1 = playing. Position prefers the live
// calculatedPlaybackPosition over the (often stale, esp. in browsers)
// ElapsedTime snapshot.
static NSString *const kJxaScript = @
    "ObjC.import('Foundation');\n"
    "function run(argv) {\n"
    "  var b = $.NSBundle.bundleWithPath('/System/Library/PrivateFrameworks/MediaRemote.framework'); b.load;\n"
    "  var req = $.NSClassFromString('MRNowPlayingRequest');\n"
    "  var item = req.localNowPlayingItem;\n"
    "  var path = req.localNowPlayingPlayerPath;\n"
    "  var client = path ? path.client : null;\n"
    "  if (argv[0] === 'cmd') {\n"
    "    var ctl = $.NSClassFromString('MRNowPlayingController').localRouteController;\n"
    "    ctl.sendCommandOptionsCompletion(Number(argv[1]), $.NSDictionary.alloc.init, null);\n"
    "    return JSON.stringify({ ok: true });\n"
    "  }\n"
    "  var info = item ? item.nowPlayingInfo : null;\n"
    "  if (!info || !info.valueForKey('kMRMediaRemoteNowPlayingInfoTitle')) {\n"
    "    return JSON.stringify({ state: 'none' });\n"
    "  }\n"
    "  var g = function(k, n) {\n"
    "    try { var v = info.valueForKey(k); return v ? Number(v.js) : null; } catch (e) { return null; }\n"
    "  };\n"
    "  var rate = g('kMRMediaRemoteNowPlayingInfoPlaybackRate', 1);\n"
    "  var pos = null;\n"
    "  try { pos = Number(item.metadata.calculatedPlaybackPosition); } catch (e) {}\n"
    "  if (pos === null || isNaN(pos)) pos = g('kMRMediaRemoteNowPlayingInfoElapsedTime', 1);\n"
    "  if (pos === null || isNaN(pos)) pos = 0;\n"
    "  var dur = g('kMRMediaRemoteNowPlayingInfoDuration', 1); if (dur === null) dur = 0;\n"
    "  var title = '' + info.valueForKey('kMRMediaRemoteNowPlayingInfoTitle').js;\n"
    "  var artistV = info.valueForKey('kMRMediaRemoteNowPlayingInfoArtist');\n"
    "  var albumV = info.valueForKey('kMRMediaRemoteNowPlayingInfoAlbum');\n"
    "  var name = client ? ('' + client.displayName.js) : '';\n"
    "  var nameId = null;\n"
    "  try { var bId = client.bundleIdentifier; nameId = bId ? ('' + bId.js) : null; } catch (e) {}\n"
    "  var art = null;\n"
    "  try {\n"
    "    var artData = info.valueForKey('kMRMediaRemoteNowPlayingInfoArtworkData');\n"
    "    if (artData && artData.length && artData.length > 16)\n"
    "      art = artData.base64EncodedStringWithOptions(0).js;\n"
    "  } catch (e) {}\n"
    "  return JSON.stringify({\n"
    "    state: (rate > 0 ? 'playing' : 'paused'),\n"
    "    src: name,\n"
    "    srcId: nameId,\n"
    "    title: title,\n"
    "    artist: artistV ? ('' + artistV.js) : '',\n"
    "    album: albumV ? ('' + albumV.js) : '',\n"
    "    pos: Math.round(pos * 1000),\n"
    "    dur: Math.round(dur * 1000),\n"
    "    art: art,\n"
    "    id: title + '|' + (artistV ? artistV.js : '') + '|' + (albumV ? albumV.js : '')\n"
    "  });\n"
    "}\n";

// ── MRCommand ids exposed to MRNowPlayingController (per the community
//    MediaRemote headers; verified working: 4 = next, 2 = toggle) ────────────
enum {
    MRCommandPlay = 0,
    MRCommandPause = 1,
    MRCommandTogglePlayPause = 2,
    MRCommandStop = 3,
    MRCommandNextTrack = 4,
    MRCommandPreviousTrack = 5
};

static NSString *JxaPath(void) {
    NSString *dir = [NSTemporaryDirectory()
        stringByAppendingPathComponent:@"quicknotch-bridge"];
    [[NSFileManager defaultManager] createDirectoryAtPath:dir
                              withIntermediateDirectories:YES
                                               attributes:nil
                                                    error:nil];
    NSString *path = [dir stringByAppendingPathComponent:@"nowplaying.jxa"];
    NSString *existing = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
    if (existing == nil || ![existing isEqualToString:kJxaScript]) {
        [kJxaScript writeToFile:path atomically:YES encoding:NSUTF8StringEncoding error:nil];
    }
    return path;
}

// Serial background queue that serialises every completion callback, so
// SendCommand (blocking on the main thread) and Poll (on the main runloop)
// can never deadlock each other.
static dispatch_queue_t getEmitQueue(void) {
    static dispatch_queue_t q;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        q = dispatch_queue_create("quicknotch.emit", NULL);
    });
    return q;
}

static NSString *lastEmittedKey;

// ── Album art for the Music app ──────────────────────────────────────────────
// MediaRemote rarely exposes artwork bytes through MRNowPlayingRequest, but the
// system Music app hands out the cover through its own scripting interface.
// This dumps `artwork 1 of current track` to a temp file from a plain
// AppleScript process (no MediaRemote at all), and the result is cached per
// content id so a track change is the only thing that refetches it.
static NSString *const kJxaArtScript =
    @"tell application \"Music\" to set artData to data of artwork 1 of current track\n"
    @"set f to open for access (POSIX file \"/tmp/quicknotch-art.bin\") with write permission\n"
    @"write artData to f\n"
    @"close access f\n";

static void ArtFetched(void);
static void Poll(void);
static volatile BOOL artFetchInFlight;
static NSString *artContentId;
static NSString *artCacheB64;

/** The `"id":"…"` value of a snapshot line (so art caches per track). */
static NSString *ContentIdFromLine(NSString *line) {
    NSRange r = [line rangeOfString:@"\"id\":\""];
    if (r.location == NSNotFound) return @"";
    NSUInteger start = r.location + r.length;
    NSUInteger i = start;
    while (i < line.length) {
        unichar c = [line characterAtIndex:i];
        if (c == '"' && !(i > start && [line characterAtIndex:i - 1] == '\\')) break;
        i++;
    }
    if (i >= line.length || i <= start) return @"";
    return [line substringWithRange:NSMakeRange(start, i - start)];
}

/** Runs one plain AppleScript, bounded by a 4s deadline, returns base64 art. */
static NSString *RunMusicArtScript(void) {
    NSTask *task = [[NSTask alloc] init];
    task.executableURL = [NSURL fileURLWithPath:@"/usr/bin/osascript"];
    task.arguments = @[ @"-e", kJxaArtScript ];
    NSPipe *devNull = [NSPipe pipe];
    task.standardOutput = devNull;
    task.standardError = devNull;
    NSError *err = nil;
    if (![task launchAndReturnError:&err]) return nil;
    dispatch_queue_t q = dispatch_queue_create("quicknotch.artd", NULL);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 4 * NSEC_PER_SEC), q, ^{
        if (task.isRunning) [task terminate];
    });
    [task waitUntilExit];
    if (task.terminationStatus != 0) return nil;
    NSData *data = [NSData dataWithContentsOfFile:@"/tmp/quicknotch-art.bin"];
    if (data.length < 16) return nil;
    return [data base64EncodedStringWithOptions:0];
}

/**
 * Fills in missing artwork on the fly. Only refetches when the content id
 * moves; a negative result is also cached so non-Music sources never spin up
 * an osascript per poll.
 */
static void AttachMusicArt(NSString **line) {
    if (![*line containsString:@"\"art\":null"]) return;
    NSString *idKey = ContentIdFromLine(*line);
    if (!idKey.length) return;
    if ([idKey isEqualToString:artContentId]) {
        if (artCacheB64) {
            *line = [*line stringByReplacingOccurrencesOfString:@"\"art\":null"
                                                     withString:[NSString stringWithFormat:@"\"art\":\"%@\"", artCacheB64]];
        }
        return;
    }
    // A fetch is already running for an older track: let it finish (it will see
    // the id moved and drop its result) rather than stacking a second
    // osascript on top of it.
    if (artFetchInFlight) return;
    // Kick the fetch and emit without art. The fetch is an osascript call into
    // Music that can take hundreds of milliseconds, and it used to run inline
    // here - i.e. on the poll callback - so every track change stalled the
    // whole metadata feed (title, state, position) behind an AppleScript, and
    // that AppleScript competed with every other app on the machine exactly
    // when a track started. Detaching it costs nothing: the poll emits now, and
    // ArtFetched() re-emits the moment the art lands.
    artContentId = idKey;
    artCacheB64 = nil;
    artFetchInFlight = YES;
    NSString *requestedId = idKey;
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_UTILITY, 0), ^{
        NSString *art = RunMusicArtScript();
        if ([requestedId isEqualToString:artContentId]) {
            artCacheB64 = art;
        }
        artFetchInFlight = NO;
        ArtFetched();
    });
}

/** Called once a detached art fetch completed (whatever its outcome). */
static void ArtFetched(void) {
    if (artCacheB64) {
        // Force an immediate re-emit so the art appears without waiting up to 2s
        // for the heartbeat to notice the changed line.
        lastEmittedKey = nil;
    }
    // Always poll: if the track changed while this fetch was running, the new id
    // is waiting for its own fetch and should not wait a whole poll cycle for it.
    Poll();
}

// Runs `osascript -l JavaScript <jxa> <args…>`, captures stdout, and calls
// the block on the serial emit queue (nil on failure). Enforces a 5s
// wall-clock deadline so a wedged osascript cannot stall the feed.
static void RunOsa(NSArray<NSString *> *args, void (^done)(NSString *)) {
    NSTask *task = [[NSTask alloc] init];
    task.executableURL = [NSURL fileURLWithPath:@"/usr/bin/osascript"];
    task.arguments = [@[@"-l", @"JavaScript", JxaPath()] arrayByAddingObjectsFromArray:args];
    NSPipe *pipe = [NSPipe pipe];
    task.standardOutput = pipe;
    task.standardError = [NSPipe pipe];

    NSMutableData *out = [NSMutableData data];
    dispatch_queue_t q = dispatch_queue_create("quicknotch.osa", NULL);
    __block BOOL finished = NO;
    [pipe.fileHandleForReading setReadabilityHandler:^(NSFileHandle *fh) {
        NSData *d = [fh availableData];
        if (d.length) {
            dispatch_async(q, ^{ [out appendData:d]; });
        } else {
            [fh setReadabilityHandler:nil];
        }
    }];

    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        NSError *err = nil;
        BOOL started = [task launchAndReturnError:&err];
        if (!started) {
            dispatch_async(getEmitQueue(), ^{ done(nil); });
            return;
        }
        // wall-clock deadline
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 5 * NSEC_PER_SEC),
                       dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
            if (task.isRunning && !finished) [task terminate];
        });
        [task waitUntilExit];
        [pipe.fileHandleForReading setReadabilityHandler:nil];
        dispatch_async(q, ^{
            NSString *text = [[NSString alloc] initWithData:out encoding:NSUTF8StringEncoding];
            finished = YES;
            dispatch_async(getEmitQueue(), ^{ done(text); });
        });
    });
}

static void Emit(NSString *line) {
    printf("%s\n", line.UTF8String);
    fflush(stdout);
}

static volatile BOOL pollInFlight;

static void Poll(void) {
    if (pollInFlight) return;
    pollInFlight = YES;
    RunOsa(@[ @"get" ], ^(NSString *output) {
        pollInFlight = NO;
        if (!output) return;
        NSString *line = nil;
        for (NSString *part in [output componentsSeparatedByString:@"\n"]) {
            NSString *t = [part stringByTrimmingCharactersInSet:
                           [NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (t.length && [t characterAtIndex:0] == '{') { line = t; break; }
        }
        if (!line) return;
        AttachMusicArt(&line);
        // De-dup: re-emit only when the snapshot actually changed (position is
        // interpolated client-side, so purely temporal moves are skipped).
        if ([line isEqualToString:lastEmittedKey]) return;
        lastEmittedKey = line;
        Emit(line);
    });
}

static void HeartbeatTimer(void) {
    NSTimer *timer = [NSTimer timerWithTimeInterval:2.0 repeats:YES block:^(NSTimer *t) {
        (void)t;
        Poll();
    }];
    [[NSRunLoop mainRunLoop] addTimer:timer forMode:NSRunLoopCommonModes];
}

static int SendCommand(NSString *name) {
    NSInteger id_ = NSNotFound;
    if ([name isEqualToString:@"toggleplaypause"]) id_ = MRCommandTogglePlayPause;
    else if ([name isEqualToString:@"nexttrack"]) id_ = MRCommandNextTrack;
    else if ([name isEqualToString:@"previoustrack"]) id_ = MRCommandPreviousTrack;
    if (id_ == NSNotFound) {
        fprintf(stderr, "nowplaying-bridge: unknown command %s\n", name.UTF8String);
        return 2;
    }
    // Run synchronously so the caller sees the outcome; the osascript call
    // itself is fast (seen ~100ms).
    __block BOOL ok = NO;
    dispatch_semaphore_t sem = dispatch_semaphore_create(0);
    RunOsa(@[ @"cmd", [NSString stringWithFormat:@"%ld", (long)id_] ], ^(NSString *output) {
        ok = output != nil && [output containsString:@"\"ok\""];
        dispatch_semaphore_signal(sem);
    });
    dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, 8 * NSEC_PER_SEC));
    return ok ? 0 : 1;
}

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        if (argc >= 3 && strcmp(argv[1], "--cmd") == 0) {
            return SendCommand([NSString stringWithUTF8String:argv[2]]);
        }

        signal(SIGPIPE, SIG_IGN);
        JxaPath(); // materialise the script before the runloop starts
        Poll();
        HeartbeatTimer();
        [[NSRunLoop mainRunLoop] run];
    }
    return 0;
}