# 🎓 地大课表 (CUG Schedule Desktop)

<p align="center">
  <img src="https://img.shields.io/badge/Electron-44.7.0-47848F?style=for-the-badge&logo=electron&logoColor=white" alt="Electron">
  <img src="https://img.shields.io/badge/Platform-Windows-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Windows">
  <img src="https://img.shields.io/badge/CUG-中国地质大学（武汉）-10B981?style=for-the-badge" alt="CUG">
  <img src="https://img.shields.io/badge/UI-Neo--Glassmorphism-6366F1?style=for-the-badge" alt="UI">
</p>

专为**中国地质大学（武汉）**本科生量身打造的高颜值桌面课表客户端。  
支持**统一身份认证直连登录、自动抓取教务系统真实课表、离线毫秒秒开、防撞色智能排课与单文件打包分发**。

---

## ✨ 核心特性

- 📥 **直连官方统一认证（CAS SSO）**  
  内置安全隔离沙箱（`<webview>`），直接加载学校官方认证中心登录，无需手动抓包或复制 Cookie。登录成功后全自动捕获 `JSESSIONID` 会话并完成课表同步。
- 🎨 **Neo-Glassmorphism + Bento Grid 现代设计**  
  融合 Apple / Linear 的现代毛玻璃与 Bento 状态卡片设计。支持深色黑曜石（Obsidian）与浅色晨光（Airy Light）一键切换，支持自定义上传桌面壁纸。
- 🌈 **18 色高对比度防撞色系统**  
  彻底解决课程相近色混淆痛点！采用色轮 $\ge 120^\circ$ 互补色彩分配算法，对当学期所有课程建立确定性映射，保证周内相邻课程色彩绝不撞色。
- ⚡ **离线毫秒级秒开**  
  课表数据持久化缓存于本地。自习室、无网络或教务系统维护时仍可 0 毫秒秒开查看；联网时后台静默同步最新变动。
- 🧩 **复杂冲突课程横向并排算法**  
  遇到单双周重叠或多门实践/实验课占用同一时段时，系统自动识别并横向分割排布，保证所有课程信息清晰可见、悬浮独立查看。
- 🔍 **课程详情 Bento 模态弹窗**  
  点击任一课程卡片即可呼出详细弹窗，清晰展示上课地点、任课教师、周次、考核方式、学分及具体作息时间区间（如 `1-2节 (08:30 ~ 10:05)`）。
- ⚙️ **学期配置与作息自定义**  
  支持自定义开学基准日期（自动对齐周一换算 25 周）、学年学期切换（秋季/春季/短学期）及每日 6 大节上课时间自定义。

---

## 🛠️ 项目架构

```
课表项目/
├── main.js           # Electron 主进程：窗口管理、Cookie 嗅探与网络请求代理
├── preload.js        # 上下文隔离桥：安全向前端暴露 electronAPI
├── 美化课表1.html     # 渲染进程前端：Bento 界面、排课算法引擎与离线存储
├── package.json      # 项目元数据与 electron-builder 构建配置
└── .gitignore        # Git 忽略规范
```

---

## 🚀 快速上手与运行

### 1. 环境准备
确保你的电脑已安装 [Node.js](https://nodejs.org/)（推荐 Node.js ≥ 18）。

### 2. 本地开发与启动
```bash
# 克隆仓库
git clone https://github.com/9191mjmj/my_schedule.git
cd my_schedule

# 安装依赖
npm install

# 启动开发调试窗口
npm start
```

### 3. 打包生成 Windows 独立可执行程序
```bash
# 编译打包（输出安装包与便携版至 dist/ 目录）
npm run build
```
打包成功后，将在 `dist/` 目录下生成：
- **`地大课表 1.0.0.exe`**：免安装绿色单文件版，双击即用，便携分享；
- **`地大课表 Setup 1.0.0.exe`**：标准 Windows 安装向导，自动创建桌面与开始菜单快捷方式。

---

## 🔒 隐私与安全声明

- **零凭据存储**：本软件不收集、不上传、不在本地保存任何用户的学号与密码明文。
- **官方认证直连**：登录过程完全借由学校官方 CAS 页面完成，Cookie 安全由学校教务系统机制直接保障。
- **纯本地运行**：除向学校官方教务接口发起课表查询外，无任何第三方数据上报行为。

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。