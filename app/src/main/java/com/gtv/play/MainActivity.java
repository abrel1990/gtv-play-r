package com.gtv.play;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GTV_DEBUG";

    private WebView myWebView;
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private TextureView videoTexture;
    private FrameLayout playerContainer;
    private FrameLayout rotWrapper;
    private LinearLayout infoCanal;
    private ImageView logoCanal;
    private TextView nombreCanal;
    private TextView numeroCanal;
    private TextView exoLoader;
    private Button btnPrev, btnNext, btnClose, btnRotate;
    private View btnShield;

    private final ArrayList<Canal> canales = new ArrayList<>();
    private int indiceActual = -1;

    private boolean rotado = false;
    private boolean enExoPlayer = false;
    private boolean controlesVisibles = false;
    private final Handler handler = new Handler();
    private Runnable ocultarControlesRunnable;

    private String deviceIdCache = null;

    static class Canal {
        final String url, nombre, logo;
        Canal(String url, String nombre, String logo) {
            this.url = url; this.nombre = nombre; this.logo = logo;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myWebView = findViewById(R.id.webview);
        playerView = findViewById(R.id.player_view);
        playerContainer = findViewById(R.id.player_container);
        rotWrapper = findViewById(R.id.rot_wrapper);
        infoCanal = findViewById(R.id.info_canal);
        logoCanal = findViewById(R.id.logo_canal);
        nombreCanal = findViewById(R.id.nombre_canal);
        numeroCanal = findViewById(R.id.numero_canal);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnClose = findViewById(R.id.btn_close);
        btnRotate = findViewById(R.id.btn_rotate);
        btnShield = findViewById(R.id.btn_shield);
        // ★ NUEVO: loader nativo para ExoPlayer
        exoLoader = findViewById(R.id.exo_loader);

        rotado = getSharedPreferences("gtv_prefs", MODE_PRIVATE).getBoolean("rotado", false);

        DefaultHttpDataSource.Factory httpFactory =
            new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);

        DefaultMediaSourceFactory mediaSourceFactory =
            new DefaultMediaSourceFactory(httpFactory);

        exoPlayer = new ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build();

        inyectarTextureView();
        playerView.setPlayer(exoPlayer);

        // ★ NUEVO: listener para mostrar/ocultar el loader según estado
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (exoLoader == null) return;
                if (state == Player.STATE_READY) {
                    exoLoader.setVisibility(View.GONE);
                } else if (state == Player.STATE_BUFFERING) {
                    exoLoader.setVisibility(View.VISIBLE);
                }
            }
        });

        videoTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                exoPlayer.setVideoTextureView(videoTexture);
                Log.e(TAG, "TextureView disponible: " + w + "x" + h);
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });

        configurarWebView();
        configurarBotones();

        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("X-GTV-Origin", "gtv_app_2026_secreto_xyz789");
        myWebView.loadUrl("https://gtv-portero.abrel03.workers.dev/", extraHeaders);

        ocultarBarrasSistema();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.content.pm.PackageInfo wi = WebView.getCurrentWebViewPackage();
            if (wi != null) {
                Log.e(TAG, "WebView: " + wi.packageName + " v" + wi.versionName);
            }
        }
    }

    private String generarDeviceId() {
        if (deviceIdCache != null) return deviceIdCache;

        String androidId = null;
        try {
            androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo ANDROID_ID: " + e.getMessage());
        }

        if (androidId == null || androidId.isEmpty()) {
            androidId = "GTV_FALLBACK_" + Build.MODEL + "_" + Build.SERIAL;
        }

        final String ALFABETO = "123456789ABCDEFGHIJKLMNPQRSTUVWXYZ";

        long hash = 0;
        for (int i = 0; i < androidId.length(); i++) {
            hash = (hash * 31 + androidId.charAt(i)) & 0xFFFFFFFFL;
        }
        hash = hash ^ (hash >> 16);
        hash = (hash * 0x45d9f3bL) & 0xFFFFFFFFL;

        StringBuilder sb = new StringBuilder("GTV-");
        long h = hash;
        for (int i = 0; i < 6; i++) {
            int idx = (int)(h % ALFABETO.length());
            sb.append(ALFABETO.charAt(idx));
            h = h / ALFABETO.length();
            h = h ^ (h >> 3);
        }

        deviceIdCache = sb.toString();
        Log.e(TAG, "deviceId generado: " + deviceIdCache);
        return deviceIdCache;
    }

    private void inyectarTextureView() {
        View contentFrame = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        if (!(contentFrame instanceof ViewGroup)) {
            Log.e(TAG, "exo_content_frame no encontrado o no es ViewGroup");
            return;
        }
        ViewGroup cf = (ViewGroup) contentFrame;

        for (int i = cf.getChildCount() - 1; i >= 0; i--) {
            View child = cf.getChildAt(i);
            if (child instanceof SurfaceView) {
                cf.removeViewAt(i);
            }
        }

        videoTexture = new TextureView(this);
        videoTexture.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        cf.addView(videoTexture, 0);

        Log.e(TAG, "TextureView inyectado en exo_content_frame");
    }

    private void navegar(int delta) {
        if (canales.isEmpty()) return;
        int n = canales.size();
        indiceActual = ((indiceActual + delta) % n + n) % n;
        Canal c = canales.get(indiceActual);

        if (c.url.startsWith("http://")) {
            reproducirEnExo(c);
        } else {
            enExoPlayer = false;
            try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
            playerContainer.setVisibility(View.GONE);
            ocultarControlesNativos();
            myWebView.setVisibility(View.VISIBLE);
            myWebView.onResume();
            myWebView.resumeTimers();
            final int idx = indiceActual;
            myWebView.post(() -> myWebView.evaluateJavascript(
                "javascript:if(typeof window.irACanal==='function')window.irACanal(" + idx + ");", null));
        }
    }

    private void reproducirEnExo(Canal c) {
        enExoPlayer = true;

        myWebView.onPause();
        myWebView.pauseTimers();

        myWebView.setVisibility(View.VISIBLE);
        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.bringToFront();

        nombreCanal.setText(c.nombre);
        numeroCanal.setText((indiceActual + 1) + "/" + canales.size());
        if (c.logo != null && !c.logo.isEmpty()) {
            Glide.with(this).load(c.logo).into(logoCanal);
        }

        // ★ FIX: stop + clear antes de cargar nuevo, para evitar zapping trabado
        try {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        } catch (Exception e) {}

        // Mostrar el loader "Sintonizando..."
        if (exoLoader != null) exoLoader.setVisibility(View.VISIBLE);

        exoPlayer.setMediaItem(MediaItem.fromUri(c.url));
        exoPlayer.prepare();
        exoPlayer.play();

        mostrarControles();
        playerContainer.post(() -> {
            aplicarRotacion();
            reposicionarBotones();
        });
    }

    private void rotar() {
        rotado = !rotado;
        getSharedPreferences("gtv_prefs", MODE_PRIVATE)
            .edit().putBoolean("rotado", rotado).apply();
        aplicarRotacion();
        reposicionarBotones();
    }

    private void aplicarRotacion() {
        if (playerContainer.getWidth() == 0 || playerContainer.getHeight() == 0) return;

        int w = playerContainer.getWidth();
        int h = playerContainer.getHeight();

        ViewGroup.LayoutParams lp = rotWrapper.getLayoutParams();
        if (rotado) {
            lp.width = h;
            lp.height = w;
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        rotWrapper.setLayoutParams(lp);

        rotWrapper.setRotation(rotado ? 90f : 0f);

        rotWrapper.requestLayout();

        Log.e(TAG, "aplicarRotacion: rotado=" + rotado);
    }

    private void reposicionarBotones() {
        FrameLayout.LayoutParams lpRot = (FrameLayout.LayoutParams) btnRotate.getLayoutParams();
        FrameLayout.LayoutParams lpClose = (FrameLayout.LayoutParams) btnClose.getLayoutParams();

        if (rotado) {
            lpRot.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            lpRot.topMargin = 40;
            lpRot.rightMargin = 40;
            lpRot.bottomMargin = 0;

            lpClose.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            lpClose.bottomMargin = 40;
            lpClose.rightMargin = 40;
        } else {
            lpRot.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            lpRot.bottomMargin = 160;
            lpRot.rightMargin = 40;
            lpRot.topMargin = 0;

            lpClose.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            lpClose.bottomMargin = 40;
            lpClose.rightMargin = 40;
        }
        btnRotate.setLayoutParams(lpRot);
        btnClose.setLayoutParams(lpClose);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configurarWebView() {
        WebSettings s = myWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(false);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setTextZoom(100);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);

        myWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            myWebView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "No se pudo abrir: " + url);
                    }
                    return true;
                }
                return false;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "No se pudo abrir: " + url);
                    }
                    return true;
                }
                return false;
            }
        });

        myWebView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidPlayer");
    }

    private void configurarBotones() {
        btnShield.setOnClickListener(v -> mostrarControles());
        btnNext.setOnClickListener(v -> { navegar(+1); mostrarControles(); });
        btnPrev.setOnClickListener(v -> { navegar(-1); mostrarControles(); });
        btnClose.setOnClickListener(v -> cerrarExoPlayer());
        btnRotate.setOnClickListener(v -> { rotar(); mostrarControles(); });
    }

    public class WebAppInterface {

        @JavascriptInterface
        public String getDeviceId() {
            return generarDeviceId();
        }

        @JavascriptInterface
        public String getSignatureHash() {
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                android.content.pm.PackageInfo packageInfo;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo = pm.getPackageInfo(
                        getPackageName(),
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                    );
                    android.content.pm.SigningInfo signingInfo = packageInfo.signingInfo;
                    if (signingInfo == null) return "";
                    android.content.pm.Signature[] signatures;
                    if (signingInfo.hasMultipleSigners()) {
                        signatures = signingInfo.getApkContentsSigners();
                    } else {
                        signatures = signingInfo.getSigningCertificateHistory();
                    }
                    if (signatures == null || signatures.length == 0) return "";
                    return calcularSHA256(signatures[0].toByteArray());
                } else {
                    packageInfo = pm.getPackageInfo(
                        getPackageName(),
                        android.content.pm.PackageManager.GET_SIGNATURES
                    );
                    if (packageInfo.signatures == null || packageInfo.signatures.length == 0) return "";
                    return calcularSHA256(packageInfo.signatures[0].toByteArray());
                }
            } catch (Exception e) {
                Log.e(TAG, "getSignatureHash error: " + e.getMessage());
                return "";
            }
        }

        private String calcularSHA256(byte[] data) throws Exception {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                if (i > 0) sb.append(":");
                sb.append(String.format("%02X", hash[i]));
            }
            return sb.toString();
        }

        @JavascriptInterface
        public void setLista(final String json) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(json);
                    canales.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String nombre = o.optString("nombre", o.optString("name", "Canal " + (i + 1)));
                        canales.add(new Canal(
                            o.getString("url"),
                            nombre,
                            o.optString("logo", "")
                        ));
                    }
                    Log.e(TAG, "setLista OK: " + canales.size());
                } catch (Exception e) {
                    Log.e(TAG, "setLista ERROR: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void sincronizarIndice(final int i) {
            runOnUiThread(() -> indiceActual = i);
        }

        @JavascriptInterface
        public void detener() {
            runOnUiThread(() -> {
                try {
                    exoPlayer.stop();
                    exoPlayer.clearMediaItems();
                } catch (Exception e) {}
            });
        }

        @JavascriptInterface
        public void reproducir(String url, String nombre, String numero, String logo) {
            runOnUiThread(() -> {
                int idx = -1;
                for (int i = 0; i < canales.size(); i++) {
                    if (canales.get(i).url.equals(url)) { idx = i; break; }
                }
                if (idx >= 0) {
                    indiceActual = idx;
                    reproducirEnExo(canales.get(idx));
                } else {
                    Canal c = new Canal(url, nombre, logo);
                    reproducirEnExo(c);
                }
            });
        }

        @JavascriptInterface
        public void mostrarWebView() {
            runOnUiThread(() -> {
                enExoPlayer = false;
                try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
                playerContainer.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume();
                myWebView.resumeTimers();
                ocultarControlesNativos();
            });
        }

        @JavascriptInterface
        public void cerrarPlayer() {
            runOnUiThread(() -> cerrarExoPlayer());
        }

        @JavascriptInterface
        public void actualizarInfo(String nombre, String numero, String logo) {
            runOnUiThread(() -> {
                nombreCanal.setText(nombre);
                numeroCanal.setText(numero);
                if (logo != null && !logo.isEmpty()) {
                    Glide.with(MainActivity.this).load(logo).into(logoCanal);
                }
            });
        }
    }

    private void cerrarExoPlayer() {
        enExoPlayer = false;
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
        playerContainer.setVisibility(View.GONE);
        ocultarControlesNativos();
        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume();
        myWebView.resumeTimers();
        myWebView.post(() -> myWebView.evaluateJavascript(
            "javascript:if(typeof window.volverDelExo==='function')window.volverDelExo();", null));
    }

    private void ocultarControlesNativos() {
        controlesVisibles = false;
        infoCanal.setVisibility(View.GONE);
        btnPrev.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE);
        btnRotate.setVisibility(View.GONE);
    }

    private void mostrarControles() {
        if (!enExoPlayer) return;
        controlesVisibles = true;
        infoCanal.setVisibility(View.VISIBLE);
        btnPrev.setVisibility(View.VISIBLE);
        btnNext.setVisibility(View.VISIBLE);
        btnClose.setVisibility(View.VISIBLE);
        btnRotate.setVisibility(View.VISIBLE);
        if (ocultarControlesRunnable != null) handler.removeCallbacks(ocultarControlesRunnable);
        ocultarControlesRunnable = this::ocultarControlesNativos;
        handler.postDelayed(ocultarControlesRunnable, 5000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (playerContainer.getVisibility() == View.VISIBLE) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    navegar(+1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    navegar(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (enExoPlayer) { cerrarExoPlayer(); return; }
        if (myWebView.canGoBack()) { myWebView.goBack(); return; }
        super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) ocultarBarrasSistema();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myWebView != null && !enExoPlayer) {
            myWebView.onPause();
            myWebView.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myWebView != null && !enExoPlayer) {
            myWebView.onResume();
            myWebView.resumeTimers();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (exoPlayer != null) {
            try { exoPlayer.pause(); } catch (Exception e) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            try { exoPlayer.release(); } catch (Exception e) {}
            exoPlayer = null;
        }
        if (myWebView != null) {
            try {
                myWebView.loadUrl("about:blank");
                myWebView.stopLoading();
                myWebView.destroy();
                myWebView = null;
            } catch (Exception e) {}
        }
    }

    @SuppressLint("WrongConstant")
    private void ocultarBarrasSistema() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
