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
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest; // === FIX VOZ: Import agregado ===
import android.webkit.WebChromeClient;   // === FIX VOZ: Import agregado ===
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;          // === FIX VOZ: Import agregado ===
import androidx.core.content.ContextCompat;       // === FIX VOZ: Import agregado ===
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GTV_R_DEBUG";

    private static final String WORKER_URL = "https://gtv-portero.abrel03.workers.dev/";
    private static final String CONFIG_URL = "https://gtv-portero.abrel03.workers.dev/api/config";
    private static final String ESTADO_URL = "https://gtv-portero.abrel03.workers.dev/api/estado-canales";
    private static final String VALIDAR_URL = "https://gtv-portero.abrel03.workers.dev/api/reproductor/validar";
    private static final String SELLO = "gtv_app_2026_secreto_xyz789";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final String PREFS = "gtv_r_prefs";
    private static final String PREF_MOTOR = "motor"; // "e_plus" | "eme_plus"

    // === FIX VOZ: Constante para el código de solicitud de permiso ===
    private static final int REQUEST_AUDIO_PERMISSION = 101;

    private enum Motor { NINGUNO, EXO, MPV, YT, WEB }
    private enum Tipo { YT, WEB, DIRECTO }

    // ---- vistas ----
    private WebView myWebView;
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private TextureView videoTexture;
    private TextureView mpvTextureView;
    private FrameLayout playerContainer, rotWrapper;
    private LinearLayout infoCanal;
    private ImageView logoCanal;
    private TextView nombreCanal, numeroCanal, exoLoader, exoLoaderError;
    private Button btnPrev, btnNext, btnClose, btnRotate;
    private View btnShield, overlayControls;
    private YouTubePlayerView youtubePlayerView;
    private YouTubePlayer ytPlayer = null;
    private String ytVideoPendiente = null;

    private MpvEngine mpv;

    // ---- estado de reproducción ----
    private volatile Motor motorActivo = Motor.NINGUNO;
    private volatile int sesion = 0;
    private Canal canalActual;
    private boolean fallbackProbado = false;
    private int reintentos = 0;
    private int mpvReintentos = 0;
    private boolean mpvCargado = false;

    private final ArrayList<Canal> canales = new ArrayList<>();
    private int indiceActual = -1;
    private int zapPend = -1;
    private boolean modoPeliculas = false;
    private boolean peliZapActivo = false;
    private String peliNumero = "";

    private boolean rotado = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler loaderHandler = new Handler(Looper.getMainLooper());
    private Runnable ocultarControlesRunnable, loaderAnim, timeoutCanalCaido;
    private int loaderPunto = 0;

    private String deviceIdCache = null;

    // ---- publicidad nativa ----
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
    private final Handler pubHandler = new Handler(Looper.getMainLooper());
    private Runnable pubModalClose;

    private volatile boolean webViewListo = false;
    private volatile boolean indexListo = false;
    private volatile String listaLegada = null;
    private volatile int listaToken = 0;

    // ---- watch next ----
    private static final String WATCH_NEXT_CHANNEL_NAME = "GTV Play R Recientes";
    private long watchNextChannelId = -1;
    private final ArrayList<String> historialCanales = new ArrayList<>();
    private static final int MAX_HISTORIAL = 10;

    // ---- updates / listas ----
    private static final String UPDATE_PREFS = "gtv_r_update";
    private static final String UPDATE_TRIES_PREFIX = "gtv_r_update_tries_";
    private static final String VERSION_URL = "https://gtv-portero.abrel03.workers.dev/api/version-r";
    private static final int MAX_SKIPS = 3;
    private static final String LISTAS_PREFS = "gtv_r_listas";
    private static final String LISTAS_KEY = "listas_guardadas";
    private static final String ULTIMA_LISTA_KEY = "ultima_lista";

    private final ExecutorService ytExec = Executors.newSingleThreadExecutor();

    // =====================================================================
    //  MODELO
    // =====================================================================
    static class Canal {
        final String url, nombre, logo, referer;
        final float gain;
        boolean oculto, esPeliculas;
        String urlPeliculasM3U;
        String motor;

        Canal(String url, String nombre, String logo, String referer, float gain, boolean oculto) {
            this.url = url;
            this.nombre = nombre;
            this.logo = logo;
            this.referer = (referer != null) ? referer : "";
            this.gain = (gain > 0f && gain <= 2f) ? gain : 1.0f;
            this.oculto = oculto;
        }
        Canal(String url, String nombre, String logo, String referer, float gain) {
            this(url, nombre, logo, referer, gain, false);
        }
    }

    // =====================================================================
    //  MPV (envoltorio reflexivo)
    // =====================================================================
    private final class MpvEngine {
        private static final int EV_START_FILE = 6, EV_END_FILE = 7, EV_FILE_LOADED = 8;

        private Class<?> cls;
        private Object inst;
        private boolean listo = false;
        private Surface surface;
        private boolean surfaceOk = false, attached = false;
        private Canal pendiente = null;
        private volatile boolean armado = false;

        boolean iniciar() {
            if (listo) return true;
            try {
                cls = Class.forName("is.xyz.mpv.MPVLib");
                try { inst = cls.getField("INSTANCE").get(null); } catch (NoSuchFieldException e) { inst = null; }
                call("create", getApplicationContext());
                opt("vo", "gpu");
                opt("gpu-context", "android");
                opt("opengl-es", "yes");
                opt("hwdec", "mediacodec-copy");
                opt("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1");
                opt("ao", "audiotrack,opensles");
                opt("tls-verify", "no");
                opt("idle", "yes");
                opt("keep-open", "no");
                opt("cache", "yes");
                opt("demuxer-max-bytes", "16MiB");
                opt("demuxer-max-back-bytes", "4MiB");
                opt("network-timeout", "10");
                opt("volume-max", "200");
                opt("framedrop", "vo");
                opt("video-sync", "audio");
                opt("stream-lavf-o", "reconnect=1,reconnect_streamed=1,reconnect_delay_max=5");
                call("init");
                registrarObservador();
                listo = true;
                if (surfaceOk) attach();
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "MPV no pudo iniciar: " + t);
                listo = false;
                return false;
            }
        }

        private void registrarObservador() throws Exception {
            Class<?> oc = Class.forName("is.xyz.mpv.MPVLib$EventObserver");
            Object obs = Proxy.newProxyInstance(oc.getClassLoader(), new Class<?>[]{oc}, (proxy, m, a) -> {
                if (m.getDeclaringClass() == Object.class) {
                    switch (m.getName()) {
                        case "equals": return proxy == a[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        default: return "MpvObserver";
                    }
                }
                if ("event".equals(m.getName()) && a != null && a.length > 0 && a[0] instanceof Integer) {
                    onEvento((Integer) a[0]);
                }
                return null;
            });
            call("addObserver", obs);
        }

        private void onEvento(int id) {
            if (id == EV_START_FILE) {
                armado = true;
            } else if (id == EV_FILE_LOADED) {
                if (armado) handler.post(MainActivity.this::onMpvCargado);
            } else if (id == EV_END_FILE) {
                if (armado) {
                    armado = false;
                    handler.post(MainActivity.this::onMpvFin);
                }
            }
        }

        void onSurface(SurfaceTexture st) {
            if (surface != null) { try { surface.release(); } catch (Exception ignored) {} }
            surface = new Surface(st);
            surfaceOk = true;
            if (listo) attach();
        }

        void onSurfaceDestruida() {
            if (listo && attached) detach();
            surfaceOk = false;
            if (surface != null) { try { surface.release(); } catch (Exception ignored) {} surface = null; }
        }

        private void attach() {
            try {
                call("attachSurface", surface);
                opt("force-window", "yes");
                prop("vo", "gpu");
                attached = true;
                if (pendiente != null) { Canal c = pendiente; pendiente = null; lanzar(c); }
            } catch (Throwable t) { Log.e(TAG, "MPV attach: " + t); }
        }

        private void detach() {
            try {
                prop("vo", "null");
                opt("force-window", "no");
                call("detachSurface");
            } catch (Throwable t) { Log.e(TAG, "MPV detach: " + t); }
            attached = false;
        }

        void cargar(Canal c) {
            armado = false;
            if (listo && attached) lanzar(c); else pendiente = c;
        }

        private void lanzar(Canal c) {
            try {
                armado = false;
                prop("user-agent", UA);
                String hdr = "";
                if (c.referer != null && !c.referer.isEmpty()) {
                    hdr = "Referer: " + c.referer;
                    try {
                        URL u = new URL(c.referer);
                        hdr += ",Origin: " + u.getProtocol() + "://" + u.getHost();
                    } catch (Exception ignored) {}
                }
                prop("http-header-fields", hdr);
                prop("volume", String.valueOf(Math.round(c.gain * 100f)));
                prop("pause", "no");
                call("command", (Object) new String[]{"loadfile", c.url, "replace"});
            } catch (Throwable t) { Log.e(TAG, "MPV loadfile: " + t); }
        }

        void parar() {
            armado = false;
            pendiente = null;
            if (!listo) return;
            try {
                call("command", (Object) new String[]{"stop"});
                prop("pause", "yes");
            } catch (Throwable ignored) {}
        }

        void pausa(boolean p) {
            if (!listo) return;
            try { prop("pause", p ? "yes" : "no"); } catch (Throwable ignored) {}
        }

        void destruir() {
            if (!listo) return;
            try { if (attached) detach(); call("destroy"); } catch (Throwable ignored) {}
            listo = false;
        }

        private void opt(String k, String v) { try { call("setOptionString", k, v); } catch (Throwable ignored) {} }
        private void prop(String k, String v) throws Exception { call("setPropertyString", k, v); }

        private Object call(String name, Object... args) throws Exception {
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(name)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != args.length || !compatible(p, args)) continue;
                return m.invoke(Modifier.isStatic(m.getModifiers()) ? null : inst, args);
            }
            throw new NoSuchMethodException("MPVLib." + name);
        }

        private boolean compatible(Class<?>[] p, Object[] a) {
            for (int i = 0; i < p.length; i++) {
                if (a[i] == null) { if (p[i].isPrimitive()) return false; continue; }
                if (!wrap(p[i]).isInstance(a[i])) return false;
            }
            return true;
        }

        private Class<?> wrap(Class<?> c) {
            if (!c.isPrimitive()) return c;
            if (c == int.class) return Integer.class;
            if (c == long.class) return Long.class;
            if (c == boolean.class) return Boolean.class;
            if (c == double.class) return Double.class;
            if (c == float.class) return Float.class;
            return c;
        }
    }

    private void onMpvCargado() {
        if (motorActivo != Motor.MPV) return;
        mpvCargado = true;
        handler.removeCallbacks(mpvTimeoutRunnable);
        detenerLoaderAnim();
        ocultarLoaders();
        avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
    }

    private void onMpvFin() {
        if (motorActivo != Motor.MPV || canalActual == null) return;
        if (mpvReintentos < 2) {
            mpvReintentos++;
            mpvCargado = false;
            mostrarLoaderSintonizando();
            final int ses = sesion;
            handler.postDelayed(() -> {
                if (ses != sesion || motorActivo != Motor.MPV || canalActual == null) return;
                mpv.cargar(canalActual);
                programarTimeoutMpv();
            }, 1500L * mpvReintentos);
        } else {
            fallaMotor(Motor.MPV);
        }
    }

    private final Runnable mpvTimeoutRunnable = new Runnable() {
        @Override public void run() {
            if (motorActivo == Motor.MPV && !mpvCargado) fallaMotor(Motor.MPV);
        }
    };

    private void programarTimeoutMpv() {
        handler.removeCallbacks(mpvTimeoutRunnable);
        handler.postDelayed(mpvTimeoutRunnable, 20000);
    }

    // =====================================================================
    //  ELECCIÓN DE MOTOR
    // =====================================================================
    private Tipo tipoDe(String url) {
        if (url == null) return Tipo.DIRECTO;
        String u = url.toLowerCase();
        if (u.contains("youtube.com") || u.contains("youtu.be")) return Tipo.YT;
        if (u.contains("kick.com") || u.contains("twitch.tv") || u.contains("ok.ru")
            || u.contains("odnoklassniki.ru") || u.contains("dailymotion.com") || u.contains("tokyvideo.com")) return Tipo.WEB;
        return Tipo.DIRECTO;
    }

    private boolean esEnlaceComplicado(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        if (!u.startsWith("http://")) return false;
        if (u.contains(":8000/play/") || u.contains(":8080/play/")
            || u.contains(":25461/play/") || u.contains(":9000/play/")) return true;
        if (u.contains("45.170.130.224")) return true;
        if (u.contains(".m3u8")) {
            try {
                int port = new URL(url).getPort();
                if (port > 0 && port != 80 && port != 443) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private String motorPreferido() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_MOTOR, "e_plus");
    }

    private Motor elegirMotor(Canal c) {
        String m = (c.motor != null) ? c.motor : motorPreferido();
        if ("eme_plus".equals(m) || "mpv".equals(m)) return Motor.MPV;
        return Motor.EXO;
    }

    private boolean nativoActivo() {
        Motor m = motorActivo;
        return m == Motor.EXO || m == Motor.MPV || m == Motor.YT;
    }

    // =====================================================================
    //  NÚCLEO: apagar todo / arrancar uno
    // =====================================================================
    private void apagarTodo() {
        sesion++;
        handler.removeCallbacks(reintentoRunnable);
        handler.removeCallbacks(mpvTimeoutRunnable);
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        motorActivo = Motor.NINGUNO;
        try {
            if (exoPlayer != null) { exoPlayer.setPlayWhenReady(false); exoPlayer.stop(); exoPlayer.clearMediaItems(); }
        } catch (Exception ignored) {}
        if (mpv != null) mpv.parar();
        try { if (ytPlayer != null) ytPlayer.pause(); } catch (Exception ignored) {}
        ytVideoPendiente = null;
        if (playerView != null) playerView.setVisibility(View.GONE);
        if (mpvTextureView != null) mpvTextureView.setVisibility(View.INVISIBLE);
        if (youtubePlayerView != null) youtubePlayerView.setVisibility(View.GONE);
    }

    private void reproducirCanal(Canal c) {
        if (c == null) return;
        fallbackProbado = false;
        switch (tipoDe(c.url)) {
            case YT: iniciarYouTube(c); break;
            case WEB: iniciarWeb(c); break;
            default:
                if (elegirMotor(c) == Motor.MPV) iniciarMpv(c); else iniciarExo(c);
        }
    }

    private void mostrarCapaNativa(Canal c) {
        myWebView.setVisibility(View.GONE);
        myWebView.onPause();
        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.bringToFront();
        nombreCanal.setText(c.nombre);
        numeroCanal.setText(modoPeliculas ? peliNumero : (indiceActual + 1) + "/" + canales.size());
        if (c.logo != null && !c.logo.isEmpty()) Glide.with(this).load(c.logo).into(logoCanal);
    }

    private void finalizarUI() {
        mostrarControles();
        playerContainer.post(() -> { aplicarRotacion(); reposicionarBotones(); });
        enfocarOverlay();
    }

    private void enfocarOverlay() {
        overlayControls.setVisibility(View.VISIBLE);
        overlayControls.bringToFront();
        overlayControls.setFocusable(true);
        overlayControls.setFocusableInTouchMode(true);
        overlayControls.requestFocus();
    }

    // ---------- EXO ----------
    private void iniciarExo(Canal c) {
        apagarTodo();
        motorActivo = Motor.EXO;
        canalActual = c;
        reintentos = 0;
        mostrarCapaNativa(c);
        playerView.setVisibility(View.VISIBLE);
        mostrarLoaderSintonizando();
        exoPlayer.setMediaSource(construirMediaSource(c));
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
        try { exoPlayer.setVolume(c.gain); } catch (Exception ignored) {}
        programarTimeoutCanal();
        avisarAlWebView("window.gtv&&window.gtv.onNativoIniciado&&window.gtv.onNativoIniciado();");
        handler.postDelayed(this::actualizarWatchNext, 5000);
        finalizarUI();
    }

    private final Runnable reintentoRunnable = new Runnable() {
        @Override public void run() {
            if (motorActivo != Motor.EXO || exoPlayer == null || canalActual == null) return;
            exoPlayer.setMediaSource(construirMediaSource(canalActual));
            exoPlayer.prepare();
            exoPlayer.play();
            programarTimeoutCanal();
        }
    };

    private boolean reintentar() {
        if (reintentos >= 2 || canalActual == null || motorActivo != Motor.EXO) return false;
        reintentos++;
        mostrarLoaderSintonizando();
        handler.removeCallbacks(reintentoRunnable);
        handler.postDelayed(reintentoRunnable, 1500L * reintentos);
        return true;
    }

    private void programarTimeoutCanal() {
        if (timeoutCanalCaido != null) handler.removeCallbacks(timeoutCanalCaido);
        timeoutCanalCaido = () -> {
            if (motorActivo == Motor.EXO && exoPlayer != null && exoPlayer.getPlaybackState() != Player.STATE_READY) {
                if (!reintentar()) fallaMotor(Motor.EXO);
            }
        };
        handler.postDelayed(timeoutCanalCaido, 15000);
    }

    // ---------- MPV ----------
    private void iniciarMpv(Canal c) {
        apagarTodo();
        motorActivo = Motor.MPV;
        canalActual = c;
        mpvReintentos = 0;
        mpvCargado = false;
        mostrarCapaNativa(c);
        mpvTextureView.setVisibility(View.VISIBLE);
        mostrarLoaderSintonizando();
        if (!mpv.iniciar()) { fallaMotor(Motor.MPV); return; }
        mpv.cargar(c);
        programarTimeoutMpv();
        avisarAlWebView("window.gtv&&window.gtv.onNativoIniciado&&window.gtv.onNativoIniciado();");
        handler.postDelayed(this::actualizarWatchNext, 5000);
        finalizarUI();
    }

    private void fallaMotor(Motor fallo) {
        if (canalActual == null) return;
        if (!fallbackProbado) {
            fallbackProbado = true;
            Canal c = canalActual;
            Log.e(TAG, "Falló " + fallo + ", probando el otro motor: " + c.url);
            if (fallo == Motor.EXO) iniciarMpv(c); else iniciarExo(c);
            return;
        }
        detenerLoaderAnim();
        mostrarLoaderError();
        avisarAlWebView("window.gtv&&window.gtv.onNativoError&&window.gtv.onNativoError();");
        final int ses = sesion;
        handler.postDelayed(() -> { if (ses == sesion) ocultarLoaders(); }, 5000);
    }

    // ---------- YOUTUBE NATIVO ----------
    private void iniciarYouTube(Canal c) {
        apagarTodo();
        motorActivo = Motor.YT;
        canalActual = c;
        final int ses = sesion;
        mostrarCapaNativa(c);
        youtubePlayerView.setVisibility(View.VISIBLE);
        mostrarLoaderSintonizando();

        String handle = null, channelId = null, videoId = null;
        Matcher mCh = Pattern.compile("channel/(UC[\\w-]{22})").matcher(c.url);
        if (mCh.find()) channelId = mCh.group(1);
        Matcher mVid = Pattern.compile("(?:watch\\?v=|youtu\\.be/|/live/|/embed/|/shorts/)([\\w-]{11})").matcher(c.url);
        if (mVid.find()) videoId = mVid.group(1);
        Matcher mH = Pattern.compile("youtube\\.com/(@[\\w.-]+)").matcher(c.url);
        if (mH.find()) handle = mH.group(1);

        if (videoId != null) {
            cargarVideoEnYoutubeNativo(videoId, ses);
        } else if (channelId != null || handle != null) {
            final String chId = channelId, h = handle;
            ytExec.execute(() -> {
                String vid = resolverVideoIdLive(chId, h);
                runOnUiThread(() -> {
                    if (ses != sesion) return;
                    if (vid != null) cargarVideoEnYoutubeNativo(vid, ses);
                    else { detenerLoaderAnim(); mostrarLoaderError(); }
                });
            });
        } else {
            detenerLoaderAnim();
            mostrarLoaderError();
        }
        youtubePlayerView.setFocusable(false);
        youtubePlayerView.setFocusableInTouchMode(false);
        finalizarUI();
        handler.postDelayed(() -> { if (ses == sesion && motorActivo == Motor.YT) { enfocarOverlay(); mostrarControles(); } }, 800);
    }

    private void cargarVideoEnYoutubeNativo(final String videoId, final int ses) {
        runOnUiThread(() -> {
            if (ses != sesion) return;
            try {
                if (ytPlayer != null) {
                    ytPlayer.loadVideo(videoId, 0f);
                    detenerLoaderAnim();
                    ocultarLoaders();
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                } else {
                    ytVideoPendiente = videoId;
                }
            } catch (Exception e) {
                detenerLoaderAnim();
                mostrarLoaderError();
            }
        });
    }

    private String resolverVideoIdLive(String channelId, String handle) {
        HttpURLConnection conn = null;
        try {
            String url = (channelId != null)
                ? "https://www.youtube.com/channel/" + channelId + "/live"
                : "https://www.youtube.com/" + handle + "/live";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            if (conn.getResponseCode() == 200) {
                Matcher m = Pattern.compile("[?&]v=([\\w-]{11})").matcher(conn.getURL().toString());
                if (m.find()) return m.group(1);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String html = sb.toString();
                int idx = html.indexOf("\"videoDetails\"");
                if (idx != -1) {
                    String block = html.substring(idx, Math.min(idx + 5000, html.length()));
                    Matcher vm = Pattern.compile("\"videoId\":\"([\\w-]{11})\"").matcher(block);
                    Matcher lm = Pattern.compile("\"isLive\"\\s*:\\s*true").matcher(block);
                    if (vm.find() && lm.find()) return vm.group(1);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    // ---------- WEBVIEW (Kick, Twitch, ok.ru, ...) ----------
    private void iniciarWeb(Canal c) {
        apagarTodo();
        motorActivo = Motor.WEB;
        canalActual = c;
        playerContainer.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim();
        ocultarLoaders();
        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume();
        myWebView.resumeTimers();
        irACanalWebView(indiceActual);
    }

    // ---------- CERRAR / VOLVER A LA LISTA ----------
    private void cerrarReproductor() {
        tickerDetener(true);
        apagarTodo();
        canalActual = null;
        handler.removeCallbacks(zapRun);
        zapPend = -1;
        modoPeliculas = false; peliZapActivo = false;
        playerContainer.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim();
        ocultarLoaders();
        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume();
        myWebView.resumeTimers();
        myWebView.postDelayed(() -> {
            try {
                myWebView.evaluateJavascript(
                    "javascript:window.sincronizarIndiceJS&&window.sincronizarIndiceJS(" + indiceActual + ");"
                  + "window.volverDelExo&&window.volverDelExo();", null);
            } catch (Exception ignored) {}
        }, 32);
    }

    // =====================================================================
    //  ZAPPING
    // =====================================================================
    private final Runnable zapRun = new Runnable() {
        @Override public void run() {
            modoPeliculas = false; peliZapActivo = false;
            if (zapPend < 0 || zapPend >= canales.size()) { zapPend = -1; return; }
            indiceActual = zapPend;
            zapPend = -1;
            reproducirCanal(canales.get(indiceActual));
        }
    };

    private void navegar(int delta) {
        if (modoPeliculas) {
            if (peliZapActivo) avisarAlWebView("window.zapPeliculas&&window.zapPeliculas(" + delta + ");");
            return;
        }
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

    private void peliTerminada() {
        if (modoPeliculas && peliZapActivo) avisarAlWebView("window.zapPeliculas&&window.zapPeliculas(1,true);");
    }

    // =====================================================================
    //  CICLO DE VIDA
    // =====================================================================
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

        overlayControls.setFocusable(true);
        overlayControls.setFocusableInTouchMode(true);

        youtubePlayerView.setFocusable(false);
        youtubePlayerView.setFocusableInTouchMode(false);
        youtubePlayerView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && motorActivo == Motor.YT) handler.postDelayed(() -> {
                if (motorActivo == Motor.YT) enfocarOverlay();
            }, 200);
        });

        inicializarPublicidadNativa();

        rotado = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("rotado", false);

        // ----- Exo -----
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(10000, 30000, 2500, 5000)
            .setTargetBufferBytes(24 * 1024 * 1024)
            .build();
        exoPlayer = new ExoPlayer.Builder(this, renderers).setLoadControl(loadControl).build();
        inyectarTextureView();
        playerView.setPlayer(exoPlayer);
        videoTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { exoPlayer.setVideoTextureView(videoTexture); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
        exoPlayer.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (motorActivo != Motor.EXO) return;
                if (state == Player.STATE_READY) {
                    reintentos = 0;
                    detenerLoaderAnim();
                    ocultarLoaders();
                    if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                } else if (state == Player.STATE_BUFFERING) {
                    mostrarLoaderSintonizando();
                } else if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                    detenerLoaderAnim();
                    ocultarLoaders();
                    if (state == Player.STATE_ENDED) peliTerminada();
                }
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (motorActivo != Motor.EXO) return;
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    try { exoPlayer.seekToDefaultPosition(); exoPlayer.prepare(); } catch (Exception ignored) {}
                    return;
                }
                if (reintentar()) return;
                Log.e(TAG, "ExoPlayer error (" + error.errorCode + "): " + error.getMessage());
                fallaMotor(Motor.EXO);
            }
        });

        // ----- MPV -----
        mpv = new MpvEngine();
        mpvTextureView = new TextureView(this);
        mpvTextureView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mpvTextureView.setVisibility(View.INVISIBLE);
        mpvTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { mpv.onSurface(st); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { mpv.onSurfaceDestruida(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
        rotWrapper.addView(mpvTextureView, 0);

        // ----- YouTube nativo -----
        getLifecycle().addObserver(youtubePlayerView);
        youtubePlayerView.setVisibility(View.GONE);
        youtubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override public void onReady(YouTubePlayer youTubePlayer) {
                ytPlayer = youTubePlayer;
                if (ytVideoPendiente != null && motorActivo == Motor.YT) {
                    String v = ytVideoPendiente;
                    ytVideoPendiente = null;
                    try { youTubePlayer.loadVideo(v, 0f); } catch (Exception ignored) {}
                    detenerLoaderAnim();
                    ocultarLoaders();
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                }
            }
            @Override public void onStateChange(YouTubePlayer youTubePlayer, com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState state) {
                if (motorActivo == Motor.YT && state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.ENDED) peliTerminada();
            }
        });

        // === FIX VOZ: Solicitar permiso de micrófono al iniciar ===
        checkAudioPermission();
        
        configurarWebView();
        configurarBotones();

        myWebView.loadUrl("file:///android_asset/splash.html");
        final long splashStart = System.currentTimeMillis();
        final long SPLASH_MIN_MS = 3000;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                long remaining = SPLASH_MIN_MS - (System.currentTimeMillis() - splashStart);
                if (remaining > 0) { handler.postDelayed(this, remaining); return; }
                if (myWebView == null) return;
                String current = myWebView.getUrl();
                if (current != null && current.contains("splash.html")) myWebView.loadUrl("file:///android_asset/index.html");
            }
        }, 300);

        ocultarBarrasSistema();

        handler.postDelayed(this::checkForUpdates, 2000);
        handler.postDelayed(() -> {
            try { inicializarWatchNext(); } catch (Throwable t) { Log.e(TAG, "WatchNext init error: " + t); }
        }, 5000);
        manejarIntentEntrante(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        manejarIntentEntrante(intent);
    }

    @Override protected void onStart() {
        super.onStart();
        if (motorActivo == Motor.EXO && exoPlayer != null) { try { exoPlayer.play(); } catch (Exception ignored) {} }
        if (motorActivo == Motor.MPV) mpv.pausa(false);
    }

    @Override protected void onPause() {
        super.onPause();
        if (myWebView != null && !nativoActivo()) { myWebView.onPause(); myWebView.pauseTimers(); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (myWebView != null && !nativoActivo()) { myWebView.onResume(); myWebView.resumeTimers(); }
    }

    @Override protected void onStop() {
        super.onStop();
        if (exoPlayer != null) { try { exoPlayer.pause(); } catch (Exception ignored) {} }
        if (mpv != null) mpv.pausa(true);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        tickerDetener(true);
        detenerLoaderAnim();
        if (pubModalClose != null) { try { pubHandler.removeCallbacks(pubModalClose); } catch (Exception ignored) {} pubModalClose = null; }
        handler.removeCallbacksAndMessages(null);
        try { File f = new File(getCacheDir(), "update.apk"); if (f.exists()) f.delete(); } catch (Exception ignored) {}
        if (exoPlayer != null) { try { exoPlayer.release(); } catch (Exception ignored) {} exoPlayer = null; }
        if (mpv != null) { mpv.destruir(); mpv = null; }
        if (youtubePlayerView != null) { try { youtubePlayerView.release(); } catch (Exception ignored) {} }
        if (myWebView != null) {
            try { myWebView.loadUrl("about:blank"); myWebView.stopLoading(); myWebView.destroy(); } catch (Exception ignored) {}
            myWebView = null;
        }
        ytExec.shutdownNow();
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
        if (motorActivo != Motor.NINGUNO) { cerrarReproductor(); return; }
        if (myWebView == null) { finish(); return; }
        myWebView.evaluateJavascript(
            "(function(){try{return window.atrasNativo?window.atrasNativo():false}catch(e){return false}})()",
            v -> { if (!"true".equals(v)) finishAndRemoveTask(); });
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) ocultarBarrasSistema();
    }

    // =====================================================================
    //  EXO: media source
    // =====================================================================
    private MediaSource construirMediaSource(Canal c) {
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000);
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "*/*");
        if (c.referer != null && !c.referer.isEmpty()) {
            h.put("Referer", c.referer);
            try {
                URL u = new URL(c.referer);
                h.put("Origin", u.getProtocol() + "://" + u.getHost());
            } catch (Exception ignored) {}
        }
        f.setDefaultRequestProperties(h);

        MediaItem.Builder ib = new MediaItem.Builder().setUri(c.url);
        String mime = mimeDe(c.url);
        if (mime != null) ib.setMimeType(mime);
        MediaItem mediaItem = ib.build();

        String ul = c.url.toLowerCase();
        if (ul.contains(".m3u8") || ul.contains("type=m3u8") || ul.contains("format=m3u8")) {
            DefaultHlsExtractorFactory hlsEf = new DefaultHlsExtractorFactory(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES, false);
            return new HlsMediaSource.Factory(f)
                .setExtractorFactory(hlsEf)
                .setAllowChunklessPreparation(false)
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6))
                .createMediaSource(mediaItem);
        }

        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS);
        return new DefaultMediaSourceFactory(f, extractors)
            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6))
            .createMediaSource(mediaItem);
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

    private void inyectarTextureView() {
        View contentFrame = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        if (!(contentFrame instanceof ViewGroup)) return;
        ViewGroup cf = (ViewGroup) contentFrame;
        for (int i = cf.getChildCount() - 1; i >= 0; i--) if (cf.getChildAt(i) instanceof SurfaceView) cf.removeViewAt(i);
        videoTexture = new TextureView(this);
        videoTexture.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cf.addView(videoTexture, 0);
    }

    // =====================================================================
    //  ROTACIÓN / BOTONES / CONTROLES
    // =====================================================================
    private void rotar() {
        rotado = !rotado;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("rotado", rotado).apply();
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

    private void configurarBotones() {
        btnShield.setOnClickListener(v -> mostrarControles());
        btnNext.setOnClickListener(v -> { navegar(+1); mostrarControles(); });
        btnPrev.setOnClickListener(v -> { navegar(-1); mostrarControles(); });
        btnClose.setOnClickListener(v -> cerrarReproductor());
        btnRotate.setOnClickListener(v -> { rotar(); mostrarControles(); });
        overlayControls.setOnClickListener(v -> mostrarControles());
        overlayControls.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { navegar(+1); mostrarControles(); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { navegar(-1); mostrarControles(); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { mostrarControles(); return true; }
            }
            return false;
        });
        overlayControls.setOnTouchListener(new View.OnTouchListener() {
            private float startX = 0, startY = 0;
            private long startTime = 0;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX(); startY = event.getY();
                        startTime = System.currentTimeMillis();
                        return false;
                    case MotionEvent.ACTION_UP:
                        float dx = event.getX() - startX, dy = event.getY() - startY;
                        long dur = System.currentTimeMillis() - startTime;
                        if (Math.abs(dx) > 80 && Math.abs(dy) < 120 && dur < 500) {
                            navegar(dx > 0 ? -1 : +1);
                            mostrarControles();
                            return true;
                        }
                        return false;
                    default: return false;
                }
            }
        });
    }

    private void mostrarControles() {
        if (!nativoActivo()) return;
        if (playerContainer.getVisibility() != View.VISIBLE) playerContainer.setVisibility(View.VISIBLE);
        infoCanal.setVisibility(View.VISIBLE);
        int vz = (modoPeliculas && !peliZapActivo) ? View.GONE : View.VISIBLE;
        btnPrev.setVisibility(vz); btnNext.setVisibility(vz);
        btnClose.setVisibility(View.VISIBLE); btnRotate.setVisibility(View.VISIBLE);
        overlayControls.setVisibility(View.VISIBLE);
        overlayControls.bringToFront();
        infoCanal.bringToFront();
        btnPrev.bringToFront(); btnNext.bringToFront();
        btnClose.bringToFront(); btnRotate.bringToFront();
        overlayControls.setFocusable(true);
        overlayControls.setFocusableInTouchMode(true);
        overlayControls.requestFocus();
        if (ocultarControlesRunnable != null) handler.removeCallbacks(ocultarControlesRunnable);
        ocultarControlesRunnable = this::ocultarControlesNativos;
        handler.postDelayed(ocultarControlesRunnable, 5000);
    }

    private void ocultarControlesNativos() {
        infoCanal.setVisibility(View.GONE);
        btnPrev.setVisibility(View.GONE); btnNext.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE); btnRotate.setVisibility(View.GONE);
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
                exoLoader.setText("Sintonizando" + (paso == 0 ? "   " : paso == 1 ? ".  " : paso == 2 ? ".. " : "..."));
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

    // =====================================================================
    //  WEBVIEW
    // =====================================================================
    private void irACanalWebView(final int idx) { irACanalWebView(idx, 0); }

    private void irACanalWebView(final int idx, final int intento) {
        runOnUiThread(() -> {
            if (myWebView == null) return;
            myWebView.evaluateJavascript(
                "(function(){try{return typeof window.irACanal==='function';}catch(e){return false;}})()",
                value -> {
                    if ("true".equals(value)) myWebView.evaluateJavascript("window.irACanal(" + idx + ");", null);
                    else if (intento < 10) handler.postDelayed(() -> irACanalWebView(idx, intento + 1), 300);
                });
        });
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
        s.setUserAgentString(UA);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(myWebView, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            myWebView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);

        myWebView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("splash.html")) return;
                webViewListo = true;
                if (url != null && url.contains("index.html")) {
                    indexListo = true;
                    refrescarListaRemota();
                }
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    String url = request.getUrl().toString();
                    if (url.startsWith(WORKER_URL) || url.startsWith("https://gtv-portero.abrel03.workers.dev")) {
                        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                        conn.setRequestMethod(request.getMethod());
                        for (Map.Entry<String, String> e : request.getRequestHeaders().entrySet())
                            conn.setRequestProperty(e.getKey(), e.getValue());
                        conn.setRequestProperty("X-GTV-Origin", SELLO);
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(15000);
                        String contentType = conn.getContentType();
                        String mimeType = "text/html", charset = "UTF-8";
                        if (contentType != null) {
                            String[] partes = contentType.split(";");
                            mimeType = partes[0].trim();
                            for (int i = 1; i < partes.length; i++) {
                                if (partes[i].trim().toLowerCase().startsWith("charset="))
                                    charset = partes[i].trim().substring(8).replace("\"", "");
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

        // === FIX VOZ: WebChromeClient para autorizar el micrófono al WebView ===
        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                            return;
                        }
                    }
                    request.deny();
                });
            }
        });
        // =====================================================================

        myWebView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidPlayer");
    }

    private void avisarAlWebView(String js) {
        if (myWebView == null || !webViewListo) return;
        myWebView.post(() -> {
            try { if (myWebView != null) myWebView.evaluateJavascript("javascript:(function(){try{" + js + "}catch(e){}})();", null); } catch (Exception ignored) {}
        });
    }

    // =====================================================================
    //  PUBLICIDAD NATIVA (Ticker optimizado)
    // =====================================================================
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
        tickerArrancar(++pubTickerGen, prog, 150);
    }

    private Typeface fuenteDe(String f) {
        if (f == null) return Typeface.SANS_SERIF;
        try {
            switch (f.toLowerCase().trim()) {
                case "montserrat": return ResourcesCompat.getFont(this, R.font.montserrat);
                case "poppins": return ResourcesCompat.getFont(this, R.font.poppins);
                case "inter": return ResourcesCompat.getFont(this, R.font.inter);
                case "bebasneue": return ResourcesCompat.getFont(this, R.font.bebasneue);
                default: return Typeface.SANS_SERIF;
            }
        } catch (Exception e) { return Typeface.SANS_SERIF; }
    }

    private void mostrarTickerImpl(final String texto, final String badge, final int velocidad,
                                   final String colorBg, final String colorAc, final int tamPx, final String fuente) {
        runOnUiThread(() -> {
            if (pubTickerBox == null || pubTickerTxt == null) return;
            tickerDetener(false);
            final int gen = pubTickerGen;
            pubTickerTxt.setText((badge != null && !badge.isEmpty()) ? (badge + "  " + texto) : String.valueOf(texto));
            int tam = Math.max(8, Math.min(72, tamPx));
            pubTickerTxt.setTextSize(TypedValue.COMPLEX_UNIT_SP, tam);
            pubTickerTxt.setTypeface(fuenteDe(fuente));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pubTickerBox.getLayoutParams();
            int altura = Math.max(dp(32), dp(tam + 10));
            if (lp.height != altura) { lp.height = altura; pubTickerBox.setLayoutParams(lp); }
            pubTickerBox.setBackgroundColor(parsearColor(colorBg, 0x88000000));
            pubTickerTxt.setTextColor(parsearColor(colorAc, 0xFFFFFFFF));
            pubTickerDurMs = Math.max(1, velocidad) * 1000;
            tickerArrancar(gen, 0f, 0);
        });
    }

    // =====================================================================
    //  ACTUALIZACIONES
    // =====================================================================
    private void checkForUpdates() {
        if (!hayInternet()) return;
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(VERSION_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("X-GTV-Origin", SELLO);
                if (conn.getResponseCode() != 200) return;
                JSONObject json = new JSONObject(leer(conn.getInputStream(), false));
                int remote = json.optInt("versionCode", 0);
                String apkUrl = json.optString("apkUrl", "");
                if (remote > getCurrentVersionCode() && !apkUrl.isEmpty()) runOnUiThread(() -> mostrarDialogoUpdate(remote, apkUrl));
            } catch (Exception ignored) {}
        }).start();
    }

    private String leer(InputStream is, boolean saltos) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) { sb.append(line); if (saltos) sb.append("\n"); }
        br.close();
        return sb.toString();
    }

    private boolean hayInternet() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return false; }
    }

    @SuppressWarnings("deprecation")
    private int getCurrentVersionCode() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; }
        catch (Exception e) { return 0; }
    }

    // === FIX VOZ: Método para solicitar permiso de micrófono ===
    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
        }
    }
    // =====================================================================

    private void mostrarDialogoUpdate(int remoteVersionCode, String apkUrl) {
        SharedPreferences up = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        final String key = UPDATE_TRIES_PREFIX + remoteVersionCode;
        int skips = up.getInt(key, 0);
        boolean obligatorio = skips >= MAX_SKIPS;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update, null);
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
        dialogVersionActual.setText("Versión actual:  v" + getCurrentVersionCode());
        dialogVersionNueva.setText("Nueva versión:  v" + remoteVersionCode);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(!obligatorio);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnActualizar.setOnClickListener(v -> { dialog.dismiss(); descargarEInstalar(apkUrl); });
        if (obligatorio) btnMasTarde.setVisibility(View.GONE);
        else btnMasTarde.setOnClickListener(v -> { up.edit().putInt(key, up.getInt(key, 0) + 1).apply(); dialog.dismiss(); });
        dialog.show();
        if (dialog.getWindow() != null) {
            int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        btnActualizar.post(() -> { btnActualizar.requestFocus(); btnActualizar.setFocusable(true); btnActualizar.setFocusableInTouchMode(true); });
    }

    @SuppressWarnings("deprecation")
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
                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("X-GTV-Origin", SELLO);
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

    // =====================================================================
    //  ANDROID TV: WATCH NEXT + DEEP LINK
    // =====================================================================
    private boolean esTV() { return getPackageManager().hasSystemFeature("android.software.leanback"); }

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
            WatchNextProgram.Builder b = new WatchNextProgram.Builder();
            b.setType(TvContractCompat.WatchNextPrograms.TYPE_CLIP);
            b.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE);
            b.setLastEngagementTimeUtcMillis(System.currentTimeMillis());
            b.setTitle(canal.nombre != null ? canal.nombre : "Canal");
            b.setDescription("GTV Play R");
            b.setIntentUri(Uri.parse(intentUri));
            if (canal.logo != null && !canal.logo.isEmpty()) b.setPosterArtUri(Uri.parse(canal.logo));
            b.setInternalProviderId(canal.url);
            try {
                getContentResolver().delete(TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID + "=?", new String[]{canal.url});
            } catch (Exception ignored) {}
            getContentResolver().insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues());
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

    private void manejarIntentEntrante(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (!"gtvplayr".equals(data.getScheme())) return;
        String url = data.getQueryParameter("url");
        if (url == null || url.isEmpty()) return;
        irACanalPorUrl(url, 0);
    }

    private void irACanalPorUrl(final String url, final int intento) {
        int idx = -1;
        for (int i = 0; i < canales.size(); i++) if (canales.get(i).url.equals(url)) { idx = i; break; }
        if (idx >= 0) { indiceActual = idx; zapPend = idx; handler.post(zapRun); }
        else if (intento < 12) handler.postDelayed(() -> irACanalPorUrl(url, intento + 1), 1000);
    }

    // =====================================================================
    //  LISTAS
    // =====================================================================
    private void refrescarListaRemota() {
        final int miToken = ++listaToken;
        new Thread(() -> {
            SharedPreferences prefs = getSharedPreferences(LISTAS_PREFS, MODE_PRIVATE);
            boolean ultimaFueMaestra = prefs.getString("ultima_lista_maestra", "false").equals("true");
            String listaUrl = null;
            boolean validarConWorker = ultimaFueMaestra;

            if (!validarConWorker) {
                try {
                    JSONArray listas = new JSONArray(prefs.getString(LISTAS_KEY, "[]"));
                    if (listas.length() > 0) {
                        String ultimaNombre = prefs.getString(ULTIMA_LISTA_KEY, null);
                        JSONObject elegida = null;
                        if (ultimaNombre != null) {
                            for (int i = 0; i < listas.length(); i++) {
                                if (listas.getJSONObject(i).optString("nombre").equals(ultimaNombre)) { elegida = listas.getJSONObject(i); break; }
                            }
                        }
                        if (elegida == null) elegida = listas.getJSONObject(0);
                        listaUrl = elegida.optString("url", "");
                    }
                } catch (Exception ignored) {}
            }

            if (validarConWorker || listaUrl == null || listaUrl.isEmpty()) {
                try {
                    HttpURLConnection connVal = (HttpURLConnection) new URL(VALIDAR_URL).openConnection();
                    connVal.setRequestMethod("POST");
                    connVal.setConnectTimeout(10000);
                    connVal.setReadTimeout(10000);
                    connVal.setDoOutput(true);
                    connVal.setRequestProperty("Content-Type", "application/json");
                    connVal.setRequestProperty("X-GTV-Origin", SELLO);
                    JSONObject body = new JSONObject();
                    body.put("deviceId", generarDeviceId());
                    body.put("auto", true);
                    OutputStream os = connVal.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();
                    int code = connVal.getResponseCode();
                    JSONObject json = new JSONObject(leer(code == 200 ? connVal.getInputStream() : connVal.getErrorStream(), false));
                    if (!json.optString("error", "").isEmpty()) {
                        if (miToken == listaToken) runOnUiThread(() -> {
                            prefs.edit().clear().apply();
                            mostrarErrorLista("Acceso denegado. La clave ha cambiado. Ingresá una nueva lista.");
                            if (myWebView != null)
                                myWebView.evaluateJavascript("javascript:window.mostrarPantallaIngresoLista&&window.mostrarPantallaIngresoLista();", null);
                        });
                        return;
                    }
                    listaUrl = json.optString("url", "");
                    if (listaUrl.startsWith("/")) listaUrl = "https://gtv-portero.abrel03.workers.dev" + listaUrl;
                    prefs.edit().putString("ultima_lista_maestra", "true").apply();
                } catch (Exception e) {
                    if (miToken == listaToken) runOnUiThread(() -> mostrarErrorLista("Sin conexión. Verificá tu internet."));
                    return;
                }
            }

            if (listaUrl == null || listaUrl.isEmpty()) {
                if (miToken == listaToken) runOnUiThread(() -> mostrarErrorLista("No tenés lista. Cargala desde el menú."));
                return;
            }

            try {
                HttpURLConnection connList = (HttpURLConnection) new URL(listaUrl).openConnection();
                connList.setRequestMethod("GET");
                connList.setConnectTimeout(15000);
                connList.setReadTimeout(20000);
                connList.setRequestProperty("X-GTV-Origin", SELLO);
                connList.setRequestProperty("User-Agent", UA);
                if (connList.getResponseCode() != 200) {
                    if (miToken == listaToken) runOnUiThread(() -> mostrarErrorLista("Error al descargar la lista."));
                    return;
                }
                final String contenidoNuevo = leer(connList.getInputStream(), true);
                final ArrayList<Canal> listaNueva = parsearM3u(contenidoNuevo);
                if (listaNueva.isEmpty()) {
                    if (miToken == listaToken) runOnUiThread(() -> mostrarErrorLista("La lista está vacía."));
                    return;
                }
                runOnUiThread(() -> {
                    if (miToken != listaToken) return;
                    canales.clear();
                    canales.addAll(listaNueva);
                    indiceActual = 0;
                    listaLegada = contenidoNuevo;
                    enviarListaAlHtmlConReintentos(contenidoNuevo, 0);
                    Log.d(TAG, "lista fresca (" + listaNueva.size() + " canales)");
                });
            } catch (Exception e) {
                if (miToken == listaToken) runOnUiThread(() -> mostrarErrorLista("Sin conexión. Verificá tu internet."));
            }
        }).start();
    }

    private void mostrarErrorLista(String msg) {
        if (myWebView != null && webViewListo) {
            String m = msg.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
            myWebView.evaluateJavascript(
                "javascript:(function(){try{var d=document.getElementById('list');"
              + "if(d)d.innerHTML='<div class=\"empty\"><div class=\"icon\">⚠️</div><div class=\"title\">Sin lista</div><div class=\"subtitle\">"
              + m + "</div><button onclick=\"location.reload()\">🔄 Reintentar</button></div>';}catch(e){}})();", null);
        }
    }

    private void enviarListaAlHtmlConReintentos(final String contenido, final int intento) {
        if (myWebView == null || intento > 30) return;
        myWebView.evaluateJavascript(
            "(function(){try{return typeof window.gtvSetCanales==='function';}catch(e){return false;}})()",
            value -> {
                if ("true".equals(value)) enviarListaAlHtml(contenido);
                else handler.postDelayed(() -> enviarListaAlHtmlConReintentos(contenido, intento + 1), 300);
            });
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
                if (c.motor != null) o.put("motor", c.motor);
                if (c.esPeliculas) o.put("esPeliculas", true);
                if (c.urlPeliculasM3U != null) o.put("urlPeliculasM3U", c.urlPeliculasM3U);
                arr.put(o);
            }
            final String json = arr.toString();
            runOnUiThread(() -> {
                if (myWebView != null && webViewListo) {
                    try { myWebView.evaluateJavascript("window.gtvSetCanales&&window.gtvSetCanales(" + JSONObject.quote(json) + ");", null); } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    private void guardarNuevaLista(String nombre, String urlOriginal) {
        try {
            SharedPreferences prefs = getSharedPreferences(LISTAS_PREFS, MODE_PRIVATE);
            JSONArray listas = new JSONArray(prefs.getString(LISTAS_KEY, "[]"));
            JSONArray nuevas = new JSONArray();
            for (int i = 0; i < listas.length(); i++) {
                JSONObject obj = listas.getJSONObject(i);
                if (!obj.optString("nombre").equals(nombre)) nuevas.put(obj);
            }
            JSONObject nueva = new JSONObject();
            nueva.put("nombre", nombre);
            nueva.put("url", urlOriginal);
            nueva.put("fecha", System.currentTimeMillis());
            nuevas.put(nueva);
            prefs.edit().putString(LISTAS_KEY, nuevas.toString()).putString(ULTIMA_LISTA_KEY, nombre).commit();
        } catch (Exception e) { Log.e(TAG, "Error guardando lista: " + e.getMessage()); }
    }

    private String derivarNombreDeUrl(String url) {
        try {
            String host = new URL(url).getHost();
            if (host == null || host.isEmpty()) return "Lista externa";
            return host.replace("www.", "");
        } catch (Exception e) { return "Lista externa"; }
    }

    private void registrarListaEnWorker(String url, String nombre) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://gtv-portero.abrel03.workers.dev/api/reproductor/registrar-lista").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-GTV-Origin", SELLO);
                JSONObject body = new JSONObject();
                body.put("deviceId", generarDeviceId());
                body.put("url", url);
                body.put("nombre", nombre);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) { Log.e(TAG, "Error registrando lista: " + e.getMessage()); }
        }).start();
    }

    private void eliminarListaGuardada(String nombre) {
        try {
            SharedPreferences prefs = getSharedPreferences(LISTAS_PREFS, MODE_PRIVATE);
            JSONArray listas = new JSONArray(prefs.getString(LISTAS_KEY, "[]"));
            JSONArray nuevas = new JSONArray();
            for (int i = 0; i < listas.length(); i++) {
                JSONObject obj = listas.getJSONObject(i);
                if (!obj.optString("nombre").equals(nombre)) nuevas.put(obj);
            }
            SharedPreferences.Editor ed = prefs.edit().putString(LISTAS_KEY, nuevas.toString());
            if (prefs.getString(ULTIMA_LISTA_KEY, "").equals(nombre)) {
                if (nuevas.length() > 0) ed.putString(ULTIMA_LISTA_KEY, nuevas.getJSONObject(0).optString("nombre"));
                else ed.remove(ULTIMA_LISTA_KEY);
            }
            ed.commit();
        } catch (Exception e) { Log.e(TAG, "Error eliminando lista: " + e.getMessage()); }
    }

    private String listarListasJSON() {
        try {
            JSONArray listas = new JSONArray(getSharedPreferences(LISTAS_PREFS, MODE_PRIVATE).getString(LISTAS_KEY, "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < listas.length(); i++) {
                JSONObject obj = listas.getJSONObject(i), item = new JSONObject();
                item.put("nombre", obj.optString("nombre"));
                item.put("url", obj.optString("url"));
                item.put("fecha", obj.optLong("fecha", 0));
                item.put("canales", "—");
                out.put(item);
            }
            return out.toString();
        } catch (Exception e) { return "[]"; }
    }

    private void usarListaGuardada(String nombre) {
        try {
            SharedPreferences prefs = getSharedPreferences(LISTAS_PREFS, MODE_PRIVATE);
            JSONArray listas = new JSONArray(prefs.getString(LISTAS_KEY, "[]"));
            for (int i = 0; i < listas.length(); i++) {
                JSONObject obj = listas.getJSONObject(i);
                if (obj.optString("nombre").equals(nombre)) {
                    if (obj.optString("url", "").isEmpty()) { Toast.makeText(this, "La lista no tiene URL guardada", Toast.LENGTH_SHORT).show(); return; }
                    prefs.edit().putString(ULTIMA_LISTA_KEY, nombre).putString("ultima_lista_maestra", "false").commit();
                    refrescarListaRemota();
                    Toast.makeText(this, "Actualizando lista...", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Toast.makeText(this, "Lista no encontrada", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Log.e(TAG, "Error usando lista: " + e.getMessage()); }
    }

    private String generarDeviceId() {
        if (deviceIdCache != null) return deviceIdCache;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String guardado = prefs.getString("device_id_guardado", null);
        if (guardado != null && guardado.startsWith("GTV-R-") && guardado.length() == 10) { deviceIdCache = guardado; return deviceIdCache; }

        String androidId = null;
        try { androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID); } catch (Exception ignored) {}
        if (androidId == null || androidId.isEmpty()) androidId = "GTVR_" + Build.MANUFACTURER + "_" + Build.MODEL;

        final String ALFABETO = "123456789ABCDEFGHIJKLMNPQRSTUVWXYZ";
        long hash = 0;
        for (int i = 0; i < androidId.length(); i++) hash = (hash * 31 + androidId.charAt(i)) & 0xFFFFFFFFL;
        hash = hash ^ (hash >> 16);
        hash = (hash * 0x45d9f3bL) & 0xFFFFFFFFL;

        StringBuilder sb = new StringBuilder("GTV-R-");
        long h = hash;
        for (int i = 0; i < 6; i++) {
            sb.append(ALFABETO.charAt((int) (h % ALFABETO.length())));
            h = h / ALFABETO.length();
            h = h ^ (h >> 3);
        }
        deviceIdCache = sb.toString();
        prefs.edit().putString("device_id_guardado", deviceIdCache).apply();
        return deviceIdCache;
    }

    private void validarYcargar(final String texto) {
        @SuppressWarnings("deprecation") final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Validando...");
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(VALIDAR_URL).openConnection();
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
                JSONObject json = new JSONObject(leer(code == 200 ? conn.getInputStream() : conn.getErrorStream(), false));
                String error = json.optString("error", "");
                if (!error.isEmpty()) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(this, error.equals("clave_invalida") ? "Palabra clave incorrecta"
                            : error.equals("licencia_requerida") ? "Necesitás una licencia activa" : "Error: " + error, Toast.LENGTH_LONG).show();
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

    @SuppressWarnings("deprecation")
    private void cargarLista(final String listaUrl, final ProgressDialog progress) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(listaUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("X-GTV-Origin", SELLO);
                conn.setRequestProperty("User-Agent", UA);
                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> { progress.dismiss(); Toast.makeText(this, "Error al descargar lista (HTTP " + code + ")", Toast.LENGTH_LONG).show(); });
                    return;
                }
                final String contenido = leer(conn.getInputStream(), true);
                final ArrayList<Canal> lista = parsearM3u(contenido);
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (lista.isEmpty()) { Toast.makeText(this, "La lista está vacía o no es válida", Toast.LENGTH_LONG).show(); return; }
                    canales.clear();
                    canales.addAll(lista);
                    indiceActual = 0;
                    listaLegada = contenido;
                    enviarListaAlHtmlConReintentos(contenido, 0);
                    boolean maestra = listaUrl.contains("/api/m3u") || listaUrl.contains("gtv-portero.abrel03.workers.dev");
                    if (!maestra) {
                        String nombre = derivarNombreDeUrl(listaUrl);
                        guardarNuevaLista(nombre, listaUrl);
                        registrarListaEnWorker(listaUrl, nombre);
                        getSharedPreferences(LISTAS_PREFS, MODE_PRIVATE).edit().putString("ultima_lista_maestra", "false").apply();
                    } else {
                        getSharedPreferences(LISTAS_PREFS, MODE_PRIVATE).edit()
                            .putString("ultima_lista_maestra", "true")
                            .putString(ULTIMA_LISTA_KEY, "Lista maestra").apply();
                    }
                    Toast.makeText(this, "Lista cargada: " + lista.size() + " canales", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { progress.dismiss(); Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private ArrayList<Canal> parsearM3u(String texto) {
        ArrayList<Canal> out = new ArrayList<>();
        Pattern pLogo = Pattern.compile("tvg-logo\\s*=\\s*\"([^\"]*)\"");
        Pattern pRef = Pattern.compile("tvg-referer\\s*=\\s*\"([^\"]*)\"");
        Pattern pGain = Pattern.compile("tvg-gain\\s*=\\s*\"([^\"]*)\"");
        Pattern pMotor = Pattern.compile("tvg-player\\s*=\\s*\"([^\"]*)\"");
        Pattern pPeli = Pattern.compile("peliculas|videoteca|peplis|/api/m3u-peliculas");
        String nombre = "", logo = "", referer = "", motor = null, urlPeli = null;
        float gain = 1.0f;
        boolean esPeliculas = false;
        for (String l0 : texto.split("\\r?\\n")) {
            String l = l0.trim();
            if (l.isEmpty() || l.equals("#EXTM3U") || l.startsWith("#EXTVLCOPT")) continue;
            if (l.startsWith("#EXTINF")) {
                nombre = "Canal " + (out.size() + 1);
                logo = ""; referer = ""; gain = 1.0f; esPeliculas = false; urlPeli = null; motor = null;
                Matcher m = pLogo.matcher(l);
                if (m.find()) logo = m.group(1);
                m = pRef.matcher(l);
                if (m.find()) referer = m.group(1).trim();
                m = pGain.matcher(l);
                if (m.find()) { try { float gv = Float.parseFloat(m.group(1)); if (gv > 0 && gv <= 2f) gain = gv; } catch (Exception ignored) {} }
                m = pMotor.matcher(l);
                if (m.find()) { String mv = m.group(1).trim().toLowerCase(); if (mv.equals("mpv") || mv.equals("exo")) motor = mv; }
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
                if (esPeliculas) urlPeli = l;
                if (pPeli.matcher(l).find()) { esPeliculas = true; urlPeli = l; }
                Canal c = new Canal(l, nombre, logo, referer, gain);
                c.esPeliculas = esPeliculas;
                c.urlPeliculasM3U = urlPeli;
                c.motor = motor;
                out.add(c);
                nombre = ""; logo = ""; referer = ""; gain = 1.0f; esPeliculas = false; urlPeli = null; motor = null;
            }
        }
        return out;
    }

    // =====================================================================
    //  PUENTE JS (FIX PELÍCULAS INFALIBLE)
    // =====================================================================
    public class WebAppInterface {
        @JavascriptInterface public String getDeviceId() { return generarDeviceId(); }
        @JavascriptInterface public String getDeviceIdParaWorker() { return generarDeviceId(); }

        @JavascriptInterface public String getMotor() { 
            String m = motorPreferido();
            if ("eme_plus".equals(m) || "mpv".equals(m)) return "eme_plus";
            return "e_plus";
        }

        @JavascriptInterface public void setMotor(String m) {
            if (m == null) return;
            m = m.toLowerCase().trim();
            if ("e_plus".equals(m) || "auto".equals(m) || "exo".equals(m)) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_MOTOR, "e_plus").apply();
            } else if ("eme_plus".equals(m) || "mpv".equals(m)) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_MOTOR, "eme_plus").apply();
            }
        }

        @JavascriptInterface public String getConfigNativa() { return getJson(CONFIG_URL, 12000, true); }
        @JavascriptInterface public String getEstadoCanalesNativo() { return getJson(ESTADO_URL, 8000, false); }

        @JavascriptInterface public String getTextoNativo(String urlStr) {
            if (urlStr == null || !(urlStr.startsWith("http://") || urlStr.startsWith("https://"))) return "";
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", UA);
                conn.setRequestProperty("Accept", "*/*");
                if (urlStr.startsWith(WORKER_URL)) conn.setRequestProperty("X-GTV-Origin", SELLO);
                if (conn.getResponseCode() != 200) {
                    Log.e(TAG, "getTextoNativo HTTP " + conn.getResponseCode() + " " + urlStr);
                    return "";
                }
                return leer(conn.getInputStream(), true);
            } catch (Exception e) {
                Log.e(TAG, "getTextoNativo error: " + e.getMessage());
                return "";
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        private String getJson(String base, int timeout, boolean noCache) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(base + "?t=" + System.currentTimeMillis()).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
                conn.setRequestProperty("X-GTV-Origin", SELLO);
                if (noCache) conn.setRequestProperty("Cache-Control", "no-cache");
                if (conn.getResponseCode() != 200) return "";
                return leer(conn.getInputStream(), false);
            } catch (Exception e) {
                return "";
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @JavascriptInterface public void setLista(final String json) {
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(json);
                    ArrayList<Canal> nueva = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        float gain = (float) o.optDouble("gain", 1.0);
                        if (gain <= 0f || gain > 2f) gain = 1.0f;
                        Canal c = new Canal(o.getString("url"), o.optString("nombre", o.optString("name", "Canal " + (i + 1))),
                            o.optString("logo", ""), o.optString("referer", ""), gain, o.optBoolean("oculto", false));
                        c.esPeliculas = o.optBoolean("esPeliculas", false);
                        c.urlPeliculasM3U = o.isNull("urlPeliculasM3U") ? null : o.optString("urlPeliculasM3U", null);
                        String mv = o.optString("motor", "");
                        c.motor = (mv.equals("mpv") || mv.equals("exo")) ? mv : null;
                        nueva.add(c);
                    }
                    canales.clear();
                    canales.addAll(nueva);
                } catch (Exception e) { Log.e(TAG, "setLista error: " + e.getMessage()); }
            });
        }

        @JavascriptInterface public void sincronizarIndice(final int i) { runOnUiThread(() -> indiceActual = i); }
        @JavascriptInterface public void detener() { runOnUiThread(MainActivity.this::apagarTodo); }

        @JavascriptInterface public void setModoPeliculas(final boolean activo, final boolean zapActivo, final String numero) {
            runOnUiThread(() -> {
                modoPeliculas = activo;
                peliZapActivo = activo && zapActivo;
                peliNumero = (numero == null) ? "" : numero;
            });
        }

        @JavascriptInterface public void reproducir(String url, String nombre, String numero, String logo, String referer, float gainArg) {
            final float gain = (gainArg > 0f && gainArg <= 2f) ? gainArg : 1.0f;
            final String ref = (referer != null) ? referer : "";
            
            runOnUiThread(() -> {
                int idx = -1;
                for (int i = 0; i < canales.size(); i++) {
                    if (canales.get(i).url.equals(url)) { 
                        idx = i; 
                        break; 
                    }
                }

                if (idx >= 0) {
                    Canal c = canales.get(idx);
                    
                    boolean esPeli = c.esPeliculas || 
                                     c.nombre.toUpperCase().contains("PELICULA") || 
                                     c.nombre.toUpperCase().contains("VIDEOTECA") || 
                                     c.nombre.toUpperCase().contains("PELIS") ||
                                     c.url.toLowerCase().contains("m3u-peliculas");
                    
                    if (esPeli && c.urlPeliculasM3U != null && !c.urlPeliculasM3U.isEmpty()) {
                        String safeUrl = c.urlPeliculasM3U.replace("'", "\\'");
                        avisarAlWebView("window.abrirPeliculas&&window.abrirPeliculas('" + safeUrl + "');");
                        return;
                    }
                    
                    indiceActual = idx;
                    zapPend = idx;
                    handler.post(zapRun);
                } else {
                    MainActivity.this.reproducirCanal(new Canal(url, nombre, logo, ref, gain));
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

        @JavascriptInterface public boolean esExoPlayer(String url) { return tipoDe(url) == Tipo.DIRECTO; }
        @JavascriptInterface public boolean estaEnExo() { return motorActivo == Motor.EXO || motorActivo == Motor.MPV; }

        @JavascriptInterface public void cargarListaDesdeHtml(final String texto) { runOnUiThread(() -> validarYcargar(texto)); }
        @JavascriptInterface public String listarListas() { return listarListasJSON(); }
        @JavascriptInterface public void usarLista(final String nombre) { runOnUiThread(() -> usarListaGuardada(nombre)); }
        @JavascriptInterface public void eliminarLista(final String nombre) {
            runOnUiThread(() -> { eliminarListaGuardada(nombre); Toast.makeText(MainActivity.this, "Lista eliminada", Toast.LENGTH_SHORT).show(); });
        }

        @JavascriptInterface public void mostrarWebView() {
            runOnUiThread(() -> {
                tickerDetener(true);
                handler.removeCallbacks(zapRun);
                zapPend = -1;
                apagarTodo();
                playerContainer.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume();
                myWebView.resumeTimers();
                ocultarControlesNativos();
            });
        }

        @JavascriptInterface public void cerrarPlayer() { runOnUiThread(MainActivity.this::cerrarReproductor); }
        @JavascriptInterface public void salirApp() { runOnUiThread(() -> finishAndRemoveTask()); }

        @JavascriptInterface public void mostrarTicker(final String texto, final String badge, final int velocidad, final String colorBg, final String colorAc) {
            mostrarTickerImpl(texto, badge, velocidad, colorBg, colorAc, 14, "arial");
        }
        @JavascriptInterface public void mostrarTickerConTamano(final String texto, final String badge, final int velocidad, final String colorBg, final String colorAc, final int tamPx) {
            mostrarTickerImpl(texto, badge, velocidad, colorBg, colorAc, tamPx, "arial");
        }
        @JavascriptInterface public void mostrarTickerConFuente(final String texto, final String badge, final int velocidad, final String colorBg, final String colorAc, final int tamPx, final String fuente) {
            mostrarTickerImpl(texto, badge, velocidad, colorBg, colorAc, tamPx, fuente);
        }
        @JavascriptInterface public void ocultarTicker() { runOnUiThread(() -> tickerDetener(true)); }

        @JavascriptInterface public void mostrarModalPublicidad(final String url, final boolean esVideo, final int duracionSeg) {
            runOnUiThread(() -> {
                if (pubModal == null) return;
                if (pubModalVid != null) { try { pubModalVid.stopPlayback(); pubModalVid.setVideoURI(null); } catch (Exception ignored) {} }
                if (pubModalClose != null) pubHandler.removeCallbacks(pubModalClose);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pubModal.getLayoutParams();
                lp.width = dp(180); lp.height = dp(180);
                pubModal.setLayoutParams(lp);
                pubModal.setVisibility(View.VISIBLE);
                final String finJs = "window.gtv&&window.gtv.onModalTerminado&&window.gtv.onModalTerminado();";
                if (esVideo) {
                    pubModalImg.setVisibility(View.GONE);
                    pubModalVid.setVisibility(View.VISIBLE);
                    try {
                        pubModalVid.setVideoURI(Uri.parse(url));
                        pubModalVid.setOnPreparedListener(mp -> {
                            try { mp.setLooping(false); mp.setVolume(0f, 0f); } catch (Exception ignored) {}
                            try { pubModalVid.start(); } catch (Exception ignored) {}
                        });
                    } catch (Exception e) { pubModal.setVisibility(View.GONE); avisarAlWebView(finJs); return; }
                } else {
                    pubModalVid.setVisibility(View.GONE);
                    pubModalImg.setVisibility(View.VISIBLE);
                    try { Glide.with(MainActivity.this).load(url).override(dp(180), dp(180)).centerCrop().dontAnimate().into(pubModalImg); }
                    catch (Exception e) { pubModal.setVisibility(View.GONE); avisarAlWebView(finJs); return; }
                }
                pubModalClose = () -> {
                    if (pubModal != null) pubModal.setVisibility(View.GONE);
                    if (pubModalVid != null) { try { pubModalVid.stopPlayback(); pubModalVid.setVideoURI(null); } catch (Exception ignored) {} }
                    avisarAlWebView(finJs);
                };
                pubHandler.postDelayed(pubModalClose, Math.max(2, duracionSeg) * 1000L);
            });
        }

        @JavascriptInterface public void ocultarPublicidad() {
            runOnUiThread(() -> {
                tickerDetener(true);
                if (pubModal != null) pubModal.setVisibility(View.GONE);
                if (pubModalClose != null) pubHandler.removeCallbacks(pubModalClose);
            });
        }
    }

    // =====================================================================
    //  BARRAS DEL SISTEMA
    // =====================================================================
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
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
