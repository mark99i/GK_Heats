package ru.mark99.gk_heats;

import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GKClient {
    private static final long TIMEOUT_MS = 1000L;

    private static final RequestBody EMPTY_BODY = RequestBody.create(new byte[0], null);

    private final String baseUrl;
    private final OkHttpClient http;

    public GKClient(String host, String username, String password) {
        this.baseUrl = "http://" + host;
        final String credentials = Credentials.basic(username, password);
        this.http = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("Authorization", credentials)
                        .build()))
                .build();
    }

    public StatusResponse fetchStatus() throws IOException {
        final String path = "/GP_update?modeWarmLeft,modeWarmRight=";
        final Request request = new Request.Builder().url(baseUrl + path).get().build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " на " + path);
            }
            final ResponseBody body = response.body();
            final var b = body.bytes();
            final var state = new StatusResponse();
            state.left = convStateInt(b[0] - 48);
            // b[1] delimiter
            state.right = convStateInt(b[2] - 48);
            return state;
        }
    }

    public void setMode(int pos, int mode) throws IOException {
        mode = convStateInt(mode);

        final String path = "/GP_click?" + (pos == 1 ? "modeWarmLeft" : "modeWarmRight") + '=' + mode;
        final Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(EMPTY_BODY)
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " на " + path);
            }
        }
    }

    private int convStateInt(int v) {
        return switch (v) {
            case 1 -> 3;
            case 2 -> 2;
            case 3 -> 1;
            default -> 0;
        };
    }

    public static class StatusResponse {
        int left;
        int right;
    }
}
