package com.lumina.engine.Views;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumina.engine.ui.Dialogs;
import com.lumina.engine.ui.Snack;
import com.lumina.engine.ui.Tr;
import com.lumina.engine.ui.Ui;
import com.lumina.engine.ui.blocks.BlockModel;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** New project / edit project / duplicate project dialogs (home screen). */
public final class ProjectDialogs {
    private ProjectDialogs() {}

    public interface OnProject { void on(File dir); }

    static String T(String k, String d) { return Tr.t(k, d); }

    /** Orientation chooser: two cards (portrait / landscape). Returns selected state holder. */
    private static boolean[] orientationPicker(Activity a, LinearLayout parent, boolean landscape) {
        final boolean[] land = {landscape};
        parent.addView(Ui.sectionLabel(a, T("home_orientation", "Screen orientation")));
        LinearLayout row = Ui.row(a);
        LinearLayout[] cards = new LinearLayout[2];
        for (int i = 0; i < 2; i++) {
            final boolean isLand = i == 1;
            LinearLayout c = Ui.column(a);
            c.setGravity(Gravity.CENTER);
            c.setPadding(Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 10));
            View phone = new View(a);
            phone.setBackground(Ui.rounded(0x00000000, Ui.dp(a, 5), Ui.TEXT_2, Ui.dp(a, 2)));
            c.addView(phone, new LinearLayout.LayoutParams(Ui.dp(a, isLand ? 46 : 26), Ui.dp(a, isLand ? 26 : 46)));
            TextView t = Ui.text(a, isLand ? T("home_landscape", "Landscape") : T("home_portrait", "Portrait"), 13.5f, Ui.TEXT);
            t.setPadding(0, Ui.dp(a, 8), 0, 0);
            c.addView(t);
            c.setOnClickListener(v -> {
                land[0] = isLand;
                for (int k = 0; k < 2; k++) cards[k].setSelected((k == 1) == land[0]);
            });
            c.setBackground(Ui.toggleBg(a));
            cards[i] = c;
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
            row.addView(c, lp);
        }
        cards[landscape ? 1 : 0].setSelected(true);
        parent.addView(row, Ui.matchWrap());
        return land;
    }

    /** 2D / 3D chooser of the first scene. Returns the selected state holder (true = 3D). */
    private static boolean[] typePicker(Activity a, LinearLayout parent) {
        final boolean[] three = {false};
        parent.addView(Ui.sectionLabel(a, T("s3_scene_type", "Scene type")));
        LinearLayout row = Ui.row(a);
        LinearLayout[] cards = new LinearLayout[2];
        for (int i = 0; i < 2; i++) {
            final boolean is3 = i == 1;
            LinearLayout c = Ui.column(a);
            c.setGravity(Gravity.CENTER);
            c.setPadding(Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 10));
            c.addView(Ui.smallIcon(a, is3 ? com.lumina.engine.R.drawable.ic_lumina_cube : com.lumina.engine.R.drawable.ic_lumina_square, Ui.TEXT_2, 30));
            TextView t = Ui.text(a, is3 ? T("s3_scene_3d", "3D scene") : T("s3_scene_2d", "2D scene"), 13.5f, Ui.TEXT);
            t.setPadding(0, Ui.dp(a, 8), 0, 0);
            c.addView(t);
            c.setOnClickListener(v -> {
                three[0] = is3;
                for (int k = 0; k < 2; k++) cards[k].setSelected((k == 1) == three[0]);
            });
            c.setBackground(Ui.toggleBg(a));
            cards[i] = c;
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
            row.addView(c, lp);
        }
        cards[0].setSelected(true);
        parent.addView(row, Ui.matchWrap());
        return three;
    }

    /** Stores the selected language and creates starter files visible in the Files panel. */
    static void writeProjectLanguage(File project, String language) {
        try {
            File cfg = new File(project, "configs/project.json");
            cfg.getParentFile().mkdirs();
            com.lumina.engine.Helpers.FileUtil.writeFile(cfg.getAbsolutePath(), "{\n  \"language\": \"" + language + "\",\n  \"compiler\": \"" + ("cpp".equals(language) ? "ndk-clang" : "javac") + "\"\n}\n");
            if ("cpp".equals(language)) {
                File cpp = new File(project, "cpp");
                cpp.mkdirs();
                com.lumina.engine.Helpers.FileUtil.writeFile(new File(cpp, "main.cpp").getAbsolutePath(), "#include \"lumina_game.h\"\n\nnamespace lumina {\nvoid update(float deltaSeconds) {\n    (void)deltaSeconds;\n    // Escribe aquí la lógica de tu juego\n}\n}\n");
                com.lumina.engine.Helpers.FileUtil.writeFile(new File(cpp, "main.h").getAbsolutePath(), "#pragma once\n\n// Declaraciones del juego\n");
            }
        } catch (Throwable ignored) {
        }
    }

    /** First scene of a new 3D project: 3D settings + a ground, a capsule player and a camera view. */
    static void write3D(Activity a, File project) {
        try {
            File cfg = new File(project, "configs/scene1.json");
            JsonObject o = JsonParser.parseString(BlockModel.read(cfg)).getAsJsonObject();
            o.addProperty("sceneType", "3D");
            for (String[] f : com.lumina.engine.ui.Scene3DSettings.FIELDS) if (!o.has(f[0])) o.addProperty(f[0], f[2]);
            BlockModel.write(cfg, o.toString());
            String def = asset(a, "items/default.json");
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            arr.add(item(def, asset(a, "items/obj3d.json"), "ground", new String[]{"Mesh", "CUBE", "x", "0", "y", "-0.12", "Scale X", "20", "Scale Y", "0.12", "Scale Z", "20", "Color", "#70757D", "Body", "STATIC", "Collider", "BOX"}));
            arr.add(item(def, asset(a, "items/obj3d.json"), "player", new String[]{"Mesh", "CAPSULE", "y", "1", "Color", "#A6D928", "Body", "DYNAMIC", "Collider", "CAPSULE", "Lock Rotation X", "true", "Lock Rotation Z", "true", "behaviors", "[{\"type\":\"ThirdPersonController3D\",\"params\":{\"joystick\":\"moveStick\",\"cameraMode\":\"ORBIT_TOUCH\"},\"enabled\":true}]"}));
            arr.add(item(def, asset(a, "items/camera3d.json"), "camera", new String[]{"y", "4", "z", "9", "Rotation X", "-20", "Active", "true"}));
            arr.add(item(def, asset(a, "items/joystick.json"), "moveStick", new String[]{"x", "34", "y", "34", "width", "112", "height", "112", "z", "120", "type", "UI"}));
            File scene = new File(project, "scenes/scene1");
            scene.getParentFile().mkdirs();
            BlockModel.write(scene, arr.toString());
        } catch (Exception ignored) {
        }
    }

    private static String asset(Activity a, String name) throws java.io.IOException {
        try (InputStream in = a.getAssets().open(name)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            return bo.toString("UTF-8");
        }
    }

    private static JsonObject item(String def, String other, String name, String[] kv) {
        JsonObject o = JsonParser.parseString(def.replace("--other--", "," + other).replace("ItemName", name)).getAsJsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) o.addProperty(kv[i], kv[i + 1]);
        return o;
    }

    public static void create(Activity a, File projectsDir, OnProject done) {
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), 0);
        col.addView(Ui.text(a, T("name", "Name") + " (A-Z, 0-9, _)", 12.5f, Ui.TEXT_2));
        String def = "MyGame";
        int n = 2;
        while (new File(projectsDir, def).exists()) def = "MyGame" + (n++);
        EditText name = Ui.input(a, def, "MyGame");
        name.selectAll();
        col.addView(name, Ui.matchWrap());
        boolean[] land = orientationPicker(a, col, false);
        boolean[] three = typePicker(a, col);
        final String[] projectLanguage = {"java"};
        col.addView(Ui.sectionLabel(a, T("project_language", "Lenguaje del proyecto")));
        TextView languageChoice = Ui.text(a, "Java", 14, Ui.TEXT);
        languageChoice.setGravity(Gravity.CENTER_VERTICAL);
        languageChoice.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        languageChoice.setBackground(Ui.toggleBg(a));
        languageChoice.setOnClickListener(v -> Dialogs.choose(a, T("project_language", "Lenguaje del proyecto"), new String[]{"Java", "C++"}, i -> {
            projectLanguage[0] = i == 0 ? "java" : "cpp";
            languageChoice.setText(i == 0 ? "Java" : "C++");
        }));
        col.addView(languageChoice, Ui.matchWrap());
        TextView tip = Ui.text(a, T("home_orientation_tip", "You can change it later from Edit project."), 11.5f, Ui.TEXT_3);
        tip.setPadding(0, Ui.dp(a, 8), 0, 0);
        col.addView(tip);
        final com.lumina.engine.git.ui.UploadForm upload = new com.lumina.engine.git.ui.UploadForm(a, col, def);
        name.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(android.text.Editable s) {
                upload.projectNameChanged(s.toString().trim().replace(" ", "_"));
            }
        });
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.addView(col);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(T("home_new_project", "New project"))
                .setView(scroll)
                .setPositiveButton(T("home_create", "Create"), null)
                .setNegativeButton(T("cancel", "Cancel"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim().replace(" ", "_");
            String err = Dialogs.checkName(nm);
            if (err == null && new File(projectsDir, nm).exists()) err = T("projectConflict", "A project with this name already exists");
            if (err != null) {
                name.setError(err);
                return;
            }
            String uerr = upload.validate();
            if (uerr != null) {
                Snack.show(uerr);
                return;
            }
            File dir = new File(projectsDir, nm);
            if (!dir.mkdirs()) {
                name.setError(T("ui_failed", "Failed"));
                return;
            }
            for (String p : new String[]{"sounds", "anims", "images", "files", "icon"}) new File(dir, p).mkdirs();
            writeOrientation(a, dir, land[0]);
            writeProjectLanguage(dir, projectLanguage[0]);
            if (three[0]) write3D(a, dir);
            d.dismiss();
            if (!upload.enabled()) {
                done.on(dir);
                return;
            }
            final String rn = upload.repoName(), rd = upload.description();
            final boolean priv = upload.isPrivate();
            final com.lumina.engine.git.ui.BusyDialog busy =
                    new com.lumina.engine.git.ui.BusyDialog(a, T("gh_uploading", "Uploading to GitHub"), false).show();
            com.lumina.engine.git.GitHubSession.background(() -> {
                String fail = null;
                try {
                    com.lumina.engine.git.ProjectGit.publish(a, dir, rn, rd, priv, busy);
                } catch (Exception e) {
                    fail = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                final String f = fail;
                Ui.ui(() -> {
                    busy.dismiss();
                    Snack.show(f == null ? T("gh_uploaded", "Uploaded to GitHub") : T("gh_upload_failed", "Upload failed") + ": " + f);
                    done.on(dir);
                });
            });
        }));
        d.show();
    }

    public static void edit(Activity a, File project, Runnable done) {
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), 0);
        // info
        File scenes = new File(project, "scenes");
        String[] sc = scenes.list();
        long size = size(project);
        TextView info = Ui.text(a, T("scenes", "Scenes") + ": " + (sc == null ? 0 : sc.length) + "   ·   " + human(size) + "\n"
                + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(new Date(project.lastModified())), 12.5f, Ui.TEXT_3);
        col.addView(info);
        col.addView(Ui.sectionLabel(a, T("name", "Name")));
        EditText name = Ui.input(a, project.getName(), "");
        col.addView(name, Ui.matchWrap());
        boolean[] land = orientationPicker(a, col, readLandscape(project));
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(T("home_edit_project", "Edit project"))
                .setView(col)
                .setPositiveButton(T("save", "Save"), null)
                .setNegativeButton(T("cancel", "Cancel"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim().replace(" ", "_");
            String err = Dialogs.checkName(nm);
            File target = new File(project.getParentFile(), nm);
            if (err == null && !nm.equals(project.getName()) && target.exists()) err = T("projectConflict", "A project with this name already exists");
            if (err != null) {
                name.setError(err);
                return;
            }
            File dir = project;
            if (!nm.equals(project.getName())) {
                if (!project.renameTo(target)) {
                    name.setError(T("ui_failed", "Failed"));
                    return;
                }
                dir = target;
            }
            if (land[0] != readLandscape(dir)) writeOrientation(a, dir, land[0]);
            d.dismiss();
            Snack.show(T("saved", "Saved"));
            done.run();
        }));
        d.show();
    }

    public static void duplicate(Activity a, File project, Runnable done) {
        String base = project.getName() + "_copy";
        String nm = base;
        int i = 2;
        while (new File(project.getParentFile(), nm).exists()) nm = base + (i++);
        final String fnm = nm;
        Snack.show(T("home_creating", "Creating project..."));
        new Thread(() -> {
            try {
                File copy = new File(project.getParentFile(), fnm);
                com.lumina.engine.ui.panels.FilesPanel.copy(project, copy);
                // a duplicate is a new project: it must not share the original's GitHub repository
                com.lumina.engine.git.ui.CloneDialog.deleteTree(new File(copy, ".git"));
                Ui.ui(() -> {
                    Snack.show(T("home_created", "Project created") + ": " + fnm);
                    done.run();
                });
            } catch (Exception e) {
                Snack.show(T("ui_failed", "Failed") + ": " + e);
            }
        }).start();
    }

    // ---------------------------------------------------------------- orientation (configs/scene1.json "or")

    static boolean readLandscape(File project) {
        try {
            File cfg = new File(project, "configs/scene1.json");
            if (!cfg.exists()) return true;
            JsonObject o = JsonParser.parseString(BlockModel.read(cfg)).getAsJsonObject();
            String or = o.has("or") ? o.get("or").getAsString() : "";
            return or.isEmpty() || or.equals("landscape");
        } catch (Exception e) {
            return true;
        }
    }

    static void writeOrientation(Activity a, File project, boolean landscape) {
        try {
            File cfg = new File(project, "configs/scene1.json");
            JsonObject o;
            if (cfg.exists()) o = JsonParser.parseString(BlockModel.read(cfg)).getAsJsonObject();
            else {
                try (InputStream in = a.getAssets().open("scene.json")) {
                    byte[] b = new byte[in.available()];
                    int r = in.read(b);
                    o = JsonParser.parseString(new String(b, 0, r, "UTF-8")).getAsJsonObject();
                }
            }
            o.addProperty("or", landscape ? "" : "portrait");
            // logic size follows the orientation (keep the long side)
            if (o.has("logicWidth") && o.has("logicHeight")) {
                float w = o.get("logicWidth").getAsFloat(), h = o.get("logicHeight").getAsFloat();
                o.addProperty("logicWidth", String.valueOf(Math.max(w, h)));
                o.addProperty("logicHeight", String.valueOf(Math.min(w, h)));
            }
            BlockModel.write(cfg, o.toString());
        } catch (Exception ignored) {}
    }

    static long size(File f) {
        if (f.isFile()) return f.length();
        long s = 0;
        File[] l = f.listFiles();
        if (l != null) for (File c : l) s += size(c);
        return s;
    }

    static String human(long b) {
        if (b < 1024 * 1024) return String.format(Locale.ENGLISH, "%.1f KB", b / 1024f);
        return String.format(Locale.ENGLISH, "%.1f MB", b / 1048576f);
    }
}
