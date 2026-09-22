# Serious Study — Android App

A companion Android app for the Serious Study desktop application. It pairs with your laptop over Wi-Fi via WebSocket to receive focus session commands, blocks distracting apps using Android's Accessibility Service, and tracks upcoming exams.

---

## Prerequisites

| Tool | Minimum version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| Android device / emulator | API 26 (Android 8.0) or higher |

---

## Opening in Android Studio

1. Clone / unzip the project.
2. Open **Android Studio → File → Open** and select the `serious-study-android/` folder.
3. Let Gradle sync finish (it will download dependencies automatically).

---

## Building

```bash
# Debug APK
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

On Windows use `gradlew.bat assembleDebug`.

---

## Installing

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or use **Run ▶** in Android Studio with a connected device / emulator.

---

## First-time Setup

### 1. Grant Notification Permission (Android 13+)
The app requests this automatically on first launch. Tap **Allow**.

### 2. Enable App Blocking (Accessibility Service)
Tap **"Enable App Blocking Permission"** in the app, then:

> **Settings → Accessibility → Installed Services → Serious Study → Enable**

You may need to tap "Use service" to confirm.

### 3. Pair with Laptop
1. On your laptop, open the Serious Study desktop app and navigate to **Connect Phone**.
2. The desktop app displays a QR code containing the WebSocket address and pairing token.
3. In the Android app, tap **"📷 Scan QR to Connect Laptop"** and point the camera at the QR code.
4. The app connects automatically and shows "Connected to \<laptop name\>".

**QR code payload format (for reference):**
```json
{ "ip": "192.168.x.x", "port": 5417, "token": "<random hex>", "laptop": "<hostname>" }
```

---

## Granting Battery Optimization Exemption (Recommended)

On most Android devices, the system will eventually kill the background WebSocket service to save battery. To prevent this:

> **Settings → Battery → Battery Optimization → All apps → Serious Study → Don't optimize**

The exact path varies by manufacturer (see Troubleshooting below).

---

## Platform Limitations

> **Android does not allow true OS-level app locking without MDM / Device Owner status.**

What this means:

- A **Device Owner** or **MDM profile** can use `DevicePolicyManager` APIs to hard-lock the device to a set of allowed apps. This requires either enterprise enrollment or a rooted device.
- The **Accessibility Service** approach used here is the closest *legitimate* alternative available to a regular third-party app:
  - The `FocusAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED` events.
  - Whenever a blocked app comes to the foreground, the service immediately calls `performGlobalAction(GLOBAL_ACTION_HOME)` to return the user to the home screen.
  - This is *not* instantaneous — there is a brief (~100–200 ms) flash of the blocked app before the home screen appears.
  - A motivated user can still access a blocked app by repeatedly tapping quickly, by using split-screen mode, or by disabling the Accessibility Service.
- For a fully locked-down study environment, consider a dedicated **second device** used only for studying, or an MDM solution such as [Android Enterprise](https://developers.google.com/android/work).

---

## Troubleshooting

### OEM battery optimization kills the background service
Many OEMs (Xiaomi, Samsung, OnePlus, Huawei, etc.) apply aggressive battery restrictions beyond Android's standard `doze` mode.

**Fix:**
- Go to **Settings → Apps → Serious Study → Battery** and select **"No restrictions"** or **"Unrestricted"**.
- For Xiaomi / MIUI: Settings → Apps → Manage Apps → Serious Study → Battery Saver → No restrictions.
- Visit [dontkillmyapp.com](https://dontkillmyapp.com) for device-specific instructions.

### Wi-Fi client isolation breaks pairing
Some routers and corporate/guest Wi-Fi networks enable **AP client isolation**, which prevents devices on the same Wi-Fi network from communicating directly.

**Symptoms:** The QR scan succeeds but the WebSocket connection never establishes; the notification stays on "Connecting…".

**Fix:**
- Connect both phone and laptop to a **personal hotspot** or a router where AP isolation is disabled.
- Alternatively, use the laptop as a hotspot and connect the phone to it.

### "Accessibility service keeps getting disabled"
Some OEMs (notably MIUI and ColorOS) automatically disable third-party accessibility services after a reboot or when background processes are cleared, as a "security" measure.

**Fix:**
- Re-enable the service manually after each reboot: **Settings → Accessibility → Serious Study → Enable**.
- Add Serious Study to **Autostart** (Settings → Apps → Manage Apps → Serious Study → Autostart → Allow).
- This is an OEM limitation with no programmatic workaround available to third-party apps.

### Notification permission not requested
On Android 13+ the `POST_NOTIFICATIONS` permission must be granted for the persistent foreground service notification to appear. The app requests this on first launch. If you denied it, re-grant it:

> **Settings → Apps → Serious Study → Notifications → Allow**

---

## WebSocket Protocol Reference

### Phone → Laptop

| Message | Description |
|---|---|
| `{"type":"PAIR","token":"…","deviceId":"…"}` | Initial pairing handshake |
| `{"type":"HEARTBEAT"}` | Sent every 30 s to keep connection alive |
| `{"type":"INTERRUPTION","app":"com.example.app"}` | Sent when a blocked app is detected |

### Laptop → Phone

| Message | Description |
|---|---|
| `{"type":"PAIR_OK","laptopName":"…"}` | Pairing accepted |
| `{"type":"PAIR_REJECTED"}` | Pairing rejected (wrong token) |
| `{"type":"FOCUS_ON","subject":"…"}` | Start a focus session |
| `{"type":"FOCUS_OFF"}` | End the focus session |
| `{"type":"BLOCKLIST","apps":["com.instagram.android",…]}` | Update blocked app list |
| `{"type":"HEARTBEAT_ACK"}` | Heartbeat acknowledgement |
