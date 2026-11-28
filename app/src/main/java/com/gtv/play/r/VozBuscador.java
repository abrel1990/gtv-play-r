package com.gtv.play.r;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Búsqueda por voz para el buscador del index.
 *
 * Es una clase independiente a propósito: se puede pegar en cualquier versión de
 * MainActivity con 5 ganchos mínimos (ver MainActivity: busca "voz.").
 *
 * Avisa al HTML con:  window.gtvVoz(estado, texto)
 *   estados: escuchando | parcial | final | sin-voz | red | permiso-denegado |
 *            no-soportado | ocupado | error
 *
 * Cadena de intentos (para que ande en TV, TV Box y celulares distintos):
 *   1) reconocedor por defecto del sistema, idioma es-AR
 *   2) si da "idioma no soportado" (error 12/13): idioma del sistema, después es-ES, es-US
 *   3) si el servicio por defecto falla o no existe: el servicio de voz de Google en forma explícita
 *   4) último recurso: la pantalla de voz del sistema (RecognizerIntent), que también tapa
 *      problemas de permiso del reconocedor
 *
 * Con iniciarNativo(destino) los mismos estados llegan a un callback Java en vez de al HTML
 * (se usa en el reproductor nativo, donde el WebView está pausado).
 */
final class VozBuscador {

    interface Salida { void js(String codigo); }

    /** Receptor nativo de los estados (mismos nombres que gtvVoz). Siempre en el hilo principal. */
    interface Destino { void estado(String estado, String texto); }

    static final int REQ_AUDIO = 77;
    static final int REQ_INTENT = 78;
    private static final String[] IDIOMAS = {"es-AR", "", "es-ES", "es-US"};   // "" = idioma del sistema
    private static final ComponentName GOOGLE = new ComponentName("com.google.android.googlequicksearchbox",
        "com.google.android.voicesearch.serviceapi.GoogleRecognitionService");
    private static final String TAG = "GTV_R_VOZ";

    private final Activity act;
    private final Salida salida;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private SpeechRecognizer rec;
    private boolean pendientePermiso = false;
    private boolean escuchando = false;
    private Destino destino = null;   // null = avisar al HTML
    private int idioma = 0, servicio = 0, pasos = 0;   // servicio 0 = por defecto, 1 = Google explícito
    private boolean externa = false;                   // la pantalla de voz del sistema está abierta

    VozBuscador(Activity act, Salida salida) {
        this.act = act;
        this.salida = salida;
    }

    /**
     * Siempre true: muchos TV / TV Box dicen "no hay micrófono" o "no hay reconocedor" aunque el control
     * remoto sí tenga voz. Mejor intentar y que la cadena de intentos decida.
     */
    boolean soportada() { return true; }

    /** Para mostrar en pantalla cuando se agotaron todos los intentos. */
    String motivoNoSoportada() {
        return "No se pudo iniciar la voz en este equipo. Probá actualizar/activar la app Google";
    }

    /** true mientras está abierta la pantalla de voz del sistema (la activity pasa a segundo plano). */
    boolean externaPendiente() { return externa; }

    private void avisar(String estado, String texto) {
        Destino d = destino;
        if (d != null) { d.estado(estado, texto == null ? "" : texto); return; }
        salida.js("window.gtvVoz&&window.gtvVoz(" + JSONObject.quote(estado) + ","
            + JSONObject.quote(texto == null ? "" : texto) + ");");
    }

    /** Escucha y avisa al HTML (buscador del index). Hilo principal. */
    void iniciar() { destino = null; arrancar(); }

    /** Escucha y avisa a un receptor Java (reproductor nativo). Hilo principal. */
    void iniciarNativo(Destino d) { destino = d; arrancar(); }

    /** true mientras el micrófono está escuchando. */
    boolean activa() { return escuchando; }

    private void arrancar() {
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendientePermiso = true;
            act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        idioma = 0; servicio = 0; pasos = 0; externa = false;
        liberarRec();
        lanzar();
    }

    private void lanzar() {
        try {
            if (rec == null) {
                if (servicio == 0) {
                    rec = SpeechRecognizer.createSpeechRecognizer(act);
                } else {
                    try { rec = SpeechRecognizer.createSpeechRecognizer(act, GOOGLE); } catch (Throwable t) { rec = null; }
                }
                if (rec == null) { siguiente(-1); return; }
                rec.setRecognitionListener(listener());
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            if (!IDIOMAS[idioma].isEmpty()) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, IDIOMAS[idioma]);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, act.getPackageName());
            escuchando = true;
            rec.startListening(i);
        } catch (Throwable t) {
            Log.e(TAG, "lanzar: " + t);
            escuchando = false;
            siguiente(-1);
        }
    }

    private static boolean reintentable(int err) {
        // 3 audio, 4 servidor, 5 cliente, 9 permisos del reconocedor, 10/11 servicio, 12/13 idioma, -1 no se pudo crear
        return err == -1 || err == 3 || err == 4 || err == 5 || err == 9 || err == 10 || err == 11 || err == 12 || err == 13;
    }

    /** Pasa a la siguiente opción de la cadena. Devuelve false si no queda ninguna. */
    private boolean siguiente(int err) {
        liberarRec();
        if (++pasos > 10) return false;
        if ((err == 12 || err == 13) && idioma < IDIOMAS.length - 1) { idioma++; lanzar(); return true; }
        if (servicio == 0) { servicio = 1; idioma = 0; lanzar(); return true; }
        if (!externa && lanzarExterna()) return true;
        avisar("error", err < 0 ? "no se pudo iniciar" : "error " + err);
        return false;
    }

    /** Pantalla de voz del sistema. El resultado vuelve por onResultado(). */
    private boolean lanzarExterna() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Decí el canal");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            externa = true;
            act.startActivityForResult(i, REQ_INTENT);
            avisar("escuchando", "");
            return true;
        } catch (Throwable t) {
            externa = false;
            Log.e(TAG, "externa: " + t);
            return false;
        }
    }

    /** Llamar desde onActivityResult. Devuelve true si era de la voz. */
    boolean onResultado(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_INTENT) return false;
        externa = false;
        String t = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> l = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (l != null && !l.isEmpty()) t = l.get(0);
        }
        if (t != null && !t.trim().isEmpty()) avisar("final", t);
        else avisar("sin-voz", "");
        return true;
    }

    private void liberarRec() {
        if (rec != null) {
            try { rec.cancel(); rec.destroy(); } catch (Throwable ignored) {}
            rec = null;
        }
    }

    /** Corta la escucha (por ejemplo al cerrar el buscador). Hilo principal. */
    void detener() {
        if (rec != null && escuchando) { try { rec.cancel(); } catch (Throwable ignored) {} }
        escuchando = false;
    }

    /** Libera el reconocedor. Llamar en onStop y onDestroy. Hilo principal. */
    void liberar() {
        escuchando = false;
        liberarRec();
    }

    /** Llamar desde onRequestPermissionsResult. Devuelve true si el pedido era de la voz. */
    boolean onPermiso(int requestCode, int[] resultados) {
        if (requestCode != REQ_AUDIO) return false;
        boolean ok = resultados != null && resultados.length > 0 && resultados[0] == PackageManager.PERMISSION_GRANTED;
        boolean pendiente = pendientePermiso;
        pendientePermiso = false;
        if (ok && pendiente) arrancar();
        else if (!ok) avisar("permiso-denegado", "");
        return true;
    }

    private RecognitionListener listener() {
        return new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { avisar("escuchando", ""); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int t, Bundle p) {}

            @Override public void onPartialResults(Bundle r) {
                String t = mejor(r);
                if (t != null) avisar("parcial", t);
            }

            @Override public void onResults(Bundle r) {
                escuchando = false;
                String t = mejor(r);
                if (t != null && !t.trim().isEmpty()) avisar("final", t);
                else avisar("sin-voz", "");
            }

            @Override public void onError(int err) {
                escuchando = false;
                if (reintentable(err)) {
                    final int e = err;
                    ui.post(() -> { if (!siguiente(e)) { /* ya avisó el error */ } });
                    return;
                }
                String est;
                switch (err) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        est = "sin-voz"; break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        est = "red"; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        est = "ocupado";
                        ui.post(VozBuscador.this::liberar);
                        break;
                    default:
                        est = "error";
                }
                avisar(est, "error".equals(est) ? "error " + err : "");
            }
        };
    }

    private static String mejor(Bundle r) {
        if (r == null) return null;
        ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (l != null && !l.isEmpty()) ? l.get(0) : null;
    }
}
