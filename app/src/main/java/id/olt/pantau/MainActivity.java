package id.olt.pantau;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        bindLan();
        CookieHandler.setDefault(new java.net.CookieManager());
        web = new WebView(this);
        WebSettings w = web.getSettings();
        w.setJavaScriptEnabled(true);
        w.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "AndroidOLT");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    /** Pakai jaringan WiFi atau LAN walau tidak ada internet di sana. */
    private void bindLan() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                    cm.bindProcessToNetwork(n);
                    return;
                }
            }
        } catch (Exception ignored) { }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return o.toString("UTF-8");
    }

    private class Bridge {
        @JavascriptInterface
        public void get(final String id, final String url, final String user, final String pass) {
            new Thread(new Runnable() {
                @Override public void run() {
                    boolean ok = false;
                    String out;
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(6000);
                        c.setReadTimeout(10000);
                        if (user != null && user.length() > 0) {
                            String cred = user + ":" + (pass == null ? "" : pass);
                            c.setRequestProperty("Authorization",
                                "Basic " + Base64.encodeToString(cred.getBytes("UTF-8"), Base64.NO_WRAP));
                        }
                        int code = c.getResponseCode();
                        if (code >= 200 && code < 300) { out = read(c.getInputStream()); ok = true; }
                        else out = "HTTP " + code;
                    } catch (Exception e) {
                        out = "OLT tidak terjangkau. Pastikan HP terhubung ke jaringan OLT dan IP benar";
                    }
                    final String js = "window.__oltDone(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(out) + ")";
                    runOnUiThread(new Runnable() {
                        @Override public void run() { web.evaluateJavascript(js, null); }
                    });
                }
            }).start();
        }
    }
}
