package com.gtv.play.r;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.ui.PlayerView;
import androidx.tvprovider.media.tv.TvContractCompat;
import androidx.tvprovider.media.tv.WatchNextProgram;

import com.bumptech.glide.Glide;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GTV_R_DEBUG";

    private WebView myWebView;
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private TextureView videoTexture;
    private SurfaceView vlcSurface;
    private YouTubePlayerView youtubePlayerView;
    private YouTubePlayer youtubePlayer;
    private FrameLayout playerContainer;
    private FrameLayout rotWrapper;
    private View overlayControls;
    private LinearLayout infoCanal;
    private ImageView logoCanal;
    private TextView nombreCanal;
    private TextView numeroCanal;
    private TextView exoLoader;
    private TextView exoLoaderError;
    private Button btnPrev, btnNext, btnClose, btnRotate;
    private View btnShield;
    private android.graphics.drawable.Drawable bgContainerOrig, bgRotOrig;
    private boolean bgGuardado = false;

    private LibVLC libVLC;
    private MediaPlayer vlcPlayer;

    private static final String WORKER_URL = "https://gtv-portero.abrel03.workers.dev/";
    private static final String SELLO = "gtv_app_2026_secreto_xyz789";
    private static final String VALIDAR_URL = "https://gtv-portero.abrel03.workers.dev/api/reproductor/validar";
    private boolean enYouTubeWeb = false;
    private String jsPendienteAlVolver = null;

    private final ArrayList<Canal> canales = new ArrayList<>();
    private int indiceActual = -1;
    private boolean esPeliculas = false;
    private String urlPeliculasM3U = null;

    private boolean rotado = false;
    private volatile boolean enExoPlayer = false;
    private volatile boolean enYouTubeNativo = false;
    private volatile boolean enVlc = false;
    private boolean controlesVisibles = false;
    private final Handler handler = new Handler();
    private Runnable ocultarControlesRunnable;

    private final Handler loaderHandler = new Handler();
    private int loaderPunto = 0;
    private Runnable loaderAnim;
    private Runnable timeoutCanalCaido;

    private String deviceIdCache = null;

    private FrameLayout pubTickerBox;
    private TextView pubTickerTxt;
    private boolean pubTickerRunning = false;
    private int pubTickerGen = 0;
    private int pubTickerDurMs = 20000;
    private long pubTickerT0 = 0;
    private float pubTickerFrom = 0f, pubTickerTo = 0f;

    private FrameLayout pubModal;
    private ImageView pubModalImg;
    private VideoView pubModalVid;
    private final Handler pubHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable pubModalClose;

    private volatile boolean webViewListo = false;

    private static final String WATCH_NEXT_CHANNEL_NAME = "GTV Play R Recientes";
    private long watchNextChannelId = -1;
    private final ArrayList<String> historialCanales = new ArrayList<>();
    private static final int MAX_HISTORIAL = 10;

    private static final String UPDATE_PREFS = "gtv_update";
    private static final String UPDATE_TRIES_PREFIX = "gtv_update_tries_";
    private static final String VERSION_URL = "https://gtv-portero.abrel03.workers.dev/api/version";
    private static final int MAX_SKIPS = 3;

    private final ExecutorService ytExec = Executors.newSingleThreadExecutor();
    private volatile int ytToken = 0;

    private Canal canalActual;
    private int reintentos = 0;
    private int fallosExo = 0;
    private final Runnable reintentoRunnable = new Runnable() {
        @Override public void run() {
            if (!enExoPlayer || exoPlayer == null || canalActual == null) return;
            exoPlayer.setMediaSource(construirMediaSource(canalActual));
            exoPlayer.prepare();
            exoPlayer.play();
            programarTimeoutCanal();
        }
    };

    private int zapPend = -1;
    private final Runnable zapRun = new Runnable() {
        @Override public void run() {
            if (zapPend < 0) return;
            indiceActual = zapPend;
            zapPend = -1;
            Canal c = canales.get(indiceActual);

            nombreCanal.setText(c.nombre);
            numeroCanal.setText((indiceActual + 1) + "/" + canales.size());
            if (c.logo != null && !c.logo.isEmpty()) {
                Glide.with(MainActivity.this).load(c.logo).into(logoCanal);
            }

            String u = c.url.toLowerCase();
            boolean esYT = u.contains("youtube.com") || u.contains("youtu.be");

            if (esCanalExoPlayer(c.url)) {
                reproducirEnExo(c);
            } else if (esYT) {
                detenerTodoNativo();
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume();
                myWebView.resumeTimers();
                enYouTubeWeb = true;

                String channelId = null, videoId = null;
                java.util.regex.Matcher mCh = java.util.regex.Pattern.compile("channel/(UC[\\w-]{22})").matcher(c.url);
                if (mCh.find()) channelId = mCh.group(1);
                java.util.regex.Matcher mVid = java.util.regex.Pattern.compile("(?:watch\\?v=|youtu\\.be/)([\\w-]{11})").matcher(c.url);
                if (mVid.find()) videoId = mVid.group(1);

                String embedUrl;
                if (channelId != null) {
                    embedUrl = "https://www.youtube.com/embed/live_stream?channel=" + channelId + "&autoplay=1&playsinline=1&rel=0";
                } else if (videoId != null) {
                    embedUrl = "https://www.youtube.com/embed/" + videoId + "?autoplay=1&playsinline=1&rel=0";
                } else {
                    return;
                }
                String js = "javascript:(function(){try{" +
                    "var f=document.getElementById('ifr');" +
                    "if(f){f.src='about:blank';}" +
                    "setTimeout(function(){" +
                    "  var g=document.getElementById('ifr');" +
                    "  if(g){g.src='" + embedUrl + "';g.style.display='block';}" +
                    "},120);" +
                    "}catch(e){}})();";
                myWebView.evaluateJavascript(js, null);
                myWebView.requestFocus();
                handler.postDelayed(() -> { if (myWebView != null) myWebView.requestFocus(); }, 1500);
            } else {
                detenerTodoNativo();
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume();
                myWebView.resumeTimers();
                enYouTubeWeb = false;
                mostrarEscudo(false);
                irACanalWebView(indiceActual);
            }
        }
    };

    static class Canal {
        final String url, nombre, logo, referer;
        final float gain;
        boolean oculto;

        Canal(String url, String nombre, String logo) {
            this(url, nombre, logo, "", 1.0f, false);
        }
        Canal(String url, String nombre, String logo, String referer) {
            this(url, nombre, logo, referer, 1.0f, false);
        }
        Canal(String url, String nombre, String logo, String referer, float gain) {
            this(url, nombre, logo, referer, gain, false);
        }
        Canal(String url, String nombre, String logo, String referer, float gain, boolean oculto) {
            this.url = url;
            this.nombre = nombre;
            this.logo = logo;
            this.referer = (referer != null) ? referer : "";
            this.gain = (gain > 0f && gain <= 2f) ? gain : 1.0f;
            this.oculto = oculto;
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
        overlayControls = findViewById(R.id.overlay_controls);
        vlcSurface = findViewById(R.id.vlc_surface);
        youtubePlayerView = findViewById(R.id.youtube_player_view);
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

        inicializarPublicidadNativa();

        rotado = getSharedPreferences("gtv_prefs", MODE_PRIVATE).getBoolean("rotado", false);

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 3000, 500, 1000)
            .setTargetBufferBytes(16 * 1024 * 1024)
            .build();

        exoPlayer = new ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .build();

        inyectarTextureView();
        playerView.setPlayer(exoPlayer);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (exoLoader == null) return;
                if (state == Player.STATE_READY) {
                    reintentos = 0;
                    fallosExo = 0;
                    detenerLoaderAnim();
                    ocultarLoaders();
                    if (timeoutCanalCaido != null) {
                        handler.removeCallbacks(timeoutCanalCaido);
                        timeoutCanalCaido = null;
                    }
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                } else if (state == Player.STATE_BUFFERING) {
                    mostrarLoaderSintonizando();
                } else if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                    detenerLoaderAnim();
                    ocultarLoaders();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    try { exoPlayer.seekToDefaultPosition(); exoPlayer.prepare(); } catch (Exception e) {}
                    return;
                }
                fallosExo++;
                if (reintentar()) return;

                if (fallosExo >= 3 && canalActual != null) {
                    Log.e(TAG, "ExoPlayer falló 3 veces, probando LibVLC: " + canalActual.url);
                    reproducirConVLC(canalActual);
                    return;
                }

                detenerLoaderAnim();
                mostrarLoaderError();
                Log.e(TAG, "ExoPlayer error (" + error.errorCode + "): " + error.getMessage());
                avisarAlWebView("window.gtv&&window.gtv.onNativoError&&window.gtv.onNativoError();");
                handler.postDelayed(new Runnable() {
                    @Override public void run() { ocultarLoaders(); }
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

        youtubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(YouTubePlayer ytPlayer) {
                youtubePlayer = ytPlayer;
            }
        });
        getLifecycle().addObserver(youtubePlayerView);

        inicializarLibVLC();

        configurarWebView();
        configurarBotones();

        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("X-GTV-Origin", SELLO);
        myWebView.loadUrl("file:///android_asset/index.html", extraHeaders);

        ocultarBarrasSistema();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.content.pm.PackageInfo wi = WebView.getCurrentWebViewPackage();
            if (wi != null) Log.e(TAG, "WebView: " + wi.packageName + " v" + wi.versionName);
        }

        handler.postDelayed(this::checkForUpdates, 2000);
        handler.postDelayed(() -> {
            try { inicializarWatchNext(); }
            catch (Throwable t) { Log.e(TAG, "WatchNext init error: " + t); }
        }, 5000);
    }

    private void inicializarLibVLC() {
        try {
            ArrayList<String> options = new ArrayList<>();
            options.add("--no-drop-late-frames");
            options.add("--no-skip-frames");
            options.add("--rtsp-tcp");
            options.add("--network-caching=1500");
            options.add("--live-caching=1500");
            options.add("--audio-time-stretch");
            libVLC = new LibVLC(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando LibVLC: " + e.getMessage());
        }
    }

    private void detenerTodoNativo() {
        ytToken++;
        tickerDetener(true);
        enExoPlayer = false;
        enYouTubeNativo = false;
        enVlc = false;
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
        try { if (youtubePlayer != null) youtubePlayer.pause(); } catch (Exception ignored) {}
        try { if (vlcPlayer != null) { vlcPlayer.stop(); vlcPlayer.release(); vlcPlayer = null; } } catch (Exception ignored) {}
        playerContainer.setVisibility(View.GONE);
        youtubePlayerView.setVisibility(View.GONE);
        vlcSurface.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim();
        ocultarLoaders();
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        handler.removeCallbacks(reintentoRunnable);
    }

    private boolean reintentar() {
        if (reintentos >= 3 || canalActual == null || !enExoPlayer) return false;
        reintentos++;
        mostrarLoaderSintonizando();
        handler.removeCallbacks(reintentoRunnable);
        handler.postDelayed(reintentoRunnable, 1500L * reintentos);
        return true;
    }

    private boolean esTV() {
        return getPackageManager().hasSystemFeature("android.software.leanback");
    }

    private void inicializarWatchNext() {
        if (!esTV()) return;
        try { watchNextChannelId = obtenerOCrearCanalWatchNext(); } catch (Exception ignored) {}
    }

    private long obtenerOCrearCanalWatchNext() {
        try {
            Cursor cursor = getContentResolver().query(
                TvContractCompat.Channels.CONTENT_URI,
                new String[]{TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_DISPLAY_NAME},
                null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (WATCH_NEXT_CHANNEL_NAME.equals(cursor.getString(1))) {
                        long id = cursor.getLong(0);
                        cursor.close();
                        return id;
                    }
                }
                cursor.close();
            }
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(TvContractCompat.Channels.COLUMN_DISPLAY_NAME, WATCH_NEXT_CHANNEL_NAME);
            values.put(TvContractCompat.Channels.COLUMN_DESCRIPTION, "Últimos canales que viste en GTV Play R");
            values.put(TvContractCompat.Channels.COLUMN_TYPE, TvContractCompat.Channels.TYPE_PREVIEW);
            values.put(TvContractCompat.Channels.COLUMN_BROWSABLE, 0);
            values.put(TvContractCompat.Channels.COLUMN_SEARCHABLE, 0);
            values.put(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI, "gtvplayr://play?url=");
            Uri channelUri = getContentResolver().insert(TvContractCompat.Channels.CONTENT_URI, values);
            if (channelUri == null) return -1;
            long channelId = ContentUris.parseId(channelUri);
            try { TvContractCompat.requestChannelBrowsable(this, channelId); } catch (Exception ignored) {}
            return channelId;
        } catch (Exception e) { return -1; }
    }

    private void agregarAlWatchNext(Canal canal) {
        if (!esTV() || watchNextChannelId == -1 || canal == null || canal.url == null) return;
        try {
            String intentUri = "gtvplayr://play?url=" + Uri.encode(canal.url)
                + "&nombre=" + Uri.encode(canal.nombre != null ? canal.nombre : "")
                + "&logo=" + Uri.encode(canal.logo != null ? canal.logo : "");
            WatchNextProgram.Builder builder = new WatchNextProgram.Builder();
            builder.setType(TvContractCompat.WatchNextPrograms.TYPE_CLIP);
            builder.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE);
            builder.setLastEngagementTimeUtcMillis(System.currentTimeMillis());
            builder.setTitle(canal.nombre != null ? canal.nombre : "Canal");
            builder.setDescription("GTV Play R");
            builder.setIntentUri(Uri.parse(intentUri));
            if (canal.logo != null && !canal.logo.isEmpty()) builder.setPosterArtUri(Uri.parse(canal.logo));
            builder.setInternalProviderId(canal.url);

            try {
                getContentResolver().delete(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID + "=?",
                    new String[]{ canal.url });
            } catch (Exception ignored) {}

            getContentResolver().insert(
                TvContractCompat.WatchNextPrograms.CONTENT_URI, builder.build().toContentValues());
        } catch (Exception ignored) {}
    }

    private void actualizarWatchNext() {
        if (!esTV() || canales.isEmpty() || indiceActual < 0 || indiceActual >= canales.size()) return;
        Canal c = canales.get(indiceActual);
        if (c == null) return;
        historialCanales.remove(c.url);
        historialCanales.add(0, c.url);
        while (historialCanales.size() > MAX_HISTORIAL) historialCanales.remove(historialCanales.size() - 1);
        agregarAlWatchNext(c);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        manejarIntentEntrante(intent);
    }

    private void manejarIntentEntrante(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (!"gtvplayr".equals(data.getScheme())) return;
        String url = data.getQueryParameter("url");
        String nombre = data.getQueryParameter("nombre");
        String logo = data.getQueryParameter("logo");
        if (url == null || url.isEmpty()) return;
        new Handler().postDelayed(() -> {
            if (canales.isEmpty()) {
                new Handler().postDelayed(() -> abrirCanalDesdeWatchNext(url, nombre, logo), 3000);
                return;
            }
            abrirCanalDesdeWatchNext(url, nombre, logo);
        }, 2000);
    }

    private void abrirCanalDesdeWatchNext(String url, String nombre, String logo) {
        int idx = -1;
        for (int i = 0; i < canales.size(); i++) {
            if (canales.get(i).url.equals(url)) { idx = i; break; }
        }
        if (idx >= 0) {
            indiceActual = idx;
            Canal c = canales.get(idx);
            if (esCanalExoPlayer(c.url)) reproducirEnExo(c);
            else irACanalWebView(idx);
        } else {
            Canal c = new Canal(url, nombre != null ? nombre : "Canal", logo != null ? logo : "", "", 1.0f);
            if (esCanalExoPlayer(url)) {
                indiceActual = -1;
                reproducirEnExo(c);
            }
        }
    }

    private void irACanalWebView(final int idx) {
        irACanalWebView(idx, 0);
    }

    private void irACanalWebView(final int idx, final int intento) {
        runOnUiThread(() -> {
            if (myWebView == null) return;
            myWebView.evaluateJavascript(
                "(function(){try{return typeof window.irACanal==='function';}catch(e){return false;}})()",
                value -> {
                    if ("true".equals(value)) {
                        myWebView.evaluateJavascript("window.irACanal(" + idx + ");", null);
                    } else if (intento < 10) {
                        handler.postDelayed(() -> irACanalWebView(idx, intento + 1), 300);
                    } else {
                        Log.e(TAG, "irACanal no disponible tras reintentos, idx=" + idx);
                    }
                });
        });
    }
        private void checkForUpdates() {
        if (!hayInternet()) return;
        new Thread(() -> {
            try {
                URL url = new URL(VERSION_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("X-GTV-Origin", SELLO);
                if (conn.getResponseCode() != 200) return;
                InputStream is = conn.getInputStream();
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close(); is.close();
                JSONObject json = new JSONObject(sb.toString());
                String remoteVersion = json.optString("versionName", "");
                String apkUrl = json.optString("apkUrl", "");
                String currentVersion = getCurrentVersionName();
                if (remoteVersion.isEmpty() || apkUrl.isEmpty() || remoteVersion.equals(currentVersion)) return;
                runOnUiThread(() -> mostrarDialogoUpdate(remoteVersion, apkUrl));
            } catch (Exception ignored) {}
        }).start();
    }

    private boolean hayInternet() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return false; }
    }

    private String getCurrentVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "0"; }
    }

    private int getSkipsForVersion(String version) {
        return getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE).getInt(UPDATE_TRIES_PREFIX + version, 0);
    }

    private void incrementSkipsForVersion(String version) {
        SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        prefs.edit().putInt(UPDATE_TRIES_PREFIX + version, prefs.getInt(UPDATE_TRIES_PREFIX + version, 0) + 1).apply();
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
            dialogSubtitle.setText("Es necesario actualizar GTV Play R");
            dialogMensaje.setText("Es necesario actualizar para seguir usando la app.");
        } else {
            dialogTitle.setText("🎉 Nueva versión disponible");
            dialogSubtitle.setText("Actualización de GTV Play R");
            dialogMensaje.setText("Podés actualizar ahora o más tarde.\nTe quedan " + (MAX_SKIPS - skips) + " oportunidades antes de que sea obligatorio.");
        }
        dialogVersionActual.setText("Versión actual:  " + getCurrentVersionName());
        dialogVersionNueva.setText("Nueva versión:  " + remoteVersion);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(!obligatorio);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnActualizar.setOnClickListener(v -> { dialog.dismiss(); descargarEInstalar(apkUrl); });
        if (obligatorio) btnMasTarde.setVisibility(View.GONE);
        else btnMasTarde.setOnClickListener(v -> { incrementSkipsForVersion(remoteVersion); dialog.dismiss(); });
        dialog.show();
        if (dialog.getWindow() != null) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int dialogWidth = (screenWidth > screenHeight) ? (int)(screenWidth * 0.55) : (int)(screenWidth * 0.90);
            if (screenWidth <= screenHeight) {
                int maxAnchoPx = (int)(340 * getResources().getDisplayMetrics().density);
                if (dialogWidth > maxAnchoPx) dialogWidth = maxAnchoPx;
            }
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        btnActualizar.post(() -> { btnActualizar.requestFocus(); btnActualizar.setFocusable(true); btnActualizar.setFocusableInTouchMode(true); });
        if (!obligatorio) { btnMasTarde.setFocusable(true); btnMasTarde.setFocusableInTouchMode(true); }
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
                File apkFile = new File(getCacheDir(), "update.apk");
                if (apkFile.exists()) apkFile.delete();
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("X-GTV-Origin", SELLO);
                conn.connect();
                int fileLength = conn.getContentLength();
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                long total = 0; int count;
                while ((count = is.read(buffer)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        final int pct = (int)(total * 100 / fileLength);
                        runOnUiThread(() -> { progress.setMessage("Descargando... " + pct + "%"); progress.setProgress(pct); });
                    }
                    fos.write(buffer, 0, count);
                }
                fos.flush(); fos.close(); is.close();
                runOnUiThread(() -> { progress.dismiss(); instalarApk(apkFile); });
            } catch (Exception e) {
                runOnUiThread(() -> { progress.dismiss(); Toast.makeText(MainActivity.this, "Error al descargar: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private void instalarApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir instalador: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void mostrarLoaderSintonizando() {
        if (exoLoader == null) return;
        if (exoLoaderError != null) exoLoaderError.setVisibility(View.GONE);
        exoLoader.setVisibility(View.VISIBLE);
        if (loaderAnim != null) loaderHandler.removeCallbacks(loaderAnim);
        loaderPunto = 0;
        loaderAnim = new Runnable() {
            @Override public void run() {
                if (exoLoader == null || exoLoader.getVisibility() != View.VISIBLE) return;
                int paso = loaderPunto % 4;
                String texto = "Sintonizando" + (paso == 0 ? "   " : paso == 1 ? ".  " : paso == 2 ? ".. " : "...");
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
        if (exoLoader != null) { exoLoader.setVisibility(View.GONE); exoLoader.setText("Sintonizando..."); }
        if (exoLoaderError != null) exoLoaderError.setVisibility(View.GONE);
    }

    private void detenerLoaderAnim() { if (loaderAnim != null) loaderHandler.removeCallbacks(loaderAnim); }

    private void programarTimeoutCanal() {
        if (timeoutCanalCaido != null) handler.removeCallbacks(timeoutCanalCaido);
        timeoutCanalCaido = () -> {
            if (exoPlayer != null && exoPlayer.getPlaybackState() != Player.STATE_READY) {
                if (!reintentar()) { detenerLoaderAnim(); mostrarLoaderError(); }
            }
        };
        handler.postDelayed(timeoutCanalCaido, 15000);
    }

    private String generarDeviceId() {
        if (deviceIdCache != null) return deviceIdCache;
        String androidId = null;
        try { androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID); } catch (Exception ignored) {}
        if (androidId == null || androidId.isEmpty()) androidId = "GTV_FALLBACK_" + Build.MODEL + "_" + Build.SERIAL;
        final String ALFABETO = "123456789ABCDEFGHIJKLMNPQRSTUVWXYZ";
        long hash = 0;
        for (int i = 0; i < androidId.length(); i++) hash = (hash * 31 + androidId.charAt(i)) & 0xFFFFFFFFL;
        hash = hash ^ (hash >> 16);
        hash = (hash * 0x45d9f3bL) & 0xFFFFFFFFL;
        StringBuilder sb = new StringBuilder("GTV-R-");
        long h = hash;
        for (int i = 0; i < 6; i++) { sb.append(ALFABETO.charAt((int)(h % ALFABETO.length()))); h = h / ALFABETO.length(); h = h ^ (h >> 3); }
        deviceIdCache = sb.toString();
        return deviceIdCache;
    }

    private void inyectarTextureView() {
        View contentFrame = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        if (!(contentFrame instanceof ViewGroup)) return;
        ViewGroup cf = (ViewGroup) contentFrame;
        for (int i = cf.getChildCount() - 1; i >= 0; i--) if (cf.getChildAt(i) instanceof SurfaceView) cf.removeViewAt(i);
        videoTexture = new TextureView(this);
        videoTexture.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cf.addView(videoTexture, 0);
    }

    private boolean esCanalExoPlayer(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        if (u.contains(".m3u8") || u.contains(".mpd") || u.contains(".mp4")
            || u.contains(".ts") || u.contains(".webm") || u.contains(".mkv")) return true;
        if (u.contains("/hls/") || u.contains("type=hls") || u.contains("format=hls")
            || u.contains("type=m3u8") || u.contains("format=m3u8")) return true;
        if (u.contains("youtube.com") || u.contains("youtu.be")) return false;
        return false;
    }

    private boolean esEmbedTercero(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("kick.com") || u.contains("twitch.tv") || u.contains("ok.ru")
            || u.contains("odnoklassniki.ru") || u.contains("dailymotion.com") || u.contains("tokyvideo.com");
    }

    private String mimeDe(String u) {
        String s = u.toLowerCase();
        if (s.contains(".m3u8") || s.contains("type=hls") || s.contains("format=hls")
            || s.contains("type=m3u8") || s.contains("format=m3u8")) return "application/x-mpegURL";
        if (s.contains(".mpd")) return "application/dash+xml";
        if (s.contains(".ts")) return "video/mp2t";
        if (s.contains(".mp4")) return "video/mp4";
        if (s.contains(".webm")) return "video/webm";
        if (s.contains(".mkv")) return "video/x-matroska";
        return null;
    }

    private void navegar(int delta) {
        if (canales.isEmpty()) return;
        int n = canales.size();
        int i = (zapPend >= 0 ? zapPend : indiceActual);
        for (int k = 0; k < n; k++) {
            i = ((i + delta) % n + n) % n;
            if (!canales.get(i).oculto) break;
        }
        zapPend = i;
        Canal c = canales.get(i);
        nombreCanal.setText(c.nombre);
        numeroCanal.setText((i + 1) + "/" + n);
        handler.removeCallbacks(zapRun);
        handler.postDelayed(zapRun, 300);
    }

    private void reproducirEnExo(Canal c) {
        ytToken++;
        mostrarEscudo(false);
        enYouTubeWeb = false;
        enYouTubeNativo = false;
        enVlc = false;
        enExoPlayer = true;
        canalActual = c;
        reintentos = 0;
        handler.removeCallbacks(reintentoRunnable);
        myWebView.setVisibility(View.GONE);
        myWebView.onPause();
        youtubePlayerView.setVisibility(View.GONE);
        vlcSurface.setVisibility(View.GONE);
        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.bringToFront();
        nombreCanal.setText(c.nombre);
        numeroCanal.setText((indiceActual + 1) + "/" + canales.size());
        if (c.logo != null && !c.logo.isEmpty()) Glide.with(this).load(c.logo).into(logoCanal);
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
        mostrarLoaderSintonizando();
        exoPlayer.setMediaSource(construirMediaSource(c));
        exoPlayer.prepare();
        exoPlayer.play();
        try { exoPlayer.setVolume(c.gain); } catch (Exception ignored) {}
        programarTimeoutCanal();
        avisarAlWebView("window.gtv&&window.gtv.onNativoIniciado&&window.gtv.onNativoIniciado();");
        handler.postDelayed(this::actualizarWatchNext, 5000);
        mostrarControles();
        playerContainer.post(() -> { aplicarRotacion(); reposicionarBotones(); });
    }

    private void reproducirConVLC(Canal c) {
        if (libVLC == null || c == null) return;
        Log.e(TAG, "Reproduciendo con LibVLC: " + c.url);
        runOnUiThread(() -> {
            try {
                if (vlcPlayer != null) { vlcPlayer.stop(); vlcPlayer.release(); vlcPlayer = null; }
                ytToken++;
                enExoPlayer = false;
                enYouTubeNativo = false;
                enYouTubeWeb = false;
                enVlc = true;
                canalActual = c;
                try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
                playerView.setVisibility(View.GONE);
                youtubePlayerView.setVisibility(View.GONE);
                myWebView.setVisibility(View.GONE);
                vlcSurface.setVisibility(View.VISIBLE);
                playerContainer.setVisibility(View.VISIBLE);
                playerContainer.bringToFront();
                mostrarLoaderSintonizando();
                vlcPlayer = new MediaPlayer(libVLC);
                vlcPlayer.getVLCVout().setVideoView(vlcSurface);
                vlcPlayer.getVLCVout().attachViews();
                vlcPlayer.setEventListener(event -> {
                    if (event.type == MediaPlayer.Event.Playing) {
                        runOnUiThread(() -> { detenerLoaderAnim(); ocultarLoaders(); });
                    } else if (event.type == MediaPlayer.Event.EncounteredError) {
                        runOnUiThread(() -> { detenerLoaderAnim(); mostrarLoaderError(); });
                    }
                });
                Media media = new Media(libVLC, Uri.parse(c.url));
                media.setHWDecoderEnabled(true, false);
                media.addOption(":network-caching=1500");
                if (c.referer != null && !c.referer.isEmpty()) media.addOption(":http-referrer=" + c.referer);
                vlcPlayer.setMedia(media);
                media.release();
                vlcPlayer.play();
                mostrarControles();
            } catch (Exception e) {
                Log.e(TAG, "Error LibVLC: " + e.getMessage());
            }
        });
    }

    private void reproducirYouTubeNativo(String videoId) {
        runOnUiThread(() -> {
            ytToken++;
            enExoPlayer = false;
            enVlc = false;
            enYouTubeWeb = false;
            enYouTubeNativo = true;
            try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
            playerContainer.setVisibility(View.GONE);
            myWebView.setVisibility(View.GONE);
            vlcSurface.setVisibility(View.GONE);
            youtubePlayerView.setVisibility(View.VISIBLE);
            youtubePlayerView.bringToFront();
            if (youtubePlayer != null) {
                youtubePlayer.loadVideo(videoId, 0);
            }
            mostrarControles();
        });
    }

    private MediaSource construirMediaSource(Canal c) {
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000);

        Map<String, String> h = new HashMap<>();
        h.put("User-Agent",
            "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        if (c.referer != null && !c.referer.isEmpty()) {
            h.put("Referer", c.referer);
            try {
                URL u = new URL(c.referer);
                h.put("Origin", u.getProtocol() + "://" + u.getHost());
            } catch (Exception ignored) {}
        }
        f.setDefaultRequestProperties(h);

        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                    | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS);

        MediaItem.Builder ib = new MediaItem.Builder().setUri(c.url);
        String mime = mimeDe(c.url);
        if (mime != null) ib.setMimeType(mime);

        return new DefaultMediaSourceFactory(f, extractors)
            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6))
            .createMediaSource(ib.build());
    }

    private void rotar() {
        rotado = !rotado;
        getSharedPreferences("gtv_prefs", MODE_PRIVATE).edit().putBoolean("rotado", rotado).apply();
        aplicarRotacionYouTube();
        aplicarRotacion();
        reposicionarBotones();
        reiniciarTickerSiVisible();
    }

    private void aplicarRotacion() {
        if (playerContainer.getWidth() == 0 || playerContainer.getHeight() == 0) return;
        int w = playerContainer.getWidth(), h = playerContainer.getHeight();
        ViewGroup.LayoutParams lp = rotWrapper.getLayoutParams();
        if (rotado) { lp.width = h; lp.height = w; }
        else { lp.width = ViewGroup.LayoutParams.MATCH_PARENT; lp.height = ViewGroup.LayoutParams.MATCH_PARENT; }
        rotWrapper.setLayoutParams(lp);
        rotWrapper.setRotation(rotado ? 90f : 0f);
        rotWrapper.requestLayout();
    }

    private void reposicionarBotones() {
        FrameLayout.LayoutParams lpRot = (FrameLayout.LayoutParams) btnRotate.getLayoutParams();
        FrameLayout.LayoutParams lpClose = (FrameLayout.LayoutParams) btnClose.getLayoutParams();
        if (rotado) {
            lpRot.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            lpRot.topMargin = 40; lpRot.rightMargin = 40; lpRot.bottomMargin = 0;
            lpClose.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            lpClose.bottomMargin = 40; lpClose.rightMargin = 40;
        } else {
            lpRot.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            lpRot.bottomMargin = 160; lpRot.rightMargin = 40; lpRot.topMargin = 0;
            lpClose.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            lpClose.bottomMargin = 40; lpClose.rightMargin = 40;
        }
        btnRotate.setLayoutParams(lpRot);
        btnClose.setLayoutParams(lpClose);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configurarWebView() {
        WebSettings s = myWebView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        s.setDatabaseEnabled(true); s.setSupportZoom(false);
        s.setSaveFormData(false); s.setGeolocationEnabled(false);
        s.setTextZoom(100);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(myWebView, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            myWebView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        myWebView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webViewListo = true;
                if (enYouTubeWeb && url != null && url.contains("youtube.com/embed")) {
                    aplicarRotacionYouTube();
                    myWebView.requestFocus();
                    view.postDelayed(() -> MainActivity.this.aplicarRotacionYouTube(), 1500);
                    handler.postDelayed(() -> { if (myWebView != null) myWebView.requestFocus(); }, 1500);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return super.shouldInterceptRequest(view, request);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });
        myWebView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidPlayer");
    }

    private void mostrarEscudo(boolean on) {
        if (playerContainer == null || rotWrapper == null || playerView == null || myWebView == null) return;
        if (on) {
            if (!bgGuardado) {
                bgContainerOrig = playerContainer.getBackground();
                bgRotOrig = rotWrapper.getBackground();
                bgGuardado = true;
            }
            playerContainer.setBackgroundColor(Color.TRANSPARENT);
            rotWrapper.setBackgroundColor(Color.TRANSPARENT);
            playerView.setVisibility(View.INVISIBLE);
            myWebView.setFocusable(false);
            myWebView.setFocusableInTouchMode(false);
            playerContainer.setVisibility(View.VISIBLE);
            playerContainer.bringToFront();
            if (indiceActual >= 0 && indiceActual < canales.size()) {
                Canal c = canales.get(indiceActual);
                nombreCanal.setText(c.nombre);
                numeroCanal.setText((indiceActual + 1) + "/" + canales.size());
                if (c.logo != null && !c.logo.isEmpty()) Glide.with(this).load(c.logo).into(logoCanal);
            }
            mostrarControles();
            playerContainer.post(() -> { aplicarRotacion(); reposicionarBotones(); });
        } else {
            if (bgGuardado) {
                playerContainer.setBackground(bgContainerOrig);
                rotWrapper.setBackground(bgRotOrig);
            }
            playerView.setVisibility(View.VISIBLE);
            myWebView.setFocusable(true);
            myWebView.setFocusableInTouchMode(true);
        }
    }

    private void entrarModoYouTube() {
        enYouTubeWeb = true;
        jsPendienteAlVolver = null;
        mostrarEscudo(true);
    }

    private void salirDeYouTube() {
        tickerDetener(true);
        ytToken++;
        enYouTubeWeb = false;
        mostrarEscudo(false);
        enExoPlayer = false;
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
        playerContainer.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim();
        ocultarLoaders();

        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume();
        myWebView.resumeTimers();

        String js = "window.sincronizarIndiceJS&&window.sincronizarIndiceJS(" + indiceActual + ");"
            + "window.volverDelExo&&window.volverDelExo();";
        myWebView.postDelayed(() -> {
            try {
                if (js != null && !js.isEmpty())
                    myWebView.evaluateJavascript("javascript:(function(){try{" + js + "}catch(e){}})();", null);
            } catch (Exception ignored) {}
        }, 100);
    }

    private void aplicarRotacionYouTube() {
        if (myWebView == null || !enYouTubeWeb) return;
        String css = rotado
            ? "html{position:fixed!important;top:50%!important;left:50%!important;width:100vh!important;"
              + "height:100vw!important;margin-top:-50vw!important;margin-left:-50vh!important;"
              + "transform:rotate(90deg)!important;background:#000!important;}"
            : "";
        String js = "(function(){"
            + "window.__gtvRotCss='" + css + "';"
            + "function ap(){var s=document.getElementById('gtv-rot');"
            + "if(!s){s=document.createElement('style');s.id='gtv-rot';(document.head||document.documentElement).appendChild(s);}"
            + "if(s.textContent!==window.__gtvRotCss)s.textContent=window.__gtvRotCss;}"
            + "ap();"
            + "if(!window.__gtvRotTimer)window.__gtvRotTimer=setInterval(ap,500);"
            + "})();";
        myWebView.evaluateJavascript(js, null);
    }

    private void configurarBotones() {
        btnShield.setOnClickListener(v -> mostrarControles());
        btnNext.setOnClickListener(v -> { navegar(+1); mostrarControles(); });
        btnPrev.setOnClickListener(v -> { navegar(-1); mostrarControles(); });
        btnClose.setOnClickListener(v -> cerrarExoPlayer());
        btnRotate.setOnClickListener(v -> { rotar(); mostrarControles(); });
    }

    private void inicializarPublicidadNativa() {
        if (rotWrapper == null) return;

        pubTickerBox = new FrameLayout(this);
        pubTickerBox.setVisibility(View.GONE);
        pubTickerBox.setBackgroundColor(0x88000000);
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(36));
        boxLp.gravity = Gravity.BOTTOM;
        pubTickerBox.setLayoutParams(boxLp);

        pubTickerTxt = new TextView(this);
        pubTickerTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        pubTickerTxt.setSingleLine(true);
        pubTickerTxt.setHorizontallyScrolling(true);
        pubTickerTxt.setEllipsize(null);
        pubTickerTxt.setTextColor(0xFFFFFFFF);
        pubTickerTxt.setShadowLayer(4f, 0f, 1f, 0xD9000000);
        pubTickerTxt.setGravity(Gravity.CENTER_VERTICAL);
        pubTickerTxt.setPadding(dp(12), 0, dp(12), 0);
        pubTickerTxt.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pubTickerBox.addView(pubTickerTxt);
        rotWrapper.addView(pubTickerBox);

        pubModal = new FrameLayout(this);
        pubModal.setVisibility(View.GONE);
        pubModal.setBackgroundColor(0xCC000000);
        FrameLayout.LayoutParams modalLp = new FrameLayout.LayoutParams(dp(180), dp(180));
        modalLp.gravity = Gravity.BOTTOM | Gravity.END;
        modalLp.setMargins(dp(16), dp(16), dp(16), dp(48));
        pubModal.setLayoutParams(modalLp);

        pubModalImg = new ImageView(this);
        pubModalImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pubModalImg.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pubModal.addView(pubModalImg);

        pubModalVid = new VideoView(this);
        pubModalVid.setVisibility(View.GONE);
        pubModalVid.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pubModal.addView(pubModalVid);

        rotWrapper.addView(pubModal);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int parsearColor(String c, int def) {
        if (c == null) return def;
        String s = c.trim().toLowerCase();
        if (s.isEmpty()) return def;
        if (s.equals("transparent")) return Color.TRANSPARENT;
        try {
            if (s.startsWith("rgb")) {
                int a = s.indexOf('('), z = s.indexOf(')');
                if (a < 0 || z < 0) return def;
                String[] p = s.substring(a + 1, z).split(",");
                if (p.length < 3) return def;
                int r = Math.round(Float.parseFloat(p[0].trim()));
                int g = Math.round(Float.parseFloat(p[1].trim()));
                int bl = Math.round(Float.parseFloat(p[2].trim()));
                float al = p.length > 3 ? Float.parseFloat(p[3].trim()) : 1f;
                al = Math.max(0f, Math.min(1f, al));
                return Color.argb(Math.round(al * 255), r, g, bl);
            }
            if (s.startsWith("#") && s.length() == 4) {
                s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2) + s.charAt(3) + s.charAt(3);
            } else if (s.startsWith("#") && s.length() == 9) {
                s = "#" + s.substring(7, 9) + s.substring(1, 7);
            }
            return Color.parseColor(s);
        } catch (Exception e) { return def; }
    }

    private final Runnable pubTickerLoop = new Runnable() {
        @Override public void run() {
            if (!pubTickerRunning || pubTickerBox == null) return;
            float t = (SystemClock.uptimeMillis() - pubTickerT0) / (float) pubTickerDurMs;
            if (t >= 1f) {
                pubTickerRunning = false;
                pubTickerBox.setVisibility(View.GONE);
                avisarAlWebView("window.gtv&&window.gtv.onTickerTerminado&&window.gtv.onTickerTerminado();");
                return;
            }
            if (pubTickerTxt != null)
                pubTickerTxt.setTranslationX(pubTickerFrom + (pubTickerTo - pubTickerFrom) * t);
            pubTickerBox.postOnAnimation(this);
        }
    };

    private void tickerDetener(boolean ocultar) {
        pubTickerRunning = false;
        pubTickerGen++;
        if (pubTickerBox != null) {
            pubTickerBox.removeCallbacks(pubTickerLoop);
            if (ocultar) pubTickerBox.setVisibility(View.GONE);
        }
    }

    private void tickerArrancar(final int gen, final float prog, long delayMs) {
        if (pubTickerBox == null || pubTickerTxt == null) return;
        pubTickerBox.removeCallbacks(pubTickerLoop);
        pubTickerRunning = false;
        pubTickerBox.setVisibility(View.INVISIBLE);
        final int[] intentos = {0};
        pubTickerBox.postDelayed(new Runnable() {
            @Override public void run() {
                if (gen != pubTickerGen || pubTickerBox == null) return;
                int ancho = pubTickerBox.getWidth();
                if (ancho <= 0) { if (++intentos[0] < 50) pubTickerBox.postDelayed(this, 100); return; }
                float anchoTexto = pubTickerTxt.getPaint().measureText(pubTickerTxt.getText().toString());
                int anchoView = (int) Math.ceil(anchoTexto) + dp(24);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pubTickerTxt.getLayoutParams();
                if (lp.width != anchoView) { lp.width = anchoView; pubTickerTxt.setLayoutParams(lp); }
                pubTickerFrom = ancho;
                pubTickerTo = -anchoView;
                pubTickerT0 = SystemClock.uptimeMillis() - (long) (prog * pubTickerDurMs);
                pubTickerTxt.setTranslationX(pubTickerFrom + (pubTickerTo - pubTickerFrom) * prog);
                pubTickerBox.setVisibility(View.VISIBLE);
                pubTickerRunning = true;
                pubTickerBox.postOnAnimation(pubTickerLoop);
            }
        }, delayMs);
    }

    private void reiniciarTickerSiVisible() {
        if (pubTickerBox == null || !pubTickerRunning) return;
        float prog = (SystemClock.uptimeMillis() - pubTickerT0) / (float) pubTickerDurMs;
        prog = Math.max(0f, Math.min(0.99f, prog));
        int gen = ++pubTickerGen;
        tickerArrancar(gen, prog, 150);
    }

    private Typeface fuenteDe(String f) {
        if (f == null) return Typeface.SANS_SERIF;
        String s = f.toLowerCase().trim();
        try {
            switch (s) {
                case "roboto":     return Typeface.create("sans-serif", Typeface.NORMAL);
                case "montserrat": return ResourcesCompat.getFont(this, R.font.montserrat);
                case "poppins":    return ResourcesCompat.getFont(this, R.font.poppins);
                case "inter":      return ResourcesCompat.getFont(this, R.font.inter);
                case "bebasneue":  return ResourcesCompat.getFont(this, R.font.bebasneue);
                case "arial":
                default:           return Typeface.SANS_SERIF;
            }
        } catch (Exception e) {
            return Typeface.SANS_SERIF;
        }
    }
    private void avisarAlWebView(String js) {
        if (myWebView == null || !webViewListo) return;
        myWebView.post(() -> {
            try {
                myWebView.evaluateJavascript("javascript:(function(){try{" + js + "}catch(e){}})();", null);
            } catch (Exception ignored) {}
        });
    }
    
    public class WebAppInterface {
        @JavascriptInterface public String getDeviceId() { return generarDeviceId(); }

        @JavascriptInterface public String getSignatureHash() {
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                android.content.pm.PackageInfo pi;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pi = pm.getPackageInfo(getPackageName(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                    android.content.pm.SigningInfo si = pi.signingInfo;
                    if (si == null) return "";
                    android.content.pm.Signature[] sigs = si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
                    if (sigs == null || sigs.length == 0) return "";
                    return calcularSHA256(sigs[0].toByteArray());
                } else {
                    pi = pm.getPackageInfo(getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
                    if (pi.signatures == null || pi.signatures.length == 0) return "";
                    return calcularSHA256(pi.signatures[0].toByteArray());
                }
            } catch (Exception e) { return ""; }
        }

        private String calcularSHA256(byte[] data) throws Exception {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hash.length; i++) { if (i > 0) sb.append(":"); sb.append(String.format("%02X", hash[i])); }
            return sb.toString();
        }

        @JavascriptInterface public void setLista(final String json) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(json);
                    canales.clear();
                    esPeliculas = false;
                    urlPeliculasM3U = null;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String nombre = o.optString("nombre", o.optString("name", "Canal " + (i + 1)));
                        String referer = o.optString("referer", "");
                        float gain = (float) o.optDouble("gain", 1.0);
                        if (gain <= 0f || gain > 2f) gain = 1.0f;
                        boolean oculto = o.optBoolean("oculto", false);
                        boolean esP = o.optBoolean("esPeliculas", false);
                        String urlPM = o.optString("urlPeliculasM3U", null);
                        if (esP && urlPM != null && !urlPM.isEmpty()) { esPeliculas = true; urlPeliculasM3U = urlPM; }
                        canales.add(new Canal(o.getString("url"), nombre, o.optString("logo", ""), referer, gain, oculto));
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface public void sincronizarIndice(final int i) { runOnUiThread(() -> indiceActual = i); }

        @JavascriptInterface public void detener() {
            runOnUiThread(() -> {
                ytToken++;
                try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface public void reproducir(String url, String nombre, String numero, String logo, String referer, float gainArg) {
            final float gain = (gainArg > 0f && gainArg <= 2f) ? gainArg : 1.0f;
            final String refererFinal = (referer != null) ? referer : "";
            runOnUiThread(() -> {
                int idx = -1;
                for (int i = 0; i < canales.size(); i++) if (canales.get(i).url.equals(url)) { idx = i; break; }
                if (idx >= 0) {
                    indiceActual = idx;
                    Canal c = canales.get(idx);
                    String refFinal = (c.referer != null && !c.referer.isEmpty()) ? c.referer : refererFinal;
                    float gainFinal = (c.gain > 0f) ? c.gain : gain;
                    reproducirEnExo(new Canal(c.url, c.nombre, c.logo, refFinal, gainFinal, c.oculto));
                } else reproducirEnExo(new Canal(url, nombre, logo, refererFinal, gain));
            });
        }
        @JavascriptInterface public void reproducir(String url, String nombre, String numero, String logo, String referer) { reproducir(url, nombre, numero, logo, referer, 1.0f); }
        @JavascriptInterface public void reproducir(String url, String nombre, String numero, String logo) { reproducir(url, nombre, numero, logo, "", 1.0f); }

        @JavascriptInterface public void reproducirCanal(final int idx) {
            runOnUiThread(() -> {
                if (idx < 0 || idx >= canales.size()) return;
                indiceActual = idx;
                Canal c = canales.get(idx);
                if (esCanalExoPlayer(c.url)) {
                    reproducirEnExo(c);
                } else if (c.url.toLowerCase().contains("youtube.com") || c.url.toLowerCase().contains("youtu.be")) {
                    String vid = null;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:watch\\?v=|youtu\\.be/|embed/|live/)([\\w-]{11})").matcher(c.url);
                    if (m.find()) vid = m.group(1);
                    if (vid != null) reproducirYouTubeNativo(vid);
                    else irACanalWebView(idx);
                } else if (esEmbedTercero(c.url)) {
                    detenerTodoNativo();
                    myWebView.setVisibility(View.VISIBLE);
                    myWebView.onResume();
                    myWebView.resumeTimers();
                    enYouTubeWeb = false;
                    irACanalWebView(idx);
                } else {
                    irACanalWebView(idx);
                }
            });
        }

        @JavascriptInterface public boolean esExoPlayer(String url) { return esCanalExoPlayer(url); }

        @JavascriptInterface public void mostrarWebView() {
            runOnUiThread(() -> {
                ytToken++;
                tickerDetener(true);
                enExoPlayer = false;
                try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
                playerContainer.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume(); myWebView.resumeTimers();
                ocultarControlesNativos();
            });
        }

        @JavascriptInterface public void cerrarPlayer() { runOnUiThread(() -> cerrarExoPlayer()); }

        @JavascriptInterface public void abrirDialogo() {
            runOnUiThread(() -> mostrarDialogoAgregarLista());
        }

        @JavascriptInterface public void actualizarInfo(String nombre, String numero, String logo) {
            runOnUiThread(() -> {
                nombreCanal.setText(nombre); numeroCanal.setText(numero);
                if (logo != null && !logo.isEmpty()) Glide.with(MainActivity.this).load(logo).into(logoCanal);
            });
        }

        @JavascriptInterface public void salirApp() {
            runOnUiThread(() -> finishAndRemoveTask());
        }

        @JavascriptInterface public boolean estaEnExo() { return enExoPlayer; }

        @JavascriptInterface public void rotarDesdeAndroid(final boolean r) {
            runOnUiThread(() -> { rotado = r; getSharedPreferences("gtv_prefs", MODE_PRIVATE).edit().putBoolean("rotado", rotado).apply(); aplicarRotacion(); });
        }

        @JavascriptInterface
        public void mostrarTicker(final String texto, final String badge, final int velocidad,
                                  final String colorBg, final String colorAc) {
            mostrarTickerImpl(texto, badge, velocidad, colorBg, colorAc, 14, "arial");
        }

        @JavascriptInterface
        public void mostrarTickerConTamano(final String texto, final String badge, final int velocidad,
                                            final String colorBg, final String colorAc, final int tamPx) {
            mostrarTickerImpl(texto, badge, velocidad, colorBg, colorAc, tamPx, "arial");
        }

        @JavascriptInterface
        public void mostrarTickerConFuente(final String texto, final String badge, final int velocidad,
                                            final String colorBg, final String colorAc, final int tamPx,
                                            final String fuente) {
            mostrarTickerImpl(texto, badge, velocidad, colorBg, colorAc, tamPx, fuente);
        }

        @JavascriptInterface
        public void ocultarTicker() {
            runOnUiThread(() -> tickerDetener(true));
        }

        @JavascriptInterface
        public void mostrarModalPublicidad(final String url, final boolean esVideo, final int duracionSeg) {
            runOnUiThread(() -> {
                if (pubModal == null) return;
                if (pubModalVid != null) {
                    try { pubModalVid.stopPlayback(); pubModalVid.setVideoURI(null); } catch (Exception ignored) {}
                }
                if (pubModalClose != null) pubHandler.removeCallbacks(pubModalClose);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pubModal.getLayoutParams();
                lp.width = dp(180);
                lp.height = dp(180);
                pubModal.setLayoutParams(lp);
                pubModal.setVisibility(View.VISIBLE);
                if (esVideo) {
                    pubModalImg.setVisibility(View.GONE);
                    pubModalVid.setVisibility(View.VISIBLE);
                    try {
                        pubModalVid.setVideoURI(Uri.parse(url));
                        pubModalVid.setOnPreparedListener(mp -> {
                            try {
                                mp.setLooping(false);
                                mp.setVolume(0f, 0f);
                                mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                            } catch (Exception ignored) {}
                            try { pubModalVid.start(); } catch (Exception ignored) {}
                        });
                        pubModalVid.setOnCompletionListener(mp -> {
                            try { mp.stop(); } catch (Exception ignored) {}
                        });
                        pubModalVid.setOnErrorListener((mp, what, extra) -> {
                            pubModal.setVisibility(View.GONE);
                            avisarAlWebView("window.gtv&&window.gtv.onModalTerminado&&window.gtv.onModalTerminado();");
                            return true;
                        });
                    } catch (Exception e) {
                        pubModal.setVisibility(View.GONE);
                        avisarAlWebView("window.gtv&&window.gtv.onModalTerminado&&window.gtv.onModalTerminado();");
                        return;
                    }
                } else {
                    pubModalVid.setVisibility(View.GONE);
                    pubModalImg.setVisibility(View.VISIBLE);
                    try {
                        Glide.with(MainActivity.this)
                            .load(url)
                            .override(dp(180), dp(180))
                            .centerCrop()
                            .dontAnimate()
                            .into(pubModalImg);
                    } catch (Exception e) {
                        pubModal.setVisibility(View.GONE);
                        avisarAlWebView("window.gtv&&window.gtv.onModalTerminado&&window.gtv.onModalTerminado();");
                        return;
                    }
                }
                pubModalClose = () -> {
                    if (pubModal != null) pubModal.setVisibility(View.GONE);
                    if (pubModalVid != null) {
                        try { pubModalVid.stopPlayback(); pubModalVid.setVideoURI(null); } catch (Exception ignored) {}
                    }
                    avisarAlWebView("window.gtv&&window.gtv.onModalTerminado&&window.gtv.onModalTerminado();");
                };
                pubHandler.postDelayed(pubModalClose, Math.max(2, duracionSeg) * 1000L);
            });
        }

        @JavascriptInterface
        public void ocultarPublicidad() {
            runOnUiThread(() -> {
                tickerDetener(true);
                if (pubModal != null) pubModal.setVisibility(View.GONE);
                if (pubModalClose != null) pubHandler.removeCallbacks(pubModalClose);
            });
        }
    }

    private void mostrarTickerImpl(final String texto, final String badge, final int velocidad,
                                    final String colorBg, final String colorAc, final int tamPx,
                                    final String fuente) {
        runOnUiThread(() -> {
            if (pubTickerBox == null || pubTickerTxt == null) return;
            tickerDetener(false);
            final int gen = pubTickerGen;
            String tf = (badge != null && !badge.isEmpty()) ? (badge + "  " + texto) : String.valueOf(texto);
            pubTickerTxt.setText(tf);
            int tamSeguro = Math.max(8, Math.min(72, tamPx));
            pubTickerTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamSeguro);
            pubTickerTxt.setTypeface(fuenteDe(fuente));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pubTickerBox.getLayoutParams();
            int altura = Math.max(dp(32), dp(tamSeguro + 10));
            if (lp.height != altura) { lp.height = altura; pubTickerBox.setLayoutParams(lp); }
            pubTickerBox.setBackgroundColor(parsearColor(colorBg, 0x88000000));
            pubTickerTxt.setTextColor(parsearColor(colorAc, 0xFFFFFFFF));
            pubTickerDurMs = Math.max(1, velocidad) * 1000;
            tickerArrancar(gen, 0f, 0);
        });
    }

    private void mostrarDialogoAgregarLista() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Agregar lista");

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        ll.setPadding(pad, pad, pad, pad);

        TextView tv = new TextView(this);
        tv.setText("Ingresá la palabra clave o una URL de lista M3U:");
        tv.setTextColor(0xFFCCCCCC);
        tv.setTextSize(14);
        ll.addView(tv);

        final EditText et = new EditText(this);
        et.setHint("Palabra clave o https://...");
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF888888);
        et.setSingleLine(false);
        LinearLayout.LayoutParams lpEt = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpEt.topMargin = dp(12);
        et.setLayoutParams(lpEt);
        ll.addView(et);

        builder.setView(ll);
        builder.setPositiveButton("Cargar", (dialog, which) -> {
            String texto = et.getText().toString().trim();
            if (!texto.isEmpty()) validarYcargar(texto);
        });
        builder.setNegativeButton("Cancelar", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFFD700);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFFFD700);
            et.requestFocus();
        });
        dialog.show();
    }

    private void validarYcargar(final String texto) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Validando...");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                URL url = new URL(VALIDAR_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-GTV-Origin", SELLO);

                JSONObject body = new JSONObject();
                body.put("texto", texto);
                body.put("deviceId", generarDeviceId());

                java.io.OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close(); is.close();

                final JSONObject resp = new JSONObject(sb.toString());
                runOnUiThread(() -> {
                    progress.dismiss();
                    String tipo = resp.optString("tipo", "");
                    String urlLista = resp.optString("url", "");
                    String error = resp.optString("error", "");
                    if (!error.isEmpty()) {
                        String msg;
                        switch (error) {
                            case "clave_invalida": msg = "Palabra clave incorrecta."; break;
                            case "licencia_requerida": msg = "Necesitás una licencia activa para usar la lista maestra."; break;
                            default: msg = "Error: " + error;
                        }
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (tipo.equals("maestra") || tipo.equals("externa")) {
                        cargarLista(urlLista);
                    } else {
                        Toast.makeText(MainActivity.this, "Respuesta inválida del servidor.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(MainActivity.this, "Error de conexión: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void cargarLista(final String urlLista) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Cargando lista...");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                URL url = new URL(urlLista);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("X-GTV-Origin", SELLO);
                if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
                InputStream is = conn.getInputStream();
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close(); is.close();

                final String contenido = sb.toString();
                final ArrayList<Canal> listaParseada = parsearM3u(contenido);

                runOnUiThread(() -> {
                    progress.dismiss();
                    if (listaParseada.isEmpty()) {
                        Toast.makeText(MainActivity.this, "La lista está vacía o es inválida.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    canales.clear();
                    canales.addAll(listaParseada);
                    indiceActual = 0;
                    enviarListaAlHtml();
                    Toast.makeText(MainActivity.this, "Lista cargada: " + canales.size() + " canales", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(MainActivity.this, "Error al cargar lista: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private ArrayList<Canal> parsearM3u(String texto) {
        ArrayList<Canal> out = new ArrayList<>();
        String[] lines = texto.split("\r?\n");
        String nombre = null, logo = "", referer = "";
        float gain = 1.0f;
        boolean esPeliculasTmp = false;
        String urlPeliculasTmp = null;

        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.isEmpty() || l.equals("#EXTM3U") || l.startsWith("#EXTVLCOPT")) continue;

            if (l.startsWith("#EXTINF")) {
                java.util.regex.Matcher lg = java.util.regex.Pattern.compile("tvg-logo=\"([^\"]*)\"").matcher(l);
                logo = lg.find() ? lg.group(1) : "";
                java.util.regex.Matcher rf = java.util.regex.Pattern.compile("tvg-referer=\"([^\"]*)\"").matcher(l);
                referer = rf.find() ? rf.group(1) : "";
                java.util.regex.Matcher gn = java.util.regex.Pattern.compile("tvg-gain=\"([^\"]*)\"").matcher(l);
                gain = 1.0f;
                if (gn.find()) {
                    try { float g = Float.parseFloat(gn.group(1)); if (g > 0f && g <= 2f) gain = g; } catch (Exception ignored) {}
                }
                int comma = l.lastIndexOf(',');
                if (comma != -1 && comma < l.length() - 1) nombre = l.substring(comma + 1).trim();
                else nombre = "Canal " + (out.size() + 1);

                String up = nombre.toUpperCase();
                esPeliculasTmp = up.contains("PELÍCULAS") || up.contains("PELICULAS") || up.contains("VIDEOTECA") || up.contains("PELIS");
                urlPeliculasTmp = null;
                continue;
            }

            if (l.startsWith("http") && nombre != null) {
                if (l.contains("peliculas") || l.contains("videoteca") || l.contains("m3u-peliculas")) {
                    esPeliculasTmp = true;
                }
                if (esPeliculasTmp) urlPeliculasTmp = l;
                out.add(new Canal(l, nombre, logo, referer, gain, false));
                nombre = null; logo = ""; referer = ""; gain = 1.0f;
                esPeliculasTmp = false; urlPeliculasTmp = null;
            }
        }
        return out;
    }

    private void enviarListaAlHtml() {
        try {
            JSONArray arr = new JSONArray();
            for (Canal c : canales) {
                JSONObject o = new JSONObject();
                o.put("nombre", c.nombre);
                o.put("name", c.nombre);
                o.put("url", c.url);
                o.put("logo", c.logo != null ? c.logo : "");
                o.put("referer", c.referer != null ? c.referer : "");
                o.put("gain", c.gain);
                o.put("oculto", c.oculto);
                arr.put(o);
            }
            final String json = arr.toString();
            runOnUiThread(() -> {
                if (myWebView != null) {
                    myWebView.evaluateJavascript(
                        "javascript:(function(){try{window.gtvSetCanales(" + json + ");}catch(e){}})();",
                        null);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error enviando lista al HTML: " + e.getMessage());
        }
    }

    private void volverAlWebViewConJS(String js) {
        tickerDetener(true);
        mostrarEscudo(false);
        final boolean veniaDeYouTube = enYouTubeWeb;
        enExoPlayer = false;
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
        playerContainer.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim(); ocultarLoaders();
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        handler.removeCallbacks(reintentoRunnable);
        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume(); myWebView.resumeTimers();
        myWebView.postDelayed(() -> {
            try {
                if (js != null && !js.isEmpty())
                    myWebView.evaluateJavascript("javascript:(function(){try{" + js + "}catch(e){}})();", null);
            } catch (Exception ignored) {}
        }, 100);
    }

    private void cerrarExoPlayer() {
        if (enYouTubeWeb) { salirDeYouTube(); return; }
        if (myWebView != null) {
            myWebView.evaluateJavascript(
                "(function(){try{return window._zappingEnCurso?'SI':'NO';}catch(e){return 'NO';}})()",
                value -> ejecutarCierreExo(value != null && value.contains("SI")));
            return;
        }
        ejecutarCierreExo(false);
    }

    private void ejecutarCierreExo(boolean esZapping) {
        tickerDetener(true);
        mostrarEscudo(false);
        enExoPlayer = false;
        enYouTubeNativo = false;
        enVlc = false;
        canalActual = null;
        handler.removeCallbacks(reintentoRunnable);
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception ignored) {}
        try { if (youtubePlayer != null) youtubePlayer.pause(); } catch (Exception ignored) {}
        try { if (vlcPlayer != null) { vlcPlayer.stop(); vlcPlayer.release(); vlcPlayer = null; } } catch (Exception ignored) {}
        playerContainer.setVisibility(View.GONE);
        youtubePlayerView.setVisibility(View.GONE);
        vlcSurface.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim(); ocultarLoaders();
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume(); myWebView.resumeTimers();
        if (!esZapping) myWebView.postDelayed(() -> {
            try {
                myWebView.evaluateJavascript(
                    "javascript:window.sincronizarIndiceJS&&window.sincronizarIndiceJS(" + indiceActual + ");"
                  + "window.volverDelExo&&window.volverDelExo();", null);
            } catch (Exception ignored) {}
        }, 32);
    }

    private void ocultarControlesNativos() {
        controlesVisibles = false;
        infoCanal.setVisibility(View.GONE);
        btnPrev.setVisibility(View.GONE); btnNext.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE); btnRotate.setVisibility(View.GONE);
    }

    private void mostrarControles() {
        if (!enExoPlayer && !enYouTubeWeb && !enYouTubeNativo && !enVlc) return;
        controlesVisibles = true;
        infoCanal.setVisibility(View.VISIBLE);
        btnPrev.setVisibility(View.VISIBLE); btnNext.setVisibility(View.VISIBLE);
        btnClose.setVisibility(View.VISIBLE); btnRotate.setVisibility(View.VISIBLE);
        if (ocultarControlesRunnable != null) handler.removeCallbacks(ocultarControlesRunnable);
        ocultarControlesRunnable = this::ocultarControlesNativos;
        handler.postDelayed(ocultarControlesRunnable, 5000);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (playerContainer.getVisibility() == View.VISIBLE || youtubePlayerView.getVisibility() == View.VISIBLE || vlcSurface.getVisibility() == View.VISIBLE) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT: navegar(+1); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: navegar(-1); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (enYouTubeWeb || enYouTubeNativo || enVlc) mostrarControles();
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (enYouTubeWeb) { salirDeYouTube(); return; }
        if (enExoPlayer || enYouTubeNativo || enVlc) { ytToken++; cerrarExoPlayer(); return; }
        ytToken++;
        if (myWebView == null) { finish(); return; }
        myWebView.evaluateJavascript(
            "(function(){try{return window.atrasNativo?window.atrasNativo():false}catch(e){return false}})()",
            v -> { if (!"true".equals(v)) finishAndRemoveTask(); });
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) ocultarBarrasSistema(); }

    @Override protected void onStart() {
        super.onStart();
        if (enExoPlayer && exoPlayer != null) {
            try { exoPlayer.play(); } catch (Exception ignored) {}
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (myWebView != null && !enExoPlayer && !enYouTubeNativo && !enVlc) { myWebView.onPause(); myWebView.pauseTimers(); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (myWebView != null && !enExoPlayer && !enYouTubeNativo && !enVlc) { myWebView.onResume(); myWebView.resumeTimers(); }
    }

    @Override protected void onStop() {
        super.onStop();
        if (exoPlayer != null) try { exoPlayer.pause(); } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        tickerDetener(true);
        detenerLoaderAnim();
        if (pubModalClose != null) { try { pubHandler.removeCallbacks(pubModalClose); } catch (Exception ignored) {} pubModalClose = null; }
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        handler.removeCallbacks(reintentoRunnable);
        handler.removeCallbacks(zapRun);
        try { File f = new File(getCacheDir(), "update.apk"); if (f.exists()) f.delete(); } catch (Exception ignored) {}
        if (exoPlayer != null) { try { exoPlayer.release(); } catch (Exception ignored) {} exoPlayer = null; }
        try { if (vlcPlayer != null) { vlcPlayer.stop(); vlcPlayer.release(); vlcPlayer = null; } } catch (Exception ignored) {}
        try { if (libVLC != null) { libVLC.release(); libVLC = null; } } catch (Exception ignored) {}
        try { if (youtubePlayerView != null) youtubePlayerView.release(); } catch (Exception ignored) {}
        if (myWebView != null) {
            try {
                myWebView.loadUrl("about:blank");
                myWebView.stopLoading();
                myWebView.destroy();
                myWebView = null;
            } catch (Exception ignored) {}
        }
        ytExec.shutdownNow();
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
