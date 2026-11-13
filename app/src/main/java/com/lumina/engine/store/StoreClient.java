package com.lumina.engine.store;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.lumina.engine.remote.RemoteSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

/** Talks to the public Store API served by the same Worker as the remote control. */
public final class StoreClient {
    private StoreClient() {}

    public interface CatalogCallback {
        void onResult(List<StoreAsset> assets, String error);
    }

    public interface Done {
        void onDone(String error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build();
    private static final Gson GSON = new Gson();

    private static final class Catalog {
        List<StoreAsset> assets = new ArrayList<>();
    }

    public static File storeDir(Context c) {
        File d = new File(c.getFilesDir(), "store");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File assetDir(Context c, StoreAsset a) {
        return new File(storeDir(c), safe(a.id));
    }

    public static boolean isInstalled(Context c, StoreAsset a) {
        File d = assetDir(c, a);
        return new File(d, "manifest.json").isFile() || (d.isDirectory() && d.list() != null && d.list().length > 0);
    }

    public static String base(Context c) {
        return RemoteSettings.server(c) + "/store";
    }

    public static String resolve(Context c, String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String u = url.trim();
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("file://")) return u;
        if (u.startsWith("/")) return RemoteSettings.server(c) + u;
        return base(c) + "/" + u;
    }

    /** Loads the public catalog. Never falls back to local demo data. */
    public static void catalog(Context c, CatalogCallback cb) {
        final Context app = c.getApplicationContext();
        IO.execute(() -> {
            String error = null;
            List<StoreAsset> assets = new ArrayList<>();
            try {
                Request req = new Request.Builder().url(base(app) + "/assets").get().build();
                try (Response res = HTTP.newCall(req).execute()) {
                    if (!res.isSuccessful()) throw new IOException("HTTP " + res.code());
                    String body = res.body() == null ? "" : res.body().string();
                    Catalog cat = GSON.fromJson(body, Catalog.class);
                    if (cat != null && cat.assets != null) assets = cat.assets;
                }
            } catch (Exception | Error e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final List<StoreAsset> out = assets == null ? new ArrayList<>() : assets;
            final String err = error;
            MAIN.post(() -> cb.onResult(out, err));
        });
    }

    /**
     * Downloads the asset package and returns the extracted folder. The package is a ZIP with
     * manifest.json, content/ and optional preview/ + LICENSE.txt.
     */
    public static void install(final Context c, final StoreAsset a, final Done done) {
        final Context app = c.getApplicationContext();
        IO.execute(() -> {
            String error = null;
            try {
                File dir = assetDir(app, a);
                File zip = new File(storeDir(app), safe(a.id) + ".luminaasset");
                File tmp = new File(storeDir(app), safe(a.id) + ".part");
                downloadTo(app, a, tmp);
                if (tmp.length() == 0) throw new IOException("Empty download");
                deleteTree(dir);
                dir.mkdirs();
                unzip(tmp, dir);
                if (!new File(dir, "manifest.json").isFile())
                    throw new IOException("Invalid package: manifest.json is missing");
                if (!tmp.renameTo(zip)) {
                    copy(tmp, zip);
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            } catch (Exception | Error e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String err = error;
            MAIN.post(() -> done.onDone(err));
        });
    }

    /**
     * Copies the package content/ tree into an existing project. Files keep their relative paths
     * (content/models/car.glb -> <project>/models/car.glb).
     */
    public static void importIntoProject(Context c, StoreAsset a, File project, Done done) {
        final Context app = c.getApplicationContext();
        IO.execute(() -> {
            String error = null;
            try {
                File dir = assetDir(app, a);
                File manifest = new File(dir, "manifest.json");
                if (!manifest.isFile()) {
                    // Download first, then import.
                    File zip = new File(storeDir(app), safe(a.id) + ".luminaasset");
                    if (!zip.isFile()) throw new IOException("Asset is not downloaded");
                    deleteTree(dir);
                    dir.mkdirs();
                    unzip(zip, dir);
                    if (!new File(dir, "manifest.json").isFile())
                        throw new IOException("Invalid package: manifest.json is missing");
                }
                File content = new File(dir, "content");
                if (!content.isDirectory()) throw new IOException("Invalid package: content/ is missing");
                for (String p : new String[]{"sounds", "anims", "images", "files", "icon", "models", "prefabs"})
                    //noinspection ResultOfMethodCallIgnored
                    new File(project, p).mkdirs();
                copyTree(content, project);
            } catch (Exception | Error e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String err = error;
            MAIN.post(() -> done.onDone(err));
        });
    }

    /** Publishes a real .luminaasset package. The token is the Worker's STORE_ADMIN_TOKEN. */
    public static void publish(final Context c, final String token, final String manifest,
                               final Uri pkg, final List<Uri> previews, final Done done) {
        final Context app = c.getApplicationContext();
        IO.execute(() -> {
            String error = null;
            try {
                if (token == null || token.trim().isEmpty()) throw new IOException("Publish token is required");
                if (pkg == null) throw new IOException("Package is required");
                MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("manifest", manifest)
                        .addFormDataPart("package", fileName(app, pkg, "asset.luminaasset"),
                                uriBody(app, pkg, MediaType.parse("application/zip")));
                if (previews != null) {
                    for (Uri u : previews) {
                        String mime = app.getContentResolver().getType(u);
                        if (mime == null || !mime.startsWith("image/")) mime = "image/png";
                        body.addFormDataPart("previews", fileName(app, u, "preview.png"),
                                uriBody(app, u, MediaType.parse(mime)));
                    }
                }
                Request req = new Request.Builder()
                        .url(base(app) + "/assets")
                        .header("Authorization", "Bearer " + token.trim())
                        .post(body.build())
                        .build();
                try (Response res = HTTP.newCall(req).execute()) {
                    String text = res.body() == null ? "" : res.body().string();
                    if (!res.isSuccessful())
                        throw new IOException("HTTP " + res.code() + (text.isEmpty() ? "" : ": " + shorten(text, 300)));
                }
            } catch (Exception | Error e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String err = error;
            MAIN.post(() -> done.onDone(err));
        });
    }

    private static RequestBody uriBody(final Context c, final Uri uri, final MediaType type) {
        return new RequestBody() {
            @Override public MediaType contentType() { return type; }
            @Override public long contentLength() { return fileSize(c, uri); }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("Cannot read file");
                    sink.writeAll(Okio.source(in));
                }
            }
        };
    }

    private static long fileSize(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !cur.isNull(i)) return cur.getLong(i);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static String fileName(Context c, Uri uri, String fallback) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && !cur.isNull(i)) {
                    String n = cur.getString(i);
                    if (n != null && !n.trim().isEmpty()) return n.trim();
                }
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last == null || last.trim().isEmpty() ? fallback : last;
    }

    private static String shorten(String s, int max) {
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private static void downloadTo(Context c, StoreAsset a, File dest) throws Exception {
        String url = resolve(c, a.download);
        if (url.isEmpty()) {
            if (a.id == null || a.id.isEmpty()) throw new IOException("Asset has no download URL");
            url = base(c) + "/assets/" + safe(a.id) + "/download";
        }
        if (url.startsWith("file:///android_asset/")) {
            String path = url.substring("file:///android_asset/".length());
            try (InputStream in = c.getAssets().open(path); OutputStream out = new FileOutputStream(dest)) {
                copy(in, out);
            }
            return;
        }
        Request req = new Request.Builder().url(url).get().build();
        try (Response res = HTTP.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("HTTP " + res.code());
            if (res.body() == null) throw new IOException("Empty response");
            try (InputStream in = res.body().byteStream(); OutputStream out = new FileOutputStream(dest)) {
                copy(in, out);
            }
        }
    }

    private static void unzip(File zip, File target) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[16 * 1024];
            while ((e = in.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) throw new IOException("Unsafe path in package");
                File out = new File(target, name);
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                }
            }
        }
    }

    private static void copyTree(File src, File dst) throws IOException {
        File[] children = src.listFiles();
        if (children == null) return;
        for (File f : children) {
            File out = new File(dst, f.getName());
            if (f.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                out.mkdirs();
                copyTree(f, out);
            } else {
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                copy(f, out);
            }
        }
    }

    private static void copy(File src, File dst) throws IOException {
        try (InputStream in = new java.io.FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String safe(String id) {
        if (id == null) return "asset";
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
