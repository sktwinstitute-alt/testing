'use strict';

// ─── State ────────────────────────────────────────────────────────────────────
let appState = {
  subjects: [],
  settings: { dailyGoalMinutes: 120, streakMinutes: 30, blockedApps: [] },
  todayStats: { totalMinutes: 0, bySubject: [] },
  streak: 0,
  exams: [],
  qrDataUrl: '',
  ip: '',
  token: '',
  hostname: '',
  phoneConnected: false,
  // session
  activeSession: null,  // { sessionId, startTime, subjectId, subjectName }
  timerInterval: null
};

// ─── Utility ──────────────────────────────────────────────────────────────────

function getGreeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

function formatDate(d) {
  return new Date(d).toLocaleDateString('en-US', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
  });
}

function formatDateShort(d) {
  return new Date(d).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric'
  });
}

function daysUntil(dateStr) {
  const today = new Date(); today.setHours(0,0,0,0);
  const target = new Date(dateStr); target.setHours(0,0,0,0);
  return Math.round((target - today) / 86400000);
}

function pad2(n) { return String(n).padStart(2, '0'); }

function formatHMS(totalSeconds) {
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  return `${pad2(h)}:${pad2(m)}:${pad2(s)}`;
}

function getElapsedSeconds(startTime) {
  return Math.floor((Date.now() - new Date(startTime).getTime()) / 1000);
}

// ─── Toast ────────────────────────────────────────────────────────────────────

function showToast(title, body) {
  const container = document.getElementById('toast-container');
  const el = document.createElement('div');
  el.className = 'toast';
  el.innerHTML = `<div class="toast-title">${title}</div><div class="toast-body">${body}</div>`;
  container.appendChild(el);
  setTimeout(() => { el.remove(); }, 5000);
}

// ─── Navigation ───────────────────────────────────────────────────────────────

function switchView(viewId) {
  document.querySelectorAll('.view-section').forEach(s => s.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));

  const target = document.getElementById(`view-${viewId}`);
  if (target) target.classList.add('active');

  const navBtn = document.querySelector(`.nav-item[data-view="${viewId}"]`);
  if (navBtn) navBtn.classList.add('active');

  // Load data for the view
  if (viewId === 'analytics') loadAnalytics();
  if (viewId === 'exams')     loadExams();
  if (viewId === 'settings')  loadSettings();
}

// ─── Dashboard ────────────────────────────────────────────────────────────────

function renderDashboard() {
  // Greeting
  document.getElementById('greeting').textContent = `${getGreeting()}!`;
  document.getElementById('today-date').textContent = formatDate(new Date());

  // Streak
  document.getElementById('streak-badge').textContent = `🔥 ${appState.streak} Day Streak`;

  // Progress
  const { totalMinutes } = appState.todayStats;
  const goal = appState.settings.dailyGoalMinutes || 120;
  const pct = Math.min(100, Math.round((totalMinutes / goal) * 100));
  document.getElementById('progress-text').textContent = `${totalMinutes} / ${goal} min`;
  document.getElementById('progress-bar-inner').style.width = `${pct}%`;

  // Today's subjects
  const pillsEl = document.getElementById('today-subjects');
  pillsEl.innerHTML = '';
  for (const s of appState.todayStats.bySubject) {
    const pill = document.createElement('div');
    pill.className = 'subject-pill';
    pill.textContent = `${s.subjectName} · ${s.minutes}m`;
    pillsEl.appendChild(pill);
  }

  // Subject dropdown
  populateSubjectDropdown();

  // Upcoming exams
  renderUpcomingExams();
}

function populateSubjectDropdown() {
  const sel = document.getElementById('subject-select');
  const currentVal = sel.value;
  sel.innerHTML = '<option value="" disabled>Select a subject…</option>';
  for (const sub of appState.subjects) {
    const opt = document.createElement('option');
    opt.value = sub.id;
    opt.dataset.name = sub.name;
    opt.textContent = sub.name;
    sel.appendChild(opt);
  }
  const addOpt = document.createElement('option');
  addOpt.value = '__add__';
  addOpt.textContent = '+ Add custom subject…';
  sel.appendChild(addOpt);

  if (currentVal) sel.value = currentVal;
}

function renderUpcomingExams() {
  const container = document.getElementById('upcoming-exams-list');
  container.innerHTML = '';
  if (!appState.exams || appState.exams.length === 0) {
    container.innerHTML = '<p class="no-data">No upcoming exams</p>';
    return;
  }
  for (const exam of appState.exams.slice(0, 3)) {
    const days = daysUntil(exam.date);
    const row = document.createElement('div');
    row.className = 'exam-compact-row';
    row.innerHTML = `
      <div>
        <div class="exam-compact-name">${exam.name}</div>
        <div class="exam-compact-meta">${exam.subject} · ${formatDateShort(exam.date)}</div>
      </div>
      <div class="exam-days-left">${days === 0 ? 'Today!' : days < 0 ? 'Past' : `${days}d`}</div>
    `;
    container.appendChild(row);
  }
}

// ─── Active Session ───────────────────────────────────────────────────────────

function startTimerTick() {
  if (appState.timerInterval) clearInterval(appState.timerInterval);
  appState.timerInterval = setInterval(() => {
    if (!appState.activeSession) return;
    const elapsed = getElapsedSeconds(appState.activeSession.startTime);
    document.getElementById('timer-display').textContent = formatHMS(elapsed);
  }, 1000);
}

function showActiveSession() {
  // Hide idle, show active
  document.getElementById('view-dashboard').classList.remove('active');
  document.getElementById('view-active').classList.add('active');
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  document.querySelector('.nav-item[data-view="dashboard"]').classList.add('active');

  // Set subject name
  document.getElementById('active-subject-name').textContent =
    appState.activeSession.subjectName;

  // Reset timer display
  document.getElementById('timer-display').textContent = '00:00:00';

  // Phone status
  updatePhoneFocusStatus();

  startTimerTick();
}

function showIdleDashboard() {
  document.getElementById('view-active').classList.remove('active');
  document.getElementById('view-dashboard').classList.add('active');
  if (appState.timerInterval) {
    clearInterval(appState.timerInterval);
    appState.timerInterval = null;
  }
  appState.activeSession = null;
}

function updatePhoneFocusStatus() {
  const el = document.getElementById('phone-focus-status');
  if (appState.phoneConnected) {
    el.className = 'badge badge-active mt-12';
    el.textContent = '🔒 Focus Mode Active';
  } else {
    el.className = 'badge badge-disconnected mt-12';
    el.textContent = '⚠️ Phone not connected';
  }
}

// ─── Analytics ────────────────────────────────────────────────────────────────

async function loadAnalytics() {
  const data = await window.api.getAnalytics();
  document.getElementById('analytics-streak').textContent  = data.currentStreak;
  document.getElementById('analytics-longest').textContent = data.longestStreak;
  document.getElementById('analytics-total').textContent   = data.allTimeMinutes;

  const tbody = document.getElementById('analytics-tbody');
  tbody.innerHTML = '';
  if (!data.perSubject || data.perSubject.length === 0) {
    tbody.innerHTML = '<tr><td colspan="3" class="no-data">No sessions yet</td></tr>';
    return;
  }
  for (const s of data.perSubject) {
    const tr = document.createElement('tr');
    const hours = (s.totalMinutes / 60).toFixed(1);
    tr.innerHTML = `<td>${s.name}</td><td>${s.totalMinutes}</td><td>${hours}</td>`;
    tbody.appendChild(tr);
  }
}

// ─── Exams ────────────────────────────────────────────────────────────────────

async function loadExams() {
  const exams = await window.api.getExams();
  renderExamsList(exams);
}

function renderExamsList(exams) {
  const container = document.getElementById('exams-list');
  container.innerHTML = '';
  if (!exams || exams.length === 0) {
    container.innerHTML = '<p class="no-data">No exams added yet.</p>';
    return;
  }
  for (const exam of exams) {
    const days = daysUntil(exam.date);
    const urgentClass = days <= 3 && days >= 0 ? 'urgent' : '';
    const daysText = days < 0 ? 'Past' : days === 0 ? 'Today!' : `${days} days`;

    const row = document.createElement('div');
    row.className = 'exams-list-row';
    row.innerHTML = `
      <div class="exam-row-info">
        <div class="exam-row-name">${exam.name}</div>
        <div class="exam-row-meta">
          ${exam.subject}
          ${exam.date ? ' · ' + formatDateShort(exam.date) : ''}
          ${exam.time ? ' · ' + exam.time : ''}
          ${exam.location ? ' · ' + exam.location : ''}
        </div>
      </div>
      <div class="exam-row-days ${urgentClass}">${daysText}</div>
      <button class="btn btn-sm btn-danger" data-exam-id="${exam.id}">🗑</button>
    `;
    container.appendChild(row);
  }

  container.querySelectorAll('[data-exam-id]').forEach(btn => {
    btn.addEventListener('click', async () => {
      await window.api.deleteExam({ id: btn.dataset.examId });
      await loadExams();
    });
  });
}

// ─── Settings ─────────────────────────────────────────────────────────────────

async function loadSettings() {
  const s = await window.api.getSettings();
  document.getElementById('setting-goal').value   = s.dailyGoalMinutes;
  document.getElementById('setting-streak').value = s.streakMinutes;
  document.getElementById('setting-blocked').value = (s.blockedApps || []).join('\n');
  appState.settings = s;

  await refreshSubjectsList();
}

async function refreshSubjectsList() {
  const subjects = await window.api.getSubjects();
  appState.subjects = subjects;
  const container = document.getElementById('subjects-list');
  container.innerHTML = '';
  for (const s of subjects) {
    const tag = document.createElement('div');
    tag.className = 'subject-tag';
    tag.textContent = s.name;
    container.appendChild(tag);
  }
  populateSubjectDropdown();
}

// ─── Event Wiring ─────────────────────────────────────────────────────────────

function wireEvents() {
  // Sidebar nav
  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.addEventListener('click', () => {
      const v = btn.dataset.view;
      if (appState.activeSession) {
        // If session active, only allow switching away with confirmation
        if (v !== 'dashboard') {
          switchView(v);
        }
      } else {
        switchView(v);
      }
    });
  });

  // Subject select – handle "+ Add custom"
  document.getElementById('subject-select').addEventListener('change', (e) => {
    if (e.target.value === '__add__') {
      e.target.value = '';
      document.getElementById('add-subject-inline').style.display = 'block';
      document.getElementById('new-subject-input').focus();
    }
  });

  // Add subject confirm
  document.getElementById('add-subject-confirm').addEventListener('click', async () => {
    const name = document.getElementById('new-subject-input').value.trim();
    if (!name) return;
    const subject = await window.api.addSubject({ name });
    appState.subjects.push(subject);
    populateSubjectDropdown();
    document.getElementById('subject-select').value = subject.id;
    document.getElementById('add-subject-inline').style.display = 'none';
    document.getElementById('new-subject-input').value = '';
  });

  // Add subject cancel
  document.getElementById('add-subject-cancel').addEventListener('click', () => {
    document.getElementById('add-subject-inline').style.display = 'none';
    document.getElementById('new-subject-input').value = '';
  });

  // START button
  document.getElementById('btn-start').addEventListener('click', async () => {
    const sel = document.getElementById('subject-select');
    const subjectId = sel.value;
    if (!subjectId || subjectId === '__add__') {
      showToast('No Subject', 'Please select a subject before starting.');
      return;
    }
    const subjectName = sel.options[sel.selectedIndex].dataset.name || sel.options[sel.selectedIndex].textContent;
    const result = await window.api.startSession({ subjectId, subjectName });
    appState.activeSession = { sessionId: result.sessionId, startTime: result.startTime, subjectId, subjectName };
    // Send FOCUS_ON to phone
    await window.api.sendFocusOn({ subject: subjectName });
    showActiveSession();
  });

  // END button
  document.getElementById('btn-end').addEventListener('click', async () => {
    if (!appState.activeSession) return;
    const notes = document.getElementById('session-notes').value;
    const result = await window.api.endSession({ sessionId: appState.activeSession.sessionId, notes });
    // Update state
    appState.todayStats = result.todayStats || appState.todayStats;
    appState.streak     = result.streak     || appState.streak;
    document.getElementById('session-notes').value = '';
    showIdleDashboard();
    renderDashboard();
    showToast('Session Complete', `Studied for ${result.durationMinutes || 0} minutes.`);
  });

  // Regenerate QR token
  document.getElementById('btn-regen').addEventListener('click', async () => {
    const result = await window.api.regenerateToken();
    appState.qrDataUrl = result.qrDataUrl;
    appState.token     = result.token;
    document.getElementById('qr-img').src = result.qrDataUrl;
    document.getElementById('connect-token').textContent = result.token;
  });

  // Add exam
  document.getElementById('btn-add-exam').addEventListener('click', async () => {
    const name     = document.getElementById('exam-name').value.trim();
    const subject  = document.getElementById('exam-subject').value.trim();
    const date     = document.getElementById('exam-date').value;
    const time     = document.getElementById('exam-time').value;
    const location = document.getElementById('exam-location').value.trim();
    const notes    = document.getElementById('exam-notes').value.trim();
    if (!name || !subject || !date) {
      showToast('Missing Fields', 'Exam name, subject, and date are required.');
      return;
    }
    await window.api.addExam({ name, subject, date, time, location, notes });
    document.getElementById('exam-name').value = '';
    document.getElementById('exam-subject').value = '';
    document.getElementById('exam-date').value = '';
    document.getElementById('exam-time').value = '';
    document.getElementById('exam-location').value = '';
    document.getElementById('exam-notes').value = '';
    await loadExams();
    // Refresh upcoming exams on dashboard too
    const dashData = await window.api.getDashboardData();
    appState.exams = dashData.upcomingExams || [];
    renderUpcomingExams();
  });

  // Save settings
  document.getElementById('btn-save-settings').addEventListener('click', async () => {
    const dailyGoalMinutes = parseInt(document.getElementById('setting-goal').value, 10);
    const streakMinutes    = parseInt(document.getElementById('setting-streak').value, 10);
    const blockedAppsRaw   = document.getElementById('setting-blocked').value;
    const blockedApps = blockedAppsRaw
      .split('\n')
      .map(l => l.trim())
      .filter(l => l.length > 0);
    await window.api.saveSettings({ dailyGoalMinutes, streakMinutes, blockedApps });
    appState.settings.dailyGoalMinutes = dailyGoalMinutes;
    appState.settings.streakMinutes    = streakMinutes;
    appState.settings.blockedApps      = blockedApps;
    renderDashboard();
    showToast('Settings Saved', 'Your preferences have been updated.');
  });

  // Add subject from settings
  document.getElementById('btn-add-subject-settings').addEventListener('click', async () => {
    const name = document.getElementById('new-subject-settings').value.trim();
    if (!name) return;
    await window.api.addSubject({ name });
    document.getElementById('new-subject-settings').value = '';
    await refreshSubjectsList();
  });

  // Phone events
  window.api.onPhoneConnected((data) => {
    appState.phoneConnected = true;
    updateSidebarPhoneStatus(true);
    updatePhoneFocusStatus();
    updateConnectStatus(true);
    showToast('Phone Connected', `${data.deviceId || 'Device'} is now paired.`);
  });

  window.api.onPhoneDisconnected(() => {
    appState.phoneConnected = false;
    updateSidebarPhoneStatus(false);
    updatePhoneFocusStatus();
    updateConnectStatus(false);
    showToast('Phone Disconnected', 'Your phone has disconnected.');
  });

  // Interruption event
  window.api.onInterruption((data) => {
    showToast('📱 App Interruption Detected', `App opened on phone: ${data.app}`);
  });
}

function updateSidebarPhoneStatus(connected) {
  const el = document.getElementById('phone-status-sidebar');
  if (connected) {
    el.className = 'badge badge-connected';
    el.textContent = '● Phone Connected';
  } else {
    el.className = 'badge badge-disconnected';
    el.textContent = '● Phone Disconnected';
  }
}

function updateConnectStatus(connected) {
  const el = document.getElementById('connect-status-badge');
  if (connected) {
    el.className = 'badge badge-connected mb-16';
    el.textContent = '● Connected';
  } else {
    el.className = 'badge badge-disconnected mb-16';
    el.textContent = '● Not Connected';
  }
}

// ─── Bootstrap ────────────────────────────────────────────────────────────────

async function init() {
  const data = await window.api.getInitData();

  appState.subjects      = data.subjects       || [];
  appState.todayStats    = data.todayStats      || { totalMinutes: 0, bySubject: [] };
  appState.streak        = data.streak          || 0;
  appState.exams         = data.exams           || [];
  appState.settings      = data.settings        || { dailyGoalMinutes: 120, streakMinutes: 30, blockedApps: [] };
  appState.qrDataUrl     = data.qrDataUrl       || '';
  appState.ip            = data.ip              || '';
  appState.token         = data.token           || '';
  appState.hostname      = data.hostname        || '';
  appState.phoneConnected = data.phoneConnected || false;

  // Connect Phone screen static data
  document.getElementById('qr-img').src                 = appState.qrDataUrl;
  document.getElementById('connect-ip').textContent     = `ws://${appState.ip}:5417`;
  document.getElementById('connect-token').textContent  = appState.token;
  document.getElementById('connect-hostname').textContent = appState.hostname;

  // Sidebar phone status
  updateSidebarPhoneStatus(appState.phoneConnected);

  // Wire all events
  wireEvents();

  // Render initial dashboard
  renderDashboard();
  switchView('dashboard');
}

document.addEventListener('DOMContentLoaded', init);
