package com.lumina.engine.team;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumina.engine.team.TeamClient.Auth;
import com.lumina.engine.team.TeamClient.Chat;
import com.lumina.engine.team.TeamClient.FileState;
import com.lumina.engine.team.TeamClient.Info;
import com.lumina.engine.team.TeamClient.Member;
import com.lumina.engine.team.TeamSettings.Membership;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class TeamSession {
    public interface Listener {
        default void onState(TeamSession session) {}
        default void onMembers(TeamSession session) {}
        default void onChat(TeamSession session, Chat message) {}
        default void onText(TeamSession session, String path, String text) {}
        default void onError(TeamSession session, String message) {}
    }

    public interface Callback<T> {
        void on(T value);
    }

    private static final TeamSession INSTANCE = new TeamSession();
    private static final Gson GSON = new Gson();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final TeamClient client = new TeamClient();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, TeamCrdt> files = new ConcurrentHashMap<>();
    private final Deque<JsonObject> outbox = new ArrayDeque<>();

    private Context app;
    private String projectPath;
    private String site;
    private String state = "idle";
    private Membership membership;
    private Info info;
    private WebSocket socket;
    private int generation;

    private TeamSession() {}

    public static TeamSession get() {
        return INSTANCE;
    }

    public void init(Context context) {
        if (app == null) app = context.getApplicationContext();
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public Membership membership() {
        return membership;
    }

    public Info info() {
        return info;
    }

    public String projectPath() {
        return projectPath;
    }

    public String state() {
        return state;
    }

    public boolean hasRoom() {
        return membership != null;
    }

    public boolean canEdit() {
        return membership != null && membership.canEdit();
    }

    public boolean canManage() {
        return membership != null && membership.canManage();
    }

    public boolean isConnected() {
        return "connected".equals(state);
    }

    public List<Member> members() {
        return info == null || info.members == null ? Collections.emptyList() : info.members;
    }

    public List<Chat> chat() {
        return info == null || info.chat == null ? Collections.emptyList() : info.chat;
    }

    public void load(Context context, String path, Runnable done) {
        init(context);
        if (app == null) return;
        if (path != null && path.equals(projectPath) && membership != null) {
            if (done != null) main.post(done);
            return;
        }
        projectPath = path;
        closeSocket();
        outbox.clear();
        membership = TeamSettings.get(app, path);
        site = TeamSettings.site(app);
        files.clear();
        info = null;
        final int gen = ++generation;
        if (membership == null) {
            state = "idle";
            notifyState();
            if (done != null) main.post(done);
            return;
        }
        state = "connecting";
        notifyState();
        io.execute(() -> {
            try {
                Info loaded = client.info(membership);
                Map<String, FileState> snapshots = client.files(membership);
                main.post(() -> {
                    if (gen != generation) return;
                    info = loaded;
                    syncMembership(loaded);
                    applySnapshots(snapshots);
                    connect();
                    notifyMembers();
                    notifyState();
                    if (done != null) done.run();
                });
            } catch (IOException e) {
                main.post(() -> {
                    if (gen != generation) return;
                    state = "offline";
                    notifyState();
                    notifyError(message(e));
                    if (done != null) done.run();
                });
            }
        });
    }

    public void create(String roomName, String ownerName, Callback<String> done) {
        if (app == null) return;
        if (projectPath == null) {
            if (done != null) done.on("no_project");
            return;
        }
        closeSocket();
        outbox.clear();
        state = "connecting";
        notifyState();
        final int gen = ++generation;
        io.execute(() -> {
            try {
                Auth auth = client.create(app, roomName, ownerName);
                main.post(() -> {
                    if (gen != generation) return;
                    membership = fromAuth(auth);
                    site = TeamSettings.site(app);
                    TeamSettings.save(app, projectPath, membership);
                    state = "connected";
                    info = new Info();
                    info.room = auth.room;
                    info.me = auth.me;
                    info.members = new ArrayList<>();
                    info.members.add(auth.me);
                    info.chat = new ArrayList<>();
                    files.clear();
                    connect();
                    notifyMembers();
                    notifyState();
                    if (done != null) done.on(null);
                });
            } catch (IOException e) {
                main.post(() -> {
                    if (gen != generation) return;
                    state = "offline";
                    notifyState();
                    notifyError(message(e));
                    if (done != null) done.on(message(e));
                });
            }
        });
    }

    public void join(String roomId, String code, String name, Callback<String> done) {
        if (app == null) return;
        if (projectPath == null) {
            if (done != null) done.on("no_project");
            return;
        }
        closeSocket();
        outbox.clear();
        state = "connecting";
        notifyState();
        final int gen = ++generation;
        io.execute(() -> {
            try {
                Auth auth = client.join(app, roomId, code, name);
                main.post(() -> {
                    if (gen != generation) return;
                    membership = fromAuth(auth);
                    site = TeamSettings.site(app);
                    TeamSettings.save(app, projectPath, membership);
                    state = "connected";
                    info = new Info();
                    info.room = auth.room;
                    info.me = auth.me;
                    info.members = new ArrayList<>();
                    info.members.add(auth.me);
                    info.chat = new ArrayList<>();
                    files.clear();
                    connect();
                    notifyMembers();
                    notifyState();
                    if (done != null) done.on(null);
                });
            } catch (IOException e) {
                main.post(() -> {
                    if (gen != generation) return;
                    state = "offline";
                    notifyState();
                    notifyError(message(e));
                    if (done != null) done.on(message(e));
                });
            }
        });
    }

    public void leave() {
        generation++;
        closeSocket();
        files.clear();
        outbox.clear();
        if (app != null && projectPath != null) TeamSettings.clear(app, projectPath);
        membership = null;
        info = null;
        state = "idle";
        notifyMembers();
        notifyState();
    }

    public void reconnect() {
        if (membership == null) return;
        state = "connecting";
        notifyState();
        closeSocket();
        connect();
    }

    public void openFile(String path, String localText, Callback<String> done) {
        final Membership m = membership;
        if (m == null) {
            done.on(localText);
            return;
        }
        TeamCrdt existing = files.get(path);
        if (existing != null) {
            done.on(existing.text());
            return;
        }
        io.execute(() -> {
            try {
                Map<String, FileState> snapshots = client.files(m);
                FileState state = snapshots.get(path);
                if ((state == null || state.ops == null || state.ops.isEmpty())
                        && m.canEdit() && localText != null && !localText.isEmpty()) {
                    client.seed(m, Collections.singletonMap(path, localText));
                    snapshots = client.files(m);
                    state = snapshots.get(path);
                }
                final FileState loaded = state;
                main.post(() -> {
                    if (membership != m) {
                        done.on(localText);
                        return;
                    }
                    TeamCrdt crdt = files.get(path);
                    if (crdt == null) {
                        crdt = new TeamCrdt(site);
                        if (loaded != null && loaded.ops != null) crdt.applyAll(loaded.ops);
                        files.put(path, crdt);
                    }
                    done.on(crdt.text());
                });
            } catch (IOException e) {
                main.post(() -> done.on(localText));
            }
        });
    }

    public void localInsert(String path, int index, String text) {
        if (!canEdit() || text == null || text.isEmpty()) return;
        TeamCrdt crdt = files.get(path);
        if (crdt == null) return;
        for (TeamCrdt.Op op : crdt.insert(index, text)) sendOp(path, op);
    }

    public void localDelete(String path, int start, int end) {
        if (!canEdit()) return;
        TeamCrdt crdt = files.get(path);
        if (crdt == null) return;
        for (TeamCrdt.Op op : crdt.delete(start, end)) sendOp(path, op);
    }

    /** Undo / redo or a full replace in the editor: send only the changed middle region. */
    public void localReplace(String path, String text) {
        if (!canEdit() || text == null) return;
        TeamCrdt crdt = files.get(path);
        if (crdt == null) return;
        String old = crdt.text();
        if (old.equals(text)) return;
        int start = 0;
        int max = Math.min(old.length(), text.length());
        while (start < max && old.charAt(start) == text.charAt(start)) start++;
        int oldEnd = old.length();
        int newEnd = text.length();
        while (oldEnd > start && newEnd > start && old.charAt(oldEnd - 1) == text.charAt(newEnd - 1)) {
            oldEnd--;
            newEnd--;
        }
        if (oldEnd > start) for (TeamCrdt.Op op : crdt.delete(start, oldEnd)) sendOp(path, op);
        if (newEnd > start) for (TeamCrdt.Op op : crdt.insert(start, text.substring(start, newEnd))) sendOp(path, op);
    }

    public void sendChat(String text) {
        if (membership == null || text == null || text.trim().isEmpty()) return;
        String value = text.trim();
        if (isConnected() && socket != null) {
            JsonObject m = new JsonObject();
            m.addProperty("t", "chat");
            m.addProperty("text", value);
            m.addProperty("lang", java.util.Locale.getDefault().getLanguage());
            socket.send(m.toString());
            return;
        }
        io.execute(() -> {
            try {
                Chat chat = client.chat(membership, value, java.util.Locale.getDefault().getLanguage());
                if (chat != null) main.post(() -> addChat(chat));
            } catch (IOException e) {
                main.post(() -> notifyError(message(e)));
            }
        });
    }

    public void translate(String text, String target, Callback<String> done) {
        if (membership == null) {
            done.on(null);
            return;
        }
        io.execute(() -> {
            try {
                String translated = client.translate(membership, text, target);
                main.post(() -> done.on(translated));
            } catch (IOException e) {
                main.post(() -> {
                    notifyError(message(e));
                    done.on(null);
                });
            }
        });
    }

    public void setRole(String memberId, String role, Callback<String> done) {
        if (membership == null) return;
        io.execute(() -> {
            try {
                client.setRole(membership, memberId, role);
                main.post(() -> {
                    reloadInfo(done);
                });
            } catch (IOException e) {
                main.post(() -> {
                    notifyError(message(e));
                    if (done != null) done.on(message(e));
                });
            }
        });
    }

    public void removeMember(String memberId, Callback<String> done) {
        if (membership == null) return;
        io.execute(() -> {
            try {
                client.remove(membership, memberId);
                main.post(() -> reloadInfo(done));
            } catch (IOException e) {
                main.post(() -> {
                    notifyError(message(e));
                    if (done != null) done.on(message(e));
                });
            }
        });
    }

    public void regenerateInvite(Callback<String> done) {
        if (membership == null) return;
        io.execute(() -> {
            try {
                String code = client.regenerateInvite(membership);
                main.post(() -> {
                    if (code != null) {
                        membership.inviteCode = code;
                        TeamSettings.save(app, projectPath, membership);
                    }
                    if (done != null) done.on(code);
                });
            } catch (IOException e) {
                main.post(() -> {
                    notifyError(message(e));
                    if (done != null) done.on(null);
                });
            }
        });
    }

    public void seedProject(File root, Callback<List<String>> done) {
        if (membership == null || root == null) {
            if (done != null) done.on(Collections.emptyList());
            return;
        }
        io.execute(() -> {
            Map<String, String> files = new LinkedHashMap<>();
            collect(root, files, new long[]{3_000_000L});
            try {
                List<String> changed = client.seed(membership, files);
                main.post(() -> {
                    notifyState();
                    if (done != null) done.on(changed);
                });
            } catch (IOException e) {
                main.post(() -> {
                    notifyError(message(e));
                    if (done != null) done.on(Collections.emptyList());
                });
            }
        });
    }

    private void reloadInfo(Callback<String> done) {
        final Membership m = membership;
        if (m == null) return;
        final int gen = generation;
        io.execute(() -> {
            try {
                Info loaded = client.info(m);
                main.post(() -> {
                    if (gen != generation || membership != m) return;
                    info = loaded;
                    syncMembership(loaded);
                    notifyMembers();
                    notifyState();
                    if (done != null) done.on(null);
                });
            } catch (IOException e) {
                main.post(() -> {
                    notifyError(message(e));
                    if (done != null) done.on(message(e));
                });
            }
        });
    }

    private void connect() {
        if (membership == null) return;
        closeSocket();
        state = "connecting";
        notifyState();
        socket = client.connect(membership, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                if (webSocket != socket) return;
                main.post(() -> {
                    if (webSocket != socket) return;
                    state = "connected";
                    flush();
                    reloadFiles();
                    notifyState();
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (webSocket != socket) return;
                JsonObject json;
                try {
                    json = GSON.fromJson(text, JsonObject.class);
                } catch (Throwable ignored) {
                    return;
                }
                if (json == null) return;
                main.post(() -> {
                    if (webSocket == socket) handle(json);
                });
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (webSocket != socket) return;
                main.post(() -> {
                    if (webSocket != socket) return;
                    state = "offline";
                    notifyState();
                    notifyError(message(t));
                });
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (webSocket != socket) return;
                main.post(() -> {
                    if (webSocket != socket) return;
                    state = "offline";
                    notifyState();
                });
            }
        });
    }

    private void handle(JsonObject json) {
        String type = json.has("t") ? json.get("t").getAsString() : "";
        if ("welcome".equals(type)) {
            Info loaded = GSON.fromJson(json, Info.class);
            if (loaded != null) {
                info = loaded;
                syncMembership(loaded);
                notifyMembers();
                notifyState();
            }
            return;
        }
        if ("members".equals(type)) {
            if (info != null && json.has("members")) {
                info.members = GSON.fromJson(json.getAsJsonArray("members"), new com.google.gson.reflect.TypeToken<List<Member>>() {}.getType());
                notifyMembers();
            }
            return;
        }
        if ("chat".equals(type)) {
            if (json.has("msg")) {
                Chat chat = GSON.fromJson(json.get("msg"), Chat.class);
                if (chat != null) addChat(chat);
            }
            return;
        }
        if ("op".equals(type)) {
            String file = json.has("file") ? json.get("file").getAsString() : "";
            if (file.isEmpty() || !json.has("op")) return;
            TeamCrdt crdt = files.get(file);
            if (crdt == null) return;
            TeamCrdt.Op op = GSON.fromJson(json.get("op"), TeamCrdt.Op.class);
            if (op != null && crdt.apply(op)) notifyText(file, crdt.text());
            return;
        }
        if ("reset".equals(type)) {
            reloadFiles();
            return;
        }
        if ("error".equals(type)) {
            notifyError(json.has("message") ? json.get("message").getAsString() : "error");
        }
    }

    private void reloadFiles() {
        final Membership m = membership;
        if (m == null) return;
        final int gen = generation;
        io.execute(() -> {
            try {
                Map<String, FileState> snapshots = client.files(m);
                main.post(() -> {
                    if (gen != generation || membership != m) return;
                    applySnapshots(snapshots);
                });
            } catch (IOException ignored) {
            }
        });
    }

    private void applySnapshots(Map<String, FileState> snapshots) {
        if (snapshots == null) return;
        for (Map.Entry<String, FileState> entry : snapshots.entrySet()) {
            String path = entry.getKey();
            TeamCrdt crdt = files.get(path);
            if (crdt == null) {
                crdt = new TeamCrdt(site);
                files.put(path, crdt);
            }
            FileState state = entry.getValue();
            if (state != null && state.ops != null) crdt.applyAll(state.ops);
            notifyText(path, crdt.text());
        }
    }

    private void addChat(Chat chat) {
        if (info == null) {
            info = new Info();
            info.chat = new ArrayList<>();
        }
        if (info.chat == null) info.chat = new ArrayList<>();
        for (Chat old : info.chat) {
            if (old != null && old.id != null && old.id.equals(chat.id)) return;
        }
        info.chat.add(chat);
        while (info.chat.size() > 200) info.chat.remove(0);
        for (Listener listener : listeners) listener.onChat(this, chat);
    }

    private void sendOp(String path, TeamCrdt.Op op) {
        JsonObject m = new JsonObject();
        m.addProperty("t", "op");
        m.addProperty("file", path);
        m.addProperty("site", site);
        m.add("op", GSON.toJsonTree(op));
        outbox.addLast(m);
        flush();
    }

    private void flush() {
        WebSocket ws = socket;
        if (ws == null) return;
        while (!outbox.isEmpty()) {
            if (!ws.send(outbox.peekFirst().toString())) return;
            outbox.removeFirst();
        }
    }

    private void syncMembership(Info loaded) {
        if (loaded == null || membership == null) return;
        if (loaded.me != null) {
            membership.memberId = loaded.me.id;
            membership.name = loaded.me.name;
            membership.role = loaded.me.role;
        }
        if (loaded.room != null) {
            membership.roomName = loaded.room.name;
            membership.ownerId = loaded.room.ownerId;
            if (loaded.room.inviteCode != null) membership.inviteCode = loaded.room.inviteCode;
        }
        if (app != null && projectPath != null) TeamSettings.save(app, projectPath, membership);
    }

    private Membership fromAuth(Auth auth) {
        Membership m = new Membership();
        m.server = com.lumina.engine.remote.RemoteSettings.server(app);
        m.roomId = auth.room == null ? "" : auth.room.id;
        m.roomName = auth.room == null ? "" : auth.room.name;
        m.ownerId = auth.room == null ? "" : auth.room.ownerId;
        m.inviteCode = auth.room == null ? "" : auth.room.inviteCode;
        m.token = auth.token;
        m.memberId = auth.me == null ? "" : auth.me.id;
        m.name = auth.me == null ? "" : auth.me.name;
        m.role = auth.me == null ? "" : auth.me.role;
        m.projectPath = projectPath;
        return m;
    }

    private void closeSocket() {
        WebSocket old = socket;
        socket = null;
        if (old != null) {
            try {
                old.close(1000, "bye");
            } catch (Throwable ignored) {
            }
        }
    }

    private void notifyState() {
        for (Listener listener : listeners) listener.onState(this);
    }

    private void notifyMembers() {
        for (Listener listener : listeners) listener.onMembers(this);
    }

    private void notifyText(String path, String text) {
        for (Listener listener : listeners) listener.onText(this, path, text);
    }

    private void notifyError(String message) {
        for (Listener listener : listeners) listener.onError(this, message);
    }

    private String message(Throwable error) {
        if (error == null) return "error";
        String text = error.getMessage();
        return text == null || text.isEmpty() ? error.getClass().getSimpleName() : text;
    }

    private void collect(File file, Map<String, String> out, long[] budget) {
        if (file == null || budget[0] <= 0) return;
        if (file.isDirectory()) {
            String name = file.getName();
            if (name.startsWith(".") || "build".equals(name) || "bin".equals(name) || "obj".equals(name)
                    || "node_modules".equals(name)) return;
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) collect(child, out, budget);
            return;
        }
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (!(name.endsWith(".java") || name.endsWith(".cs") || name.endsWith(".json") || name.endsWith(".txt")
                || name.endsWith(".xml") || name.endsWith(".md") || name.endsWith(".cpp") || name.endsWith(".h")
                || name.endsWith(".hpp") || name.endsWith(".glsl") || name.endsWith(".frag") || name.endsWith(".vert")
                || name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".gradle"))) return;
        if (file.length() > 400_000 || file.length() > budget[0]) return;
        try {
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            if (text.indexOf('\0') >= 0) return;
            String root = projectPath == null ? "" : new File(projectPath).getAbsolutePath();
            String full = file.getAbsolutePath();
            String relative = full.startsWith(root + File.separator) ? full.substring(root.length() + 1) : file.getName();
            relative = relative.replace(File.separatorChar, '/');
            out.put(relative, text);
            budget[0] -= text.length();
        } catch (Throwable ignored) {
        }
    }
}
