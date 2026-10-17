package com.cug.schedule;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
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

    @PluginMethod
    public void openLogin(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
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

                // 快捷“进入教务/同步课表”按钮
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

                // 关键：启用第三方 Cookie 与持久化 Cookie 接收
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                cookieManager.setAcceptThirdPartyCookies(webView, true);

                btnAction.setOnClickListener(v -> {
                    String curUrl = webView.getUrl();
                    if (curUrl != null && curUrl.contains("jwglxt")) {
                        title.setText("⚡ 正在立即提取课表凭据...");
                        checkCookies(call, webView);
                    } else {
                        title.setText("⚡ 正在触发进入教务管理...");
                        triggerClickJw(webView);
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

                // 处理新窗口与多窗口重定向，直接在当前配置好的主 webView 中打开
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
                        checkCookies(call, view);

                        if (url != null) {
                            if (url.contains("i.cug.edu.cn")) {
                                title.setText("⚡ 登录成功！正在跳转教务管理...");
                                btnAction.setText("点击进入教务");
                                triggerClickJw(view);
                            } else if (url.contains("jwglxt")) {
                                title.setText("🎉 已进入教务系统！正在同步课表...");
                                btnAction.setText("立即同步");
                                checkCookies(call, view);
                            }
                        }
                    }
                });

                layout.addView(webView);
                loginDialog.setContentView(layout);
                loginDialog.show();

                startCookiePolling(call);

                webView.loadUrl("https://sfrz.cug.edu.cn/authserver/login?service=https%3A%2F%2Fi.cug.edu.cn%2Fweb%2F%3FCASLOGIN%3DCASLOGIN");

            } catch (Exception e) {
                call.reject("打开登录窗口失败: " + e.getMessage());
            }
        });
    }

    private void triggerClickJw(WebView webView) {
        String js = "javascript:(function(){" +
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
                "if(!clickTarget()){" +
                "  var c = 0;" +
                "  var tId = setInterval(function(){" +
                "    c++;" +
                "    if(clickTarget() || c > 30) clearInterval(tId);" +
                "  }, 400);" +
                "}" +
                "})()";
        webView.evaluateJavascript(js, null);
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
        CookieManager cm = CookieManager.getInstance();
        String currentUrl = (webView != null) ? webView.getUrl() : null;

        // 覆盖所有可能的正方教务系统 Path（Path=/jwglxt）与顶级域名
        String[] testUrls = new String[] {
                currentUrl,
                "https://jwgl.cug.edu.cn/jwglxt/",
                "https://jwgl.cug.edu.cn/jwglxt/xtgl/index_initMenu.html",
                "https://jwgl.cug.edu.cn/jwglxt/kbcx/xskbcx_cxXsgrkbIndex.html",
                "https://jwgl.cug.edu.cn/",
                "http://jwgl.cug.edu.cn/jwglxt/",
                "http://jwgl.cug.edu.cn/",
                "https://cug.edu.cn/"
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

                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    ret.put("rawJson", sb.toString());
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
