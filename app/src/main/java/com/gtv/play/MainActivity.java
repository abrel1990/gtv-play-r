package com.gtv.play;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
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
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private FrameLayout playerContainer;
    private LinearLayout infoCanal;
    private ImageView logoCanal;
    private TextView nombreCanal;
    private TextView numeroCanal;
    private Button btnPrev, btnNext, btnClose, btnRotate, btnShield;

    private boolean rotated = false;
    private boolean controlesVisibles = false;
    private Handler handler = new Handler();
    private Runnable ocultarControlesRunnable;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myWebView = findViewById(R.id.webview);
        playerView = findViewById(R.id.player_view);
        playerContainer = findViewById(R.id.player_container);
        infoCanal = findViewById(R.id.info_canal);
        logoCanal = findViewById(R.id.logo_canal);
        nombreCanal = findViewById(R.id.nombre_canal);
        numeroCanal = findViewById(R.id.numero_canal);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnClose = findViewById(R.id.btn_close);
        btnRotate = findViewById(R.id.btn_rotate);
        btnShield = findViewById(R.id.btn_shield);

        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);

        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSaveFormData(false);
        webSettings.setGeolocationEnabled(false);
        webSettings.setTextZoom(100);
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);

        myWebView.setWebViewClient(new WebViewClient());
        myWebView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidPlayer");

        // Botones
        btnPrev.setOnClickListener(v -> { myWebView.evaluateJavascript("nextFn && prevFn ? prevFn() : null", null); mostrarControles(); });
        btnNext.setOnClickListener(v -> { myWebView.evaluateJavascript("nextFn && nextFn() ? nextFn() : null", null); mostrarControles(); });
        btnClose.setOnClickListener(v -> cerrarPlayer());
        btnRotate.setOnClickListener(v -> rotarExoPlayer());
        btnShield.setOnClickListener(v -> toggleControles());

        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("X-GTV-Origin", "gtv_app_2026_secreto_xyz789");

        myWebView.loadUrl("https://gtv-portero.abrel03.workers.dev/", extraHeaders);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void reproducir(String url, String nombre, String numero, String logo) {
            runOnUiThread(() -> {
                playerContainer.setVisibility(View.VISIBLE);
                myWebView.setVisibility(View.GONE);
                nombreCanal.setText(nombre);
                numeroCanal.setText(numero);
                if (logo != null && !logo.isEmpty()) {
                    Glide.with(MainActivity.this).load(logo).into(logoCanal);
                }
                MediaItem mediaItem = MediaItem.fromUri(url);
                exoPlayer.setMediaItem(mediaItem);
                exoPlayer.prepare();
                exoPlayer.play();
                mostrarControles();
            });
        }

        @JavascriptInterface
        public void cerrarPlayer() {
            runOnUiThread(() -> MainActivity.this.cerrarPlayer());
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

    private void cerrarPlayer() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
        playerContainer.setVisibility(View.GONE);
        myWebView.setVisibility(View.VISIBLE);
        controlesVisibles = false;
        infoCanal.setVisibility(View.GONE);
        btnPrev.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE);
        btnRotate.setVisibility(View.GONE);
        // Avisar al HTML que cerramos
        myWebView.evaluateJavascript("if(typeof volverDelExo === 'function') volverDelExo();", null);
    }

    private void rotarExoPlayer() {
        rotated = !rotated;
        if (rotated) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        mostrarControles();
    }

    private void mostrarControles() {
        controlesVisibles = true;
        infoCanal.setVisibility(View.VISIBLE);
        btnPrev.setVisibility(View.VISIBLE);
        btnNext.setVisibility(View.VISIBLE);
        btnClose.setVisibility(View.VISIBLE);
        btnRotate.setVisibility(View.VISIBLE);

        if (ocultarControlesRunnable != null) handler.removeCallbacks(ocultarControlesRunnable);
        ocultarControlesRunnable = () -> {
            infoCanal.setVisibility(View.GONE);
            btnPrev.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
            btnClose.setVisibility(View.GONE);
            btnRotate.setVisibility(View.GONE);
            controlesVisibles = false;
        };
        handler.postDelayed(ocultarControlesRunnable, 3000);
    }

    private void toggleControles() {
        if (controlesVisibles) {
            if (ocultarControlesRunnable != null) handler.removeCallbacks(ocultarControlesRunnable);
            infoCanal.setVisibility(View.GONE);
            btnPrev.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
            btnClose.setVisibility(View.GONE);
            btnRotate.setVisibility(View.GONE);
            controlesVisibles = false;
        } else {
            mostrarControles();
        }
    }

    @Override
    public void onBackPressed() {
        if (playerContainer.getVisibility() == View.VISIBLE) {
            cerrarPlayer();
            return;
        }
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
