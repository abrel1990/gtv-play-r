package com.gtv.play;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
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
import android.webkit.CookieManager;
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
import androidx.tvprovider.media.tv.TvContractCompat;
import androidx.tvprovider.media.tv.WatchNextProgram;
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

// ✅ NUEVO: imports de NewPipe
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import java.io.IOException;

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
    private volatile boolean webViewListo = false;

    private static final String WATCH_NEXT_CHANNEL_NAME = "GTV Play Recientes";
    private long watchNextChannelId = -1;
    private final ArrayList<String> historialCanales = new ArrayList<>();
    private static final int MAX_HISTORIAL = 10;

    private static final String UPDATE_PREFS = "gtv_update";
    private static final String UPDATE_TRIES_PREFIX = "gtv_update_tries_";
    private static final String VERSION_URL = "https://gtv-portero.abrel03.workers.dev/api/version";
    private static final int MAX_SKIPS = 3;

    static class Canal {
        final String url, nombre, logo, referer;
        final float gain;
        Canal(String url, String nombre, String logo) {
            this(url, nombre, logo, "", 1.0f);
        }
        Canal(String url, String nombre, String logo, String referer) {
            this(url, nombre, logo, referer, 1.0f);
        }
        Canal(String url, String nombre, String logo, String referer, float gain) {
            this.url = url;
            this.nombre = nombre;
            this.logo = logo;
            this.referer = (referer != null) ? referer : "";
            this.gain = (gain > 0f && gain <= 2f) ? gain : 1.0f;
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
                    avisarAlWebView("window.gtv&&window.gtv.onNativoListo&&window.gtv.onNativoListo();");
                } else if (state == Player.STATE_BUFFERING) {
                    mostrarLoaderSintonizando();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                detenerLoaderAnim();
                mostrarLoaderError();
                Log.e(TAG, "ExoPlayer error: " + error.getMessage());
                avisarAlWebView("window.gtv&&window.gtv.onNativoError&&window.gtv.onNativoError();");
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

        // ✅ NUEVO: Inicializar NewPipe
        try {
            NewPipe.init(ServiceList.YouTube.getService());
            Log.e(TAG, "NewPipe inicializado");
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando NewPipe: " + e.getMessage());
        }

        handler.postDelayed(this::checkForUpdates, 3000);
        handler.postDelayed(this::inicializarWatchNext, 1500);
    }

    // ✅ NUEVO: Resolver YouTube con NewPipe (hilo de fondo)
    private void resolverYouTubeConNewPipe(String url) {
        new Thread(() -> {
            try {
                String videoId = null;
                String handle = null;
                
                // Detectar tipo de URL
                if (url.contains("@")) {
                    handle = url.substring(url.indexOf("@")).split("/")[0];
                } else if (url.contains("watch?v=")) {
                    videoId = url.substring(url.indexOf("watch?v=") + 8).substring(0, 11);
                }
                
                // Si es handle, buscar el canal y el live
                if (handle != null) {
                    ChannelInfo channelInfo = ChannelInfo.getInfo(
                        ServiceList.YouTube,
                        "https://www.youtube.com/" + handle
                    );
                    
                    // Buscar el primer video que esté en vivo
                    for (StreamInfoItem item : channelInfo.getRelatedItems()) {
                        if (item.isLive()) {
                            videoId = item.getUrl().replace("https://www.youtube.com/watch?v=", "");
                            break;
                        }
                    }
                    
                    if (videoId == null) {
                        runOnUiThread(() -> {
                            mostrarSinSenal();
                            Log.e(TAG, "NewPipe: no hay live en " + handle);
                        });
                        return;
                    }
                }
                
                if (videoId == null) {
                    runOnUiThread(() -> mostrarSinSenal());
                    return;
                }
                
                // Obtener el stream del video
                StreamInfo info = StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=" + videoId
                );
                
                // Buscar el stream en formato M3U8 o MP4
                String videoUrl = null;
                for (VideoStream vs : info.getVideoStreams()) {
                    if (vs.getUrl().contains("m3u8") || vs.getUrl().contains("mp4")) {
                        videoUrl = vs.getUrl();
                        break;
                    }
                }
                
                if (videoUrl == null && !info.getVideoStreams().isEmpty()) {
                    videoUrl = info.getVideoStreams().get(0).getUrl();
                }
                
                if (videoUrl != null) {
                    final String finalUrl = videoUrl;
                    runOnUiThread(() -> {
                        Log.e(TAG, "NewPipe OK: " + finalUrl);
                        Canal c = canales.get(indiceActual);
                        reproducirEnExo(new Canal(finalUrl, c.nombre, c.logo, c.referer, c.gain));
                    });
                } else {
                    runOnUiThread(() -> mostrarSinSenal());
                }
            } catch (Exception e) {
                Log.e(TAG, "NewPipe error: " + e.getMessage());
                runOnUiThread(() -> {
                    // Fallback al WebView
                    if (url.contains("watch?v=")) {
                        String vid = url.substring(url.indexOf("watch?v=") + 8).substring(0, 11);
                        cargarYouTubeSeguro(vid);
                    } else {
                        mostrarSinSenal();
                    }
                });
            }
        }).start();
    }

    // ✅ NUEVO: Mostrar sin señal
    private void mostrarSinSenal() {
        runOnUiThread(() -> {
            mostrarLoaderError();
            Toast.makeText(this, "Canal sin señal", Toast.LENGTH_SHORT).show();
        });
    }

    private void avisarAlWebView(String js) {
        if (myWebView == null || !webViewListo) return;
        myWebView.post(() -> {
            try {
                myWebView.evaluateJavascript("javascript:(function(){try{" + js + "}catch(e){}})();", null);
            } catch (Exception e) {}
        });
    }

    // ============================================================
    // WATCH NEXT (Android TV)
    // ============================================================

    private boolean esTV() {
        return getPackageManager().hasSystemFeature("android.software.leanback");
    }

    private void inicializarWatchNext() {
        if (!esTV()) return;
        try {
            watchNextChannelId = obtenerOCrearCanalWatchNext();
        } catch (Exception e) {}
    }

    private long obtenerOCrearCanalWatchNext() {
        try {
            Cursor cursor = getContentResolver().query(
                TvContractCompat.Channels.CONTENT_URI,
                new String[]{
                    TvContractCompat.Channels._ID,
                    TvContractCompat.Channels.COLUMN_DISPLAY_NAME
                },
                null, null, null
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String nombre = cursor.getString(1);
                    if (WATCH_NEXT_CHANNEL_NAME.equals(nombre)) {
                        cursor.close();
                        return id;
                    }
                }
                cursor.close();
            }
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(TvContractCompat.Channels.COLUMN_DISPLAY_NAME, WATCH_NEXT_CHANNEL_NAME);
            values.put(TvContractCompat.Channels.COLUMN_DESCRIPTION, "Últimos canales que viste en GTV Play");
            values.put(TvContractCompat.Channels.COLUMN_TYPE, TvContractCompat.Channels.TYPE_PREVIEW);
            values.put(TvContractCompat.Channels.COLUMN_BROWSABLE, 0);
            values.put(TvContractCompat.Channels.COLUMN_SEARCHABLE, 0);
            values.put(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI, "gtvplay://play?url=");
            Uri channelUri = getContentResolver().insert(
                TvContractCompat.Channels.CONTENT_URI, values);
            if (channelUri == null) return -1;
            long channelId = ContentUris.parseId(channelUri);
            try {
                TvContractCompat.requestChannelBrowsable(this, channelId);
            } catch (Exception e) {}
            return channelId;
        } catch (Exception e) { return -1; }
    }

    private void agregarAlWatchNext(Canal canal) {
        if (!esTV() || watchNextChannelId == -1) return;
        if (canal == null || canal.url == null) return;
        try {
            try {
                Cursor cursor = getContentResolver().query(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    new String[]{
                        TvContractCompat.PreviewPrograms._ID,
                        TvContractCompat.PreviewPrograms.COLUMN_INTENT_URI
                    },
                    null, null, null
                );
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        String intentUri = cursor.getString(1);
                        if (intentUri != null && intentUri.contains(Uri.encode(canal.url))) {
                            getContentResolver().delete(
                                TvContractCompat.PreviewPrograms.CONTENT_URI.buildUpon()
                                    .appendPath(String.valueOf(id)).build(),
                                null, null
                            );
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {}
            String intentUri = "gtvplay://play?url=" + Uri.encode(canal.url)
                + "&nombre=" + Uri.encode(canal.nombre != null ? canal.nombre : "")
                + "&logo=" + Uri.encode(canal.logo != null ? canal.logo : "");
            WatchNextProgram.Builder builder = new WatchNextProgram.Builder();
            builder.setType(TvContractCompat.WatchNextPrograms.TYPE_CLIP);
            builder.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE);
            builder.setLastEngagementTimeUtcMillis(System.currentTimeMillis());
            builder.setTitle(canal.nombre != null ? canal.nombre : "Canal");
            builder.setDescription("GTV Play");
            builder.setIntentUri(Uri.parse(intentUri));
            if (canal.logo != null && !canal.logo.isEmpty()) {
                builder.setPosterArtUri(Uri.parse(canal.logo));
            }
            builder.setInternalProviderId(canal.url);
            WatchNextProgram programa = builder.build();
            getContentResolver().insert(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                programa.toContentValues()
            );
        } catch (Exception e) {}
    }

    private void actualizarWatchNext() {
        if (!esTV() || canales.isEmpty()) return;
        if (indiceActual < 0 || indiceActual >= canales.size()) return;
        Canal c = canales.get(indiceActual);
        if (c == null) return;
        historialCanales.remove(c.url);
        historialCanales.add(0, c.url);
        while (historialCanales.size() > MAX_HISTORIAL) {
            historialCanales.remove(historialCanales.size() - 1);
        }
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
        if (!"gtvplay".equals(data.getScheme())) return;
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
            if (esCanalExoPlayer(c.url)) {
                reproducirEnExo(c);
            } else {
                irACanalWebView(idx);
            }
        } else {
            Canal c = new Canal(url, nombre != null ? nombre : "Canal", logo != null ? logo : "", "", 1.0f);
            if (esCanalExoPlayer(url)) {
                indiceActual = -1;
                reproducirEnExo(c);
            }
        }
    }

    private void irACanalWebView(final int idx) {
        runOnUiThread(() -> {
            if (myWebView != null) {
                myWebView.evaluateJavascript(
                    "javascript:if(typeof window.irACanal==='function')window.irACanal(" + idx + ");", null);
            }
        });
    }

    // ... (checkForUpdates, mostrarDialogoUpdate, descargarEInstalar, instalarApk, loaders, deviceId, etc.) ...
    // Son exactamente iguales a los que ya tenés. NO SE TOCAN.

    // ============================================================
    // REPRODUCTOR
    // ============================================================

    private void inyectarTextureView() {
        View contentFrame = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        if (!(contentFrame instanceof ViewGroup)) return;
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
    }

    private boolean esCanalExoPlayer(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        if (u.contains(".m3u8")) return true;
        if (u.contains(".mpd")) return true;
        if (u.contains(".mp4") && u.startsWith("http")) return true;
        if (u.contains(".ts") && u.startsWith("http")) return true;
        if (u.contains(".webm") && u.startsWith("http")) return true;
        if (u.startsWith("http")) {
            if (u.contains("/hls/")) return true;
            if (u.contains("/live/") && !u.contains("youtube")) return true;
            if (u.contains("type=hls") || u.contains("format=hls")) return true;
            if (u.contains("type=m3u8") || u.contains("format=m3u8")) return true;
            if (u.contains(".m3u8?")) return true;
        }
        if (u.startsWith("http")
            && !u.contains("youtube.com")
            && !u.contains("youtu.be")
            && !u.contains("twitch.tv")
            && !u.contains("kick.com")
            && !u.contains("ok.ru")
            && !u.contains("odnoklassniki.ru")
            && !u.contains("dailymotion.com")
            && !u.contains("tokyvideo.com")
            && !u.contains("facebook.com")
            && !u.contains("instagram.com")
            && !u.contains("tiktok.com")
            && !u.contains("vimeo.com")) {
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
            volverAlWebViewConJS("javascript:if(typeof window.irACanal==='function')window.irACanal(" + indiceActual + ");");
        }
    }

    private void reproducirEnExo(Canal c) {
        enExoPlayer = true;
        myWebView.onPause();
        myWebView.pauseTimers();
        myWebView.setVisibility(View.GONE);
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
        exoPlayer.setMediaSource(construirMediaSource(c));
        exoPlayer.prepare();
        exoPlayer.play();
        try {
            exoPlayer.setVolume(c.gain);
        } catch (Exception e) {}
        programarTimeoutCanal();
        avisarAlWebView("window.gtv&&window.gtv.onNativoIniciado&&window.gtv.onNativoIniciado();");
        handler.postDelayed(this::actualizarWatchNext, 5000);
        mostrarControles();
        playerContainer.post(() -> {
            aplicarRotacion();
            reposicionarBotones();
        });
    }

    private androidx.media3.exoplayer.source.MediaSource construirMediaSource(Canal c) {
        if (c.referer == null || c.referer.isEmpty()) {
            DefaultHttpDataSource.Factory factoryDefault =
                new DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true);
            return new DefaultMediaSourceFactory(factoryDefault)
                .createMediaSource(MediaItem.fromUri(c.url));
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", c.referer);
        try {
            java.net.URL u = new java.net.URL(c.referer);
            headers.put("Origin", u.getProtocol() + "://" + u.getHost());
        } catch (Exception e) {}
        DefaultHttpDataSource.Factory factoryReferer = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers);
        return new DefaultMediaSourceFactory(factoryReferer)
            .createMediaSource(MediaItem.fromUri(c.url));
    }

    // ... (rotar, aplicarRotacion, reposicionarBotones, configurarWebView, configurarBotones, WebAppInterface, etc.) ...
    // Son exactamente iguales a los que ya tenés. NO SE TOCAN.

    // ✅ NUEVO: cargarYouTubeSeguro (fallback si NewPipe falla)
    private void cargarIframeConBaseUrl(String videoId) {
        if (myWebView == null) return;
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
            "<style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}" +
            "iframe{width:100%;height:100%;border:0;display:block}</style>" +
            "<meta name='referrer' content='origin'>" +
            "</head><body>" +
            "<iframe src='https://www.youtube.com/embed/" + videoId +
            "?autoplay=1&playsinline=1&rel=0&modestbranding=1&cc_load_policy=0&iv_load_policy=3'" +
            " allow='autoplay; encrypted-media; fullscreen; picture-in-picture'" +
            " allowfullscreen></iframe>" +
            "</body></html>";
        myWebView.loadDataWithBaseURL(
            "https://www.youtube.com/",
            html,
            "text/html",
            "UTF-8",
            null
        );
    }

    @JavascriptInterface
    public void cargarYouTubeSeguro(String videoId) {
        runOnUiThread(() -> {
            try {
                playerContainer.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume();
                myWebView.resumeTimers();
                cargarIframeConBaseUrl(videoId);
            } catch (Exception e) {}
        });
    }

    // ✅ NUEVO: llamada desde el HTML para usar NewPipe
    @JavascriptInterface
    public void resolverYouTube(String url) {
        resolverYouTubeConNewPipe(url);
    }
}
