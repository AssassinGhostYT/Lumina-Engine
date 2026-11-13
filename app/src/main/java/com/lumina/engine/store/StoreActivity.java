package com.lumina.engine.store;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.lumina.engine.R;
import com.lumina.engine.ui.Tr;
import com.lumina.engine.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Public Lumina Store: browse free assets published on the Worker, preview them, download the
 * .luminaasset package and copy its content/ tree into a project.
 */
public class StoreActivity extends AppCompatActivity {
    private LinearLayout list;
    private TextView status;
    private EditText search;
    private final List<StoreAsset> all = new ArrayList<>();
    private String query = "";
    private boolean offline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tr.init(this);
        setContentView(buildUi());
        Ui.insets(this, Ui.BG);
        load();
    }

    private View buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 6));
        ImageView back = Ui.iconButton(this, R.drawable.ic_lumina_arrow_back, T("back", "Back"), v -> finish());
        bar.addView(back);
        TextView title = Ui.title(this, T("store_title", "Lumina Store"));
        title.setTextSize(20);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(title, Ui.weight(1));
        ImageView refresh = Ui.iconButton(this, R.drawable.ic_lumina_refresh, T("store_refresh", "Refresh"), v -> load());
        bar.addView(refresh);
        root.addView(bar, Ui.matchWrap());

        Ui.divider(this);
        root.addView(lastDivider());

        search = Ui.input(this, "", T("store_search", "Search assets..."));
        search.setSingleLine(true);
        LinearLayout.LayoutParams slp = Ui.margins(Ui.matchWrap(), this, 14, 10, 14, 4);
        root.addView(search, slp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(java.util.Locale.US);
                render();
            }
        });

        status = Ui.text(this, "", 12.5f, Ui.TEXT_3);
        status.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        root.addView(status, Ui.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = Ui.column(this);
        list.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 24));
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View lastDivider() {
        LinearLayout holder = Ui.column(this);
        holder.addView(Ui.divider(this));
        return holder;
    }

    private void load() {
        status.setText(T("store_loading", "Loading store..."));
        list.removeAllViews();
        StoreClient.catalog(this, (assets, error) -> {
            all.clear();
            if (assets != null) all.addAll(assets);
            offline = error != null;
            if (error != null) {
                status.setText(T("store_offline", "Could not connect to the Store."));
                showError(error);
            } else if (all.isEmpty()) {
                status.setText(T("store_empty", "No assets have been published yet."));
            } else {
                status.setText(all.size() + " " + T("store_assets", "assets"));
            }
            render();
        });
    }

    private void showError(String detail) {
        LinearLayout box = Ui.column(this);
        box.setPadding(0, Ui.dp(this, 18), 0, 0);
        TextView msg = Ui.text(this, T("store_server_hint", "Check the server configured in Remote control:") + "\n" + StoreClient.base(this), 13, Ui.TEXT_2);
        box.addView(msg, Ui.matchWrap());
        TextView d = Ui.text(this, detail == null ? "" : detail, 11.5f, Ui.TEXT_3);
        d.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        box.addView(d, Ui.matchWrap());
        LinearLayout retry = Ui.button(this, R.drawable.ic_lumina_refresh, T("retry", "Retry"), true, v -> load());
        box.addView(retry);
        list.addView(box);
    }

    private void render() {
        if (list == null) return;
        if (offline) return;
        list.removeAllViews();
        int shown = 0;
        for (StoreAsset a : all) {
            if (!matches(a, query)) continue;
            list.addView(card(a));
            shown++;
        }
        if (all.isEmpty()) return;
        if (shown == 0) {
            TextView t = Ui.text(this, T("store_no_results", "No matches."), 13, Ui.TEXT_3);
            t.setPadding(0, Ui.dp(this, 18), 0, 0);
            list.addView(t);
        }
    }

    private boolean matches(StoreAsset a, String q) {
        if (q.isEmpty()) return true;
        StringBuilder b = new StringBuilder();
        b.append(a.name).append(' ').append(a.author).append(' ').append(a.type).append(' ').append(a.description);
        if (a.tags != null) for (String t : a.tags) b.append(' ').append(t);
        return b.toString().toLowerCase(java.util.Locale.US).contains(q);
    }

    private View card(StoreAsset a) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 12), Ui.BORDER, Ui.dp(this, 1)));
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        LinearLayout.LayoutParams lp = Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 10);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setOnClickListener(v -> detail(a));

        LinearLayout top = Ui.row(this);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Ui.SURFACE_2);
        int w = Ui.dp(this, 118), h = Ui.dp(this, 86);
        top.addView(img, new LinearLayout.LayoutParams(w, h));
        String preview = StoreClient.resolve(this, a.firstPreview());
        if (!preview.isEmpty()) {
            Glide.with(this).load(preview).centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(img);
        }

        LinearLayout info = Ui.column(this);
        info.setPadding(Ui.dp(this, 12), 0, 0, 0);
        TextView n = Ui.text(this, a.name == null ? "" : a.name, 16, Ui.TEXT);
        n.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        n.setMaxLines(2);
        info.addView(n, Ui.matchWrap());
        TextView meta = Ui.text(this, a.typeLabel() + "  ·  " + (a.price == null || a.price.isEmpty() ? "Free" : a.price), 12, Ui.ACCENT);
        meta.setPadding(0, Ui.dp(this, 3), 0, 0);
        info.addView(meta, Ui.matchWrap());
        String by = (a.author == null || a.author.isEmpty() ? "" : "by " + a.author) + (a.version == null || a.version.isEmpty() ? "" : "  v" + a.version);
        if (!by.isEmpty()) {
            TextView author = Ui.text(this, by, 11.5f, Ui.TEXT_3);
            author.setPadding(0, Ui.dp(this, 3), 0, 0);
            info.addView(author, Ui.matchWrap());
        }
        top.addView(info, Ui.weight(1));
        card.addView(top, Ui.matchWrap());

        if (a.description != null && !a.description.trim().isEmpty()) {
            TextView desc = Ui.text(this, a.description, 12.5f, Ui.TEXT_2);
            desc.setMaxLines(2);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            desc.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(desc, Ui.matchWrap());
        }
        return card;
    }

    private void detail(StoreAsset a) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout dv = Ui.column(this);
        dv.setBackgroundColor(Ui.BG);
        dv.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 18));

        TextView title = Ui.title(this, a.name == null ? "" : a.name);
        title.setTextSize(22);
        dv.addView(title, Ui.matchWrap());
        TextView meta = Ui.text(this, a.typeLabel() + "  ·  " + (a.price == null ? "Free" : a.price)
                + (a.sizeLabel().isEmpty() ? "" : "  ·  " + a.sizeLabel())
                + (a.version == null || a.version.isEmpty() ? "" : "  ·  v" + a.version), 12.5f, Ui.TEXT_3);
        meta.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        dv.addView(meta, Ui.matchWrap());

        if (a.previews != null && !a.previews.isEmpty()) {
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = Ui.row(this);
            for (String p : a.previews) {
                ImageView im = new ImageView(this);
                im.setScaleType(ImageView.ScaleType.CENTER_CROP);
                im.setBackgroundColor(Ui.SURFACE_2);
                String url = StoreClient.resolve(this, p);
                if (!url.isEmpty()) Glide.with(this).load(url).centerCrop().into(im);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 280), Ui.dp(this, 158));
                ilp.setMarginEnd(Ui.dp(this, 8));
                strip.addView(im, ilp);
            }
            hsv.addView(strip);
            dv.addView(hsv, Ui.matchWrap());
        }

        TextView desc = Ui.text(this, a.description == null ? "" : a.description, 14, Ui.TEXT_2);
        desc.setLineSpacing(Ui.dp(this, 3), 1f);
        desc.setPadding(0, Ui.dp(this, 14), 0, 0);
        dv.addView(desc, Ui.matchWrap());

        if (a.tags != null && !a.tags.isEmpty()) {
            TextView tags = Ui.text(this, android.text.TextUtils.join("  ·  ", a.tags), 12, Ui.ACCENT);
            tags.setPadding(0, Ui.dp(this, 10), 0, 0);
            dv.addView(tags, Ui.matchWrap());
        }
        String by = (a.author == null || a.author.isEmpty() ? "" : "by " + a.author)
                + (a.license == null || a.license.isEmpty() ? "" : "   License: " + a.license);
        if (!by.isEmpty()) {
            TextView author = Ui.text(this, by, 12, Ui.TEXT_3);
            author.setPadding(0, Ui.dp(this, 10), 0, 0);
            dv.addView(author, Ui.matchWrap());
        }

        final TextView state = Ui.text(this, "", 12.5f, Ui.TEXT_3);
        state.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        dv.addView(state, Ui.matchWrap());

        LinearLayout actions = Ui.row(this);
        boolean installed = StoreClient.isInstalled(this, a);
        LinearLayout importBtn = Ui.button(this, R.drawable.ic_lumina_download,
                T("store_import", "Import to project"), true, v -> chooseProject(a, d, state));
        actions.addView(importBtn);
        LinearLayout dlBtn = Ui.button(this, R.drawable.ic_lumina_download,
                installed ? T("store_redownload", "Re-download") : T("store_download", "Download"), false,
                v -> {
                    state.setText(T("store_downloading", "Downloading..."));
                    StoreClient.install(this, a, err -> {
                        if (err != null) state.setText(T("store_failed", "Failed") + ": " + err);
                        else {
                            state.setText(T("store_downloaded", "Downloaded. You can import it now."));
                            Toast.makeText(this, T("store_downloaded", "Downloaded."), Toast.LENGTH_SHORT).show();
                        }
                    });
                });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38));
        dlp.setMarginStart(Ui.dp(this, 8));
        actions.addView(dlBtn, dlp);
        dv.addView(actions, Ui.matchWrap());

        if (a.video != null && !a.video.trim().isEmpty()) {
            LinearLayout video = Ui.button(this, R.drawable.ic_lumina_play, T("store_video", "Watch video"), false,
                    v -> openVideo(a.video));
            video.setLayoutParams(Ui.margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38)), this, 0, 10, 0, 0));
            dv.addView(video);
        }

        LinearLayout close = Ui.button(this, 0, T("close", "Close"), false, v -> d.dismiss());
        close.setLayoutParams(Ui.margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38)), this, 0, 12, 0, 0));
        dv.addView(close);

        scroll.addView(dv);
        d.setContentView(scroll);
        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Ui.BG));
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    private void openVideo(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, T("store_no_video_app", "No app can open this video."), Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseProject(StoreAsset a, Dialog detail, TextView state) {
        File projectsDir = new File(getFilesDir(), "projects");
        //noinspection ResultOfMethodCallIgnored
        projectsDir.mkdirs();
        File[] found = projectsDir.listFiles(File::isDirectory);
        final File[] dirs = found == null ? new File[0] : found;
        Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        String[] names = new String[dirs.length + 1];
        for (int i = 0; i < dirs.length; i++) names[i] = dirs[i].getName();
        names[dirs.length] = T("store_new_project", "New project...");
        new AlertDialog.Builder(this)
                .setTitle(T("store_choose_project", "Import into project"))
                .setItems(names, (dlg, which) -> {
                    if (which == dirs.length) askNewProject(a, detail, state);
                    else runImport(a, dirs[which], detail, state);
                })
                .setNegativeButton(T("cancel", "Cancel"), null)
                .show();
    }

    private void askNewProject(StoreAsset a, Dialog detail, TextView state) {
        EditText input = Ui.input(this, suggestedName(a), "MyGame");
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(input, Ui.matchWrap());
        new AlertDialog.Builder(this)
                .setTitle(T("store_new_project", "New project"))
                .setView(box)
                .setPositiveButton(T("home_create", "Create"), (dlg, w) -> {
                    String name = input.getText().toString().trim().replace(' ', '_');
                    if (!name.matches("[A-Za-z0-9_]{1,40}")) {
                        Toast.makeText(this, T("store_bad_name", "Use letters, numbers and _ only."), Toast.LENGTH_LONG).show();
                        return;
                    }
                    File dir = new File(getFilesDir(), "projects/" + name);
                    if (dir.exists()) {
                        Toast.makeText(this, T("projectConflict", "A project with this name already exists"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!dir.mkdirs()) {
                        Toast.makeText(this, T("ui_failed", "Failed"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (String p : new String[]{"sounds", "anims", "images", "files", "icon"})
                        //noinspection ResultOfMethodCallIgnored
                        new File(dir, p).mkdirs();
                    runImport(a, dir, detail, state);
                })
                .setNegativeButton(T("cancel", "Cancel"), null)
                .show();
    }

    private String suggestedName(StoreAsset a) {
        String n = a.name == null ? "Asset" : a.name.replaceAll("[^A-Za-z0-9_]", "_");
        if (n.isEmpty()) n = "Asset";
        if (Character.isDigit(n.charAt(0))) n = "Game_" + n;
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    private void runImport(StoreAsset a, File project, Dialog detail, TextView state) {
        state.setText(T("store_preparing", "Preparing package..."));
        StoreClient.install(this, a, err -> {
            if (err != null) {
                state.setText(T("store_failed", "Failed") + ": " + err);
                return;
            }
            state.setText(T("store_importing", "Importing into ") + project.getName() + "...");
            StoreClient.importIntoProject(this, a, project, err2 -> {
                if (err2 != null) {
                    state.setText(T("store_failed", "Failed") + ": " + err2);
                    return;
                }
                detail.dismiss();
                Toast.makeText(this, T("store_imported", "Imported into") + " " + project.getName(), Toast.LENGTH_LONG).show();
            });
        });
    }

    private String T(String key, String def) {
        return Tr.t(key, def);
    }
}
