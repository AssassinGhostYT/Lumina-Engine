package com.lumina.engine.csharp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

/** Extracts the compiled C# host assembly from the APK once per app update. */
final class CSharpAssets {
    private static final String TAG = "NebulonCSharp";
    private static final String SOURCE = "Managed";

    private CSharpAssets() {}

    static File ensureManaged(Context context) throws IOException {
        File root = new File(context.getFilesDir(), "csharp");
        File managed = new File(root, SOURCE);
        File marker = new File(managed, ".last_update");
        long updateTime = lastUpdateTime(context);

        if (marker.isFile() && readLong(marker) == updateTime) {
            return managed;
        }

        managed.mkdirs();
        extract(context.getAssets(), SOURCE, managed);
        writeLong(marker, updateTime);
        Log.i(TAG, "C# host extraido en " + managed);
        return managed;
    }

    private static long lastUpdateTime(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 0L;
        }
    }

    private static long readLong(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line == null ? 0L : Long.parseLong(line.trim());
        } catch (IOException | NumberFormatException e) {
            return 0L;
        }
    }

    private static void writeLong(File file, long value) throws IOException {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(Long.toString(value));
            writer.write("\n");
        }
    }

    private static void extract(AssetManager assets, String source,
                                File destination) throws IOException {
        String[] children = assets.list(source);
        if (children == null) {
            throw new IOException("No existe el asset " + source);
        }

        byte[] buffer = new byte[8192];
        for (String child : children) {
            String assetPath = source + "/" + child;
            File output = new File(destination, child);

            try (InputStream in = assets.open(assetPath);
                 FileOutputStream out = new FileOutputStream(output)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } catch (IOException file) {
                output.mkdirs();
                extract(assets, assetPath, output);
            }
        }
    }
}
