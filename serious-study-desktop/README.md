# Serious Study — Desktop App

A focused study tracker for Windows, built with Electron. Pair with your Android phone to automatically block distracting apps while you study.

---

## Prerequisites

- **Node.js 18+** — [nodejs.org](https://nodejs.org)
- **npm** (included with Node.js)

---

## Quick Start

```bash
cd serious-study-desktop
npm install
npm start
```

The app window will open. On first launch, default subjects and settings are written to your user-data folder.

---

## Building a Windows Installer

```bash
npx electron-builder --win
```

The installer (`.exe`) will be placed in the `dist/` folder.

---

## Pairing Your Android Phone

1. Install the **Serious Study** companion app on your Android device.
2. Make sure your laptop and phone are on the **same Wi-Fi network**.
3. In the desktop app, click **Connect Phone** in the sidebar.
4. Open the companion app on your phone and tap **Scan QR**.
5. Point your camera at the QR code displayed on the laptop screen.
6. Once paired, you'll see "● Phone Connected" in the sidebar.

### What happens during a study session

- When you press **START SERIOUS STUDY**, the laptop sends a `FOCUS_ON` command plus a `BLOCKLIST` of distracting apps to your phone.
- The phone's companion app blocks those apps and shows a focus lock screen.
- If the phone detects an app-open attempt, it sends an **INTERRUPTION** alert back to the laptop — you'll see a toast notification.
- When you press **END SESSION**, a `FOCUS_OFF` command is sent and the phone unlocks normally.

---

## Troubleshooting

### Port 5417 blocked by firewall

The WebSocket server listens on port **5417**. If pairing fails:

1. Open **Windows Defender Firewall** → Advanced Settings.
2. Add a new **Inbound Rule** → Port → TCP → 5417 → Allow the connection.
3. Restart the app.

Or run this in an elevated PowerShell:

```powershell
New-NetFirewallRule -DisplayName "Serious Study WS" -Direction Inbound -Protocol TCP -LocalPort 5417 -Action Allow
```

### Finding your laptop IP

The IP shown in **Connect Phone** is auto-detected. If it shows the wrong address:

```powershell
ipconfig
```

Look for the `IPv4 Address` under your active Wi-Fi or Ethernet adapter.

### Phone can't find the laptop

- Confirm both devices are on the **same network/subnet**.
- Try disabling any VPN on the laptop.
- Some corporate or university Wi-Fi networks block peer-to-peer traffic — use a personal hotspot instead.

### QR code expired / pairing fails

Click **🔄 Regenerate Token** on the Connect Phone screen to create a new token and QR code.

---

## Data Storage

All data (sessions, exams, settings) is stored locally in a JSON file:

```
%APPDATA%\serious-study\db.json
```

No data is sent to any server.

---

## WebSocket Protocol Reference

| Direction      | Message                                                                 |
|----------------|-------------------------------------------------------------------------|
| Phone → Laptop | `{ "type": "PAIR", "token": "…", "deviceId": "…" }`                   |
| Phone → Laptop | `{ "type": "HEARTBEAT" }`                                              |
| Phone → Laptop | `{ "type": "INTERRUPTION", "app": "com.instagram.android" }`          |
| Laptop → Phone | `{ "type": "PAIR_OK", "laptopName": "…" }`                             |
| Laptop → Phone | `{ "type": "PAIR_REJECTED" }`                                          |
| Laptop → Phone | `{ "type": "FOCUS_ON", "subject": "DSA" }`                             |
| Laptop → Phone | `{ "type": "FOCUS_OFF" }`                                              |
| Laptop → Phone | `{ "type": "BLOCKLIST", "apps": ["com.instagram.android", …] }`       |
| Laptop → Phone | `{ "type": "HEARTBEAT_ACK" }`                                          |

QR payload: `{ "ip": "192.168.x.x", "port": 5417, "token": "<hex>", "laptop": "<hostname>" }`
