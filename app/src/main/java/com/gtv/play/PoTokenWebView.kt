package com.gtv.play

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

class PoTokenWebView private constructor(
    context: Context,
    private val generatorEmitter: SingleEmitter<PoTokenWebView>
) {
    private val webView = WebView(context)
    private val TAG = "GTV_PO_WEBVIEW"

    init {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        settings.blockNetworkLoads = true
        
        webView.addJavascriptInterface(this, "PoTokenWebView")
        
        try {
            val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            generatorEmitter.onError(e)
        }
    }

    @JavascriptInterface
    fun onInitializationFinished() {
        Log.d(TAG, "PoTokenWebView inicializado OK")
        generatorEmitter.onSuccess(this)
    }

    @JavascriptInterface
    fun onInitializationError(error: String) {
        Log.e(TAG, "Error init: $error")
        generatorEmitter.onError(Exception(error))
    }

    fun generatePoToken(identifier: String): Single<PoTokenResult> {
        return Single.create { emitter ->
            // El script de po_token.html llamará a este método de vuelta
            webView.post {
                webView.evaluateJavascript("generatePoToken('$identifier')", null)
            }
        }
    }

    companion object {
        fun newPoTokenGenerator(context: Context): Single<PoTokenWebView> {
            return Single.create { emitter ->
                PoTokenWebView(context, emitter)
            }
        }
    }
}
