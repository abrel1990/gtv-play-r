package com.gtv.play.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

object PoTokenProviderImpl : PoTokenProvider {
    val TAG = PoTokenProviderImpl::class.simpleName

    private var appContext: Context? = null
    private var webViewBadImpl = false

    private object WebPoTokenGenLock
    private var webPoTokenVisitorData: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenGenerator? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (appContext == null || webViewBadImpl) {
            return null
        }

        return try {
            getWebClientPoToken(videoId = videoId, forceRecreate = false)
        } catch (e: Throwable) {
            Log.e(TAG, "PoToken error, returning null", e)
            webViewBadImpl = true
            null
        }
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? {
        return null
    }

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? {
        return null
    }

    override fun getIosClientPoToken(videoId: String): PoTokenResult? {
        return null
    }

    private fun getWebClientPoToken(videoId: String, forceRecreate: Boolean): PoTokenResult {
        data class Quadruple<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

        val (poTokenGenerator, visitorData, streamingPot, hasBeenRecreated) =
            synchronized(WebPoTokenGenLock) {
                val shouldRecreate = true
                if (shouldRecreate) {
                    val innertubeClientRequestInfo = InnertubeClientRequestInfo.ofWebClient()
                    innertubeClientRequestInfo.clientInfo.clientVersion =
                        YoutubeParsingHelper.getClientVersion()

                    webPoTokenVisitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                        innertubeClientRequestInfo,
                        NewPipe.getPreferredLocalization(),
                        NewPipe.getPreferredContentCountry(),
                        YoutubeParsingHelper.getYouTubeHeaders(),
                        YoutubeParsingHelper.YOUTUBEI_V1_URL,
                        null,
                        false
                    )
                    webPoTokenGenerator?.let { Handler(Looper.getMainLooper()).post { it.close() } }

                    val ctx = appContext ?: throw IllegalStateException("PoTokenProviderImpl not initialized")
                    webPoTokenGenerator = PoTokenWebView
                        .newPoTokenGenerator(ctx).blockingGet()

                    webPoTokenStreamingPot = webPoTokenGenerator!!
                        .generatePoToken(webPoTokenVisitorData!!).blockingGet()
                }

                return@synchronized Quadruple(
                    webPoTokenGenerator!!,
                    webPoTokenVisitorData!!,
                    webPoTokenStreamingPot!!,
                    shouldRecreate
                )
            }

        // ✅ FORZAR recreación siempre para evitar tokens caducados
val playerPot = try {
    poTokenGenerator.generatePoToken(videoId).blockingGet()
} catch (throwable: Throwable) {
    Log.e(TAG, "Failed to obtain poToken, recreating", throwable)
    return getWebClientPoToken(videoId = videoId, forceRecreate = true)
}

        return PoTokenResult(visitorData, playerPot, streamingPot)
    }
}
