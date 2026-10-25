# PresentMon (Windows FPS source)

The gaming HUD shows a frame rate on Windows only, read from Intel's
[PresentMon](https://github.com/GameTechDev/PresentMon). macOS exposes no
supported API for another application's frame rate, so there the HUD simply
has one fewer cell.

## Adding the binary

`PresentMon.exe` is a third-party release artifact and is **not** committed
here. Download a release (2.x preferred), then drop the single self-contained
executable into this folder:

```
composeApp/native/windows/PresentMon.exe
```

`syncWindowsBridge` copies everything in `native/windows/` into the app's
`windows/` resources, and `FpsProbe` extracts it next to the other PowerShell
helpers at run time. Rebuild (`./gradlew :composeApp:desktopJar`) after adding
it; without the file the HUD simply omits the FPS cell and logs
`PresentMon.exe not bundled` once.

## How it is invoked

Once per sample, for a two-second capture, against the detected game's pid:

```
PresentMon.exe --process_id <pid> --output_stdout --stop_existing_session \
               --timed 2 --terminate_after_timed
```

`FpsProbe` averages the `msBetweenPresents` column (`1000 / mean`) and retries
once with the 1.x single-dash spelling (`-process_id`) if a build rejects the
2.x double-dash options.

## Unverified

This path has not been exercised on a Windows machine from this repository:
the argument set follows PresentMon 2.x documentation and the CSV parser is
header-driven and version-tolerant, but a live capture still needs eyes on a
Windows box with a real game. Until then, treat FPS as best-effort — every
failure path degrades to "no FPS cell", never a wrong number.
