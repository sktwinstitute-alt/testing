'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  getInitData:      ()       => ipcRenderer.invoke('get-init-data'),
  getDashboardData: ()       => ipcRenderer.invoke('get-dashboard-data'),
  startSession:     (args)   => ipcRenderer.invoke('start-session', args),
  endSession:       (args)   => ipcRenderer.invoke('end-session', args),
  getAnalytics:     ()       => ipcRenderer.invoke('get-analytics'),
  getExams:         ()       => ipcRenderer.invoke('get-exams'),
  addExam:          (args)   => ipcRenderer.invoke('add-exam', args),
  deleteExam:       (args)   => ipcRenderer.invoke('delete-exam', args),
  getSettings:      ()       => ipcRenderer.invoke('get-settings'),
  saveSettings:     (args)   => ipcRenderer.invoke('save-settings', args),
  getSubjects:      ()       => ipcRenderer.invoke('get-subjects'),
  addSubject:       (args)   => ipcRenderer.invoke('add-subject', args),
  regenerateToken:  ()       => ipcRenderer.invoke('regenerate-token'),
  sendFocusOn:      (args)   => ipcRenderer.invoke('send-focus-on', args),

  onPhoneConnected:    (cb) => ipcRenderer.on('phone-connected',    (_e, data) => cb(data)),
  onPhoneDisconnected: (cb) => ipcRenderer.on('phone-disconnected', ()         => cb()),
  onInterruption:      (cb) => ipcRenderer.on('interruption',       (_e, data) => cb(data))
});
