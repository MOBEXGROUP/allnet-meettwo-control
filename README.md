# MeetTwo LED

Android app for the ALLNET MeetTwo meeting room tablet (RK3576, Android 16). It controls the
RGB LED bar, reports the cards held to the NFC reader, switches the display and its
brightness, sends a Bluetooth iBeacon and exposes all of it to Home Assistant over MQTT with
discovery.

## Hardware access

| Function | How |
|---|---|
| LED bar | Vendor kernel driver `/dev/ledjni` (`crwxrwxrwx`): `ioctl` 161 / 162 / 163 = red / green / blue, level 0 to 15. The app's own JNI library (`app/src/main/cpp/ledjni.c`) does the same as the vendor library `libjnielc.so` in the preinstalled `com.elc.smt_test` ("WA-LED"). |
| NFC card reader | NXP PN7xxx over the Android NFC stack. The firmware's NFC service sends every UID to the input method ("NFC auto input") and dispatches tags to apps only in reader mode in the foreground. The app follows the NFC HAL log (`logcat -s NxpNciR`) as root and decodes the NCI activation message (`61 05 …`), so the kiosk app stays in front. |
| Display on/off | `input keyevent KEYCODE_SLEEP` / `KEYCODE_WAKEUP` as root |
| Wake screen on touch | A touch on the dark screen wakes it twice over: the kernel touch driver sends a power key through the PMIC power key device (`wdt_ts: send wakeup key`), and Android wakes on the touch itself. When the option is off, the app inhibits both input devices (`goodix-ts`, `rk805 pwrkey`, sysfs `inhibited`) as root while the screen is off. |
| Display brightness | Android `screen_brightness` (1 to 255, manual mode), shown as 1 to 100 % |
| Bluetooth beacon | Android `BluetoothLeAdvertiser`, Apple iBeacon format. Bluetooth is switched on as root (`cmd bluetooth_manager enable`) when the beacon is enabled. |
| Restart | `svc power reboot` as root |

- Root: the firmware ships a setuid `su` (`/system/xbin/su`) and SELinux is permissive.
- At every service start the app grants itself, as root: notifications, Bluetooth advertising, `WRITE_SETTINGS`, usage access (foreground app sensor) and the battery-optimisation whitelist.
- 16 levels per channel: a colour is scaled by the brightness and rounded; the strongest channel of a visible colour never drops below level 1.
- The driver (kernel module `wa_two_leds`) maps the level through a 16-entry table to the PWM duty cycle (period 1 ms, 66 667 ns per level). A level above 15 causes a kernel panic and a reboot, so `ledjni.c` clamps every value to 0 to 15.
- PWM channels: red `2ade6000.pwm` (`pwmleds`), green `2ade2000.pwm`, blue `2ade5000.pwm`. They belong to the driver and cannot be exported through sysfs, and the kernel has no `/dev/mem`.

## Home Assistant (MQTT)

MQTT 3.1.1, one retained device discovery message:
`<prefix>/device/meettwo_<serial>/config`. The device is named `MeetTwo <serial>`; rename it in Home Assistant.

| Entity | Type | State topic | Command topic |
|---|---|---|---|
| LED | light (JSON schema, RGB, brightness, effects) | `meettwo/<serial>/led/state` | `meettwo/<serial>/led/set` |
| Last card | sensor, attribute `time` | `meettwo/<serial>/card/state` | |
| Screen | switch | `meettwo/<serial>/screen/state` | `meettwo/<serial>/screen/set` |
| Wake screen on touch | switch (config) | `meettwo/<serial>/screen_wake_on_touch/state` | `meettwo/<serial>/screen_wake_on_touch/set` |
| Screen brightness | number 1 to 100 % | `meettwo/<serial>/screen_brightness/state` | `meettwo/<serial>/screen_brightness/set` |
| Bluetooth beacon | switch (config) | `meettwo/<serial>/beacon/state` | `meettwo/<serial>/beacon/on/set` |
| Beacon UUID | text (config) | `meettwo/<serial>/beacon/state` | `meettwo/<serial>/beacon/uuid/set` |
| Beacon major / minor | number 0 to 65535 (config) | `meettwo/<serial>/beacon/state` | `meettwo/<serial>/beacon/major/set`, `…/minor/set` |
| Restart | button (config) | | `meettwo/<serial>/restart/press` |
| IP address, Network, Wi-Fi signal, Foreground app, Last boot | sensors (diagnostic) | `meettwo/<serial>/device/state` | |

- Availability: `meettwo/<serial>/status` (`online` / `offline`, retained, last will).
- `<serial>` is the lower-case serial number (`ro.serialno`), e.g. `a25600000000000`.
- LED effects: `none`, `blink`, `breathe`, `rainbow`. A colour command stops a running effect.
- Wake screen on touch (default on): when off, neither a touch nor the hardware power button wakes the dark screen, because the power button uses the same input device as the touch driver's wake key. Only Home Assistant (switch "Screen") switches it on.
- Card payload: `{"id":"04A1B2C3D4E5F6","time":"2026-11-16T11:07:19+01:00"}`. `time` changes with every presentation, so a state trigger without `to:` also fires for the same card again.
- When Home Assistant comes online (`<prefix>/status` = `online`), the app announces itself again.

## Setup

1. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Open the app once (it starts the background service, which then starts at every boot).
3. Enter the broker under **Settings**, or from the PC:
   ```bash
   adb shell am broadcast -n org.roomscreen.meettwo/.service.ConfigReceiver \
     --es mqtt_host 192.168.1.10 --ei mqtt_port 1883 \
     --es mqtt_user ha --es mqtt_password secret --ez mqtt_enabled true
   ```
   - Extras: `mqtt_enabled`, `mqtt_host`, `mqtt_port`, `mqtt_user`, `mqtt_password`, `mqtt_discovery_prefix`.
   - Extras that are not given keep their value.
   - Only the shell and the system may send this broadcast (`android.permission.DUMP`).

## App

- Main page: LED (on/off, quick colours, red/green/blue/brightness, effects), status (MQTT, card reader with last card, beacon, network), screen (brightness, switch off).
- Settings page: MQTT broker, wake screen on touch, beacon (on/off, UUID, major, minor), device information.
- Both pages fit the 1280x800 screen without scrolling.

## Build

Requirements: JDK 21, Android SDK with platform 37, NDK 29.0.14206865, CMake 4.1.2.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

- `versionCode` is the number of commits, `versionName` the latest tag without `v` (or the commit hash).
- GitHub Actions (`.github/workflows/build.yml`) runs tests, lint and `assembleRelease` on every push and pull request and keeps the APK as a workflow artifact.
- Pushing a tag `v*` also creates a GitHub release with the APK.
- Release signing uses the repository secrets below. Without them, CI signs with a temporary debug key; such an APK cannot update an installed one.

| Secret | Content |
|---|---|
| `KEYSTORE_BASE64` | release keystore, base64-encoded |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Release builds are not minified: R8 broke the vendor's "MeetTwo LED API" app (crash at start with `IllegalAccessError`), and the APK size does not matter on a wall tablet.

## Code

| Package | Content |
|---|---|
| `hardware` | `LedDevice`, JNI access to `/dev/ledjni` |
| `light` | light state, colour to level mapping, effects, `LightController` (single owner of the LED state), persistence |
| `card` | `CardReader` (NFC HAL log as root), `NciActivation` (UID from the NCI message) |
| `screen` | display on/off, brightness and wake on touch |
| `beacon` | iBeacon advertising |
| `device` | network, foreground app and boot time |
| `mqtt` | `MqttBridge` (HiveMQ client, reconnect), topics, discovery payload, light JSON |
| `settings` | DataStore-backed settings |
| `system` | root shell, device identity, self-provisioning |
| `service` | foreground service, boot receiver, provisioning receiver |
| `ui` | Jetpack Compose UI, `MainViewModel` |
