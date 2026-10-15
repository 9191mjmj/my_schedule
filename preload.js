const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  getJwxtCookie: () => ipcRenderer.invoke('get-jwxt-cookie'),
  fetchSchedule: (params) => ipcRenderer.invoke('fetch-schedule', params),
  onWebviewNavigate: (callback) => 
    ipcRenderer.on('webview-navigate', (e, url) => callback(url))
});