const express = require('express');
const cors = require('cors');
const axios = require('axios');
const path = require('path');

const app = express();
app.use(cors());
app.use(express.json()); // 解析前端传来的 JSON

// 提供前端静态文件（如果你直接用浏览器打开 index.html，这步其实用不到，但保留以防万一）
app.use(express.static(path.join(__dirname, 'public')));

// ⭐ 改成 POST 请求，接收用户的 Cookie
app.post('/api/schedule', async (req, res) => {
    const { cookie } = req.body;

    if (!cookie) {
        return res.status(400).json({ success: false, message: '请先填写你的 JSESSIONID' });
    }

    const url = 'https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151';
    const payload = 'xnm=2026&xqm=3&kzlx=ck&xsdm=&kclbdm=&kclxdm=';
    const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        'Referer': 'https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkbIndex.html?gnmkdm=N2151&layout=default',
        'Cookie': cookie   // ⭐ 用用户传过来的 Cookie
    };

    try {
        const response = await axios.post(url, payload, { headers, timeout: 10000 });
        if (response.data && response.data.kbList) {
            res.json({ success: true, courses: response.data.kbList });
        } else {
            res.status(401).json({ success: false, message: 'Cookie 失效，请重新登录教务系统获取' });
        }
    } catch (error) {
        res.status(500).json({ success: false, message: '抓取失败：' + error.message });
    }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`✅ 服务器已启动：http://127.0.0.1:${PORT}`));