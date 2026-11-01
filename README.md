# Battery Alert

An Android app that alerts you with sound and vibration when your battery reaches levels you choose:

- **Charge alert**: fires when the battery rises to your target level (default 80%) while charging, so you know when to unplug.
- **Low battery alert**: fires when the battery drops to your target level (default 20%) while not charging, so you know when to plug in.

The app's interface is in Greek.

## Features

- Adjustable thresholds: charge 50–100%, low battery 5–50%.
- Separate settings for each alert: enable/disable, sound, vibration.
- Sound picker with system notification, ringtone and alarm sounds, any audio file (MP3, OGG, …), or no sound.
- Optional playback on the alarm stream, so alerts are heard even in silent mode.
- Respects Do Not Disturb when the alarm stream is not used.
- Sound stops after 30 seconds, when you tap "Mute", or when you unplug the charger.
- Each alert fires once per charge or discharge cycle, even if the service restarts.
- Shows battery level, power source (charger, USB, wireless, dock) and temperature.
- Monitoring restarts automatically after a reboot or an app update.
- Setup guide for HyperOS (Xiaomi), which aggressively kills background apps: auto-start and battery restriction exemption.
- Light and dark themes.

## How it works

`ACTION_BATTERY_CHANGED` can't be received through the manifest, so the app runs a **foreground service** (`BatteryMonitorService`) with a dynamically registered receiver. The service checks every battery change against your thresholds and triggers the alert via `AlertSound`.

Sounds are played by the app itself (`MediaPlayer`) rather than by the notification channel, because a channel's sound can't be changed after creation and can't play files picked from storage.

| File | Role |
|---|---|
| `MainActivity.kt` | Main screen and settings (Jetpack Compose) |
| `BatteryMonitorService.kt` | Foreground service that watches the battery and fires alerts |
| `AlertPrefs.kt` | Settings storage (`SharedPreferences`) |
| `AlertSound.kt` | Sound and vibration |
| `Notifications.kt` | Notification channels and notifications |
| `Receivers.kt` | `BootReceiver` and `StopSoundReceiver` |
| `BatteryInfo.kt` | Battery state parsing |
| `ui/` | Theme and components |

## Permissions

| Permission | Purpose |
|---|---|
| `POST_NOTIFICATIONS` | Show notifications |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Continuous battery monitoring |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after boot |
| `VIBRATE` | Vibrate on alerts |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Exemption from battery restrictions |

The app has no internet access and collects no data.

## Requirements

- Android 12 (API 31) or newer
- To build: JDK 17 and Android SDK 35

## Installation

Download `BatteryAlert.apk` from the [latest release](../../releases/latest) and install it on your device. You may need to allow installs from unknown sources.

The APK is signed with the debug key so it can be sideloaded directly.

## Build

```bash
./gradlew assembleRelease
```

The APK is generated in `app/build/outputs/apk/release/`. You need a `local.properties` file with `sdk.dir` pointing to your Android SDK (Android Studio creates it automatically).

## Built with

Kotlin, Jetpack Compose, Material 3, Gradle (Kotlin DSL).
