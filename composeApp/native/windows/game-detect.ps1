#
#  game-detect.ps1
#
#  Fullscreen-game detector for the gaming quickview. Long-lived by design:
#  it compiles its P/Invoke surface once (Add-Type is a ~1 s csc.exe run in
#  PowerShell 5.1) and then prints one line per tick on stdout:
#
#      1|<process name>|<pid>     a fullscreen game owns the screen
#      0||                       nothing fullscreen right now
#
#  Spawning this per probe was the obvious shape and the wrong one: Add-Type
#  costs more than the probe interval, so a per-tick spawn would burn a fifth
#  of a core for the app's whole life. One process, one compile, one cheap
#  loop instead. The app reads the lines and kills the process on shutdown;
#  as a backstop the loop exits on its own once the parent JVM is gone, so a
#  crashed app can never leave a PowerShell behind.
#
#  Two signals, because borderless and exclusive fullscreen look nothing
#  alike:
#
#    - the foreground window's rect against its monitor's rect (borderless
#      fullscreen, the common modern case), and
#    - SHQueryUserNotificationState == QUNS_RUNNING_D3D_FULL_SCREEN (3),
#      which Windows sets for an exclusive Direct3D swap chain even when the
#      window rect lies.
#
#  Shell surfaces (desktop, taskbar, Start, alt-tab, the Win11 desktop host)
#  and Explorer itself are excluded: the desktop is a layer-0 window covering
#  the screen too, and it must never read as a game.
#

param(
    # Parent JVM pid: the loop exits when it disappears. 0 disables the check
    # (manual runs).
    [int]$ParentPid = 0,
    [int]$IntervalMs = 1500,
    # One probe and exit — for poking at this by hand.
    [switch]$Once
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class QnGameWatch
{
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }

    [StructLayout(LayoutKind.Sequential)]
    public struct MONITORINFO
    {
        public int cbSize;
        public RECT rcMonitor;
        public RECT rcWork;
        public uint dwFlags;
    }

    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);

    [DllImport("user32.dll")]
    public static extern IntPtr MonitorFromWindow(IntPtr hWnd, uint flags);

    [DllImport("user32.dll")]
    public static extern bool GetMonitorInfo(IntPtr hMonitor, ref MONITORINFO info);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetClassName(IntPtr hWnd, StringBuilder name, int count);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);

    [DllImport("shell32.dll")]
    public static extern int SHQueryUserNotificationState(out int state);
}
"@

$selfPid = $PID
$shellClasses = @(
    'Progman', 'WorkerW', 'Shell_TrayWnd', 'Shell_SecondaryTrayWnd',
    'MultitaskingViewFrame', 'ForegroundStaging', 'XamlExplorerHostIslandWindow',
    'TaskListThumbnailWnd', 'Windows.UI.Core.CoreWindow'
)

function Write-Probe([string]$line) {
    [Console]::Out.WriteLine($line)
    [Console]::Out.Flush()
}

function Test-ParentAlive {
    if ($ParentPid -le 0) { return $true }
    try {
        $null = [System.Diagnostics.Process]::GetProcessById($ParentPid)
        return $true
    } catch {
        return $false
    }
}

function Get-GameLine {
    try {
        $hwnd = [QnGameWatch]::GetForegroundWindow()
        if ($hwnd -eq [IntPtr]::Zero) { return '0||' }

        $classBuffer = New-Object System.Text.StringBuilder 256
        [void][QnGameWatch]::GetClassName($hwnd, $classBuffer, 256)
        if ($shellClasses -contains $classBuffer.ToString()) { return '0||' }

        $ownerPid = 0
        [void][QnGameWatch]::GetWindowThreadProcessId($hwnd, [ref]$ownerPid)
        if ($ownerPid -eq 0 -or $ownerPid -eq $selfPid -or $ownerPid -eq $ParentPid) { return '0||' }

        $process = $null
        try { $process = [System.Diagnostics.Process]::GetProcessById($ownerPid) } catch { return '0||' }
        if ($null -eq $process) { return '0||' }
        # Explorer owns the desktop/taskbar windows; our own overlay is never a game.
        if ($process.ProcessName -eq 'explorer' -or $process.ProcessName -eq 'QuickNotch') { return '0||' }

        $rect = New-Object QnGameWatch+RECT
        $hasRect = [QnGameWatch]::GetWindowRect($hwnd, [ref]$rect)

        $info = New-Object QnGameWatch+MONITORINFO
        $info.cbSize = [System.Runtime.InteropServices.Marshal]::SizeOf($info)
        # MONITOR_DEFAULTTONEAREST = 2: a game's window is on some monitor even
        # when it spans two, and the nearest one is the one it mostly covers.
        $monitor = [QnGameWatch]::MonitorFromWindow($hwnd, 2)
        $hasMonitor = [QnGameWatch]::GetMonitorInfo($monitor, [ref]$info)

        $covers = $false
        if ($hasRect -and $hasMonitor) {
            $covers =
                ($rect.Left -le ($info.rcMonitor.Left + 2)) -and
                ($rect.Top -le ($info.rcMonitor.Top + 2)) -and
                ($rect.Right -ge ($info.rcMonitor.Right - 2)) -and
                ($rect.Bottom -ge ($info.rcMonitor.Bottom - 2))
        }

        $state = 0
        [void][QnGameWatch]::SHQueryUserNotificationState([ref]$state)

        if ($covers -or $state -eq 3) {
            return '1|' + $process.ProcessName + '|' + $ownerPid
        }
        return '0||'
    } catch {
        # A detector that cannot answer must never invent a game.
        return '0||'
    }
}

if ($Once) {
    Write-Probe (Get-GameLine)
    exit 0
}

while (Test-ParentAlive) {
    Write-Probe (Get-GameLine)
    Start-Sleep -Milliseconds $IntervalMs
}
