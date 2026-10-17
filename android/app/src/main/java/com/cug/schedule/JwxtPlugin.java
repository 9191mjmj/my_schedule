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

@CapacitorPlugin(name = "Jwxt")
public class JwxtPlugin extends Plugin {

    private Dialog loginDialog;
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

                // 备用“进入教务”按钮
                Button btnJw = new Button(getActivity());
                btnJw.setText("进入教务");
                btnJw.setTextSize(12);
                btnJw.setTextColor(Color.WHITE);
                btnJw.setBackgroundColor(Color.parseColor("#2563eb"));
                btnJw.setPadding(20, 10, 20, 10);
                header.addView(btnJw);

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
                webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setUseWideViewPort(true);
                webView.getSettings().setLoadWithOverviewMode(true);
                webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
                webView.getSettings().setSupportMultipleWindows(true);

                btnJw.setOnClickListener(v -> {
                    title.setText("⚡ 正在加载教务系统...");
                    webView.loadUrl("https://jwgl.cug.edu.cn/jwglxt/xtgl/index_initMenu.html");
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

                // 处理新窗口与多窗口重定向，保证在同一个 WebView 内打开
                webView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                        WebView newWebView = new WebView(getActivity());
                        newWebView.setWebViewClient(new WebViewClient() {
                            @Override
                            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                                String targetUrl = req.getUrl().toString();
                                view.loadUrl(targetUrl);
                                return true;
                            }
                        });
                        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                        transport.setWebView(newWebView);
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
                        checkCookies(call);

                        // 当检测到已进入智慧门户 i.cug.edu.cn，自动执行点击“综合教务”
                        if (url != null && url.contains("i.cug.edu.cn")) {
                            title.setText("⚡ 登录成功！正在跳转教务系统...");
                            String js = "javascript:(function(){" +
                                    "function clickJw(){" +
                                    "  var all = document.querySelectorAll('*');" +
                                    "  for(var i=0; i<all.length; i++){" +
                                    "    var t = all[i].textContent ? all[i].textContent.trim() : '';" +
                                    "    if(t === '综合教务' || t === '教务系统'){" +
                                    "      all[i].click();" +
                                    "      if(all[i].parentElement) all[i].parentElement.click();" +
                                    "      return true;" +
                                    "    }" +
                                    "  }" +
                                    "  return false;" +
                                    "}" +
                                    "if(!clickJw()){" +
                                    "  var c = 0;" +
                                    "  var tId = setInterval(function(){" +
                                    "    c++;" +
                                    "    if(clickJw() || c > 25) clearInterval(tId);" +
                                    "  }, 400);" +
                                    "}" +
                                    "})()";
                            view.evaluateJavascript(js, null);
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

    private void startCookiePolling(PluginCall call) {
        stopCookiePolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (loginDialog != null && loginDialog.isShowing()) {
                    if (!checkCookies(call)) {
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

    private boolean checkCookies(PluginCall call) {
        String cookies = CookieManager.getInstance().getCookie("https://jwgl.cug.edu.cn");
        if (cookies == null || cookies.isEmpty()) {
            cookies = CookieManager.getInstance().getCookie("http://jwgl.cug.edu.cn");
        }
        if (cookies != null && cookies.contains("JSESSIONID=")) {
            String jsessionId = "";
            for (String part : cookies.split(";")) {
                part = part.trim();
                if (part.startsWith("JSESSIONID=")) {
                    jsessionId = part.substring("JSESSIONID=".length());
                    break;
                }
            }

            if (!jsessionId.isEmpty()) {
                stopCookiePolling();
                if (loginDialog != null && loginDialog.isShowing()) {
                    loginDialog.dismiss();
                    loginDialog = null;
                }
                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("jsessionid", jsessionId);
                call.resolve(ret);
                return true;
            }
        }
        return false;
    }

    @PluginMethod
    public void fetchSchedule(PluginCall call) {
        new Thread(() -> {
            try {
                String jsessionId = call.getString("jsessionid", "");
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
                conn.setRequestProperty("Cookie", "JSESSIONID=" + jsessionId);

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
