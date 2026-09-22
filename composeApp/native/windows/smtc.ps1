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
#          TryGetThumbnailAsync()    -> mediaProperties.Thumbnail
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
#
#  Output contract (identical to the macOS nowplaying-bridge):
#    {"state":"playing|paused|stopped|none","src":"","title":"…",
#     "artist":"…","album":"…","pos":12345,"dur":234567,
#     "art":"<base64>","id":"<global track id>"}
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
        $size = [uint32]$stream.Size
        if ($size -eq 0) { return $null }
        $reader = New-Object -TypeName Windows.Storage.Streams.DataReader -ArgumentList $stream
        $null = Await-Async ($reader.LoadAsync($size)) ([UInt32])
        $buffer = $reader.ReadBuffer($size)
        $bytes = New-Object byte[] $buffer.Length
        [System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions]::CopyTo($buffer, $bytes, 0, $buffer.Length)
        return [Convert]::ToBase64String($bytes)
    } catch {
        return $null
    }
}

$command = $args[0]
if ([string]::IsNullOrWhiteSpace($command)) { $command = 'status' }

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

# Album art is carried by the media properties in this projection.
if ($null -ne $properties) {
    try { $art = Get-Base64 $properties.Thumbnail } catch { $art = $null }
}
if ([string]::IsNullOrWhiteSpace($art)) {
    try { $art = Get-Base64 ($session.TryGetThumbnailAsync()) } catch { $art = $null }
}

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