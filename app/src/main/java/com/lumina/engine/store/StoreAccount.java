package com.lumina.engine.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Email/password store account. The Worker returns an API key that signs publish requests. */
public final class StoreAccount {
    public interface AuthDone {
        void onDone(String error);
    }

    private static final String PREFS = "lumina_store_account";
    private static final String KEY_API = "api_key";
    private static final String KEY_EMAIL = "email";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private StoreAccount() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean loggedIn(Context c) {
        return !apiKey(c).isEmpty();
    }

    public static String apiKey(Context c) {
        return prefs(c).getString(KEY_API, "");
    }

    public static String email(Context c) {
        return prefs(c).getString(KEY_EMAIL, "");
    }

    public static void logout(Context c) {
        prefs(c).edit().remove(KEY_API).remove(KEY_EMAIL).apply();
    }

    public static void login(Context c, String email, String password, AuthDone done) {
        auth(c, "/auth/login", email, password, done);
    }

    public static void register(Context c, String email, String password, AuthDone done) {
        auth(c, "/auth/register", email, password, done);
    }

    private static void auth(Context c, String path, String email, String password, AuthDone done) {
        final Context app = c.getApplicationContext();
        final String em = email == null ? "" : email.trim();
        IO.execute(() -> {
            String error = null;
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", em);
                body.addProperty("password", password == null ? "" : password);
                Request req = new Request.Builder()
                        .url(StoreClient.base(app) + path)
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (Response res = HTTP.newCall(req).execute()) {
                    String text = res.body() == null ? "" : res.body().string();
                    if (!res.isSuccessful()) throw new IOException(message(text, res.code()));
                    JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                    String key = obj.has("api_key") ? obj.get("api_key").getAsString() : "";
                    String mail = obj.has("email") ? obj.get("email").getAsString() : em;
                    if (key.isEmpty()) throw new IOException("invalid server response");
                    prefs(app).edit().putString(KEY_API, key).putString(KEY_EMAIL, mail).apply();
                }
            } catch (Exception | Error e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String err = error;
            MAIN.post(() -> done.onDone(err));
        });
    }

    private static String message(String text, int code) {
        try {
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            if (obj.has("error") && !obj.get("error").isJsonNull()) return obj.get("error").getAsString();
        } catch (Exception ignored) {
        }
        return "HTTP " + code + (text == null || text.trim().isEmpty() ? "" : ": " + text.trim());
    }
}
