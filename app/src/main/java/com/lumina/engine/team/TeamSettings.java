package com.lumina.engine.team;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lumina.engine.remote.RemoteSettings;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class TeamSettings {
    private static final String PREFS = "nebula_team";
    private static final Gson GSON = new Gson();
    private static final Type LIST = new TypeToken<ArrayList<Membership>>() {}.getType();

    private TeamSettings() {}

    public static final class Membership {
        public String server;
        public String roomId;
        public String token;
        public String memberId;
        public String name;
        public String role;
        public String roomName;
        public String inviteCode;
        public String ownerId;
        public String projectPath;

        public boolean canEdit() {
            return "owner".equals(role) || "admin".equals(role) || "editor".equals(role);
        }

        public boolean canManage() {
            return "owner".equals(role) || "admin".equals(role);
        }

        public String inviteText() {
            if (roomId == null || inviteCode == null) return "";
            return roomId + "#" + inviteCode;
        }
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String projectPath) {
        return "team_" + Integer.toHexString(projectPath == null ? 0 : projectPath.hashCode());
    }

    public static Membership get(Context c, String projectPath) {
        String raw = p(c).getString(key(projectPath), "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            return GSON.fromJson(raw, Membership.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void save(Context c, String projectPath, Membership m) {
        if (m == null) return;
        m.projectPath = projectPath;
        p(c).edit().putString(key(projectPath), GSON.toJson(m)).apply();
        addShared(c, m);
    }

    public static void clear(Context c, String projectPath) {
        Membership m = get(c, projectPath);
        p(c).edit().remove(key(projectPath)).apply();
        if (m != null && m.roomId != null) removeShared(c, m.roomId);
    }

    public static List<Membership> shared(Context c) {
        String raw = p(c).getString("shared", "[]");
        try {
            List<Membership> list = GSON.fromJson(raw, LIST);
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    public static void addShared(Context c, Membership m) {
        if (m == null || m.roomId == null) return;
        List<Membership> list = shared(c);
        for (Iterator<Membership> it = list.iterator(); it.hasNext(); ) {
            Membership old = it.next();
            if (m.roomId.equals(old.roomId)) it.remove();
        }
        list.add(0, m);
        p(c).edit().putString("shared", GSON.toJson(list)).apply();
    }

    public static void removeShared(Context c, String roomId) {
        List<Membership> list = shared(c);
        boolean changed = false;
        for (Iterator<Membership> it = list.iterator(); it.hasNext(); ) {
            if (roomId != null && roomId.equals(it.next().roomId)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) p(c).edit().putString("shared", GSON.toJson(list)).apply();
    }

    public static String site(Context c) {
        String site = p(c).getString("site", "");
        if (site == null || site.length() < 6) {
            site = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            p(c).edit().putString("site", site).apply();
        }
        return site;
    }

    public static String roomsBase(Context c) {
        return RemoteSettings.server(c) + "/v1/rooms";
    }

    public static String wsBase(Context c, String roomId) {
        String base = RemoteSettings.server(c);
        String ws = base.startsWith("https://") ? "wss://" + base.substring(8)
                : base.startsWith("http://") ? "ws://" + base.substring(7) : base;
        return ws + "/v1/rooms/" + roomId + "/ws";
    }

    public static String roomLink(Membership m) {
        if (m == null) return "";
        return m.inviteText();
    }
}
