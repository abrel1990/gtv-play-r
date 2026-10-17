package com.gtv.play

import android.content.Context
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

class PoTokenProviderImpl(private val context: Context) : PoTokenProvider {
    
    private var generator: PoTokenWebView? = null

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        // Si no hay generador, lo creamos de forma síncrona (bloqueante) para simplificar
        if (generator == null) {
            generator = PoTokenWebView.newPoTokenGenerator(context).blockingGet()
        }
        return generator?.generatePoToken(videoId)?.blockingGet()
    }

    // Los otros métodos (Android, iOS, Embed) devuelven null porque usamos el cliente WEB
    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null
    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null
    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null
}
