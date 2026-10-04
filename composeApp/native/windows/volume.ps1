#
#  volume.ps1
#
#  Prints the current system master volume as one plain line:
#    "<percent>,<muted>"   e.g. "40,False"
#  or, when invoked with `-Action toggle|mute|unmute`, flips the mute flag of
#  the default render device (no output). SMTC carries no volume, so the music
#  widget reads this from the OS directly — the same endpoint the volume
#  flyout reflects (the default render device's master volume + mute).
#
#  The CoreAudio COM API (MMDeviceEnumerator -> IAudioEndpointVolume) is not
#  scriptable from PowerShell, so the whole read/write lives in a tiny C#
#  helper compiled on the fly with Add-Type and driven through COM interop.
#

param([string]$Action = "get")

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Add-Type -Language CSharp -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

namespace QuickNotchAudio {

    [ComImport, Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IAudioEndpointVolume {
        int RegisterControlChangeNotify(IntPtr pNotify);
        int UnregisterControlChangeNotify(IntPtr pNotify);
        int GetChannelCount(out int count);
        int SetMasterVolumeLevel(float level, Guid eventContext);
        int SetMasterVolumeLevelScalar(float level, Guid eventContext);
        int GetMasterVolumeLevel(out float level);
        int GetMasterVolumeLevelScalar(out float level);
        int SetChannelVolumeLevel(uint channel, float level, Guid eventContext);
        int SetChannelVolumeLevelScalar(uint channel, float level, Guid eventContext);
        int GetChannelVolumeLevel(uint channel, out float level);
        int GetChannelVolumeLevelScalar(uint channel, out float level);
        int SetMute(bool mute, Guid eventContext);
        int GetMute(out bool mute);
        int GetVolumeStepInfo(out uint step, out uint stepCount);
        int VolumeStepUp(Guid eventContext);
        int VolumeStepDown(Guid eventContext);
        int QueryHardwareSupport(out uint capsMask);
        int GetVolumeRange(out float minVolume, out float maxVolume, out float increment);
    }

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    public class MMDeviceEnumeratorComObject { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IMMDeviceEnumerator {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IMMDevice {
        IntPtr Activate(ref Guid iid, int clsCtx, IntPtr activationParams);
        int OpenPropertyStore(int access, out IntPtr properties);
        int GetId(out IntPtr id);
        int GetState(out int state);
    }

    public static class VolumeReader {
        private static readonly Guid EndpointVolumeIid = Guid.Parse("5CDF2C82-841E-4546-9722-0CF74078229A");

        private static IAudioEndpointVolume OpenEndpoint() {
            var manager = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
            IMMDevice device;
            manager.GetDefaultAudioEndpoint(0, 1, out device); // eRender, eMultimedia
            Guid iid = EndpointVolumeIid;
            IntPtr unk = device.Activate(ref iid, 0x17, IntPtr.Zero);
            var endpoint = (IAudioEndpointVolume)Marshal.GetObjectForIUnknown(unk);
            Marshal.Release(unk);
            return endpoint;
        }

        public static string GetState() {
            var endpoint = OpenEndpoint();
            try {
                float level;
                endpoint.GetMasterVolumeLevelScalar(out level);
                float percent = level * 100f;
                if (percent < 0f) percent = 0f;
                if (percent > 100f) percent = 100f;
                bool mute;
                endpoint.GetMute(out mute);
                return ((int)Math.Round(percent)) + "," + (mute ? "True" : "False");
            } finally {
                Marshal.ReleaseComObject(endpoint);
            }
        }

        public static void SetMute(bool mute) {
            var endpoint = OpenEndpoint();
            try {
                endpoint.SetMute(mute, Guid.Empty);
            } finally {
                Marshal.ReleaseComObject(endpoint);
            }
        }
    }
}

switch ($Action) {
    "get"    { [QuickNotchAudio.VolumeReader]::GetState() }
    "mute"   { [QuickNotchAudio.VolumeReader]::SetMute($true) }
    "unmute" { [QuickNotchAudio.VolumeReader]::SetMute($false) }
    "toggle" {
        $state = [QuickNotchAudio.VolumeReader]::GetState().Split(',')[1]
        [QuickNotchAudio.VolumeReader]::SetMute($state -ne "True")
    }
}