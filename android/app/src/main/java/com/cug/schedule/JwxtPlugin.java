package com.cug.schedule;

import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.ViewGroup;

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

                TextView title = new TextView(getActivity());
                title.setText("🔒 地大统一身份认证 · 登录后自动抓取");
                title.setTextColor(Color.WHITE);
                title.setTextSize(15);
                header.addView(title);

                layout.addView(header);

                WebView webView = new WebView(getActivity());
                webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
                webView.getSettings().setUseWideViewPort(true);
                webView.getSettings().setLoadWithOverviewMode(true);

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        checkCookies(call);
                    }
                });

                layout.addView(webView);
                loginDialog.setContentView(layout);
                loginDialog.show();

                webView.loadUrl("https://sfrz.cug.edu.cn/authserver/login?service=https%3A%2F%2Fi.cug.edu.cn%2Fweb%2F%3FCASLOGIN%3DCASLOGIN");

            } catch (Exception e) {
                call.reject("打开登录窗口失败: " + e.getMessage());
            }
        });
    }

    private void checkCookies(PluginCall call) {
        String cookies = CookieManager.getInstance().getCookie("https://jwgl.cug.edu.cn");
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
                if (loginDialog != null && loginDialog.isShowing()) {
                    loginDialog.dismiss();
                    loginDialog = null;
                }
                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("jsessionid", jsessionId);
                call.resolve(ret);
            }
        }
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
