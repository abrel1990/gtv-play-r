package com.gtv.play;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class DownloaderImpl extends Downloader {
    private static DownloaderImpl instance;
    private final OkHttpClient client = new OkHttpClient();

    public static synchronized DownloaderImpl getInstance() {
        if (instance == null) instance = new DownloaderImpl();
        return instance;
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        String method = request.httpMethod();
        String url = request.url();
        Map<String, List<String>> headers = request.headers();
        byte[] dataToSend = request.dataToSend();

        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url);
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                for (String value : entry.getValue()) {
                    builder.addHeader(entry.getKey(), value);
                }
            }
        }

        RequestBody body = null;
        if (dataToSend != null && dataToSend.length > 0) {
            body = RequestBody.create(dataToSend, null);
        }
        builder.method(method, body);

        okhttp3.Response response = client.newCall(builder.build()).execute();
        ResponseBody responseBody = response.body();
        String responseBodyString = responseBody != null ? responseBody.string() : "";
        if (responseBody != null) responseBody.close();

        return new Response(
            response.code(),
            response.message(),
            response.headers().toMultimap(),
            responseBodyString,
            response.request().url().toString()
        );
    }
}
