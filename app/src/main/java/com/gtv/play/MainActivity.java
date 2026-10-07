package com.gtv.play;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.SurfaceTexture;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private TextView exoLoaderError;
    private Button btnPrev, btnNext, btnClose, btnRotate;
    private View btnShield;

    private final ArrayList<Canal> canales = new ArrayList<>();
    private int indiceActual = -1;

    private boolean rotado = false;
    private boolean enExoPlayer = false;
    private boolean controlesVisibles = false;
    private final Handler handler = new Handler();
    private Runnable ocultarControlesRunnable;

    private final Handler loaderHandler = new Handler();
    private int loaderPunto = 0;
    private Runnable loaderAnim;
    private Runnable timeoutCanalCaido;

    private String deviceIdCache = null;

    private static final String UPDATE_PREFS = "gtv_update";
    private static final String UPDATE_TRIES_PREFIX = "gtv_update_tries_";
    private static final String VERSION_URL = "https://gtv-portero.abrel03.workers.dev/api/version";
    private static final int MAX_SKIPS = 3;

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
        exoLoader = findViewById(R.id.exo_loader);
        exoLoaderError = findViewById(R.id.exo_loader_error);

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

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (exoLoader == null) return;
                if (state == Player.STATE_READY) {
                    detenerLoaderAnim();
                    ocultarLoaders();
                    if (timeoutCanalCaido != null) {
                        handler.removeCallbacks(timeoutCanalCaido);
                        timeoutCanalCaido = null;
                    }
                } else if (state == Player.STATE_BUFFERING) {
                    mostrarLoaderSintonizando();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                detenerLoaderAnim();
                mostrarLoaderError();
                handler.postDelayed(() -> {
                    ocultarLoaders();
                }, 5000);
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

        handler.postDelayed(this::checkForUpdates, 3000);
    }

    // ============================================================
    // SISTEMA DE ACTUALIZACIONES AUTOMÁTICAS
    // ============================================================

    private void checkForUpdates() {
        if (!hayInternet()) {
            Log.e(TAG, "Sin internet, no chequeo update");
            return;
        }
        new Thread(() -> {
            try {
                URL url = new URL(VERSION_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("X-GTV-Origin", "gtv_app_2026_secreto_xyz789");
                int code = conn.getResponseCode();
                if (code != 200) {
                    Log.e(TAG, "Update check HTTP " + code);
                    return;
                }
                InputStream is = conn.getInputStream();
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                is.close();

                JSONObject json = new JSONObject(sb.toString());
                String remoteVersion = json.optString("versionName", "");
                String apkUrl = json.optString("apkUrl", "");

                String currentVersion = getCurrentVersionName();
                if (remoteVersion.isEmpty() || apkUrl.isEmpty()) return;
                if (remoteVersion.equals(currentVersion)) {
                    Log.e(TAG, "App al día: " + currentVersion);
                    return;
                }

                Log.e(TAG, "Update disponible: " + currentVersion + " → " + remoteVersion);
                final String remoteVer = remoteVersion;
                final String urlApk = apkUrl;
                runOnUiThread(() -> mostrarDialogoUpdate(remoteVer, urlApk));
            } catch (Exception e) {
                Log.e(TAG, "Error chequeando update: " + e.getMessage());
            }
        }).start();
    }

    private boolean hayInternet() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private String getCurrentVersionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    private int getSkipsForVersion(String version) {
        SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        return prefs.getInt(UPDATE_TRIES_PREFIX + version, 0);
    }

    private void incrementSkipsForVersion(String version) {
        SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        int actual = prefs.getInt(UPDATE_TRIES_PREFIX + version, 0);
        prefs.edit().putInt(UPDATE_TRIES_PREFIX + version, actual + 1).apply();
    }

    private void mostrarDialogoUpdate(String remoteVersion, String apkUrl) {
        int skips = getSkipsForVersion(remoteVersion);
        boolean obligatorio = skips >= MAX_SKIPS;

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_update, null);

        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView dialogSubtitle = dialogView.findViewById(R.id.dialogSubtitle);
        TextView dialogVersionActual = dialogView.findViewById(R.id.dialogVersionActual);
        TextView dialogVersionNueva = dialogView.findViewById(R.id.dialogVersionNueva);
        TextView dialogMensaje = dialogView.findViewById(R.id.dialogMensaje);
        Button btnActualizar = dialogView.findViewById(R.id.dialogBtnActualizar);
        Button btnMasTarde = dialogView.findViewById(R.id.dialogBtnMasTarde);

        if (obligatorio) {
            dialogTitle.setText("⚠️ Actualización obligatoria");
            dialogTitle.setTextColor(0xFFE53935);
            dialogSubtitle.setText("Es necesario actualizar GTV Play");
            dialogMensaje.setText("Es necesario actualizar para seguir usando la app.");
        } else {
            dialogTitle.setText("🎉 Nueva versión disponible");
            dialogSubtitle.setText("Actualización de GTV Play");
            dialogMensaje.setText(
                "Podés actualizar ahora o más tarde.\n" +
                "Te quedan " + (MAX_SKIPS - skips) + " oportunidades antes de que sea obligatorio."
            );
        }

        dialogVersionActual.setText("Versión actual:  " + getCurrentVersionName());
        dialogVersionNueva.setText("Nueva versión:  " + remoteVersion);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(!obligatorio);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnActualizar.setOnClickListener(v -> {
            dialog.dismiss();
            descargarEInstalar(apkUrl);
        });

        if (obligatorio) {
            btnMasTarde.setVisibility(View.GONE);
        } else {
            btnMasTarde.setOnClickListener(v -> {
                incrementSkipsForVersion(remoteVersion);
                dialog.dismiss();
            });
        }

        dialog.show();

        if (dialog.getWindow() != null) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = (int)(screenWidth * 0.7);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnActualizar.post(() -> {
            btnActualizar.requestFocus();
            btnActualizar.setFocusable(true);
            btnActualizar.setFocusableInTouchMode(true);
        });
        if (!obligatorio) {
            btnMasTarde.setFocusable(true);
            btnMasTarde.setFocusableInTouchMode(true);
        }
    }

    private void descargarEInstalar(String apkUrl) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Descargando actualización");
        progress.setMessage("Descargando... 0%");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.setMax(100);
        progress.show();

        new Thread(() -> {
            try {
                File cacheDir = getCacheDir();
                final File apkFile = new File(cacheDir, "update.apk");
                if (apkFile.exists()) apkFile.delete();

                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("X-GTV-Origin", "gtv_app_2026_secreto_xyz789");
                conn.connect();

                int fileLength = conn.getContentLength();
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = is.read(buffer)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        final int pct = (int) (total * 100 / fileLength);
                        runOnUiThread(() -> {
                            progress.setMessage("Descargando... " + pct + "%");
                            progress.setProgress(pct);
                        });
                    }
                    fos.write(buffer, 0, count);
                }
                fos.flush();
                fos.close();
                is.close();

                runOnUiThread(() -> {
                    progress.dismiss();
                    instalarApk(apkFile);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error descargando APK: " + e.getMessage());
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(MainActivity.this,
                        "Error al descargar: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void instalarApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri apkUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                apkFile
            );
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            startActivity(intent);
            Log.e(TAG, "Instalador abierto");
        } catch (Exception e) {
            Log.e(TAG, "Error abriendo instalador: " + e.getMessage());
            Toast.makeText(this,
                "Error al abrir instalador: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    // ============================================================
    // LOADERS (Sintonizando y Error)
    // ============================================================

    private void mostrarLoaderSintonizando() {
        if (exoLoader == null) return;
        if (exoLoaderError != null) exoLoaderError.setVisibility(View.GONE);
        exoLoader.setVisibility(View.VISIBLE);
        if (loaderAnim != null) {
            loaderHandler.removeCallbacks(loaderAnim);
        }
        loaderPunto = 0;
        final String base = "Sintonizando";
        loaderAnim = new Runnable() {
            @Override
            public void run() {
                if (exoLoader == null || exoLoader.getVisibility() != View.VISIBLE) return;
                String texto;
                int paso = loaderPunto % 4;
                if (paso == 0) texto = base + "   ";
                else if (paso == 1) texto = base + ".  ";
                else if (paso == 2) texto = base + ".. ";
                else texto = base + "...";
                exoLoader.setText(texto);
                loaderPunto++;
                loaderHandler.postDelayed(this, 400);
            }
        };
        loaderHandler.post(loaderAnim);
    }

    private void mostrarLoaderError() {
        if (exoLoader != null) exoLoader.setVisibility(View.GONE);
        if (exoLoaderError != null) exoLoaderError.setVisibility(View.VISIBLE);
    }

    private void ocultarLoaders() {
        if (exoLoader != null) {
            exoLoader.setVisibility(View.GONE);
            exoLoader.setText("Sintonizando...");
        }
        if (exoLoaderError != null) exoLoaderError.setVisibility(View.GONE);
    }

    private void detenerLoaderAnim() {
        if (loaderAnim != null) {
            loaderHandler.removeCallbacks(loaderAnim);
        }
    }

    private void programarTimeoutCanal() {
        if (timeoutCanalCaido != null) {
            handler.removeCallbacks(timeoutCanalCaido);
        }
        timeoutCanalCaido = () -> {
            if (exoPlayer != null && exoPlayer.getPlaybackState() != Player.STATE_READY) {
                detenerLoaderAnim();
                mostrarLoaderError();
            }
        };
        handler.postDelayed(timeoutCanalCaido, 15000);
    }

    // ============================================================
    // DEVICE ID
    // ============================================================

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

    // ============================================================
    // REPRODUCTOR
    // ============================================================

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

    private boolean esCanalExoPlayer(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        if (u.contains(".m3u8")) return true;
        if (u.contains(".mpd")) return true;
        if (u.contains(".mp4") && u.startsWith("http")) return true;
        if (u.startsWith("http")
            && !u.contains("youtube.com")
            && !u.contains("youtu.be")
            && !u.contains("twitch.tv")
            && !u.contains("kick.com")
            && !u.contains("ok.ru")
            && !u.contains("odnoklassniki.ru")
            && !u.contains("dailymotion.com")
            && !u.contains("tokyvideo.com")) {
            return true;
        }
        return false;
    }

    private void navegar(int delta) {
        if (canales.isEmpty()) return;
        int n = canales.size();
        indiceActual = ((indiceActual + delta) % n + n) % n;
        Canal c = canales.get(indiceActual);

        if (esCanalExoPlayer(c.url)) {
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

        try {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        } catch (Exception e) {}

        mostrarLoaderSintonizando();

        exoPlayer.setMediaItem(MediaItem.fromUri(c.url));
        exoPlayer.prepare();
        exoPlayer.play();

        programarTimeoutCanal();

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
        detenerLoaderAnim();
        ocultarLoaders();
        if (timeoutCanalCaido != null) {
            handler.removeCallbacks(timeoutCanalCaido);
            timeoutCanalCaido = null;
        }
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
        detenerLoaderAnim();
        if (timeoutCanalCaido != null) {
            handler.removeCallbacks(timeoutCanalCaido);
            timeoutCanalCaido = null;
        }
        try {
            File apkFile = new File(getCacheDir(), "update.apk");
            if (apkFile.exists()) {
                apkFile.delete();
                Log.e(TAG, "APK de actualización borrado de la caché");
            }
        } catch (Exception e) {}
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
