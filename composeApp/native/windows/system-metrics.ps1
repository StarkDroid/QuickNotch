#
#  system-metrics.ps1
#
#  One poll of the machine's health for the notch's System page. Two modes:
#
#    -Static  -> the facts that never change while the app runs:
#                {"ramTotalBytes":17179869184,"gpuName":"NVIDIA GeForce RTX 3060"}
#
#    (none)   -> one PDH sample: CPU, memory available, GPU engine utilization,
#                network throughput and boot-volume fill:
#                {"cpuPercent":12,"ramAvailableBytes":6871947673,
#                 "gpuPercent":7,"netInBps":48213,"netOutBps":11042,
#                 "diskUsedPercent":61}
#
#  Get-Counter is the right tool here: CPU and the network rates are PDH
#  delta counters, so they need two samples ~1 s apart, and Get-Counter takes
#  both inside one spawn while it sleeps. Fields the machine cannot report
#  (GPU counters on a machine without WDDM engine instances, a failed query)
#  come back null; the app then keeps the previous reading rather than
#  inventing a zero.
#
#  GPU utilization is the 3D engine's Utilization Percentage summed across
#  engine instances and clamped to 100 — the same approximation Task Manager
#  presents as "GPU". Loopback/ISATAP/Teredo pseudo-interfaces and Hyper-V
#  vEthernet adapters are excluded from the network sum: the first never leave
#  the machine, the second mirror traffic the physical adapter already counts.
#

param([switch]$Static)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if ($Static) {
    $os = Get-CimInstance Win32_ComputerSystem -ErrorAction SilentlyContinue
    $gpu = Get-CimInstance Win32_VideoController -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -and ($_.Name -notmatch 'Microsoft (Basic|Remote)') } |
        Select-Object -First 1
    [ordered]@{
        ramTotalBytes = if ($os) { [long]$os.TotalPhysicalMemory } else { 0 }
        gpuName       = if ($gpu) { [string]$gpu.Name } else { $null }
    } | ConvertTo-Json -Compress
    exit 0
}

$baseCounters = @(
    '\Processor(_Total)\% Processor Time',
    '\Memory\Available MBytes',
    '\Network Interface(*)\Bytes Received/sec',
    '\Network Interface(*)\Bytes Sent/sec'
)
$gpuCounter = '\GPU Engine(*)\Utilization Percentage'

# GPU engine counters only exist on WDDM 2.0+ machines. Ask for everything at
# once; if the batch is rejected, fall back to the four counters every Windows
# 10 install has, so a machine without the GPU provider still gets a page.
$samples = $null
try {
    $samples = Get-Counter -Counter ($baseCounters + $gpuCounter) -SampleInterval 1 -MaxSamples 1 -ErrorAction Stop
} catch {
    $samples = $null
}
if ($null -eq $samples) {
    try {
        $samples = Get-Counter -Counter $baseCounters -SampleInterval 1 -MaxSamples 1 -ErrorAction Stop
    } catch {
        '{}'
        exit 0
    }
}

$all = @($samples.CounterSamples)

# Sums every sample whose counter path matches $pathLike, optionally keeping
# only instance names matching $include and dropping ones matching $exclude.
function Get-SampleSum([string]$pathLike, [string]$include, [string]$exclude) {
    $sum = 0.0
    foreach ($sample in $all) {
        if ($sample.Path.ToLowerInvariant() -notlike $pathLike) { continue }
        $name = [string]$sample.InstanceName
        if ($include -and ($name -notmatch $include)) { continue }
        if ($exclude -and ($name -match $exclude)) { continue }
        $sum += [double]$sample.CookedValue
    }
    return $sum
}

$pseudo = 'isatap|loopback|teredo|pseudo|vethernet'
$cpu = [math]::Round([math]::Min(100.0, [math]::Max(0.0, (Get-SampleSum '*\processor(_total)\% processor time' '' ''))))
$ramAvailable = [long]((Get-SampleSum '*\memory\available mbytes' '' '') * 1MB)

$gpu = $null
$gpuSamples = @($all | Where-Object { $_.Path.ToLowerInvariant() -like '*\gpu engine(*' })
if ($gpuSamples.Count -gt 0) {
    $gpuRaw = Get-SampleSum '*\gpu engine(*)\utilization percentage' 'engtype_3d' ''
    $gpu = [math]::Round([math]::Min(100.0, [math]::Max(0.0, $gpuRaw)))
}

$diskUsed = $null
try {
    $drive = [System.IO.DriveInfo]::new([System.IO.Path]::GetPathRoot($env:SystemRoot))
    if ($drive.TotalSize -gt 0) {
        $used = ($drive.TotalSize - $drive.AvailableFreeSpace) * 100.0 / $drive.TotalSize
        $diskUsed = [math]::Round($used)
    }
} catch {
    $diskUsed = $null
}

[ordered]@{
    cpuPercent        = [int]$cpu
    ramAvailableBytes = $ramAvailable
    gpuPercent        = if ($null -ne $gpu) { [int]$gpu } else { $null }
    netInBps          = [long](Get-SampleSum '*\network interface(*)\bytes received/sec' '' $pseudo)
    netOutBps         = [long](Get-SampleSum '*\network interface(*)\bytes sent/sec' '' $pseudo)
    diskUsedPercent   = if ($null -ne $diskUsed) { [int]$diskUsed } else { $null }
} | ConvertTo-Json -Compress
exit 0
