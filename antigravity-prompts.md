# Prompts for Antigravity

Paste **Prompt 1** in one session/workspace for the desktop app, and
**Prompt 2** in another for the Android app. They reference the same
protocol so the two builds stay compatible. If Antigravity supports
one big multi-file workspace, you can also paste both back-to-back in
a single session — just keep the "SHARED PROTOCOL" block visible to
both.

---

## SHARED PROTOCOL (include in both prompts)

```
WebSocket protocol between the two apps (ws://<laptop-ip>:5417), JSON messages:

Phone -> Laptop, first message after connecting:
  { "type": "PAIR", "token": "<from QR>", "deviceId": "<phone model>" }
Laptop replies:
  { "type": "PAIR_OK", "laptopName": "<hostname>" }
  { "type": "PAIR_REJECTED" }

Laptop -> Phone:
  { "type": "FOCUS_ON", "subject": "<name>" }
  { "type": "FOCUS_OFF" }
  { "type": "BLOCKLIST", "apps": ["com.instagram.android", ...] }

Phone -> Laptop:
  { "type": "HEARTBEAT" }               -> laptop replies HEARTBEAT_ACK
  { "type": "INTERRUPTION", "app": "<package>" }

QR code payload (laptop generates, phone scans):
  { "ip": "192.168.x.x", "port": 5417, "token": "<random hex>", "laptop": "<hostname>" }
```

---

## PROMPT 1 — Windows Desktop App ("Serious Study" control center)

```
Build a complete, runnable Windows desktop app called "Serious Study" —
a student focus control center. Use Electron + HTML/CSS/JavaScript +
Node.js, no frontend framework needed.

CONCEPT
When I press START on this laptop app, my Android phone (a separate app)
should lock into distraction-blocking mode. The laptop itself is NEVER
restricted — it's the study device. When I press END, the phone unlocks.

VISUAL DESIGN — make this genuinely attractive, not a generic dashboard:
- Dark theme, near-black background (#0f1115), soft card surfaces (#171a21),
  one confident accent color (electric blue #4f8cff or similar) used sparingly
- Clean sans-serif type (system font stack), generous whitespace, subtle
  rounded corners (10-14px), soft shadows on cards, no gradients unless tasteful
- A big, satisfying full-width START button — this is the emotional center of
  the app, it should feel good to press
- Micro-interactions: smooth hover states, a live pulsing "recording" dot or
  ring animation on the timer while a session is active, an animated progress
  bar for daily goal
- Sidebar navigation with icons: Dashboard, Study, Analytics, Exams,
  Connect Phone, Settings
- No visual clutter, no unnecessary animation — polish over decoration

CORE SCREENS
1. Dashboard (idle state): greeting, current streak ("🔥 12 Day Streak"),
   today's focus time by subject, daily goal progress bar, subject dropdown
   (DSA, Java, React, Full Stack, Machine Learning, Cloud, GATE, College
   Subject, Project, Hackathon, Aptitude, Communication, + custom subjects
   the user can add), big START SERIOUS STUDY button, upcoming exams preview.
2. Dashboard (active session): "🔒 SERIOUS STUDY ACTIVE", subject name, live
   HH:MM:SS timer, "Phone: 🔒 Focus Mode Active", "Laptop: ✓ Study environment
   active", optional session notes textarea, END SESSION button.
3. Exam Tracker: form to add exam (name, subject, date, time, location,
   notes), list sorted by date with a live "days remaining" countdown per
   exam, delete button per exam.
4. Connect Phone: generates and displays a QR code containing
   { ip, port, token, laptop } (see protocol below) using the `qrcode` npm
   package, shows local IP + port, a "regenerate token" button, and live
   connected/not-connected status.
5. Settings: daily study goal (minutes), minimum daily streak minutes,
   editable list of blocked Android package names (textarea, one per line,
   defaults: Instagram, YouTube, Reddit, TikTok, Facebook, Snapchat).

FUNCTIONALITY
- Local JSON storage (lowdb or similar) for: subjects, study sessions
  (id, subjectId, subjectName, startTime, endTime, duration, status, notes),
  exams, user settings, interruption log. No cloud, no accounts, everything
  local to the machine.
- A local WebSocket server (the `ws` package) on port 5417 that the phone
  app connects to. Implement the exact protocol below — pairing token
  required before any other message is accepted; do not leave the socket
  open to unauthenticated commands.
- Calculate and display: current streak, longest streak, today's total vs
  goal, per-subject totals.
- START SERIOUS STUDY: begins a timer, sends FOCUS_ON + BLOCKLIST to the
  connected phone over the socket.
- END SESSION: stops the timer, saves the session to disk, sends FOCUS_OFF
  to the phone.
- Handle "phone not connected" gracefully — the laptop app must still work
  standalone even with no phone paired.

[PASTE THE SHARED PROTOCOL BLOCK HERE]

DELIVERABLE
- Complete, working source code, no placeholders or pseudocode.
- package.json with correct dependencies (electron, ws, qrcode, lowdb) and
  a `start` script, plus an `electron-builder` config for producing a
  Windows .exe installer later.
- Clear README: npm install, npm start, and how to build the Windows
  installer.
- Make sure the app actually runs with `npm install && npm start` with no
  errors.
```

---

## PROMPT 2 — Android Phone App ("Serious Study" focus mode)

```
Build a complete, runnable Android app called "Serious Study" in Kotlin
(Android Studio project, Gradle, min SDK 26, target/compile SDK 34). This
app pairs with a Windows desktop app over local Wi-Fi and enters a
distraction-blocking Focus Mode whenever the laptop starts a study session.

CONCEPT
The phone is the distraction-controlled device. Phone calls and emergency
dialing must ALWAYS keep working, no exceptions. Only a user-configured
list of distracting apps gets blocked, and only while the laptop's session
is active.

VISUAL DESIGN — should feel calm and premium, matching a dark "focus app"
aesthetic:
- Dark theme (#0f1115 background, #171a21 cards, #4f8cff accent) — same
  palette as the desktop companion app, so they feel like one product
- Big clear status card at the top: connection state (🟢/⚪) and current
  Focus state
- A satisfying full-width "📷 Scan QR to Connect Laptop" button
- When Focus is active, show a calm lock-themed status view: subject name,
  "☎ Phone Calls ALLOWED", "🚨 Emergency ALLOWED", a subtle "Emergency
  Unlock" text button that requires a confirmation dialog before it does
  anything
- Simple, legible Material 3 components; avoid clutter

CORE SCREENS
1. Main screen: connection status, "Scan QR to Connect Laptop" button,
   "Enable App Blocking Permission" button (with an explanatory dialog
   before sending the user to Settings), current Focus status card,
   Emergency Unlock button (visible only during an active focus session),
   button to open the Exam Tracker.
2. QR Scanner screen: uses a QR scanning library (e.g. ZXing/journeyapps
   zxing-android-embedded) to scan the laptop's QR code and extract
   { ip, port, token, laptop }, then saves it and starts the connection.
3. Exam Tracker screen: simple local list of exams (name, subject, date)
   with an "Add Exam" dialog and a live "days remaining" countdown per
   exam, stored in SharedPreferences as JSON (no backend needed).

FUNCTIONALITY — implement for real, using legitimate Android APIs only:
- An AccessibilityService that observes TYPE_WINDOW_STATE_CHANGED events
  to detect the current foreground app package, and — ONLY while Focus
  Mode is active AND that package is on the current blocklist — calls
  performGlobalAction(GLOBAL_ACTION_HOME) to exit it. Hard-code an
  always-allowed set that includes the dialer/phone/incall/system UI
  packages and this app's own package, checked before anything else, so
  calls can never be blocked by a misconfigured list.
- Require the user to manually enable this Accessibility Service in
  Android Settings (explain why in-app first) — do not attempt to
  auto-enable it, hide it, root the device, or use any Device Owner /
  Device Admin tricks. If a truly "hard" app-lock isn't possible without
  those, say so in the README rather than faking it.
- A foreground Service holding a WebSocket client (OkHttp) that connects
  to the laptop, re-connects automatically every few seconds if dropped,
  and shows a persistent (non-hidden, as required by Android) notification
  with the current status. If the laptop becomes unreachable while Focus
  was active, KEEP the phone locked (don't silently unlock) until either
  reconnection restores the real state or the user uses Emergency Unlock.
- On receiving FOCUS_ON / FOCUS_OFF / BLOCKLIST from the laptop, update a
  small persisted FocusState (SharedPreferences) that the AccessibilityService
  reads.
- Send INTERRUPTION messages back to the laptop when a blocked app is
  attempted, and HEARTBEAT on an interval.
- Request POST_NOTIFICATIONS (Android 13+) and CAMERA (for QR scanning)
  permissions properly at runtime.

[PASTE THE SHARED PROTOCOL BLOCK HERE]

DELIVERABLE
- Full Android Studio project: build.gradle (project + app), settings.gradle,
  AndroidManifest.xml with correct permissions and the accessibility
  service's config XML, all Kotlin source files, all layout XML files,
  string/color/theme resources.
- No pseudocode or "TODO: implement this" placeholders — real working logic
  everywhere, including the WebSocket client, the AccessibilityService, and
  the exam tracker persistence.
- README covering: opening the project in Android Studio, building
  app-debug.apk via ./gradlew assembleDebug, installing it with adb,
  granting the Accessibility permission, and troubleshooting (OEM battery
  optimization killing the background service, Wi-Fi client isolation
  breaking pairing, etc).
- Explicitly call out anywhere Android's platform limits prevent a
  requested behavior (e.g. no true OS-level app lock without MDM/Device
  Owner status) and implement the closest legitimate alternative instead
  of pretending a workaround exists.
```
