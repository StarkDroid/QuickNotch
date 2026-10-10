#
#  smtc.ps1
#
#  Bridges Windows' System Media Transport Controls (SMTC) to QuickNotch as
#  a single JSON line — the same surface the volume flyout, Win+G and the
#  start-menu media flyout use, so it covers any source app (Spotify, Music,
#  browsers like Chrome/Edge/YouTube Music, foobar2000, …) with no
#  special-casing.
#
#  WinRT types are reached through Windows PowerShell's type activation
#  (ContentType = WindowsRuntime). Two projection quirks matter here:
#
#    * Async WinRT calls return a raw async-operation object, never the
#      finished result, so every async call is bridged through
#      System.WindowsRuntimeSystemExtensions.AsTask<T> and Task.Result
#      (see Await-Async). Note that reflecting on WinRT COM objects inside
#      this process destabilizes that bridge, so the script never calls
#      .GetType() on session objects — member detection is done by probing
#      the calls themselves inside try/catch.
#
#    * The projected session surface renames several members compared to the
#      raw WinRT metadata:
#          GetMediaProperties()      -> TryGetMediaPropertiesAsync()
#          GetPlaybackStatus()       -> GetPlaybackInfo().PlaybackStatus
#          (there is NO session-level thumbnail call at all — artwork is
#          carried only by mediaProperties.Thumbnail)
#      The script probes which names exist and uses those, so it keeps
#      working across minor projection differences.
#
#  This runs under Windows PowerShell 5.1 (the `powershell` launcher the app
#  uses). PowerShell 7 dropped the WinRT projection, so an explicit `pwsh`
#  invocation of this file is not supported.
#
#  Usage:
#    powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File smtc.ps1
#    powershell ... -File smtc.ps1 toggle | next | previous | "seek <millis>"
#    powershell ... -File smtc.ps1 status <lastId> <haveArt>
#
#  The `status` poll accepts what the app already holds: <lastId> is the track
#  id of its current session and <haveArt> is '1' when it already decoded art
#  for that id and '2' when a fetch for that id already came back empty. The
#  thumbnail (a COM fetch plus a few hundred KB of base64 through stdout) is
#  then skipped for the steady state and only re-sent on a track change or
#  while the app still wants to try — the Kotlin side caches per id, so
#  omitting it loses nothing. Both args arrive as separate argv elements
#  (ProcessBuilder), so ids containing spaces survive intact.
#
#  Output contract (identical to the macOS nowplaying-bridge):
#    {"state":"playing|paused|stopped|none","src":"","srcId":"<aumid>",
#     "title":"…","artist":"…","album":"…","pos":12345,"dur":234567,
#     "art":"<base64>","id":"<global track id>"}
#
#  Track identity: most browsers publish no GlobalId at all. An empty id
#  would collapse every track into one cache key on the Kotlin side (the
#  first cover would stick forever) and defeat the art gate here, so when
#  GlobalId is blank a stable id is derived from the source app + track
#  fields (SHA-1, stable across spawns).
#

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$null = Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]

# Pre-bound AsTask bridge (single IAsyncOperation<TResult> overload, bound once
# up front so the invocation shape stays stable across the script's lifetime).
$asTaskGeneric = (
    [System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    }
)[0]

# Pre-bound AsStreamForRead (the single-stream overload). Calling it as a
# static method never resolves its IInputStream parameter under
# PowerShell 5.1: the async result is a System.__ComObject whose
# GetInterfaces() is empty, so PowerShell's structural conversion reports
# "Cannot find an overload" even though the RCW answers the QI (IsInstance
# is true). MethodInfo.Invoke performs a real CLR cast instead, which is
# the QI that succeeds — verified byte-for-byte round-trip.
$asStreamForRead = (
    [System.IO.WindowsRuntimeStreamExtensions].GetMethods() |
    Where-Object {
        $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1
    }
)[0]

function Await-Async {
    param(
        [Parameter(Mandatory)] [object] $Operation,
        [Parameter(Mandatory)] [type] $ResultType
    )
    if ($null -eq $Operation) { return $null }
    $task = $asTaskGeneric.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

function Get-Base64 {
    param([Parameter(Mandatory)] [object] $Reference)
    if ($null -eq $Reference) { return $null }
    try {
        $stream = Await-Async ($Reference.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        if ($null -eq $stream) { return $null }
        # Several sources (e.g. some browsers) also publish degenerate 0–1
        # byte stub streams — those read as "no art", not as undecodable
        # blobs the app would then retry every second.
        $netStream = $asStreamForRead.Invoke($null, @($stream))
        $buffer = New-Object System.IO.MemoryStream
        try {
            $netStream.CopyTo($buffer)
        } finally {
            $netStream.Dispose()
        }
        $bytes = $buffer.ToArray()
        $buffer.Dispose()
        if ($bytes.Length -lt 4) { return $null }
        return [Convert]::ToBase64String($bytes)
    } catch {
        return $null
    }
}

$command = $args[0]
if ([string]::IsNullOrWhiteSpace($command)) { $command = 'status' }
$lastId = $args[1]
$lastHaveArt = $args[2]

$manager = $null
for ($attempt = 0; $attempt -lt 3 -and $null -eq $manager; $attempt++) {
    try {
        $manager = Await-Async `
            ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
            ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    } catch {
        if ($attempt -lt 2) { Start-Sleep -Milliseconds 200 }
    }
}

if ($null -eq $manager) {
    '{"state":"none"}'
    exit 0
}

switch -Wildcard ($command) {
    'toggle' {
        $manager.GetCurrentSession().TryTogglePlayPauseAsync() | Out-Null
        exit 0
    }
    'next' {
        $manager.GetCurrentSession().TrySkipNextAsync() | Out-Null
        exit 0
    }
    'previous' {
        $manager.GetCurrentSession().TrySkipPreviousAsync() | Out-Null
        exit 0
    }
    'seek *' {
        $position = [TimeSpan]::FromMilliseconds([int64]($command.Substring(5)))
        $manager.GetCurrentSession().TryChangePlaybackPositionAsync($position) | Out-Null
        exit 0
    }
}

$session = $manager.GetCurrentSession()
if ($null -eq $session) {
    '{"state":"none"}'
    exit 0
}

$properties = $null
$timeline = $null
$status = $null
$art = $null

# Media properties arrive through an async operation; the Windows PowerShell
# projection exposes it as TryGetMediaPropertiesAsync(). Probe for it; fall
# back to the classic GetMediaProperties() spelling on other projections.
try {
    $properties = Await-Async `
        ($session.TryGetMediaPropertiesAsync()) `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
} catch { }
if ($null -eq $properties) {
    try { $properties = $session.GetMediaProperties() } catch { }
}

try { $timeline = $session.GetTimelineProperties() } catch { }

# Playback status lives on the playback-info object in this projection.
try { $status = $session.GetPlaybackInfo().PlaybackStatus.ToString() } catch { }
if ($null -eq $status) {
    try { $status = $session.GetPlaybackStatus().ToString() } catch { }
}

$aumid = ''
try { $aumid = [string]$session.SourceAppUserModelId } catch { }

$title = ''
$artist = ''
$album = ''
$id = ''
if ($null -ne $properties) {
    $title = [string]$properties.Title
    $artist = [string]$properties.Artist
    $album = [string]$properties.AlbumTitle
    $id = [string]$properties.GlobalId
}

# Browsers (Chrome/Edge/YouTube Music and other Chromium media sites) publish
# no GlobalId. Without a stable per-track id the app would cache art under ""
# and keep the very first cover forever, and the gate below could never tell
# one track from the next — derive one from the source app + track fields.
if ([string]::IsNullOrWhiteSpace($id) -and -not [string]::IsNullOrWhiteSpace($title)) {
    $raw = $aumid + '|' + $title + '|' + $artist + '|' + $album
    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    try {
        $hash = $sha1.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($raw))
        $id = ([System.BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant().Substring(0, 24)
    } finally {
        $sha1.Dispose()
    }
}

# Album art is carried by the media properties in this projection — but only
# fetched while the app still needs it (new track, or a decode that has not
# landed yet). Steady-state polls for an already-illustrated track — and for
# a track whose fetch already came back empty ('2') — skip the COM round-trip
# and the base64 transfer entirely.
$needArt = $true
if (-not [string]::IsNullOrWhiteSpace($id) -and $id -ceq $lastId -and $lastHaveArt -ne '0') {
    $needArt = $false
}
if ($needArt -and $null -ne $properties) {
    try { $art = Get-Base64 $properties.Thumbnail } catch { $art = $null }
}

$positionMs = 0
$durationMs = 0
if ($null -ne $timeline) {
    $positionMs = [int64]$timeline.Position.TotalMilliseconds
    $durationMs = [int64]$timeline.EndTime.TotalMilliseconds
}

if ([string]::IsNullOrWhiteSpace($title)) {
    '{"state":"none"}'
    exit 0
}

switch ([string]$status) {
    'Playing' { $state = 'playing' }
    'Paused' { $state = 'paused' }
    default { $state = 'stopped' }
}

$payload = [ordered]@{
    state  = $state
    src    = ''
    srcId  = $aumid
    title  = $title
    artist = $artist
    album  = $album
    pos    = $positionMs
    dur    = $durationMs
    id     = $id
}
if (-not [string]::IsNullOrWhiteSpace($art)) { $payload.art = $art }

$payload | ConvertTo-Json -Compress
exit 0