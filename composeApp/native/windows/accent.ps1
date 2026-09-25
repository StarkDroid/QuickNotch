# Reads the Windows accent colour used by the DWM (Almost Aero glass accents /
# Windows 11 personalised title bars) and prints it as "RRGGBB" on stdout, or
# "ERROR" when the registry value is unavailable (e.g. accents disabled).
# The DWM stores AccentColor as an ABGR DWORD: the low byte is red, then green,
# then blue, with alpha in the high byte — parse accordingly.
$path = 'HKCU:\Software\Microsoft\Windows\DWM'
try {
    $abgr = [int](Get-ItemProperty -Path $path -Name AccentColor -ErrorAction Stop).AccentColor
    $r = $abgr -band 0xFF
    $g = ($abgr -shr 8) -band 0xFF
    $b = ($abgr -shr 16) -band 0xFF
    '{0:X2}{1:X2}{2:X2}' -f $r, $g, $b
} catch {
    Write-Output 'ERROR'
}