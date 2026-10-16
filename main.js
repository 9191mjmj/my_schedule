const { app, BrowserWindow, session, ipcMain } = require('electron');
const path = require('path');
const axios = require('axios');

let mainWindow = null;

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    title: '我的课表',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webviewTag: true
    }
  });
  mainWindow.loadFile('index.html');
  //mainWindow.webContents.openDevTools();
}

ipcMain.handle('get-jwxt-cookie', async () => {
  const ses = session.fromPartition('persist:jwxt');
  const cookies = await ses.cookies.get({ name: 'JSESSIONID' });
  console.log('📋 所有 JSESSIONID 的 domain:', cookies.map(c => c.domain));

  // 优先匹配精准的教务系统域名，避免抓到统一认证或门户的 JSESSIONID
  const jwxtCookie = cookies.find(c => c.domain === 'jwgl.cug.edu.cn' || c.domain.endsWith('.jwgl.cug.edu.cn'))
    || cookies.find(c => c.domain.includes('jwgl.cug.edu.cn'))
    || cookies.find(c => c.domain === '.cug.edu.cn' || c.domain === 'cug.edu.cn');

  if (jwxtCookie) console.log('✅ 匹配到的 domain:', jwxtCookie.domain);
  return jwxtCookie ? jwxtCookie.value : null;
});

ipcMain.handle('fetch-schedule', async (event, params) => {
  const jsessionid = typeof params === 'string' ? params : (params && params.jsessionid);

  // 默认根据当前日期推算学年学期：8月及以后为当年秋季(3)；8月以前为前一年春季(12)
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  const defaultXnm = month >= 8 ? String(year) : String(year - 1);
  const defaultXqm = (month >= 2 && month < 8) ? '12' : '3';

  const xnm = (params && params.xnm) || defaultXnm;
  const xqm = (params && params.xqm) || defaultXqm;

  console.log(`🚀 主进程开始抓课表, 学年: ${xnm}, 学期: ${xqm}, Cookie:`, jsessionid ? jsessionid.substring(0, 15) + '...' : '(空)');
  
  const url = 'https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151';
  const payload = `xnm=${encodeURIComponent(xnm)}&xqm=${encodeURIComponent(xqm)}&kzlx=ck&xsdm=&kclbdm=&kclxdm=`;
  const headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
    'Referer': 'https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkbIndex.html?gnmkdm=N2151&layout=default',
    'Cookie': `JSESSIONID=${jsessionid}`
  };

  try {
    const res = await axios.post(url, payload, { headers, timeout: 10000 });
    if (res.data && res.data.kbList) {
      console.log('✅ 抓到', res.data.kbList.length, '门课程');
      return { success: true, courses: res.data.kbList, xnm, xqm };
    } else {
      console.log('⚠️ 返回数据为空:', res.data);
      return { success: false, message: 'Cookie 可能已失效或暂无当前学期课表数据' };
    }
  } catch (e) {
    console.error('❌ 抓取失败:', e.message);
    return { success: false, message: '抓取失败：' + e.message };
  }
});

app.on('web-contents-created', (event, contents) => {
  if (contents.getType() === 'webview') {
    contents.setWindowOpenHandler(({ url }) => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('webview-navigate', url);
      }
      return { action: 'deny' };
    });
  }
});

app.whenReady().then(createMainWindow);
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createMainWindow(); });