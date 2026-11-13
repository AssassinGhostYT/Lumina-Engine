package com.lumina.engine.store;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumina.engine.R;
import com.lumina.engine.ui.Tr;
import com.lumina.engine.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Real publishing form: uploads a .luminaasset ZIP + previews to POST /store/assets. */
public class PublishActivity extends AppCompatActivity {
    private static final String[] TYPE_KEYS = {"model3d", "vehicle", "character", "map", "sound", "sprite2d", "prefab", "other"};
    private static final String[] TYPE_LABELS = {"3D model", "Vehicle", "Character", "Map", "Sound", "2D art", "Prefab", "Other"};

    private EditText name, id, author, version, tags, video, license, description, token;
    private TextView packageName, previewCount, typeValue, status;
    private Uri packageUri;
    private final List<Uri> previews = new ArrayList<>();
    private String type = "model3d";

    private final ActivityResultLauncher<String> packagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                packageUri = uri;
                packageName.setText(displayName(uri));
                if (id.getText().toString().trim().isEmpty()) id.setText(slug(displayName(uri)));
            });

    private final ActivityResultLauncher<String> previewPicker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null) return;
                previews.clear();
                for (Uri u : uris) {
                    if (previews.size() >= 5) break;
                    previews.add(u);
                }
                previewCount.setText(previews.size() + " / 5");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tr.init(this);
        setContentView(buildUi());
        Ui.insets(this, Ui.BG);
    }

    private View buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 6));
        bar.addView(Ui.iconButton(this, R.drawable.ic_lumina_arrow_back, T("back", "Back"), v -> finish()));
        TextView title = Ui.title(this, T("store_publish", "Publish asset"));
        title.setTextSize(20);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(title, Ui.weight(1));
        root.addView(bar, Ui.matchWrap());
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = Ui.column(this);
        form.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 28));

        form.addView(Ui.sectionLabel(this, T("store_publish_package", "Package")));
        LinearLayout pick = Ui.button(this, R.drawable.ic_lumina_upload, T("store_choose_package", "Choose .luminaasset"), false,
                v -> packagePicker.launch("*/*"));
        form.addView(pick, Ui.matchWrap());
        packageName = Ui.text(this, T("store_no_package", "No package selected"), 12, Ui.TEXT_3);
        packageName.setPadding(0, Ui.dp(this, 6), 0, 0);
        form.addView(packageName, Ui.matchWrap());

        form.addView(Ui.sectionLabel(this, T("store_publish_details", "Details")));
        name = field(form, T("store_field_name", "Name"), "Low Poly Car");
        id = field(form, T("store_field_id", "ID (lowercase, numbers, -)"), "low-poly-car");
        author = field(form, T("store_field_author", "Author"), "Your name");
        version = field(form, T("store_field_version", "Version"), "1.0");

        typeValue = Ui.text(this, TYPE_LABELS[0], 14, Ui.TEXT);
        typeValue.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        typeValue.setBackground(Ui.toggleBg(this));
        typeValue.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(T("store_field_type", "Type"))
                .setItems(TYPE_LABELS, (d, which) -> {
                    type = TYPE_KEYS[which];
                    typeValue.setText(TYPE_LABELS[which]);
                }).show());
        form.addView(label(T("store_field_type", "Type")));
        form.addView(typeValue, Ui.matchWrap());

        tags = field(form, T("store_field_tags", "Tags (comma separated)"), "3d, car, lowpoly");
        video = field(form, T("store_field_video", "Video URL (optional)"), "https://youtu.be/...");
        license = field(form, T("store_field_license", "License"), "CC0");
        form.addView(label(T("store_field_description", "Description")));
        description = Ui.input(this, "", "...");
        description.setSingleLine(false);
        description.setMinLines(4);
        description.setGravity(Gravity.TOP | Gravity.START);
        description.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(description, Ui.matchWrap());

        form.addView(Ui.sectionLabel(this, T("store_publish_previews", "Previews (max 5)")));
        LinearLayout addPrev = Ui.button(this, R.drawable.ic_lumina_image, T("store_choose_previews", "Choose images"), false,
                v -> previewPicker.launch("image/*"));
        form.addView(addPrev, Ui.matchWrap());
        previewCount = Ui.text(this, "0 / 5", 12, Ui.TEXT_3);
        previewCount.setPadding(0, Ui.dp(this, 6), 0, 0);
        form.addView(previewCount, Ui.matchWrap());

        form.addView(Ui.sectionLabel(this, T("store_publish_auth", "Permission")));
        token = field(form, T("store_field_token", "Publish token"), "");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView note = Ui.text(this, T("store_token_note", "The token is the STORE_ADMIN_TOKEN of the Worker. It is not saved."), 11.5f, Ui.TEXT_3);
        note.setPadding(0, Ui.dp(this, 6), 0, 0);
        form.addView(note, Ui.matchWrap());

        status = Ui.text(this, "", 12.5f, Ui.TEXT_3);
        status.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        form.addView(status, Ui.matchWrap());
        LinearLayout publish = Ui.button(this, R.drawable.ic_lumina_upload, T("store_publish_action", "Publish"), true, v -> publish());
        form.addView(publish, Ui.matchWrap());

        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private TextView label(String text) {
        TextView t = Ui.text(this, text, 12.5f, Ui.TEXT_2);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 3));
        return t;
    }

    private EditText field(LinearLayout form, String label, String hint) {
        form.addView(label(label));
        EditText e = Ui.input(this, "", hint);
        form.addView(e, Ui.matchWrap());
        return e;
    }

    private void publish() {
        String nm = name.getText().toString().trim();
        String assetId = id.getText().toString().trim().toLowerCase(java.util.Locale.US);
        String tk = token.getText().toString().trim();
        if (packageUri == null) {
            status.setText(T("store_need_package", "Choose a .luminaasset package first."));
            return;
        }
        if (nm.isEmpty()) {
            status.setText(T("store_need_name", "Name is required."));
            return;
        }
        if (!assetId.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            status.setText(T("store_bad_id", "ID must be lowercase letters, numbers or -."));
            return;
        }
        if (tk.isEmpty()) {
            status.setText(T("store_need_token", "Publish token is required."));
            return;
        }

        JsonObject m = new JsonObject();
        m.addProperty("id", assetId);
        m.addProperty("name", nm);
        m.addProperty("type", type);
        m.addProperty("description", description.getText().toString().trim());
        m.addProperty("author", author.getText().toString().trim());
        m.addProperty("version", version.getText().toString().trim().isEmpty() ? "1.0" : version.getText().toString().trim());
        m.addProperty("price", "Free");
        m.addProperty("video", video.getText().toString().trim());
        m.addProperty("license", license.getText().toString().trim());
        JsonArray tagArray = new JsonArray();
        for (String t : tags.getText().toString().split(",")) {
            String v = t.trim();
            if (!v.isEmpty()) tagArray.add(v);
        }
        m.add("tags", tagArray);

        status.setText(T("store_publishing", "Uploading..."));
        StoreClient.publish(this, tk, m.toString(), packageUri, previews, err -> {
            if (err != null) {
                status.setText(T("store_failed", "Failed") + ": " + err);
                return;
            }
            Toast.makeText(this, T("store_published", "Asset published."), Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        });
    }

    private String displayName(Uri uri) {
        try (Cursor cur = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && !cur.isNull(i)) {
                    String n = cur.getString(i);
                    if (n != null && !n.trim().isEmpty()) return n.trim();
                }
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last == null ? "asset.luminaasset" : last;
    }

    private String slug(String file) {
        String n = file == null ? "" : file.toLowerCase(java.util.Locale.US).replace('.', '-');
        n = n.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (n.isEmpty()) return "";
        if (Character.isDigit(n.charAt(0))) n = "asset-" + n;
        return n.length() > 64 ? n.substring(0, 64) : n;
    }

    private String T(String key, String def) {
        return Tr.t(key, def);
    }
}
