#
#  battery.ps1 [-InstanceId <instance-id>]
#
#  With no argument (the 1-second poll) the script answers one question: is a
#  Bluetooth audio device connected *right now*, and which PnP instance carries
#  it. Connectivity comes from the OS's live connection state
#  (Windows.Devices.Enumeration -> BluetoothDevice.ConnectionStatus), so a
#  connect or disconnect reflects on the very next poll — cached battery
#  values the OS may still hand out after a device unplugs never confuse it,
#  and nothing here ever reads the slow battery property.
#
#    {"present":true,"instance":"BTHENUM\\...","name":"...","address":"840F2A94240A"}
#    {"present":false}
#
#  With -InstanceId (the slow ~30-second refresh) it reads that device's
#  battery level under DEVPKEY_Device_BatteryLevel
#  ({104EA319-6EE2-4701-BD47-8DDBF425BBE5}, 2), which the OS may answer by
#  polling the headset over the air (can take many seconds):
#
#    {"name":"...","level":87}    {"level":null}    {"present":false}
#

param([string]$InstanceId = '')

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$BatteryKey = '{104EA319-6EE2-4701-BD47-8DDBF425BBE5},2'

Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction SilentlyContinue | Out-Null

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Invoke-Async([object]$IAsyncOperation, [Type]$ResultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($IAsyncOperation))
    $netTask.Wait(-1) | Out-Null
    return $netTask.Result
}

# MAC-address (uppercase, no separators) -> device name, for every device the
# OS reports as currently connected. Empty on any failure (callers treat that
# as "nothing connected").
function Get-ConnectedBluetooth() {
    $map = @{}
    try {
        [void][Windows.Devices.Bluetooth.BluetoothDevice, Windows.Devices, ContentType = WindowsRuntime]
        [void][Windows.Devices.Enumeration.DeviceInformation, Windows.Devices.Enumeration, ContentType = WindowsRuntime]
        [void][Windows.Devices.Enumeration.DeviceInformationCollection, Windows.Devices.Enumeration, ContentType = WindowsRuntime]

        $selector = [Windows.Devices.Bluetooth.BluetoothDevice]::GetDeviceSelector()
        $collection = Invoke-Async ([Windows.Devices.Enumeration.DeviceInformation]::FindAllAsync($selector)) ([Windows.Devices.Enumeration.DeviceInformationCollection])
        foreach ($di in $collection) {
            try {
                $bd = Invoke-Async ([Windows.Devices.Bluetooth.BluetoothDevice]::FromIdAsync($di.Id)) ([Windows.Devices.Bluetooth.BluetoothDevice])
                if ($null -ne $bd -and $bd.ConnectionStatus.ToString() -eq 'Connected') {
                    $mac = '{0:X12}' -f $bd.BluetoothAddress
                    $map[$mac] = $di.Name
                }
            } catch { }
        }
    } catch { }
    return $map
}

function Get-DeviceName($device) {
    if ([string]::IsNullOrWhiteSpace($device.FriendlyName)) { '' } else { $device.FriendlyName }
}

# The 12-hex Bluetooth address embedded in a PnP instance id, or '' if none.
function Get-DeviceAddress([string]$InstanceId) {
    $m = [regex]::Match($InstanceId, 'DEV_([0-9A-Fa-f]{12})|&([0-9A-Fa-f]{12})_')
    if (-not $m.Success) { return '' }
    if ($m.Groups[1].Value.Length -gt 0) { return $m.Groups[1].Value.ToUpper() }
    return $m.Groups[2].Value.ToUpper()
}

function Get-DeviceBatteryLevel($device) {
    $prop = Get-PnpDeviceProperty -InstanceId $device.InstanceId -KeyName $BatteryKey -ErrorAction SilentlyContinue
    if ($null -eq $prop -or $null -eq $prop.Data) { return $null }
    $level = 0
    if (-not [int]::TryParse([string]$prop.Data, [ref]$level) -or $level -lt 0 -or $level -gt 100) { return $null }
    return $level
}

if ([string]::IsNullOrWhiteSpace($InstanceId)) {
    # ── Mode A: is a Bluetooth audio device connected right now? ─────────────
    $connected = Get-ConnectedBluetooth

    # Nothing connected at all — skip the (slower) device walk entirely so a
    # disconnect is reflected on the very next poll.
    if ($connected.Count -eq 0) {
        '{"present":false}'
        exit 0
    }

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

    # Preference order: hands-free profiles, A2DP sinks, AVRCP transports, then
    # anything else. Pick the first whose address is in the connected set.
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
        [PSCustomObject]@{ Score = $score; Device = $d; Address = (Get-DeviceAddress $id) }
    }
    $ordered = @($scored | Sort-Object -Property Score -Descending)

    foreach ($entry in $ordered) {
        if ([string]::IsNullOrWhiteSpace($entry.Address)) { continue }
        if ($connected.ContainsKey($entry.Address)) {
            $payload = [ordered]@{
                present  = $true
                instance = $entry.Device.InstanceId
                name     = Get-DeviceName $entry.Device
                address  = $entry.Address
            }
            $payload | ConvertTo-Json -Compress
            exit 0
        }
    }

    '{"present":false}'
    exit 0
}
else {
    # ── Mode B: battery level for one specific instance (slow refresh) ───────
    $device = Get-PnpDevice -PresentOnly -InstanceId $InstanceId -ErrorAction SilentlyContinue
    if ($null -eq $device) {
        '{"present":false}'
        exit 0
    }
    $level = Get-DeviceBatteryLevel $device
    $payload = [ordered]@{
        present  = $true
        instance = $device.InstanceId
        name     = Get-DeviceName $device
        level    = $level
    }
    $payload | ConvertTo-Json -Compress
    exit 0
}