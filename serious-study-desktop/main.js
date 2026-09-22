'use strict';

const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const os = require('os');
const crypto = require('crypto');

const low = require('lowdb');
const FileSync = require('lowdb/adapters/FileSync');
const WebSocket = require('ws');
const QRCode = require('qrcode');

// ─── Globals ─────────────────────────────────────────────────────────────────
let mainWindow = null;
let db = null;
let wss = null;
let pairedSocket = null;
let pairedDeviceId = null;
let currentToken = '';
let currentQrDataUrl = '';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getLocalIp() {
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const iface of ifaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

function generateToken() {
  return crypto.randomBytes(16).toString('hex');
}

async function buildQrDataUrl(ip, token) {
  const payload = JSON.stringify({
    ip,
    port: 5417,
    token,
    laptop: os.hostname()
  });
  return QRCode.toDataURL(payload, { width: 280, margin: 2 });
}

/**
 * Count consecutive calendar days ending today where user studied >= thresholdMinutes.
 */
function calculateStreak(sessions, thresholdMinutes) {
  if (!sessions || sessions.length === 0) return 0;
  const dayMap = {};
  for (const s of sessions) {
    if (!s.endTime) continue;
    const dateStr = new Date(s.startTime).toLocaleDateString('en-CA');
    dayMap[dateStr] = (dayMap[dateStr] || 0) + (s.durationMinutes || 0);
  }
  let streak = 0;
  const cursor = new Date();
  cursor.setHours(0, 0, 0, 0);
  while (true) {
    const key = cursor.toLocaleDateString('en-CA');
    if (dayMap[key] && dayMap[key] >= thresholdMinutes) {
      streak++;
      cursor.setDate(cursor.getDate() - 1);
    } else {
      break;
    }
  }
  return streak;
}

/**
 * Longest ever consecutive streak.
 */
function calculateLongestStreak(sessions, thresholdMinutes) {
  if (!sessions || sessions.length === 0) return 0;
  const dayMap = {};
  for (const s of sessions) {
    if (!s.endTime) continue;
    const dateStr = new Date(s.startTime).toLocaleDateString('en-CA');
    dayMap[dateStr] = (dayMap[dateStr] || 0) + (s.durationMinutes || 0);
  }
  const qualifyingDays = Object.entries(dayMap)
    .filter(([, mins]) => mins >= thresholdMinutes)
    .map(([d]) => d)
    .sort();
  if (qualifyingDays.length === 0) return 0;
  let longest = 1;
  let current = 1;
  for (let i = 1; i < qualifyingDays.length; i++) {
    const prev = new Date(qualifyingDays[i - 1]);
    const curr = new Date(qualifyingDays[i]);
    const diffDays = (curr - prev) / (1000 * 60 * 60 * 24);
    if (diffDays === 1) {
      current++;
      if (current > longest) longest = current;
    } else {
      current = 1;
    }
  }
  return longest;
}

function getTodayStats(sessions, subjects) {
  const todayStr = new Date().toLocaleDateString('en-CA');
  const bySubject = {};
  for (const s of sessions) {
    if (!s.endTime) continue;
    const dateStr = new Date(s.startTime).toLocaleDateString('en-CA');
    if (dateStr !== todayStr) continue;
    if (!bySubject[s.subjectId]) {
      bySubject[s.subjectId] = { subjectId: s.subjectId, subjectName: s.subjectName, minutes: 0 };
    }
    bySubject[s.subjectId].minutes += s.durationMinutes || 0;
  }
  const bySubjectArr = Object.values(bySubject);
  const totalMinutes = bySubjectArr.reduce((acc, x) => acc + x.minutes, 0);
  return { totalMinutes, bySubject: bySubjectArr };
}

function sendToPhone(msg) {
  if (pairedSocket && pairedSocket.readyState === WebSocket.OPEN) {
    pairedSocket.send(JSON.stringify(msg));
    return true;
  }
  return false;
}

// ─── Database init ────────────────────────────────────────────────────────────

function initDb() {
  const userDataPath = app.getPath('userData');
  const dbPath = path.join(userDataPath, 'db.json');
  const adapter = new FileSync(dbPath);
  db = low(adapter);
  db.defaults({
    subjects: [
      { id: '1',  name: 'DSA' },
      { id: '2',  name: 'Java' },
      { id: '3',  name: 'React' },
      { id: '4',  name: 'Full Stack' },
      { id: '5',  name: 'Machine Learning' },
      { id: '6',  name: 'Cloud' },
      { id: '7',  name: 'GATE' },
      { id: '8',  name: 'College Subject' },
      { id: '9',  name: 'Project' },
      { id: '10', name: 'Hackathon' },
      { id: '11', name: 'Aptitude' },
      { id: '12', name: 'Communication' }
    ],
    sessions: [],
    exams: [],
    interruptions: [],
    settings: {
      dailyGoalMinutes: 120,
      streakMinutes: 30,
      blockedApps: [
        'com.instagram.android',
        'com.google.android.youtube',
        'com.reddit.frontpage',
        'com.zhiliaoapp.musically',
        'com.facebook.katana',
        'com.snapchat.android'
      ]
    }
  }).write();
}

// ─── WebSocket server ─────────────────────────────────────────────────────────

function startWebSocketServer() {
  wss = new WebSocket.Server({ port: 5417 });

  wss.on('connection', (ws) => {
    let authenticated = false;

    ws.on('message', (raw) => {
      let msg;
      try { msg = JSON.parse(raw.toString()); } catch (e) { return; }

      if (msg.type === 'PAIR') {
        if (msg.token === currentToken) {
          if (pairedSocket && pairedSocket !== ws) {
            try { pairedSocket.close(); } catch (_) {}
          }
          pairedSocket = ws;
          pairedDeviceId = msg.deviceId || 'Unknown Device';
          authenticated = true;
          ws.send(JSON.stringify({ type: 'PAIR_OK', laptopName: os.hostname() }));
          const blockedApps = db.get('settings.blockedApps').value();
          ws.send(JSON.stringify({ type: 'BLOCKLIST', apps: blockedApps }));
          if (mainWindow) {
            mainWindow.webContents.send('phone-connected', { deviceId: pairedDeviceId });
          }
        } else {
          ws.send(JSON.stringify({ type: 'PAIR_REJECTED' }));
          ws.close();
        }
        return;
      }

      if (!authenticated) { ws.close(); return; }

      if (msg.type === 'HEARTBEAT') {
        ws.send(JSON.stringify({ type: 'HEARTBEAT_ACK' }));
        return;
      }

      if (msg.type === 'INTERRUPTION') {
        const id = crypto.randomBytes(8).toString('hex');
        const record = { id, app: msg.app || 'unknown', timestamp: new Date().toISOString() };
        db.get('interruptions').push(record).write();
        if (mainWindow) {
          mainWindow.webContents.send('interruption', record);
        }
      }
    });

    ws.on('close', () => {
      if (ws === pairedSocket) {
        pairedSocket = null;
        pairedDeviceId = null;
        if (mainWindow) mainWindow.webContents.send('phone-disconnected');
      }
    });

    ws.on('error', () => {
      if (ws === pairedSocket) {
        pairedSocket = null;
        pairedDeviceId = null;
        if (mainWindow) mainWindow.webContents.send('phone-disconnected');
      }
    });
  });
}

// ─── IPC Handlers ─────────────────────────────────────────────────────────────

function registerIpcHandlers() {
  ipcMain.handle('get-init-data', async () => {
    const subjects = db.get('subjects').value();
    const sessions = db.get('sessions').value();
    const settings = db.get('settings').value();
    const today = new Date().toDateString();
    const exams = db.get('exams').value()
      .filter(e => new Date(e.date) >= new Date(today))
      .sort((a, b) => new Date(a.date) - new Date(b.date))
      .slice(0, 3);
    const todayStats = getTodayStats(sessions, subjects);
    const streak = calculateStreak(sessions, settings.streakMinutes || 30);
    return {
      subjects,
      todayStats,
      streak,
      exams,
      settings,
      qrDataUrl: currentQrDataUrl,
      ip: getLocalIp(),
      token: currentToken,
      hostname: os.hostname(),
      phoneConnected: !!(pairedSocket && pairedSocket.readyState === WebSocket.OPEN)
    };
  });

  ipcMain.handle('get-dashboard-data', async () => {
    const subjects = db.get('subjects').value();
    const sessions = db.get('sessions').value();
    const settings = db.get('settings').value();
    const todayStats = getTodayStats(sessions, subjects);
    const streak = calculateStreak(sessions, settings.streakMinutes || 30);
    const today = new Date().toDateString();
    const upcomingExams = db.get('exams').value()
      .filter(e => new Date(e.date) >= new Date(today))
      .sort((a, b) => new Date(a.date) - new Date(b.date))
      .slice(0, 3);
    return { subjects, todayStats, streak, upcomingExams, settings };
  });

  ipcMain.handle('start-session', async (_e, { subjectId, subjectName }) => {
    const sessionId = crypto.randomBytes(8).toString('hex');
    const startTime = new Date().toISOString();
    db.get('sessions').push({
      id: sessionId, subjectId, subjectName,
      startTime, endTime: null, durationMinutes: 0, notes: ''
    }).write();
    return { sessionId, startTime };
  });

  ipcMain.handle('end-session', async (_e, { sessionId, notes }) => {
    const session = db.get('sessions').find({ id: sessionId }).value();
    if (!session) return { error: 'Session not found' };
    const endTime = new Date().toISOString();
    const durationMinutes = Math.round((new Date(endTime) - new Date(session.startTime)) / 60000);
    db.get('sessions').find({ id: sessionId }).assign({ endTime, durationMinutes, notes: notes || '' }).write();
    sendToPhone({ type: 'FOCUS_OFF' });
    const sessions = db.get('sessions').value();
    const subjects = db.get('subjects').value();
    const settings = db.get('settings').value();
    const todayStats = getTodayStats(sessions, subjects);
    const streak = calculateStreak(sessions, settings.streakMinutes || 30);
    return { durationMinutes, todayStats, streak };
  });

  ipcMain.handle('get-analytics', async () => {
    const sessions = db.get('sessions').filter(s => !!s.endTime).value();
    const settings = db.get('settings').value();
    const subjectMap = {};
    for (const s of sessions) {
      if (!subjectMap[s.subjectId]) subjectMap[s.subjectId] = { name: s.subjectName, totalMinutes: 0 };
      subjectMap[s.subjectId].totalMinutes += s.durationMinutes || 0;
    }
    const perSubject = Object.values(subjectMap).sort((a, b) => b.totalMinutes - a.totalMinutes);
    const allTimeMinutes = sessions.reduce((acc, s) => acc + (s.durationMinutes || 0), 0);
    const currentStreak = calculateStreak(sessions, settings.streakMinutes || 30);
    const longestStreak = calculateLongestStreak(sessions, settings.streakMinutes || 30);
    return { perSubject, longestStreak, currentStreak, allTimeMinutes };
  });

  ipcMain.handle('get-exams', async () => {
    return db.get('exams').value().sort((a, b) => new Date(a.date) - new Date(b.date));
  });

  ipcMain.handle('add-exam', async (_e, { name, subject, date, time, location, notes }) => {
    const exam = {
      id: crypto.randomBytes(8).toString('hex'),
      name, subject, date,
      time: time || '',
      location: location || '',
      notes: notes || '',
      createdAt: new Date().toISOString()
    };
    db.get('exams').push(exam).write();
    return exam;
  });

  ipcMain.handle('delete-exam', async (_e, { id }) => {
    db.get('exams').remove({ id }).write();
    return { ok: true };
  });

  ipcMain.handle('get-settings', async () => {
    return db.get('settings').value();
  });

  ipcMain.handle('save-settings', async (_e, { dailyGoalMinutes, streakMinutes, blockedApps }) => {
    db.set('settings.dailyGoalMinutes', Number(dailyGoalMinutes) || 120).write();
    db.set('settings.streakMinutes', Number(streakMinutes) || 30).write();
    db.set('settings.blockedApps', Array.isArray(blockedApps) ? blockedApps : []).write();
    sendToPhone({ type: 'BLOCKLIST', apps: db.get('settings.blockedApps').value() });
    return { ok: true };
  });

  ipcMain.handle('get-subjects', async () => {
    return db.get('subjects').value();
  });

  ipcMain.handle('add-subject', async (_e, { name }) => {
    const subject = { id: crypto.randomBytes(4).toString('hex'), name: name.trim() };
    db.get('subjects').push(subject).write();
    return subject;
  });

  ipcMain.handle('regenerate-token', async () => {
    currentToken = generateToken();
    const ip = getLocalIp();
    currentQrDataUrl = await buildQrDataUrl(ip, currentToken);
    return { token: currentToken, qrDataUrl: currentQrDataUrl };
  });

  ipcMain.handle('send-focus-on', async (_e, { subject }) => {
    const blockedApps = db.get('settings.blockedApps').value();
    const s1 = sendToPhone({ type: 'FOCUS_ON', subject });
    const s2 = sendToPhone({ type: 'BLOCKLIST', apps: blockedApps });
    return { sent: s1 && s2 };
  });
}

// ─── App lifecycle ────────────────────────────────────────────────────────────

app.whenReady().then(async () => {
  initDb();
  currentToken = generateToken();
  const ip = getLocalIp();
  currentQrDataUrl = await buildQrDataUrl(ip, currentToken);
  startWebSocketServer();
  registerIpcHandlers();

  mainWindow = new BrowserWindow({
    width: 1100,
    height: 750,
    backgroundColor: '#0f1115',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  mainWindow.on('closed', () => { mainWindow = null; });
});

app.on('window-all-closed', () => {
  if (wss) wss.close();
  app.quit();
});
