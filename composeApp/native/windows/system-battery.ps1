#
#  system-battery.ps1
#
#  Reads the machine's own battery (a laptop): level 0-100 and whether the OS
#  is actively charging it. Presence comes from Win32_Battery, so a machine
#  with no battery (a desktop) reports nothing:
#
#    {"present":true,"level":73,"charging":true}
#    {"present":false}
#
#  `charging` is true only while the OS is actively filling the battery
#  (BatteryStatus 6/7/8/9). Plugged in but full (3) or sitting idle on AC (2)
#  shows the level-based glyphs instead, exactly like an unplugged,
#  discharging laptop (1/4/5) does.
#

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$battery = @(Get-CimInstance Win32_Battery -ErrorAction SilentlyContinue)
if ($battery.Count -eq 0) {
    '{"present":false}'
    exit 0
}

$b = $battery | Where-Object { $null -ne $_.EstimatedChargeRemaining } | Select-Object -First 1
if ($null -eq $b) {
    '{"present":false}'
    exit 0
}

$level = [int]$b.EstimatedChargeRemaining
$status = [int]$b.BatteryStatus
$charging = (6, 7, 8, 9) -contains $status

[ordered]@{
    present  = $true
    level    = $level
    charging = $charging
} | ConvertTo-Json -Compress
exit 0