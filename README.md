# WinTab Launcher v3

Windows-style Android HOME launcher for the Galaxy Tab E / Android 7.1.2 project.

## Launcher v3

- Desktop shows only selected apps, not every installed app.
- Desktop apps use the real application icons and labels.
- Desktop icons automatically move into a new column before reaching the taskbar.
- Long-press a desktop icon to remove only its desktop shortcut.
- Start menu contains every launchable installed app.
- Long-press an app in Start to add/remove it from desktop, pin/unpin it from taskbar, or open Android uninstall for normal user apps.
- System apps are protected from the uninstall option.
- Taskbar uses real installed app icons and supports pin/unpin.
- The custom in-launcher keyboard from v2 was removed; Start search uses Android's selected system keyboard.
- Windows-style clock/calendar flyout remains.

## WinTab Keyboard

The repo now also builds a separate real Android IME named **WinTab Keyboard**. It can be enabled from Android Language & Input settings and used in other apps too.

## Install

Launcher:
```bat
adb install -r app-debug.apk
```

Keyboard:
```bat
adb install -r keyboard-debug.apk
```

Then open **WinTab Keyboard** once, enable it in keyboard settings, and choose it from Android's input-method picker.
