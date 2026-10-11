const express = require('express');
const cors = require('cors');
const axios = require('axios');

const app = express();
app.use(cors()); // 允许前端跨域访问
app.use(express.json());

// 你的抓取逻辑（原封不动搬过来）
async function fetchScheduleFromJwxt() {
    const url = 'https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151';
    const payload = 'xnm=2026&xqm=3&kzlx=ck&xsdm=&kclbdm=&kclxdm=';
    // ⚠️ 这里换成你最新抓到的 JSESSIONID
    const cookieValue = 'JSESSIONID=你自己的; EXPIRED_MODAL=exist'; 

    const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        'Referer': 'https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkbIndex.html?gnmkdm=N2151&layout=default',
        'Cookie': cookieValue
    };

    const response = await axios.post(url, payload, { headers });
    return response.data;
}

// 对外提供一个 API 接口
app.get('/api/schedule', async (req, res) => {
    try {
        console.log("收到前端请求，正在抓取课表...");
        const data = await fetchScheduleFromJwxt();
        if (data && data.kbList) {
            res.json({ success: true, courses: data.kbList });
        } else {
            res.status(401).json({ success: false, message: "Cookie 失效，请重新抓包" });
        }
    } catch (error) {
        console.error(error);
        res.status(500).json({ success: false, message: "服务器错误: " + error.message });
    }
});

// 启动服务器
const PORT = 3000;
app.listen(PORT, () => {
    console.log(`✅ 后端服务器已启动：http://127.0.0.1:${PORT}`);
    console.log(`📋 课表接口地址：http://127.0.0.1:${PORT}/api/schedule`);
});