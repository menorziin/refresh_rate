<p align="center">
  <b>Refresh Rate</b><br>
  Switch your screen between 60, 90 and 120 Hz with a single tap.
</p>

<p align="center">
  <b>English</b> · <a href="README.pt-BR.md">Português (Brasil)</a>
</p>

---

**Refresh Rate** is a root app for Android that lets you control the display refresh rate with one tap. It reads the rates your panel actually supports, so it isn't tied to a specific device.

## Compatibility

| ROM | Status |
| --- | --- |
| AOSP and AOSP-based ROMs | ✅ Works |
| HyperOS / MIUI | ⚠️ Untested |
| Other OEM ROMs (One UI, ColorOS, etc.) | ⚠️ Untested |

Tested on a Poco F5 running an AOSP ROM. On OEM ROMs the system may ignore or override the chosen rate. If you try it on one, please open an issue with the result.

## Features

- **Fixed rates**: 60, 90 or 120 Hz, depending on what the panel supports.
- **Dynamic mode**: the system moves between the lowest and highest rate as needed.
- **Quick Settings tile**: shows the active rate on its icon and cycles through rates on each tap.
- **Battery saver aware**: shows when battery saver is capping the rate, both in the app and on the tile.
- **Reapply on boot**: restores your last choice after a restart.
- **Force via SurfaceFlinger** (experimental): locks the mode directly in the compositor when the system keeps switching it back.
- English and Portuguese (Brazil), with per-app language on Android 13+.
- Material You, light/dark theme and themed icon.

## Requirements

- Android 12 or newer
- Root (Magisk, KernelSU or APatch)

## Installation

1. Download the latest APK from [Releases](../../releases).
2. Install and open it.
3. Grant root access when your root manager asks.
4. (Optional) Edit Quick Settings and add the **Refresh rate** tile.

## How it works

The app runs commands through `su` and writes the AOSP refresh rate keys:

```sh
settings put system min_refresh_rate  <hz>
settings put system peak_refresh_rate <hz>
```

It also writes `user_refresh_rate`, a key used by some OEM ROMs. AOSP ignores it; it's there for compatibility, with no guarantee it works.

With **Force via SurfaceFlinger** enabled, it also sends:

```sh
service call SurfaceFlinger 1035 i32 <mode index>
```

This is an internal, undocumented call. It lasts until reboot and only applies to fixed rates.

## Building

GitHub Actions builds the APK on every push to `main`. To publish a release, create a `v*` tag (or a release with a new `v*` tag on GitHub):

```sh
git tag v1.1 && git push origin v1.1
```

Signing uses a keystore stored in the repository Secrets:

| Secret | Content |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Without these Secrets, `main` builds are signed with the debug key and tag builds fail, so no release is ever published with the wrong signature.

To build locally with the Android SDK installed:

```sh
gradle assembleRelease
```

## Disclaimer

This app changes system settings with root privileges. Use it at your own risk.
