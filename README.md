# WinTabLauncher v2

Windows-style Android HOME launcher for the Galaxy Tab E / Android 7.1.2 project.

## v2 changes

- Rebuilt the v1 launcher UI instead of keeping the placeholder shortcuts.
- Desktop icons now come from the tablet's real installed applications.
- Taskbar pinned icons use the original installed app icons.
- Clicking the taskbar clock opens a Windows-style clock and monthly calendar flyout.
- Calendar supports previous/next month navigation and highlights today.
- Start menu still lists and searches real installed apps.
- Added a custom Turkish QWERTY touch keyboard for Start-menu search.
- Package remains a real Android HOME launcher.
- No native libraries: works without an ARM/ARM64 ABI dependency.

## Install

Download the APK artifact produced by GitHub Actions, extract it, then:

```bat
adb install -r app-debug.apk
```

If v1 is already installed, `-r` upgrades it in place and keeps WinTab Launcher selected when Android allows it.
