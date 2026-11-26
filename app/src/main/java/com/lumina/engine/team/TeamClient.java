package com.lumina.engine.team;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.lumina.engine.remote.RemoteSettings;
import com.lumina.engine.team.TeamSettings.Membership;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class TeamClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final Type FILES = new TypeToken<Map<String, FileState>>() {}.getType();

    public static final class Room {
        public String id;
        public String name;
        public String ownerId;
        public String inviteCode;
        public long createdAt;
    }

    public static final class Member {
        public String id;
        public String name;
        public String role;
        public boolean online;
        public long joinedAt;
    }

    public static final class Chat {
        public String id;
        public String memberId;
        public String name;
        public String text;
        public String lang;
        public long at;
    }

    public static final class FileState {
        public int rev;
        public List<TeamCrdt.Op> ops = new ArrayList<>();
    }

    public static final class Auth {
        public Room room;
        public Member me;
        public String token;
    }

    public static final class Info {
        public Room room;
        public Member me;
        public List<Member> members = new ArrayList<>();
        public List<Chat> chat = new ArrayList<>();
        public List<String> voice = new ArrayList<>();
        public List<String> voiceMuted = new ArrayList<>();
    }

    private final OkHttpClient http;

    public TeamClient() {
        http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public Auth create(Context c, String roomName, String ownerName) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", roomName);
        body.addProperty("ownerName", ownerName);
        JsonObject result = post(roomsBase(c), null, body);
        return GSON.fromJson(result, Auth.class);
    }

    public Auth join(Context c, String roomId, String code, String name) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        body.addProperty("name", name);
        JsonObject result = post(roomsBase(c) + "/" + roomId + "/join", null, body);
        return GSON.fromJson(result, Auth.class);
    }

    public Info info(Membership m) throws IOException {
        return GSON.fromJson(get(base(m) + "/" + m.roomId, m.token), Info.class);
    }

    public Map<String, FileState> files(Membership m) throws IOException {
        JsonObject result = get(base(m) + "/" + m.roomId + "/files", m.token);
        JsonObject files = result.has("files") && result.get("files").isJsonObject()
                ? result.getAsJsonObject("files") : new JsonObject();
        Map<String, FileState> out = GSON.fromJson(files, FILES);
        return out == null ? new java.util.LinkedHashMap<>() : out;
    }

    public List<String> seed(Membership m, Map<String, String> files) throws IOException {
        JsonObject body = new JsonObject();
        body.add("files", GSON.toJsonTree(files));
        JsonObject result = post(base(m) + "/" + m.roomId + "/seed", m.token, body);
        List<String> out = new ArrayList<>();
        if (result.has("seeded")) {
            for (com.google.gson.JsonElement e : result.getAsJsonArray("seeded")) out.add(e.getAsString());
        }
        return out;
    }

    public Chat chat(Membership m, String text, String lang) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("lang", lang);
        JsonObject result = post(base(m) + "/" + m.roomId + "/chat", m.token, body);
        return result.has("msg") ? GSON.fromJson(result.get("msg"), Chat.class) : null;
    }

    public String translate(Membership m, String text, String source, String target) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("source", source == null ? "" : source);
        body.addProperty("target", target);
        JsonObject result = post(base(m) + "/" + m.roomId + "/translate", m.token, body);
        if (result.has("error")) return null;
        return result.has("text") ? result.get("text").getAsString() : null;
    }

    /** Fallback used when the deployed server does not have the translation route yet. */
    public String translatePublic(String text, String source, String target) throws IOException {
        String src = source == null ? "" : source.toLowerCase(Locale.ROOT);
        String dst = target == null ? "" : target.toLowerCase(Locale.ROOT);
        if (!src.matches("[a-z]{2}")) src = "en";
        if (!dst.matches("[a-z]{2}") || src.equals(dst)) return text;
        HttpUrl base = HttpUrl.parse("https://api.mymemory.translated.net/get");
        if (base == null) return null;
        HttpUrl url = base.newBuilder()
                .addQueryParameter("q", text)
                .addQueryParameter("langpair", src + "|" + dst)
                .build();
        JsonObject result = execute(new Request.Builder().url(url).get().build());
        if (result.has("responseStatus") && !"200".equals(result.get("responseStatus").getAsString())) return null;
        if (!result.has("responseData") || !result.get("responseData").isJsonObject()) return null;
        JsonObject data = result.getAsJsonObject("responseData");
        String translated = data.has("translatedText") ? data.get("translatedText").getAsString() : "";
        translated = translated == null ? "" : translated.trim();
        if (translated.isEmpty() || translated.toUpperCase(Locale.ROOT).contains("MYMEMORY WARNING")) return null;
        return translated;
    }

    public Member setRole(Membership m, String memberId, String role) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("role", role);
        JsonObject result = post(base(m) + "/" + m.roomId + "/members/" + memberId, m.token, body);
        return result.has("member") ? GSON.fromJson(result.get("member"), Member.class) : null;
    }

    public void remove(Membership m, String memberId) throws IOException {
        delete(base(m) + "/" + m.roomId + "/members/" + memberId, m.token);
    }

    public String regenerateInvite(Membership m) throws IOException {
        JsonObject result = post(base(m) + "/" + m.roomId + "/invite", m.token, new JsonObject());
        return result.has("inviteCode") ? result.get("inviteCode").getAsString() : null;
    }

    public WebSocket connect(Membership m, WebSocketListener listener) {
        Request request = new Request.Builder()
                .url(wsBase(m))
                .addHeader("Authorization", "Bearer " + m.token)
                .build();
        return http.newWebSocket(request, listener);
    }

    private String roomsBase(Context c) {
        return RemoteSettings.server(c) + "/v1/rooms";
    }

    private String base(Membership m) {
        String server = m.server == null || m.server.isEmpty() ? RemoteSettings.DEFAULT_SERVER : m.server;
        return server + "/v1/rooms";
    }

    private String wsBase(Membership m) {
        String server = m.server == null || m.server.isEmpty() ? RemoteSettings.DEFAULT_SERVER : m.server;
        String ws = server.startsWith("https://") ? "wss://" + server.substring(8)
                : server.startsWith("http://") ? "ws://" + server.substring(7) : server;
        return ws + "/v1/rooms/" + m.roomId + "/ws";
    }

    private JsonObject post(String url, String token, JsonObject body) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).post(RequestBody.create(body.toString(), JSON));
        if (token != null && !token.isEmpty()) builder.addHeader("Authorization", "Bearer " + token);
        return execute(builder.build());
    }

    private JsonObject get(String url, String token) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (token != null && !token.isEmpty()) builder.addHeader("Authorization", "Bearer " + token);
        return execute(builder.build());
    }

    private void delete(String url, String token) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).delete();
        if (token != null && !token.isEmpty()) builder.addHeader("Authorization", "Bearer " + token);
        execute(builder.build());
    }

    private JsonObject execute(Request request) throws IOException {
        try (Response response = http.newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            JsonObject json = null;
            try {
                json = raw.isEmpty() ? new JsonObject() : GSON.fromJson(raw, JsonObject.class);
            } catch (Throwable ignored) {
            }
            if (!response.isSuccessful()) {
                String error = json != null && json.has("error") ? json.get("error").getAsString() : "HTTP " + response.code();
                throw new IOException(error);
            }
            return json == null ? new JsonObject() : json;
        }
    }
}
