package com.cug.schedule;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CapacitorPlugin(name = "Jwxt")
public class JwxtPlugin extends Plugin {

    private Dialog loginDialog;
    private WebView activeWebView;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;

    public class JwxtBridge {
        private final PluginCall savedCall;

        public JwxtBridge(PluginCall call) {
            this.savedCall = call;
        }

        @JavascriptInterface
        public void onScheduleFetched(String rawJson) {
            getActivity().runOnUiThread(() -> {
                if (rawJson != null && rawJson.contains("kbList") && !rawJson.trim().startsWith("<")) {
                    stopCookiePolling();
                    if (loginDialog != null && loginDialog.isShowing()) {
                        loginDialog.dismiss();
                        loginDialog = null;
                    }
                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    ret.put("directData", true);
                    ret.put("rawJson", rawJson);
                    savedCall.resolve(ret);
                }
            });
        }
    }

    @PluginMethod
    public void openLogin(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                // 关键修复 1：每次打开登录窗口前，彻底清除旧的过期 Cookie，防止误认旧会话
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeAllCookies(null);
                cookieManager.flush();

                loginDialog = new Dialog(getActivity(), android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);

                LinearLayout layout = new LinearLayout(getActivity());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                // 顶部标题栏
                LinearLayout header = new LinearLayout(getActivity());
                header.setBackgroundColor(Color.parseColor("#1e293b"));
                header.setPadding(30, 40, 30, 30);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setGravity(Gravity.CENTER_VERTICAL);

                TextView title = new TextView(getActivity());
                title.setText("🔒 地大统一认证 · 登录后自动抓取");
                title.setTextColor(Color.WHITE);
                title.setTextSize(14);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                title.setLayoutParams(titleParams);
                header.addView(title);

                // 快捷“进入教务/立即抓取”按钮
                Button btnAction = new Button(getActivity());
                btnAction.setText("教务管理");
                btnAction.setTextSize(12);
                btnAction.setTextColor(Color.WHITE);
                btnAction.setBackgroundColor(Color.parseColor("#2563eb"));
                btnAction.setPadding(20, 10, 20, 10);
                header.addView(btnAction);

                // 关闭按钮
                Button btnClose = new Button(getActivity());
                btnClose.setText("✕");
                btnClose.setTextSize(14);
                btnClose.setTextColor(Color.WHITE);
                btnClose.setBackgroundColor(Color.TRANSPARENT);
                btnClose.setPadding(20, 10, 20, 10);
                header.addView(btnClose);

                layout.addView(header);

                WebView webView = new WebView(getActivity());
                activeWebView = webView;
                webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setUseWideViewPort(true);
                webView.getSettings().setLoadWithOverviewMode(true);
                webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
                webView.getSettings().setSupportMultipleWindows(true);

                // 关键：挂载原生 JS 桥接通道，支持网页内部直传课表数据
                webView.addJavascriptInterface(new JwxtBridge(call), "AppBridge");

                // 关键：启用第三方 Cookie 与持久化 Cookie 接收
                cookieManager.setAcceptCookie(true);
                cookieManager.setAcceptThirdPartyCookies(webView, true);

                btnAction.setOnClickListener(v -> {
                    String curUrl = webView.getUrl();
                    if (curUrl != null && (curUrl.contains("jwglxt") || curUrl.contains("jwgl.cug.edu.cn"))) {
                        title.setText("⚡ 正在立即提取课表数据...");
                        injectFetchScript(webView);
                        checkCookies(call, webView);
                    } else {
                        title.setText("⚡ 正在直接进入教务系统...");
                        webView.loadUrl("https://jwgl.cug.edu.cn/jwglxt/xtgl/login_slogin.html");
                    }
                });

                btnClose.setOnClickListener(v -> {
                    if (loginDialog != null && loginDialog.isShowing()) {
                        loginDialog.dismiss();
                    }
                });

                loginDialog.setOnCancelListener(dialog -> {
                    stopCookiePolling();
                    call.reject("已取消登录");
                });

                // 处理新窗口与多窗口重定向，直接在当前主 webView 中打开
                webView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                        transport.setWebView(view);
                        resultMsg.sendToTarget();
                        return true;
                    }
                });

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        String u = request.getUrl().toString();
                        view.loadUrl(u);
                        return true;
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);

                        if (url != null) {
                            if (url.contains("i.cug.edu.cn")) {
                                title.setText("⚡ 登录成功！正在跳转教务管理...");
                                btnAction.setText("点击进入教务");
                                triggerClickJw(view);
                                // 保底兜底：若 1.5 秒后仍在门户，直接通过单点登录加载教务系统
                                pollHandler.postDelayed(() -> {
                                    if (activeWebView != null) {
                                        String cur = activeWebView.getUrl();
                                        if (cur != null && cur.contains("i.cug.edu.cn")) {
                                            activeWebView.loadUrl("https://jwgl.cug.edu.cn/jwglxt/xtgl/login_slogin.html");
                                        }
                                    }
                                }, 1500);
                            } else if (url.contains("jwglxt") || url.contains("jwgl.cug.edu.cn")) {
                                title.setText("🎉 已进入教务系统！正在抓取课表...");
                                btnAction.setText("立即抓取课表");
                                injectFetchScript(view);
                                checkCookies(call, view);
                            }
                        }
                    }
                });

                layout.addView(webView);
                loginDialog.setContentView(layout);
                loginDialog.show();

                startCookiePolling(call);

                // 直接以教务系统为 service 单点登录入口，认证后直奔教务系统，彻底绕过门户周历！
                webView.loadUrl("https://sfrz.cug.edu.cn/authserver/login?service=https%3A%2F%2Fjwgl.cug.edu.cn%2Fjwglxt%2Fxtgl%2Flogin_slogin.html");

            } catch (Exception e) {
                call.reject("打开登录窗口失败: " + e.getMessage());
            }
        });
    }

    private void triggerClickJw(WebView webView) {
        String js = "javascript:(function(){" +
                "var links = document.querySelectorAll('a[href*=\"jwgl\"], a[href*=\"jwxt\"]');" +
                "if(links.length > 0){ links[0].click(); return; }" +
                "function clickTarget(){" +
                "  var keywords = ['教务管理', '综合教务', '教务系统', '学生课表', '学生个人课表'];" +
                "  var all = document.querySelectorAll('*');" +
                "  for(var i=0; i<all.length; i++){" +
                "    var el = all[i];" +
                "    if(el.children.length === 0){" +
                "      var t = el.textContent ? el.textContent.trim() : '';" +
                "      for(var k=0; k<keywords.length; k++){" +
                "        if(t === keywords[k] || (t.indexOf(keywords[k]) !== -1 && t.length < 8)){" +
                "          el.click();" +
                "          if(el.parentElement) el.parentElement.click();" +
                "          if(el.parentElement && el.parentElement.parentElement) el.parentElement.parentElement.click();" +
                "          return true;" +
                "        }" +
                "      }" +
                "    }" +
                "  }" +
                "  return false;" +
                "}" +
                "function clickAppTab(){" +
                "  var tabKeywords = ['应用中心', '应用', '全部应用', '服务中心', '办事大厅', '服务', '业务系统'];" +
                "  var tabs = document.querySelectorAll('*');" +
                "  for(var j=0; j<tabs.length; j++){" +
                "    var txt = tabs[j].children.length === 0 ? (tabs[j].textContent||'').trim() : '';" +
                "    for(var k=0; k<tabKeywords.length; k++){" +
                "      if(txt === tabKeywords[k]){" +
                "        tabs[j].click();" +
                "        if(tabs[j].parentElement) tabs[j].parentElement.click();" +
                "        return true;" +
                "      }" +
                "    }" +
                "  }" +
                "  return false;" +
                "}" +
                "if(!clickTarget()){" +
                "  clickAppTab();" +
                "  var c = 0;" +
                "  var tId = setInterval(function(){" +
                "    c++;" +
                "    if(clickTarget() || c > 30) clearInterval(tId);" +
                "  }, 400);" +
                "}" +
                "})()";
        webView.evaluateJavascript(js, null);
    }

    private void injectFetchScript(WebView view) {
        String js = "javascript:(function(){" +
                "if(window.__jw_fetching) return;" +
                "window.__jw_fetching = true;" +
                "var payload = 'xnm=2026&xqm=3&kzlx=ck&xsdm=&kclbdm=&kclxdm=';" +
                "fetch('/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151', {" +
                "  method: 'POST'," +
                "  headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }," +
                "  body: payload" +
                "}).then(function(r){ return r.text(); })" +
                ".then(function(text){" +
                "  if(text && text.indexOf('kbList') !== -1 && !text.trim().startsWith('<')){" +
                "    if(window.AppBridge) window.AppBridge.onScheduleFetched(text);" +
                "  } else {" +
                "    window.__jw_fetching = false;" +
                "  }" +
                "}).catch(function(err){ window.__jw_fetching = false; });" +
                "})()";
        view.evaluateJavascript(js, null);
    }

    private void startCookiePolling(PluginCall call) {
        stopCookiePolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (loginDialog != null && loginDialog.isShowing()) {
                    if (!checkCookies(call, activeWebView)) {
                        pollHandler.postDelayed(this, 800);
                    }
                }
            }
        };
        pollHandler.postDelayed(pollRunnable, 800);
    }

    private void stopCookiePolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private boolean checkCookies(PluginCall call, WebView webView) {
        String currentUrl = (webView != null) ? webView.getUrl() : null;

        // 关键防护 2：只有当前页面已经实际跳转进入教务系统（jwglxt）时，才允许检测 Cookie 并关闭登录窗口！
        if (currentUrl == null || (!currentUrl.contains("jwglxt") && !currentUrl.contains("jwgl.cug.edu.cn"))) {
            return false;
        }

        CookieManager cm = CookieManager.getInstance();
        String[] testUrls = new String[] {
                currentUrl,
                "https://jwgl.cug.edu.cn/jwglxt/",
                "https://jwgl.cug.edu.cn/jwglxt/xtgl/index_initMenu.html",
                "https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkbIndex.html",
                "https://jwgl.cug.edu.cn/"
        };

        Pattern p = Pattern.compile("(?i)JSESSIONID=([^;\\s]+)");

        for (String u : testUrls) {
            if (u == null || u.isEmpty()) continue;
            String cookies = cm.getCookie(u);
            if (cookies == null || cookies.isEmpty()) continue;

            Matcher m = p.matcher(cookies);
            if (m.find()) {
                String jsessionId = m.group(1);
                if (jsessionId != null && !jsessionId.isEmpty()) {
                    stopCookiePolling();
                    if (loginDialog != null && loginDialog.isShowing()) {
                        loginDialog.dismiss();
                        loginDialog = null;
                    }
                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    ret.put("jsessionid", jsessionId);
                    ret.put("fullCookies", cookies);
                    call.resolve(ret);
                    return true;
                }
            }
        }
        return false;
    }

    @PluginMethod
    public void fetchSchedule(PluginCall call) {
        new Thread(() -> {
            try {
                String jsessionId = call.getString("jsessionid", "");
                String fullCookies = call.getString("fullCookies", "");
                String xnm = call.getString("xnm", "2026");
                String xqm = call.getString("xqm", "3");

                URL url = new URL("https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari/537.36");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                conn.setRequestProperty("Referer", "https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkbIndex.html?gnmkdm=N2151&layout=default");

                String cookieHeader = (fullCookies != null && !fullCookies.isEmpty()) ? fullCookies : ("JSESSIONID=" + jsessionId);
                conn.setRequestProperty("Cookie", cookieHeader);

                String payload = "xnm=" + URLEncoder.encode(xnm, "UTF-8")
                        + "&xqm=" + URLEncoder.encode(xqm, "UTF-8")
                        + "&kzlx=ck&xsdm=&kclbdm=&kclxdm=";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes("UTF-8"));
                    os.flush();
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();

                    String raw = sb.toString();
                    if (raw.trim().startsWith("<") || !raw.contains("kbList")) {
                        call.reject("教务系统会话已失效，请重新登录");
                        return;
                    }

                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    ret.put("rawJson", raw);
                    call.resolve(ret);
                } else {
                    call.reject("教务系统响应状态码错误: " + code);
                }
            } catch (Exception e) {
                call.reject("请求教务系统失败: " + e.getMessage());
            }
        }).start();
    }
}
