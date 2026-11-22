package com.lumina.engine.csharp;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent breadcrumbs for the isolated C# process, which can die without a dialog. */
public final class CSharpTrace {
    private static final Object LOCK = new Object();
    private static final String DONE = "DONE";

    private CSharpTrace() {}

    private static File file(Context context) {
        File dir = new File(context.getFilesDir(), "csharp");
        dir.mkdirs();
        return new File(dir, "last-run.txt");
    }

    public static String filePath(Context context) {
        return file(context).getAbsolutePath();
    }

    public static void begin(Context context, String text) {
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(file(context), false)) {
                out.write(format(text).getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
            }
        }
    }

    public static void append(Context context, String text) {
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(file(context), true)) {
                out.write(format(text).getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
            }
        }
    }

    public static void done(Context context) {
        append(context, DONE);
    }

    public static void crash(Context context, Throwable error) {
        append(context, "CRASH " + Log.getStackTraceString(error));
    }

    /** Returns the previous run when it ended without DONE, otherwise null. */
    public static String incomplete(Context context) {
        synchronized (LOCK) {
            try (FileInputStream in = new FileInputStream(file(context))) {
                long size = file(context).length();
                long skip = Math.max(0, size - 131072);
                while (skip > 0) {
                    long moved = in.skip(skip);
                    if (moved <= 0) break;
                    skip -= moved;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
                String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                String trimmed = text.trim();
                if (trimmed.isEmpty() || trimmed.endsWith(DONE)) return null;
                return skip > 0 ? "… (inicio del log omitido)\n" + text : text;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static String format(String text) {
        String stamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        return stamp + " " + (text == null ? "" : text) + "\n";
    }
}
