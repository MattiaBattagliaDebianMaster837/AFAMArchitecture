package com.afam.segrete;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Contenitore Android del gioco: una WebView a schermo intero che carica il gioco
 * (HTML5 canvas) dagli asset dell'APK tramite un dominio https locale, così
 * localStorage e WebAudio funzionano come in un normale browser.
 */
public class MainActivity extends Activity {

    private static final String HOST = "https://appassets.androidplatform.net/";
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES (API 28) via reflection.
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, 1);
                w.setAttributes(lp);
            } catch (Throwable ignored) {
            }
        }

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0809);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHapticFeedbackEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serve(request.getUrl().toString());
            }

            // API 24+: sovrascrive il metodo a runtime (l'SDK di compilazione è più vecchio).
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith(HOST);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith(HOST);
            }
        });

        setContentView(web);
        hideSystemUi();
        web.loadUrl(HOST + "index.html");
    }

    private WebResourceResponse serve(String url) {
        if (!url.startsWith(HOST)) {
            return null;
        }
        String path = url.substring(HOST.length());
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        cut = path.indexOf('#');
        if (cut >= 0) path = path.substring(0, cut);
        if (path.length() == 0) path = "index.html";
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Cache-Control", "no-cache");
        try {
            InputStream in = getAssets().open("www/" + path);
            return new WebResourceResponse(mime(path), "utf-8", 200, "OK", headers, in);
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers,
                    new ByteArrayInputStream(new byte[0]));
        }
    }

    private static String mime(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private void hideSystemUi() {
        web.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause() {
        if (web != null) {
            web.evaluateJavascript("window.onAndroidPause&&window.onAndroidPause()", null);
            web.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            hideSystemUi();
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null) {
            web.evaluateJavascript("window.onAndroidBack&&window.onAndroidBack()", null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** Funzioni native richiamabili dal gioco (window.Android). */
    public class Bridge {
        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) v.vibrate(Math.max(5, Math.min(ms, 400)));
            } catch (Throwable ignored) {
            }
        }

        @JavascriptInterface
        public void exit() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }

        /** Margini sicuri (notch / foro fotocamera) in pixel fisici: "sinistra,sopra,destra,sotto". */
        @JavascriptInterface
        public String insets() {
            try {
                if (Build.VERSION.SDK_INT >= 28 && web != null) {
                    Object wi = web.getRootWindowInsets();
                    if (wi != null) {
                        Object dc = wi.getClass().getMethod("getDisplayCutout").invoke(wi);
                        if (dc != null) {
                            Class<?> c = dc.getClass();
                            int l = (Integer) c.getMethod("getSafeInsetLeft").invoke(dc);
                            int t = (Integer) c.getMethod("getSafeInsetTop").invoke(dc);
                            int r = (Integer) c.getMethod("getSafeInsetRight").invoke(dc);
                            int b = (Integer) c.getMethod("getSafeInsetBottom").invoke(dc);
                            return l + "," + t + "," + r + "," + b;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return "0,0,0,0";
        }
    }
}
