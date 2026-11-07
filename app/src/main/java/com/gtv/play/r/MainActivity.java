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
import android.view.Gravity;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GTV_R_DEBUG";

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
    private View overlayControls;
    private YouTubePlayerView youtubePlayerView;
    private SurfaceView vlcSurface;
    private android.graphics.drawable.Drawable bgContainerOrig, bgRotOrig;
    private boolean bgGuardado = false;

    // YouTube nativo: un único listener registrado en onCreate
    private YouTubePlayer ytPlayer = null;
    private String ytVideoPendiente = null;

    private LibVLC libVlc;
    private MediaPlayer vlcPlayer;
    private boolean enVlc = false;

    private static final String WORKER_URL = "https://gtv-portero.abrel03.workers.dev/";
    private static final String VALIDAR_URL = "https://gtv-portero.abrel03.workers.dev/api/reproductor/validar";
    private static final String SELLO = "gtv_app_2026_secreto_xyz789";
    private boolean enYouTubeWeb = false;
    private boolean enYouTubeNativo = false;
    private String jsPendienteAlVolver = null;

    private final ArrayList<Canal> canales = new ArrayList<>();
    private int indiceActual = -1;

    private boolean rotado = false;
    private volatile boolean enExoPlayer = false;
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

    private static final String UPDATE_PREFS = "gtv_r_update";
    private static final String UPDATE_TRIES_PREFIX = "gtv_r_update_tries_";
    private static final String VERSION_URL = "https://gtv-portero.abrel03.workers.dev/api/version-r";
    private static final int MAX_SKIPS = 3;

    private final ExecutorService ytExec = Executors.newSingleThreadExecutor();
    private volatile int ytToken = 0;

    private Canal canalActual;
    private int reintentos = 0;
    private int reintentosVlc = 0;
    private boolean probandoVlc = false;

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
            if (zapPend < 0 || zapPend >= canales.size()) { zapPend = -1; return; }
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
            boolean esEmbedTercero = u.contains("kick.com") || u.contains("twitch.tv")
                || u.contains("ok.ru") || u.contains("odnoklassniki.ru")
                || u.contains("dailymotion.com") || u.contains("tokyvideo.com");
            if (esYT) {
                reproducirEnYouTube(c);
            } else if (esEmbedTercero) {
                if (enExoPlayer || enVlc || enYouTubeNativo) detenerMotores();
                playerContainer.setVisibility(View.GONE);
                youtubePlayerView.setVisibility(View.GONE);
                vlcSurface.setVisibility(View.GONE);
                playerView.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume();
                myWebView.resumeTimers();
                enYouTubeWeb = true;
                irACanalWebView(indiceActual);
            } else {
                reproducirEnExo(c);
            }
        }
    };

    static class Canal {
        final String url, nombre, logo, referer;
        final float gain;
        boolean oculto;
        boolean esPeliculas;
        String urlPeliculasM3U;

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
        overlayControls = findViewById(R.id.overlay_controls);
        youtubePlayerView = findViewById(R.id.youtube_player_view);
        vlcSurface = findViewById(R.id.vlc_surface);

        inicializarPublicidadNativa();

        rotado = getSharedPreferences("gtv_r_prefs", MODE_PRIVATE).getBoolean("rotado", false);

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

        getLifecycle().addObserver(youtubePlayerView);
        youtubePlayerView.setVisibility(View.GONE);

        // Un solo listener para YouTube (onReady se dispara una única vez)
        youtubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(YouTubePlayer youTubePlayer) {
                ytPlayer = youTubePlayer;
                if (ytVideoPendiente != null) {
                    String v = ytVideoPendiente;
                    ytVideoPendiente = null;
                    try { youTubePlayer.loadVideo(v, 0f); } catch (Exception ignored) {}
                    detenerLoaderAnim(); ocultarLoaders();
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                }
            }
        });

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (exoLoader == null) return;
                if (state == Player.STATE_READY) {
                    reintentos = 0;
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
                if (reintentar()) return;
                if (!probandoVlc && canalActual != null) {
                    probandoVlc = true;
                    reintentosVlc = 0;
                    Log.e(TAG, "ExoPlayer falló, probando LibVLC: " + canalActual.url);
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
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });

        inicializarVLC();
        configurarWebView();
        configurarBotones();

        // CARGAMOS EL HTML LOCAL
        myWebView.loadUrl("file:///android_asset/index.html");

        ocultarBarrasSistema();

        handler.postDelayed(this::checkForUpdates, 2000);
        handler.postDelayed(() -> {
            try { inicializarWatchNext(); }
            catch (Throwable t) { Log.e(TAG, "WatchNext init error: " + t); }
        }, 5000);
    }

    private void inicializarVLC() {
        try {
            ArrayList<String> args = new ArrayList<>();
            args.add("--no-drop-late-frames");
            args.add("--no-skip-frames");
            args.add("--rtsp-tcp");
            args.add("--network-caching=1500");
            libVlc = new LibVLC(this, args);
            vlcPlayer = new MediaPlayer(libVlc);
            vlcPlayer.setEventListener(event -> {});
        } catch (Throwable t) {
            libVlc = null;
            vlcPlayer = null;
        }
    }

    private boolean reintentar() {
        if (reintentos >= 3 || canalActual == null || !enExoPlayer) return false;
        reintentos++;
        mostrarLoaderSintonizando();
        handler.removeCallbacks(reintentoRunnable);
        handler.postDelayed(reintentoRunnable, 1500L * reintentos);
        return true;
    }

    private void reproducirConVLC(Canal c) {
        if (vlcPlayer == null) { mostrarLoaderError(); return; }
        enExoPlayer = false;
        enVlc = true;
        enYouTubeNativo = false;
        enYouTubeWeb = false;
        canalActual = c;
        detenerLoaderAnim();
        myWebView.setVisibility(View.GONE);
        youtubePlayerView.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.bringToFront();
        try { if (exoPlayer != null) { exoPlayer.stop(); exoPlayer.clearMediaItems(); } } catch (Exception ignored) {}
        nombreCanal.setText(c.nombre);
        numeroCanal.setText((indiceActual + 1) + "/" + canales.size());
        if (c.logo != null && !c.logo.isEmpty()) Glide.with(this).load(c.logo).into(logoCanal);
        vlcSurface.setVisibility(View.VISIBLE);
        try {
            final Media media = new Media(libVlc, Uri.parse(c.url));
            if (c.referer != null && !c.referer.isEmpty()) media.addOption(":http-referrer=" + c.referer);
            media.addOption(":network-caching=1500");
            vlcPlayer.setMedia(media);
            media.release();
            vlcPlayer.setVolume((int)(c.gain * 100));
            vlcPlayer.play();
        } catch (Exception e) { mostrarLoaderError(); return; }
        mostrarLoaderSintonizando();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!enVlc) return;
                if (vlcPlayer != null && vlcPlayer.isPlaying()) {
                    detenerLoaderAnim(); ocultarLoaders();
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                } else if (reintentosVlc < 2) {
                    reintentosVlc++;
                    handler.postDelayed(this, 3000);
                } else {
                    detenerLoaderAnim(); mostrarLoaderError();
                    avisarAlWebView("window.gtv&&window.gtv.onNativoError&&window.gtv.onNativoError();");
                }
            }
        }, 2000);
        mostrarControles();
        playerContainer.post(() -> { aplicarRotacion(); reposicionarBotones(); });
    }

    private void reproducirEnYouTube(Canal c) {
        ytToken++;
        enExoPlayer = false;
        enVlc = false;
        enYouTubeWeb = false;
        enYouTubeNativo = true;
        canalActual = c;
        detenerMotores();
        enYouTubeNativo = true; // detenerMotores lo apaga; lo reafirmamos
        myWebView.setVisibility(View.GONE);
        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.bringToFront();
        playerView.setVisibility(View.GONE);
        vlcSurface.setVisibility(View.GONE);
        youtubePlayerView.setVisibility(View.VISIBLE);
        nombreCanal.setText(c.nombre);
        numeroCanal.setText((indiceActual + 1) + "/" + canales.size());
        if (c.logo != null && !c.logo.isEmpty()) Glide.with(this).load(c.logo).into(logoCanal);
        mostrarLoaderSintonizando();
        final int tok = ytToken;
        String handle = null, channelId = null, videoId = null;
        java.util.regex.Matcher mCh = java.util.regex.Pattern.compile("channel/(UC[\\w-]{22})").matcher(c.url);
        if (mCh.find()) channelId = mCh.group(1);
        java.util.regex.Matcher mVid = java.util.regex.Pattern.compile("(?:watch\\?v=|youtu\\.be/|/live/|/embed/|/shorts/)([\\w-]{11})").matcher(c.url);
        if (mVid.find()) videoId = mVid.group(1);
        java.util.regex.Matcher mH = java.util.regex.Pattern.compile("youtube\\.com/(@[\\w.-]+)").matcher(c.url);
        if (mH.find()) handle = mH.group(1);
        if (videoId != null) {
            cargarVideoEnYoutubeNativo(videoId, tok);
        } else if (channelId != null || handle != null) {
            final String chId = channelId;
            final String h = handle;
            new Thread(() -> {
                String vid = resolverVideoIdLive(chId, h);
                runOnUiThread(() -> {
                    if (tok != ytToken) return;
                    if (vid != null) cargarVideoEnYoutubeNativo(vid, tok);
                    else { detenerLoaderAnim(); mostrarLoaderError(); }
                });
            }).start();
        } else {
            detenerLoaderAnim(); mostrarLoaderError();
        }
        mostrarControles();
        playerContainer.post(() -> { aplicarRotacion(); reposicionarBotones(); });
    }

    private void cargarVideoEnYoutubeNativo(final String videoId, final int tok) {
        runOnUiThread(() -> {
            if (tok != ytToken) return;
            try {
                if (ytPlayer != null) {
                    ytPlayer.loadVideo(videoId, 0f);
                    detenerLoaderAnim(); ocultarLoaders();
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                } else {
                    // el player todavía no está listo: onReady cargará este video
                    ytVideoPendiente = videoId;
                }
            } catch (Exception e) {
                detenerLoaderAnim(); mostrarLoaderError();
            }
        });
    }

    private String resolverVideoIdLive(String channelId, String handle) {
        try {
            String url = (channelId != null)
                ? "https://www.youtube.com/channel/" + channelId + "/live"
                : "https://www.youtube.com/" + handle + "/live";
            URL u = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            int code = conn.getResponseCode();
            if (code == 200) {
                String finalUrl = conn.getURL().toString();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]v=([\\w-]{11})").matcher(finalUrl);
                if (m.find()) { conn.disconnect(); return m.group(1); }
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String html = sb.toString();
                int idx = html.indexOf("\"videoDetails\"");
                if (idx != -1) {
                    String block = html.substring(idx, Math.min(idx + 5000, html.length()));
                    java.util.regex.Matcher vm = java.util.regex.Pattern.compile("\"videoId\":\"([\\w-]{11})\"").matcher(block);
                    java.util.regex.Matcher lm = java.util.regex.Pattern.compile("\"isLive\"\\s*:\\s*true").matcher(block);
                    if (vm.find() && lm.find()) { conn.disconnect(); return vm.group(1); }
                }
            }
            conn.disconnect();
        } catch (Exception e) {}
        return null;
    }

    private void detenerMotores() {
        try { if (exoPlayer != null) { exoPlayer.stop(); exoPlayer.clearMediaItems(); } } catch (Exception ignored) {}
        try { if (vlcPlayer != null) { vlcPlayer.stop(); } } catch (Exception ignored) {}
        try { if (ytPlayer != null) { ytPlayer.pause(); } } catch (Exception ignored) {}
        ytVideoPendiente = null;
        enExoPlayer = false;
        enVlc = false;
        enYouTubeNativo = false;
    }

    private void reproducirEnExo(Canal c) {
        ytToken++;
        mostrarEscudo(false);
        enYouTubeWeb = false;
        enYouTubeNativo = false;
        enVlc = false;
        probandoVlc = false;
        enExoPlayer = true;
        canalActual = c;
        reintentos = 0;
        handler.removeCallbacks(reintentoRunnable);
        try { if (vlcPlayer != null) vlcPlayer.stop(); } catch (Exception ignored) {}
        try { if (ytPlayer != null) ytPlayer.pause(); } catch (Exception ignored) {}
        ytVideoPendiente = null;
        myWebView.setVisibility(View.GONE);
        myWebView.onPause();
        youtubePlayerView.setVisibility(View.GONE);
        vlcSurface.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
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

    private MediaSource construirMediaSource(Canal c) {
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000);
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
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

    private boolean esCanalExoPlayer(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        if (u.contains("youtube.com") || u.contains("youtu.be")) return false;
        if (u.contains("kick.com") || u.contains("twitch.tv")
            || u.contains("ok.ru") || u.contains("odnoklassniki.ru")
            || u.contains("dailymotion.com") || u.contains("tokyvideo.com")) return false;
        return true;
    }

    private void navegar(int delta) {
        if (canales.isEmpty()) return;
        int n = canales.size();
        int i = (zapPend >= 0 ? zapPend : indiceActual);
        if (i < 0) i = 0;
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

    private void rotar() {
        rotado = !rotado;
        getSharedPreferences("gtv_r_prefs", MODE_PRIVATE).edit().putBoolean("rotado", rotado).apply();
        aplicarRotacion();
        reposicionarBotones();
        reiniciarTickerSiVisible();
        avisarAlWebView("window.rotarDesdeAndroid&&window.rotarDesdeAndroid(" + rotado + ");");
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
            lpRot.gravity = Gravity.TOP | Gravity.END;
            lpRot.topMargin = 40; lpRot.rightMargin = 40; lpRot.bottomMargin = 0;
            lpClose.gravity = Gravity.BOTTOM | Gravity.END;
            lpClose.bottomMargin = 40; lpClose.rightMargin = 40;
        } else {
            lpRot.gravity = Gravity.BOTTOM | Gravity.END;
            lpRot.bottomMargin = 160; lpRot.rightMargin = 40; lpRot.topMargin = 0;
            lpClose.gravity = Gravity.BOTTOM | Gravity.END;
            lpClose.bottomMargin = 40; lpClose.rightMargin = 40;
        }
        btnRotate.setLayoutParams(lpRot);
        btnClose.setLayoutParams(lpClose);
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
                    }
                });
        });
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
            if (enExoPlayer && exoPlayer != null && exoPlayer.getPlaybackState() != Player.STATE_READY) {
                if (!reintentar()) { detenerLoaderAnim(); mostrarLoaderError(); }
            }
        };
        handler.postDelayed(timeoutCanalCaido, 15000);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configurarWebView() {
        WebSettings s = myWebView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        s.setDatabaseEnabled(true); s.setSupportZoom(false);
        s.setSaveFormData(false); s.setGeolocationEnabled(false);
        s.setTextZoom(100);
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
                if (url != null && url.startsWith("file:///android_asset/")) {
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    String url = request.getUrl().toString();
                    if (url.startsWith(WORKER_URL) || url.startsWith("https://gtv-portero.abrel03.workers.dev")) {
                        Map<String, String> headers = new HashMap<>(request.getRequestHeaders());
                        headers.put("X-GTV-Origin", SELLO);

                        URL u = new URL(url);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setRequestMethod(request.getMethod());
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            conn.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(15000);

                        String contentType = conn.getContentType();
                        String mimeType = "text/html";
                        String charset = "UTF-8";
                        if (contentType != null) {
                            String[] partes = contentType.split(";");
                            mimeType = partes[0].trim();
                            for (int i = 1; i < partes.length; i++) {
                                if (partes[i].trim().toLowerCase().startsWith("charset=")) {
                                    charset = partes[i].trim().substring(8).replace("\"", "");
                                }
                            }
                        }
                        return new WebResourceResponse(mimeType, charset, conn.getInputStream());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error interceptando petición: " + e.getMessage());
                }
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
        });
        myWebView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidPlayer");
    }

    private void avisarAlWebView(String js) {
        if (myWebView == null || !webViewListo) return;
        myWebView.post(() -> {
            try { myWebView.evaluateJavascript("javascript:(function(){try{" + js + "}catch(e){}})();", null); } catch (Exception ignored) {}
        });
    }

    private void configurarBotones() {
        btnShield.setOnClickListener(v -> mostrarControles());
        btnNext.setOnClickListener(v -> { navegar(+1); mostrarControles(); });
        btnPrev.setOnClickListener(v -> { navegar(-1); mostrarControles(); });
        btnClose.setOnClickListener(v -> cerrarExoPlayer());
        btnRotate.setOnClickListener(v -> { rotar(); mostrarControles(); });
        overlayControls.setOnClickListener(v -> mostrarControles());
        overlayControls.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { navegar(+1); mostrarControles(); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { navegar(-1); mostrarControles(); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { mostrarControles(); return true; }
            }
            return false;
        });
    }

    private void mostrarControles() {
        if (!enExoPlayer && !enYouTubeNativo && !enVlc) return;
        controlesVisibles = true;
        infoCanal.setVisibility(View.VISIBLE);
        btnPrev.setVisibility(View.VISIBLE); btnNext.setVisibility(View.VISIBLE);
        btnClose.setVisibility(View.VISIBLE); btnRotate.setVisibility(View.VISIBLE);
        if (ocultarControlesRunnable != null) handler.removeCallbacks(ocultarControlesRunnable);
        ocultarControlesRunnable = this::ocultarControlesNativos;
        handler.postDelayed(ocultarControlesRunnable, 5000);
    }

    private void ocultarControlesNativos() {
        controlesVisibles = false;
        infoCanal.setVisibility(View.GONE);
        btnPrev.setVisibility(View.GONE); btnNext.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE); btnRotate.setVisibility(View.GONE);
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

    private void cerrarExoPlayer() {
        tickerDetener(true);
        mostrarEscudo(false);
        enExoPlayer = false;
        enVlc = false;
        enYouTubeNativo = false;
        enYouTubeWeb = false;
        canalActual = null;
        handler.removeCallbacks(reintentoRunnable);
        handler.removeCallbacks(zapRun);
        zapPend = -1;
        detenerMotores();
        playerContainer.setVisibility(View.GONE);
        youtubePlayerView.setVisibility(View.GONE);
        vlcSurface.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim(); ocultarLoaders();
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume(); myWebView.resumeTimers();
        myWebView.postDelayed(() -> {
            try {
                myWebView.evaluateJavascript(
                    "javascript:window.sincronizarIndiceJS&&window.sincronizarIndiceJS(" + indiceActual + ");"
                  + "window.volverDelExo&&window.volverDelExo();", null);
            } catch (Exception ignored) {}
        }, 32);
    }

    private void inicializarPublicidadNativa() {
        if (rotWrapper == null) return;
        pubTickerBox = new FrameLayout(this);
        pubTickerBox.setVisibility(View.GONE);
        pubTickerBox.setBackgroundColor(0x88000000);
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(36));
        boxLp.gravity = Gravity.BOTTOM;
        pubTickerBox.setLayoutParams(boxLp);
        pubTickerTxt = new TextView(this);
        pubTickerTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        pubTickerTxt.setSingleLine(true);
        pubTickerTxt.setHorizontallyScrolling(true);
        pubTickerTxt.setTextColor(0xFFFFFFFF);
        pubTickerTxt.setShadowLayer(4f, 0f, 1f, 0xD9000000);
        pubTickerTxt.setGravity(Gravity.CENTER_VERTICAL);
        pubTickerTxt.setPadding(dp(12), 0, dp(12), 0);
        pubTickerTxt.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
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
        pubModalImg.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pubModal.addView(pubModalImg);
        pubModalVid = new VideoView(this);
        pubModalVid.setVisibility(View.GONE);
        pubModalVid.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pubModal.addView(pubModalVid);
        rotWrapper.addView(pubModal);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private int parsearColor(String c, int def) {
        if (c == null) return def;
        String s = c.trim().toLowerCase();
        if (s.isEmpty()) return def;
        if (s.equals("transparent")) return Color.TRANSPARENT;
        try {
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
            if (pubTickerTxt != null) pubTickerTxt.setTranslationX(pubTickerFrom + (pubTickerTo - pubTickerFrom) * t);
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
                case "montserrat": return ResourcesCompat.getFont(this, R.font.montserrat);
                case "poppins": return ResourcesCompat.getFont(this, R.font.poppins);
                case "inter": return ResourcesCompat.getFont(this, R.font.inter);
                case "bebasneue": return ResourcesCompat.getFont(this, R.font.bebasneue);
                default: return Typeface.SANS_SERIF;
            }
        } catch (Exception e) { return Typeface.SANS_SERIF; }
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
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
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
            dialogSubtitle.setText("Es necesario actualizar GTV Play R");
            dialogMensaje.setText("Es necesario actualizar para seguir usando la app.");
        } else {
            dialogTitle.setText("🎉 Nueva versión disponible");
            dialogSubtitle.setText("Actualización de GTV Play R");
            dialogMensaje.setText("Podés actualizar ahora o más tarde.\nTe quedan " + (MAX_SKIPS - skips) + " oportunidades.");
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
            int dialogWidth = (int)(screenWidth * 0.90);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        btnActualizar.post(() -> { btnActualizar.requestFocus(); btnActualizar.setFocusable(true); btnActualizar.setFocusableInTouchMode(true); });
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
            getContentResolver().insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, builder.build().toContentValues());
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
        if (url == null || url.isEmpty()) return;
        new Handler().postDelayed(() -> {
            int idx = -1;
            for (int i = 0; i < canales.size(); i++) {
                if (canales.get(i).url.equals(url)) { idx = i; break; }
            }
            if (idx >= 0) { indiceActual = idx; zapPend = idx; handler.post(zapRun); }
        }, 2000);
    }

    public class WebAppInterface {
        @JavascriptInterface public String getDeviceId() { return generarDeviceId(); }

        @JavascriptInterface public void setLista(final String json) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(json);
                    ArrayList<Canal> nueva = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String nombre = o.optString("nombre", o.optString("name", "Canal " + (i + 1)));
                        String referer = o.optString("referer", "");
                        float gain = (float) o.optDouble("gain", 1.0);
                        if (gain <= 0f || gain > 2f) gain = 1.0f;
                        boolean oculto = o.optBoolean("oculto", false);
                        Canal c = new Canal(o.getString("url"), nombre, o.optString("logo", ""), referer, gain, oculto);
                        c.esPeliculas = o.optBoolean("esPeliculas", false);
                        c.urlPeliculasM3U = o.isNull("urlPeliculasM3U") ? null : o.optString("urlPeliculasM3U", null);
                        nueva.add(c);
                    }
                    canales.clear();
                    canales.addAll(nueva);
                } catch (Exception e) {
                    Log.e(TAG, "setLista error: " + e.getMessage());
                }
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
                    zapPend = idx;
                    handler.post(zapRun);
                } else {
                    Canal c = new Canal(url, nombre, logo, refererFinal, gain);
                    if (esCanalYouTube(url)) reproducirEnYouTube(c);
                    else if (esEmbedTercero(url)) { enYouTubeWeb = true; irACanalWebView(indiceActual); }
                    else reproducirEnExo(c);
                }
            });
        }
        @JavascriptInterface public void reproducir(String url, String nombre, String numero, String logo, String referer) { reproducir(url, nombre, numero, logo, referer, 1.0f); }
        @JavascriptInterface public void reproducir(String url, String nombre, String numero, String logo) { reproducir(url, nombre, numero, logo, "", 1.0f); }

        @JavascriptInterface public void reproducirCanal(int idx) {
            runOnUiThread(() -> {
                if (idx < 0 || idx >= canales.size()) return;
                indiceActual = idx;
                zapPend = idx;
                handler.post(zapRun);
            });
        }

        @JavascriptInterface public boolean esExoPlayer(String url) { return esCanalExoPlayer(url); }

        @JavascriptInterface public void abrirDialogo() {
            runOnUiThread(() -> mostrarDialogoAgregarLista());
        }

        @JavascriptInterface public void mostrarWebView() {
            runOnUiThread(() -> {
                ytToken++;
                tickerDetener(true);
                enExoPlayer = false;
                enVlc = false;
                enYouTubeNativo = false;
                detenerMotores();
                playerContainer.setVisibility(View.GONE);
                youtubePlayerView.setVisibility(View.GONE);
                vlcSurface.setVisibility(View.GONE);
                playerView.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume(); myWebView.resumeTimers();
                ocultarControlesNativos();
            });
        }

        @JavascriptInterface public void cerrarPlayer() { runOnUiThread(() -> cerrarExoPlayer()); }

        @JavascriptInterface public void salirApp() { runOnUiThread(() -> finishAndRemoveTask()); }

        @JavascriptInterface public boolean estaEnExo() { return enExoPlayer; }

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
        public void ocultarTicker() { runOnUiThread(() -> tickerDetener(true)); }

        @JavascriptInterface
        public void mostrarModalPublicidad(final String url, final boolean esVideo, final int duracionSeg) {
            runOnUiThread(() -> {
                if (pubModal == null) return;
                if (pubModalVid != null) { try { pubModalVid.stopPlayback(); pubModalVid.setVideoURI(null); } catch (Exception ignored) {} }
                if (pubModalClose != null) pubHandler.removeCallbacks(pubModalClose);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pubModal.getLayoutParams();
                lp.width = dp(180); lp.height = dp(180);
                pubModal.setLayoutParams(lp);
                pubModal.setVisibility(View.VISIBLE);
                if (esVideo) {
                    pubModalImg.setVisibility(View.GONE);
                    pubModalVid.setVisibility(View.VISIBLE);
                    try {
                        pubModalVid.setVideoURI(Uri.parse(url));
                        pubModalVid.setOnPreparedListener(mp -> {
                            try { mp.setLooping(false); mp.setVolume(0f, 0f); } catch (Exception ignored) {}
                            try { pubModalVid.start(); } catch (Exception ignored) {}
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
                        Glide.with(MainActivity.this).load(url).override(dp(180), dp(180)).centerCrop().dontAnimate().into(pubModalImg);
                    } catch (Exception e) {
                        pubModal.setVisibility(View.GONE);
                        avisarAlWebView("window.gtv&&window.gtv.onModalTerminado&&window.gtv.onModalTerminado();");
                        return;
                    }
                }
                pubModalClose = () -> {
                    if (pubModal != null) pubModal.setVisibility(View.GONE);
                    if (pubModalVid != null) { try { pubModalVid.stopPlayback(); pubModalVid.setVideoURI(null); } catch (Exception ignored) {} }
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

    private void mostrarDialogoAgregarLista() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📺 Agregar Lista");
        builder.setMessage("Pegá la URL de tu lista M3U o ingresá la palabra clave maestra:");
        final EditText input = new EditText(this);
        input.setHint("https://... o palabra clave");
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(20), dp(10), dp(20), 0);
        wrapper.addView(input);
        builder.setView(wrapper);
        builder.setPositiveButton("Cargar", (dialog, which) -> {
            String texto = input.getText().toString().trim();
            if (texto.isEmpty()) { Toast.makeText(this, "Ingresá una URL o palabra clave", Toast.LENGTH_SHORT).show(); return; }
            validarYcargar(texto);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        input.requestFocus();
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
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
                int code = conn.getResponseCode();
                InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject json = new JSONObject(sb.toString());
                String error = json.optString("error", "");
                if (!error.isEmpty()) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        String msg = error.equals("clave_invalida") ? "Palabra clave incorrecta"
                            : error.equals("licencia_requerida") ? "Necesitás una licencia activa"
                            : "Error: " + error;
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                String listaUrl = json.optString("url", "");
                if (listaUrl.isEmpty()) { runOnUiThread(() -> { progress.dismiss(); Toast.makeText(this, "Respuesta inválida", Toast.LENGTH_SHORT).show(); }); return; }
                if (listaUrl.startsWith("/")) listaUrl = "https://gtv-portero.abrel03.workers.dev" + listaUrl;
                cargarLista(listaUrl, progress);
            } catch (Exception e) {
                runOnUiThread(() -> { progress.dismiss(); Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private void cargarLista(final String listaUrl, final ProgressDialog progress) {
        new Thread(() -> {
            try {
                URL url = new URL(listaUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("X-GTV-Origin", SELLO);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> { progress.dismiss(); Toast.makeText(this, "Error al descargar lista (HTTP " + code + ")", Toast.LENGTH_LONG).show(); });
                    return;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                final String contenido = sb.toString();
                final ArrayList<Canal> lista = parsearM3u(contenido);
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (lista.isEmpty()) { Toast.makeText(this, "La lista está vacía o no es válida", Toast.LENGTH_LONG).show(); return; }
                    canales.clear();
                    canales.addAll(lista);
                    indiceActual = 0;
                    enviarListaAlHtml(contenido);
                    Toast.makeText(this, "Lista cargada: " + lista.size() + " canales", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { progress.dismiss(); Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private ArrayList<Canal> parsearM3u(String texto) {
        ArrayList<Canal> out = new ArrayList<>();
        String[] lines = texto.split("\\r?\\n");
        String nombre = "", logo = "", referer = "";
        float gain = 1.0f;
        boolean esPeliculas = false;
        String urlPeliculasM3U = null;
        for (String l0 : lines) {
            String l = l0.trim();
            if (l.isEmpty() || l.equals("#EXTM3U") || l.startsWith("#EXTVLCOPT")) continue;
            if (l.startsWith("#EXTINF")) {
                nombre = "Canal " + (out.size() + 1);
                logo = ""; referer = ""; gain = 1.0f; esPeliculas = false; urlPeliculasM3U = null;
                java.util.regex.Matcher lg = java.util.regex.Pattern.compile("tvg-logo\\s*=\\s*\"([^\"]*)\"").matcher(l);
                if (lg.find()) logo = lg.group(1);
                java.util.regex.Matcher rf = java.util.regex.Pattern.compile("tvg-referer\\s*=\\s*\"([^\"]*)\"").matcher(l);
                if (rf.find()) referer = rf.group(1).trim();
                java.util.regex.Matcher gn = java.util.regex.Pattern.compile("tvg-gain\\s*=\\s*\"([^\"]*)\"").matcher(l);
                if (gn.find()) { try { float gv = Float.parseFloat(gn.group(1)); if (gv > 0 && gv <= 2f) gain = gv; } catch (Exception ignored) {} }
                int c = l.lastIndexOf(',');
                if (c != -1 && c < l.length() - 1) {
                    String n = l.substring(c + 1).trim().replaceAll("^[\"']|[\"']$", "");
                    if (!n.isEmpty()) nombre = n;
                }
                String up = nombre.toUpperCase();
                if (up.contains("PELÍCULAS") || up.contains("PELICULAS") || up.contains("VIDEOTECA") || up.contains("PELIS")) esPeliculas = true;
                continue;
            }
            if (l.startsWith("http://") || l.startsWith("https://")) {
                if (esPeliculas) urlPeliculasM3U = l;
                java.util.regex.Matcher pm = java.util.regex.Pattern.compile("peliculas|videoteca|peplis|/api/m3u-peliculas").matcher(l);
                if (pm.find()) { esPeliculas = true; urlPeliculasM3U = l; }
                Canal c = new Canal(l, nombre, logo, referer, gain);
                c.esPeliculas = esPeliculas;
                c.urlPeliculasM3U = urlPeliculasM3U;
                out.add(c);
                nombre = ""; logo = ""; referer = ""; gain = 1.0f; esPeliculas = false; urlPeliculasM3U = null;
            }
        }
        return out;
    }

    private void enviarListaAlHtml(String contenidoM3U) {
        try {
            JSONArray arr = new JSONArray();
            for (Canal c : canales) {
                JSONObject o = new JSONObject();
                o.put("name", c.nombre);
                o.put("nombre", c.nombre);
                o.put("url", c.url);
                o.put("logo", c.logo);
                o.put("referer", c.referer);
                o.put("gain", c.gain);
                o.put("oculto", c.oculto);
                if (c.esPeliculas) o.put("esPeliculas", true);
                if (c.urlPeliculasM3U != null) o.put("urlPeliculasM3U", c.urlPeliculasM3U);
                arr.put(o);
            }
            final String json = arr.toString();
            runOnUiThread(() -> {
                if (myWebView != null && webViewListo) {
                    try {
                        // Guardamos la lista en el localStorage para que no se pierda al reiniciar
                        String js = "try{localStorage.setItem('gtv_channels', " + JSONObject.quote(json) + ");}catch(e){}"
                                  + "window.gtvSetCanales&&window.gtvSetCanales(" + JSONObject.quote(json) + ");";
                        myWebView.evaluateJavascript(js, null);
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {}
    }

    private boolean esCanalYouTube(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("youtube.com") || u.contains("youtu.be");
    }

    private boolean esEmbedTercero(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("kick.com") || u.contains("twitch.tv")
            || u.contains("ok.ru") || u.contains("odnoklassniki.ru")
            || u.contains("dailymotion.com") || u.contains("tokyvideo.com");
    }

    private String generarDeviceId() {
        if (deviceIdCache != null) return deviceIdCache;
        String androidId = null;
        try { androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID); } catch (Exception ignored) {}
        if (androidId == null || androidId.isEmpty()) androidId = "GTVR_FALLBACK_" + Build.MODEL + "_" + Build.SERIAL;
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

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (playerContainer.getVisibility() == View.VISIBLE) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT: navegar(+1); mostrarControles(); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: navegar(-1); mostrarControles(); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
                    mostrarControles(); return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (enYouTubeWeb) {
            enYouTubeWeb = false;
            mostrarEscudo(false);
            cerrarExoPlayer();
            return;
        }
        if (enExoPlayer || enVlc || enYouTubeNativo) { ytToken++; cerrarExoPlayer(); return; }
        ytToken++;
        if (myWebView == null) { finish(); return; }
        myWebView.evaluateJavascript(
            "(function(){try{return window.atrasNativo?window.atrasNativo():false}catch(e){return false}})()",
            v -> { if (!"true".equals(v)) finishAndRemoveTask(); });
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) ocultarBarrasSistema(); }

    @Override protected void onStart() {
        super.onStart();
        if (enExoPlayer && exoPlayer != null) { try { exoPlayer.play(); } catch (Exception ignored) {} }
    }

    @Override protected void onPause() {
        super.onPause();
        if (myWebView != null && !enExoPlayer) { myWebView.onPause(); myWebView.pauseTimers(); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (myWebView != null && !enExoPlayer) { myWebView.onResume(); myWebView.resumeTimers(); }
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
        if (vlcPlayer != null) { try { vlcPlayer.release(); } catch (Exception ignored) {} vlcPlayer = null; }
        if (libVlc != null) { try { libVlc.release(); } catch (Exception ignored) {} libVlc = null; }
        if (youtubePlayerView != null) { try { youtubePlayerView.release(); } catch (Exception ignored) {} }
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
