package com.gtv.play;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
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
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private FrameLayout playerContainer;
    private FrameLayout rotWrapper;
    private LinearLayout infoCanal;
    private ImageView logoCanal;
    private TextView nombreCanal;
    private TextView numeroCanal;
    private Button btnPrev, btnNext, btnClose, btnRotate, btnShield;

    // Lista SOLO en memoria. Nunca se guarda en disco.
    private final ArrayList<Canal> canales = new ArrayList<>();
    private int indiceActual = -1;

    private int rotacion = 0; // 0, 90, 180, 270
    private int wOriginal = 0, hOriginal = 0;
    private boolean controlesVisibles = false;
    private boolean enExoPlayer = false;
    private final Handler handler = new Handler();
    private Runnable ocultarControlesRunnable;

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

        rotacion = getSharedPreferences("gtv_prefs", MODE_PRIVATE).getInt("rotacion", 0);

        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);

        configurarWebView();
        configurarBotones();

        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("X-GTV-Origin", "gtv_app_2026_secreto_xyz789");
        myWebView.loadUrl("https://gtv-portero.abrel03.workers.dev/", extraHeaders);

        ocultarBarrasSistema();
    }

    // ============================================================
    // NAVEGACIÓN NATIVA (ya no depende del WebView oculto)
    // ============================================================

    private void navegar(int delta) {
        if (canales.isEmpty()) return;
        int n = canales.size();
        indiceActual = ((indiceActual + delta) % n + n) % n;
        Canal c = canales.get(indiceActual);

        if (c.url.startsWith("http://")) {
            reproducirEnExo(c);
        } else {
            // HTTPS → WebView visible otra vez, y recién ahí hablamos con JS
            enExoPlayer = false;
            try { exoPlayer.pause(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
            playerContainer.setVisibility(View.GONE);
            ocultarControlesNativos();
            myWebView.setVisibility(View.VISIBLE);
            final int idx = indiceActual;
            myWebView.post(() -> myWebView.evaluateJavascript(
                "javascript:if(typeof window.irACanal==='function')window.irACanal(" + idx + ");", null));
        }
    }

    private void reproducirEnExo(Canal c) {
        enExoPlayer = true;
        myWebView.setVisibility(View.VISIBLE); // VISIBLE pero tapado por player_container
        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.bringToFront();

        nombreCanal.setText(c.nombre);
        numeroCanal.setText((indiceActual + 1) + "/" + canales.size());
        if (c.logo != null && !c.logo.isEmpty()) {
            Glide.with(this).load(c.logo).into(logoCanal);
        }

        exoPlayer.setMediaItem(MediaItem.fromUri(c.url));
        exoPlayer.prepare();
        exoPlayer.play();

        mostrarControles();
        aplicarRotacion();
    }

    // ============================================================
    // ROTACIÓN (contenedor, no PlayerView)
    // ============================================================

    private void rotar() {
        rotacion = (rotacion + 90) % 360;
        getSharedPreferences("gtv_prefs", MODE_PRIVATE)
            .edit().putInt("rotacion", rotacion).apply();
        aplicarRotacion();
    }

    private void aplicarRotacion() {
        if (wOriginal == 0) return;
        rotWrapper.setPivotX(wOriginal / 2f);
        rotWrapper.setPivotY(hOriginal / 2f);
        rotWrapper.setRotation(rotacion);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) rotWrapper.getLayoutParams();
        if (rotacion == 90 || rotacion == 270) {
            lp.width = hOriginal;
            lp.height = wOriginal;
        } else {
            lp.width = wOriginal;
            lp.height = hOriginal;
        }
        rotWrapper.setLayoutParams(lp);
    }

    // ============================================================
    // WebView + puente
    // ============================================================

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
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setTextZoom(100);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);

        myWebView.setWebViewClient(new WebViewClient());
        myWebView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidPlayer");
    }

    private void configurarBotones() {
        btnNext.setOnClickListener(v -> { navegar(+1); mostrarControles(); });
        btnPrev.setOnClickListener(v -> { navegar(-1); mostrarControles(); });
        btnClose.setOnClickListener(v -> cerrarExoPlayer());
        btnRotate.setOnClickListener(v -> { rotar(); mostrarControles(); });
        btnShield.setOnClickListener(v -> mostrarControles());
    }

    public class WebAppInterface {

        // El HTML nos pasa la lista una vez, al cargar. Solo en memoria.
        @JavascriptInterface
        public void setLista(final String json) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(json);
                    canales.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        canales.add(new Canal(
                            o.getString("url"),
                            o.optString("nombre", "Canal " + (i + 1)),
                            o.optString("logo", "")
                        ));
                    }
                } catch (Exception e) {
                    // lista inválida: seguimos sin crash
                }
            });
        }

        // El HTML avisa en cada cambio de canal → índices sincronizados
        @JavascriptInterface
        public void sincronizarIndice(final int i) {
            runOnUiThread(() -> indiceActual = i);
        }

        // El HTML llama acá cuando quiere abrir un canal HTTP en Exo
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
                    // No está en la lista (por ej. canal agregado al vuelo)
                    Canal c = new Canal(url, nombre, logo);
                    reproducirEnExo(c);
                }
            });
        }

        // El HTML avisa que quiere volver a WebView
        @JavascriptInterface
        public void mostrarWebView() {
            runOnUiThread(() -> {
                enExoPlayer = false;
                try { exoPlayer.pause(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
                playerContainer.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
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
        try { exoPlayer.pause(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
        playerContainer.setVisibility(View.GONE);
        ocultarControlesNativos();
        myWebView.setVisibility(View.VISIBLE);
        myWebView.post(() -> myWebView.evaluateJavascript(
            "javascript:if(typeof window.volverDelExo==='function')window.volverDelExo();", null));
    }

    // ============================================================
    // Controles
    // ============================================================

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
        handler.postDelayed(ocultarControlesRunnable, 3000);
    }

    // ============================================================
    // D-Pad
    // ============================================================

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
                case KeyEvent.KEYCODE_MENU:
                    mostrarControles();
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

    // ============================================================
    // Ciclo de vida
    // ============================================================

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ocultarBarrasSistema();
            if (wOriginal == 0 && rotWrapper.getWidth() > 0) {
                wOriginal = rotWrapper.getWidth();
                hOriginal = rotWrapper.getHeight();
                aplicarRotacion();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Solo pausamos. release() va en onDestroy().
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
