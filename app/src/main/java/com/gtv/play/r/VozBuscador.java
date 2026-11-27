package com.gtv.play.r;

import android.Manifest;
import android.app.Activity;
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
 * Con iniciarNativo(destino) los mismos estados llegan a un callback Java en vez de al HTML
 * (se usa en el reproductor nativo, donde el WebView está pausado).
 */
final class VozBuscador {

    interface Salida { void js(String codigo); }

    /** Receptor nativo de los estados (mismos nombres que gtvVoz). Siempre en el hilo principal. */
    interface Destino { void estado(String estado, String texto); }

    static final int REQ_AUDIO = 77;
    private static final String TAG = "GTV_R_VOZ";

    private final Activity act;
    private final Salida salida;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private SpeechRecognizer rec;
    private boolean pendientePermiso = false;
    private boolean escuchando = false;
    private Destino destino = null;   // null = avisar al HTML

    VozBuscador(Activity act, Salida salida) {
        this.act = act;
        this.salida = salida;
    }

    /** true si el equipo tiene micrófono y un servicio de reconocimiento de voz instalado. */
    boolean soportada() {
        try {
            return act.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
                && SpeechRecognizer.isRecognitionAvailable(act);
        } catch (Throwable t) { return false; }
    }

    /** Para mostrar en pantalla por qué no hay voz en este equipo. */
    String motivoNoSoportada() {
        try {
            if (!act.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
                return "Este equipo no tiene micrófono";
            if (!SpeechRecognizer.isRecognitionAvailable(act))
                return "Falta el servicio de voz de Google (app Google / Servicios de voz)";
        } catch (Throwable ignored) {}
        return "Este equipo no tiene reconocimiento de voz";
    }

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
        if (!soportada()) { avisar("no-soportado", ""); return; }
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendientePermiso = true;
            act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        try {
            if (rec == null) {
                rec = SpeechRecognizer.createSpeechRecognizer(act);
                rec.setRecognitionListener(listener());
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, act.getPackageName());
            escuchando = true;
            rec.startListening(i);
        } catch (Throwable t) {
            Log.e(TAG, "iniciar: " + t);
            liberar();
            avisar("error", "");
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
        if (rec != null) {
            try { rec.cancel(); rec.destroy(); } catch (Throwable ignored) {}
            rec = null;
        }
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
                String est;
                switch (err) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        est = "sin-voz"; break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        est = "red"; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        est = "permiso-denegado"; break;
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
