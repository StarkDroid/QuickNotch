param(
    [Parameter(Mandatory = $true)][string]$Title,
    [string]$Message
)
# Best-effort Windows toast for the quickview's "Send Test Notifications"
# button. Unpackaged apps can only post WinRT toasts if the registry knows an
# AppUserModelID for them — usually the Start Menu shortcut the MSI installer
# creates — so this either posts a branded banner or prints ERROR and is
# swallowed by the caller. The quickview badge demo does NOT depend on it: the
# session-local count it feeds advances regardless.
$ErrorActionPreference = "Stop"
try {
    [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
    [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null

    $Title = [System.Security.SecurityElement]::Escape($Title)
    $Message = [System.Security.SecurityElement]::Escape($Message)
    $xml = "<toast><visual><binding template='ToastGeneric'><text>$Title</text><text>$Message</text></binding></visual></toast>"

    $doc = New-Object Windows.Data.Xml.Dom.XmlDocument
    $doc.LoadXml($xml)

    $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("QuickNotch")
    $toast = New-Object Windows.UI.Notifications.ToastNotification $doc
    $notifier.Show($toast)
    "OK"
} catch {
    "ERROR"
}