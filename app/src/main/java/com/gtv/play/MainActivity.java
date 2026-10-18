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
import java.util.List;
import java.util.Map;

// NewPipe
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

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

        // NewPipe: inicializar con DownloaderImpl y registrar PoTokenProvider
        try {
            NewPipe.init(DownloaderImpl.getInstance());
            Log.e(TAG, "NewPipe inicializado OK");

            // Inicializar PoTokenProviderImpl con el Context de la app
            com.gtv.play.potoken.PoTokenProviderImpl.INSTANCE.init(getApplicationContext());

            // Registrar el provider en el extractor de YouTube
            org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor.setPoTokenProvider(
                com.gtv.play.potoken.PoTokenProviderImpl.INSTANCE
            );
            Log.e(TAG, "PoTokenProvider registrado OK");
        } catch (Exception e) {
            Log.e(TAG, "Error inicializando NewPipe: " + e.getMessage());
        }

        handler.postDelayed(this::checkForUpdates, 3000);
        handler.postDelayed(this::inicializarWatchNext, 1500);
    }

    // ============================================================
    // NewPipe: resolver YouTube (watch?v= y @handle)
    // ============================================================
    private void resolverYouTubeConNewPipe(String url) {
        new Thread(() -> {
            try {
                String videoId = null;
                String handle = null;

                String lowerUrl = url.toLowerCase();
                if (lowerUrl.contains("@")) {
                    int atIdx = url.indexOf('@');
                    if (atIdx >= 0) {
                        String resto = url.substring(atIdx);
                        int slash = resto.indexOf('/');
                        handle = (slash > 0) ? resto.substring(0, slash) : resto;
                    }
                } else if (lowerUrl.contains("watch?v=")) {
                    int idx = lowerUrl.indexOf("watch?v=");
                    if (idx >= 0 && idx + 8 + 11 <= url.length()) {
                        videoId = url.substring(idx + 8, idx + 8 + 11);
                    }
                } else if (lowerUrl.contains("youtu.be/")) {
                    int idx = lowerUrl.indexOf("youtu.be/");
                    if (idx >= 0 && idx + 9 + 11 <= url.length()) {
                        videoId = url.substring(idx + 9, idx + 9 + 11);
                    }
                }

                if (handle != null) {
                    ChannelInfo channelInfo = ChannelInfo.getInfo(
                        ServiceList.YouTube,
                        "https://www.youtube.com/" + handle
                    );

                    // ✅ FIX: Detección robusta de la pestaña de vivos
                    for (ListLinkHandler tab : channelInfo.getTabs()) {
                        List<String> filters = tab.getContentFilters();
                        if (filters != null && !filters.isEmpty()) {
                            String filtro = filters.get(0).toLowerCase();
                            
                            // Acepta "live", "livestreams", "en vivo", "directo"
                            if (filtro.contains("live") || filtro.contains("vivo") || filtro.contains("directo")) {
                                try {
                                    ChannelTabExtractor tabExtractor = ServiceList.YouTube.getChannelTabExtractor(tab);
                                    tabExtractor.fetchPage();
                                    for (InfoItem item : tabExtractor.getInitialPage().getItems()) {
                                        if (item instanceof StreamInfoItem) {
                                            StreamInfoItem stream = (StreamInfoItem) item;
                                            if (stream.getStreamType() == StreamType.LIVE_STREAM) {
                                                String streamUrl = stream.getUrl();
                                                if (streamUrl.contains("watch?v=")) {
                                                    videoId = streamUrl.substring(streamUrl.indexOf("watch?v=") + 8, streamUrl.indexOf("watch?v=") + 8 + 11);
                                                }
                                                break;
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error en pestaña de vivos: " + e.getMessage());
                                }
                                if (videoId != null) break;
                            }
                        }
                    }

                    if (videoId == null) {
                        runOnUiThread(this::mostrarSinSenal);
                        return;
                    }
                }

                if (videoId == null) {
                    runOnUiThread(this::mostrarSinSenal);
                    return;
                }

                StreamInfo info = StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=" + videoId
                );

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

                // ✅ Plan B: si no hay streams directos, buscar en HLS o DASH
                if (videoUrl == null || videoUrl.isEmpty()) {
                    if (info.getHlsUrl() != null && !info.getHlsUrl().isEmpty()) {
                        videoUrl = info.getHlsUrl();
                    } else if (info.getDashMpdUrl() != null && !info.getDashMpdUrl().isEmpty()) {
                        videoUrl = info.getDashMpdUrl();
                    }
                }

                if (videoUrl != null) {
                    final String finalUrl = videoUrl;
                    runOnUiThread(() -> {
                        Log.e(TAG, "NewPipe OK: " + finalUrl);
                        if (indiceActual >= 0 && indiceActual < canales.size()) {
                            Canal c = canales.get(indiceActual);
                            reproducirEnExo(new Canal(finalUrl, c.nombre, c.logo, c.referer, c.gain));
                        } else {
                            reproducirEnExo(new Canal(finalUrl, "YouTube", "", "", 1.0f));
                        }
                    });
                } else {
                    runOnUiThread(this::mostrarSinSenal);
                }
            } catch (Exception e) {
                Log.e(TAG, "NewPipe error: " + e.getMessage());
                runOnUiThread(() -> {
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

    private void mostrarSinSenal() {
        runOnUiThread(() -> {
            mostrarLoaderError();
            Toast.makeText(this, "Canal sin señal", Toast.LENGTH_SHORT).show();
            avisarAlWebView("window.gtv&&window.gtv.onNativoError&&window.gtv.onNativoError();");
        });
    }

    @JavascriptInterface
    public void resolverYouTube(String url) {
        resolverYouTubeConNewPipe(url);
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
    // Fallback: iframe con baseUrl
    // ============================================================
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
        myWebView.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null);
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
            } catch (Exception e) {
                Log.e(TAG, "Error cargando YouTube seguro: " + e.getMessage());
            }
        });
    }

    // ============================================================
    // WATCH NEXT
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
            values.put(TvContractCompat.Channels.COLUMN_DESCRIPTION, "Últimos canales que viste en GTV Play");
            values.put(TvContractCompat.Channels.COLUMN_TYPE, TvContractCompat.Channels.TYPE_PREVIEW);
            values.put(TvContractCompat.Channels.COLUMN_BROWSABLE, 0);
            values.put(TvContractCompat.Channels.COLUMN_SEARCHABLE, 0);
            values.put(TvContractCompat.Channels.COLUMN_APP_LINK_INTENT_URI, "gtvplay://play?url=");
            Uri channelUri = getContentResolver().insert(TvContractCompat.Channels.CONTENT_URI, values);
            if (channelUri == null) return -1;
            long channelId = ContentUris.parseId(channelUri);
            try { TvContractCompat.requestChannelBrowsable(this, channelId); } catch (Exception e) {}
            return channelId;
        } catch (Exception e) { return -1; }
    }

    private void agregarAlWatchNext(Canal canal) {
        if (!esTV() || watchNextChannelId == -1 || canal == null || canal.url == null) return;
        try {
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
            if (canal.logo != null && !canal.logo.isEmpty()) builder.setPosterArtUri(Uri.parse(canal.logo));
            builder.setInternalProviderId(canal.url);
            getContentResolver().insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, builder.build().toContentValues());
        } catch (Exception e) {}
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
        runOnUiThread(() -> {
            if (myWebView != null) {
                myWebView.evaluateJavascript("javascript:if(typeof window.irACanal==='function')window.irACanal(" + idx + ");", null);
            }
        });
    }

    // ============================================================
    // ACTUALIZACIONES
    // ============================================================
    private void checkForUpdates() {
        if (!hayInternet()) return;
        new Thread(() -> {
            try {
                URL url = new URL(VERSION_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("X-GTV-Origin", "gtv_app_2026_secreto_xyz789");
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
            } catch (Exception e) {}
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
            dialogSubtitle.setText("Es necesario actualizar GTV Play");
            dialogMensaje.setText("Es necesario actualizar para seguir usando la app.");
        } else {
            dialogTitle.setText("🎉 Nueva versión disponible");
            dialogSubtitle.setText("Actualización de GTV Play");
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
                conn.setRequestProperty("X-GTV-Origin", "gtv_app_2026_secreto_xyz789");
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

    // ============================================================
    // LOADERS
    // ============================================================
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
        try { androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID); } catch (Exception e) {}
        if (androidId == null || androidId.isEmpty()) androidId = "GTV_FALLBACK_" + Build.MODEL + "_" + Build.SERIAL;
        final String ALFABETO = "123456789ABCDEFGHIJKLMNPQRSTUVWXYZ";
        long hash = 0;
        for (int i = 0; i < androidId.length(); i++) hash = (hash * 31 + androidId.charAt(i)) & 0xFFFFFFFFL;
        hash = hash ^ (hash >> 16);
        hash = (hash * 0x45d9f3bL) & 0xFFFFFFFFL;
        StringBuilder sb = new StringBuilder("GTV-");
        long h = hash;
        for (int i = 0; i < 6; i++) { sb.append(ALFABETO.charAt((int)(h % ALFABETO.length()))); h = h / ALFABETO.length(); h = h ^ (h >> 3); }
        deviceIdCache = sb.toString();
        return deviceIdCache;
    }

    // ============================================================
    // REPRODUCTOR
    // ============================================================
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
        if (u.contains(".m3u8") || u.contains(".mpd") || u.contains(".mp4") || u.contains(".ts") || u.contains(".webm")) return true;
        if (u.startsWith("http")) {
            if (u.contains("/hls/") || u.contains("/live/") && !u.contains("youtube")) return true;
            if (u.contains("type=hls") || u.contains("format=hls") || u.contains("type=m3u8") || u.contains("format=m3u8")) return true;
        }
        if (u.startsWith("http") && !u.contains("youtube.com") && !u.contains("youtu.be") && !u.contains("twitch.tv") && !u.contains("kick.com") && !u.contains("ok.ru") && !u.contains("dailymotion.com") && !u.contains("tokyvideo.com") && !u.contains("facebook.com") && !u.contains("instagram.com") && !u.contains("tiktok.com") && !u.contains("vimeo.com")) return true;
        return false;
    }

    private void navegar(int delta) {
        if (canales.isEmpty()) return;
        int n = canales.size();
        indiceActual = ((indiceActual + delta) % n + n) % n;
        Canal c = canales.get(indiceActual);
        if (esCanalExoPlayer(c.url)) reproducirEnExo(c);
        else volverAlWebViewConJS("javascript:if(typeof window.irACanal==='function')window.irACanal(" + indiceActual + ");");
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
        if (c.logo != null && !c.logo.isEmpty()) Glide.with(this).load(c.logo).into(logoCanal);
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
        mostrarLoaderSintonizando();
        exoPlayer.setMediaSource(construirMediaSource(c));
        exoPlayer.prepare();
        exoPlayer.play();
        try { exoPlayer.setVolume(c.gain); } catch (Exception e) {}
        programarTimeoutCanal();
        avisarAlWebView("window.gtv&&window.gtv.onNativoIniciado&&window.gtv.onNativoIniciado();");
        handler.postDelayed(this::actualizarWatchNext, 5000);
        mostrarControles();
        playerContainer.post(() -> { aplicarRotacion(); reposicionarBotones(); });
    }

    private androidx.media3.exoplayer.source.MediaSource construirMediaSource(Canal c) {
        if (c.referer == null || c.referer.isEmpty()) {
            return new DefaultMediaSourceFactory(new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)).createMediaSource(MediaItem.fromUri(c.url));
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", c.referer);
        try { java.net.URL u = new java.net.URL(c.referer); headers.put("Origin", u.getProtocol() + "://" + u.getHost()); } catch (Exception e) {}
        return new DefaultMediaSourceFactory(new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers)).createMediaSource(MediaItem.fromUri(c.url));
    }

    private void rotar() {
        rotado = !rotado;
        getSharedPreferences("gtv_prefs", MODE_PRIVATE).edit().putBoolean("rotado", rotado).apply();
        aplicarRotacion();
        reposicionarBotones();
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
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        s.setDatabaseEnabled(true); s.setSupportZoom(false);
        s.setSaveFormData(false); s.setGeolocationEnabled(false);
        s.setTextZoom(100); s.setAllowUniversalAccessFromFileURLs(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(myWebView, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) myWebView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        myWebView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { super.onPageFinished(view, url); webViewListo = true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) {}
                    return true;
                }
                return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) {}
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
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String nombre = o.optString("nombre", o.optString("name", "Canal " + (i + 1)));
                        String referer = o.optString("referer", "");
                        float gain = (float) o.optDouble("gain", 1.0);
                        if (gain <= 0f || gain > 2f) gain = 1.0f;
                        canales.add(new Canal(o.getString("url"), nombre, o.optString("logo", ""), referer, gain));
                    }
                } catch (Exception e) {}
            });
        }
        @JavascriptInterface public void sincronizarIndice(final int i) { runOnUiThread(() -> indiceActual = i); }
        @JavascriptInterface public void detener() { runOnUiThread(() -> { try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception e) {} }); }
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
                    reproducirEnExo(new Canal(c.url, c.nombre, c.logo, refFinal, gainFinal));
                } else reproducirEnExo(new Canal(url, nombre, logo, refererFinal, gain));
            });
        }
        @JavascriptInterface public void reproducir(String url, String nombre, String numero, String logo, String referer) { reproducir(url, nombre, numero, logo, referer, 1.0f); }
        @JavascriptInterface public void reproducir(String url, String nombre, String numero, String logo) { reproducir(url, nombre, numero, logo, "", 1.0f); }
        @JavascriptInterface public boolean esExoPlayer(String url) { return esCanalExoPlayer(url); }
        @JavascriptInterface public void mostrarWebView() {
            runOnUiThread(() -> {
                enExoPlayer = false;
                try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
                playerContainer.setVisibility(View.GONE);
                myWebView.setVisibility(View.VISIBLE);
                myWebView.onResume(); myWebView.resumeTimers();
                ocultarControlesNativos();
            });
        }
        @JavascriptInterface public void cerrarPlayer() { runOnUiThread(() -> cerrarExoPlayer()); }
        @JavascriptInterface public void actualizarInfo(String nombre, String numero, String logo) {
            runOnUiThread(() -> {
                nombreCanal.setText(nombre); numeroCanal.setText(numero);
                if (logo != null && !logo.isEmpty()) Glide.with(MainActivity.this).load(logo).into(logoCanal);
            });
        }
        // NewPipe
        @JavascriptInterface public void resolverYouTube(String url) { resolverYouTubeConNewPipe(url); }
    }

    private void volverAlWebViewConJS(String js) {
        enExoPlayer = false;
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
        playerContainer.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim(); ocultarLoaders();
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume(); myWebView.resumeTimers();
        myWebView.postDelayed(() -> { try { if (js != null && !js.isEmpty()) myWebView.evaluateJavascript("javascript:(function(){try{" + js + "}catch(e){}})();", null); } catch (Exception e) {} }, 100);
    }

    private void cerrarExoPlayer() {
        if (myWebView != null) {
            myWebView.evaluateJavascript("(function(){try{return window._zappingEnCurso?'SI':'NO';}catch(e){return 'NO';}})()", value -> ejecutarCierreExo(value != null && value.contains("SI")));
            return;
        }
        ejecutarCierreExo(false);
    }

    private void ejecutarCierreExo(boolean esZapping) {
        enExoPlayer = false;
        try { exoPlayer.stop(); exoPlayer.clearMediaItems(); } catch (Exception e) {}
        playerContainer.setVisibility(View.GONE);
        ocultarControlesNativos();
        detenerLoaderAnim(); ocultarLoaders();
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        myWebView.setVisibility(View.VISIBLE);
        myWebView.onResume(); myWebView.resumeTimers();
        if (!esZapping) myWebView.postDelayed(() -> { try { myWebView.evaluateJavascript("javascript:window.volverDelExo&&window.volverDelExo();", null); } catch (Exception e) {} }, 32);
    }

    private void ocultarControlesNativos() {
        controlesVisibles = false;
        infoCanal.setVisibility(View.GONE);
        btnPrev.setVisibility(View.GONE); btnNext.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE); btnRotate.setVisibility(View.GONE);
    }

    private void mostrarControles() {
        if (!enExoPlayer) return;
        controlesVisibles = true;
        infoCanal.setVisibility(View.VISIBLE);
        btnPrev.setVisibility(View.VISIBLE); btnNext.setVisibility(View.VISIBLE);
        btnClose.setVisibility(View.VISIBLE); btnRotate.setVisibility(View.VISIBLE);
        if (ocultarControlesRunnable != null) handler.removeCallbacks(ocultarControlesRunnable);
        ocultarControlesRunnable = this::ocultarControlesNativos;
        handler.postDelayed(ocultarControlesRunnable, 5000);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (playerContainer.getVisibility() == View.VISIBLE) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT: navegar(+1); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: navegar(-1); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (enExoPlayer) { cerrarExoPlayer(); return; }
        if (myWebView != null) {
            myWebView.evaluateJavascript("(function(){try{return (window.estoyEnPlayer&&window.estoyEnPlayer())?'PLAYER':'NO';}catch(e){return 'NO';}})()", value -> {
                if (value != null && value.contains("PLAYER")) myWebView.evaluateJavascript("javascript:(function(){try{if(window.gtvStop)window.gtvStop();else if(window.stop)window.stop();}catch(e){}})()", null);
                else if (myWebView.canGoBack()) myWebView.goBack();
                else finish();
            });
            return;
        }
        super.onBackPressed();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) ocultarBarrasSistema(); }
    @Override protected void onPause() { super.onPause(); if (myWebView != null && !enExoPlayer) { myWebView.onPause(); myWebView.pauseTimers(); } }
    @Override protected void onResume() { super.onResume(); if (myWebView != null && !enExoPlayer) { myWebView.onResume(); myWebView.resumeTimers(); } }
    @Override protected void onStop() { super.onStop(); if (exoPlayer != null) try { exoPlayer.pause(); } catch (Exception e) {} }

    @Override protected void onDestroy() {
        super.onDestroy();
        detenerLoaderAnim();
        if (timeoutCanalCaido != null) { handler.removeCallbacks(timeoutCanalCaido); timeoutCanalCaido = null; }
        try { File f = new File(getCacheDir(), "update.apk"); if (f.exists()) f.delete(); } catch (Exception e) {}
        if (exoPlayer != null) { try { exoPlayer.release(); } catch (Exception e) {} exoPlayer = null; }
        if (myWebView != null) { try { myWebView.loadUrl("about:blank"); myWebView.stopLoading(); myWebView.destroy(); myWebView = null; } catch (Exception e) {} }
    }

    @SuppressLint("WrongConstant")
    private void ocultarBarrasSistema() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) { c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
