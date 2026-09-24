#
#  battery.ps1 [-InstanceId <instance-id>]
#
#  Reads the battery percentage of the connected Bluetooth audio device:
#
#    {"present":true,"instance":"BTHENUM\\...","name":"...","level":87}
#    {"present":true,"instance":"BTHENUM\\...","name":"...","level":null}
#    {"present":false}
#
#  With no argument (the 1-second poll) the script finds the connected audio
#  device itself: present Bluetooth instances are scored, hands-free & A2DP
#  profiles first, and each is probed in turn until one reports a level. With
#  -InstanceId (the slow 30-second refresh) it probes exactly that device.
#
#  The level lives under DEVPKEY_Device_BatteryLevel
#  ({104EA319-6EE2-4701-BD47-8DDBF425BBE5}, 2). While the device is reachable
#  the OS may answer by polling the headset over the air, which can stall for
#  many seconds; a disconnected device answers immediately with no data. The
#  app exploits that: a probe that times out means the OS is busy talking to a
#  live device (keep showing the cached level), while a fast "no data" answer
#  means the device is gone (hide the chip right away).
#

param([string]$InstanceId = '')

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$BatteryKey = '{104EA319-6EE2-4701-BD47-8DDBF425BBE5},2'

function Get-DeviceBatteryLevel($device) {
    $prop = Get-PnpDeviceProperty -InstanceId $device.InstanceId -KeyName $BatteryKey -ErrorAction SilentlyContinue
    if ($null -eq $prop -or $null -eq $prop.Data) { return $null }
    $level = 0
    if (-not [int]::TryParse([string]$prop.Data, [ref]$level) -or $level -lt 0 -or $level -gt 100) { return $null }
    return $level
}

function Get-DeviceName($device) {
    if ([string]::IsNullOrWhiteSpace($device.FriendlyName)) { '' } else { $device.FriendlyName }
}

function Write-DeviceJson($device, $level) {
    $payload = [ordered]@{
        present  = $true
        instance = $device.InstanceId
        name     = Get-DeviceName $device
        level    = $level
    }
    $payload | ConvertTo-Json -Compress
}

if ([string]::IsNullOrWhiteSpace($InstanceId)) {
    # ── Mode A: find the connected audio device and probe its battery ────────
    $devices = @(Get-PnpDevice -PresentOnly -ErrorAction SilentlyContinue | Where-Object {
        ($_.Class -in @('Bluetooth', 'BluetoothLE')) -or
        ($_.InstanceId -like 'BTHENUM*') -or
        ($_.InstanceId -like 'BTHLE*') -or
        (($_.Class -eq 'Battery') -and ($_.InstanceId -like 'BTH*'))
    })

    if ($devices.Count -eq 0) {
        '{"present":false}'
        exit 0
    }

    # Score candidates: hands-free profiles carry the battery on most headsets,
    # then A2DP sinks, then AVRCP transports, then anything else.
    $scored = foreach ($d in $devices) {
        $id = $d.InstanceId
        $score = 10
        if ($id -like 'BTHENUM\{0000111E-*') { $score = 60 }      # Hands-Free AG
        elseif ($id -like 'BTHENUM\{0000111F-*') { $score = 55 }  # Hands-Free HF
        elseif ($id -like 'BTHENUM\{0000110A-*') { $score = 50 }  # A2DP SNK
        elseif ($id -like 'BTHENUM\{0000110B-*') { $score = 45 }  # A2DP (MEDIA)
        elseif ($id -like 'BTHENUM\{0000110C-*' -or $id -like 'BTHENUM\{0000110D-*' -or $id -like 'BTHENUM\{0000110E-*') { $score = 40 }  # AVRCP transports
        elseif ($id -like 'BTHENUM*') { $score = 30 }
        elseif ($d.Class -in @('MEDIA', 'System')) { $score = 20 }
        [PSCustomObject]@{ Score = $score; Device = $d }
    }
    $ordered = @($scored | Sort-Object -Property Score -Descending | ForEach-Object { $_.Device })

    foreach ($device in $ordered) {
        $level = Get-DeviceBatteryLevel $device
        if ($null -ne $level) {
            Write-DeviceJson $device $level
            exit 0
        }
    }
    Write-DeviceJson $ordered[0] $null
    exit 0
}
else {
    # ── Mode B: probe one specific instance (slow 30-second refresh) ─────────
    $device = Get-PnpDevice -PresentOnly -InstanceId $InstanceId -ErrorAction SilentlyContinue
    if ($null -eq $device) {
        '{"present":false}'
        exit 0
    }
    $level = Get-DeviceBatteryLevel $device
    Write-DeviceJson $device $level
    exit 0
}