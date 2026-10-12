const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  getJwxtCookie: () => ipcRenderer.invoke('get-jwxt-cookie'),
  fetchSchedule: (jsessionid) => ipcRenderer.invoke('fetch-schedule', jsessionid),
  onWebviewNavigate: (callback) => 
    ipcRenderer.on('webview-navigate', (e, url) => callback(url))
});