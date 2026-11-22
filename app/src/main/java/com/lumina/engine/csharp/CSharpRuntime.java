package com.lumina.engine.csharp;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Android entry point for the embedded Mono/.NET runtime.
 *
 * Mono is loaded lazily from the ABI libs bundled in the APK. The managed
 * framework lives in APK assets and the compiled host assembly is extracted
 * to filesDir before the first call.
 */
public final class CSharpRuntime {
    private static final String TAG = "NebulonCSharp";
    private static final boolean AVAILABLE;

    private static volatile boolean initialized;
    private static volatile Context appContext;
    private static volatile String lastPing = "";
    private static final List<String> lastLogs = new ArrayList<>();

    // Mono keeps thread-local runtime state: initialization and every run must
    // happen on the same living thread. A single worker also serializes calls.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nebulon-csharp");
        t.setDaemon(true);
        return t;
    });

    static {
        boolean available;
        try {
            System.loadLibrary("lumina_native");
            available = nativeIsAvailable();
        } catch (Throwable t) {
            Log.w(TAG, "Mono no disponible en esta ABI", t);
            available = false;
        }
        AVAILABLE = available;
    }

    private CSharpRuntime() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String status() {
        if (!AVAILABLE) {
            return "Mono no incluido para esta arquitectura (usa arm64)";
        }
        return initialized ? nativeStatus() : "Mono listo para iniciar";
    }

    /** Blocking call: run it from a worker thread, never from the UI thread. */
    public static String test(final Context context) {
        if (!AVAILABLE) {
            return "Mono no esta disponible en esta arquitectura.";
        }

        Future<String> future = WORKER.submit(() -> testOnWorker(context));
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error C#: prueba interrumpida";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Log.e(TAG, "Fallo al ejecutar el host C#", cause);
            return "Error C#: " + cause;
        } catch (TimeoutException e) {
            future.cancel(true);
            return "Error C#: la prueba tardo demasiado";
        }
    }

    /** Compiles the given C# files and runs the first Main / Run / Start. */
    public static String compileAndRun(Context context, File... files) {
        if (!AVAILABLE) {
            return "Mono no esta disponible en esta arquitectura.";
        }
        if (files == null || files.length == 0) {
            return "No hay archivos C# para compilar.";
        }

        Future<String> future = WORKER.submit(() -> compileOnWorker(context, files));
        try {
            return future.get(180, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error C#: compilacion interrumpida";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Log.e(TAG, "Fallo al compilar C#", cause);
            return "Error C#: " + cause;
        } catch (TimeoutException e) {
            future.cancel(true);
            return "Error C#: la compilacion tardo demasiado";
        }
    }

    private static String testOnWorker(Context context) {
        try {
            CSharpTrace.append(context, "test start");
            String init = ensureInitialized(context);
            if (init != null) return init;

            lastPing = "";
            CSharpTrace.append(context, "nativeRun start");
            String result = nativeRun("Nebulon");
            CSharpTrace.append(context, "nativeRun end: " + firstLine(result));
            if (lastPing.isEmpty()) {
                return result;
            }
            return result + "\n\nCallback Java <- C#:\n" + lastPing;
        } catch (Throwable t) {
            Log.e(TAG, "Fallo al ejecutar el host C#", t);
            return "Error C#: " + t;
        }
    }

    private static String compileOnWorker(Context context, File[] files) {
        try {
            CSharpTrace.append(context, "compile start");
            String init = ensureInitialized(context);
            if (init != null) return init;

            CSharpTrace.append(context, "extract Managed");
            File managed = CSharpAssets.ensureManaged(context);
            CSharpTrace.append(context, "extract Managed ok");
            File outDir = new File(context.getCacheDir(), "nebula-scripts");
            if (!outDir.isDirectory() && !outDir.mkdirs()) {
                return "No se pudo crear la carpeta de scripts C#.";
            }
            File output = new File(outDir, "UserScripts.dll");

            StringBuilder joined = new StringBuilder();
            for (File file : files) {
                if (file == null || !file.isFile()) continue;
                if (joined.length() > 0) joined.append('\n');
                joined.append(file.getAbsolutePath());
            }
            if (joined.length() == 0) return "No hay archivos C# validos.";

            File refs = new File(managed, "refs");
            CSharpTrace.append(context, "nativeCompile start");
            String compiled = nativeCompile(output.getAbsolutePath(),
                    joined.toString(), (refs.isDirectory() ? refs : managed).getAbsolutePath());
            CSharpTrace.append(context, "nativeCompile end: " + firstLine(compiled));
            if (compiled == null || !compiled.startsWith("OK:")) {
                return compiled == null ? "Error C#: compilacion sin respuesta" : compiled;
            }

            synchronized (lastLogs) {
                lastLogs.clear();
            }
            lastPing = "";
            CSharpTrace.append(context, "nativeExecute start");
            String executed = nativeExecute(output.getAbsolutePath(), "", "", "");
            CSharpTrace.append(context, "nativeExecute end: " + firstLine(executed));
            StringBuilder logs = new StringBuilder();
            synchronized (lastLogs) {
                for (String line : lastLogs) {
                    if (logs.length() > 0) logs.append('\n');
                    logs.append(line);
                }
                lastLogs.clear();
            }
            if (logs.length() == 0) return executed;
            return executed + "\n\nConsola C#:\n" + logs;
        } catch (Throwable t) {
            Log.e(TAG, "Fallo al compilar C#", t);
            return "Error C#: " + t;
        }
    }

    private static String ensureInitialized(Context context) throws Exception {
        if (initialized) return null;
        appContext = context.getApplicationContext();
        nativeSetCrashPath(CSharpTrace.filePath(context));
        CSharpTrace.append(context, "extract Managed");
        File managed = CSharpAssets.ensureManaged(context);
        CSharpTrace.append(context, "extract Managed ok");
        CSharpTrace.append(context, "nativeInit start");
        String init = nativeInit(managed.getAbsolutePath(), context.getAssets());
        CSharpTrace.append(context, "nativeInit end: " + firstLine(init));
        if (init == null || !init.startsWith("OK:")) {
            return "No se pudo iniciar C#: " + init;
        }
        initialized = true;
        return null;
    }

    private static String firstLine(String text) {
        if (text == null) return "null";
        int end = text.indexOf('\n');
        String line = end < 0 ? text : text.substring(0, end);
        return line.length() > 300 ? line.substring(0, 300) + "..." : line;
    }

    static void onNativePing(String message) {
        lastPing = message == null ? "" : message;
    }

    /** Called by Mono for every Nebulon.Engine.NativeCall invocation. */
    static String onNativeCall(String command, String a, String b, float value) {
        return CSharpApi.call(command, a, b, value);
    }

    static void onNativeLog(String level, String message) {
        String text = message == null ? "" : message;
        Context context = appContext;
        if (context != null) CSharpTrace.append(context, "csharp " + text);
        synchronized (lastLogs) {
            lastLogs.add(text);
        }
        if ("error".equals(level)) {
            Log.e(TAG, text);
        } else if ("warn".equals(level)) {
            Log.w(TAG, text);
        } else {
            Log.i(TAG, text);
        }
    }

    private static native boolean nativeIsAvailable();
    private static native void nativeSetCrashPath(String path);
    private static native String nativeInit(String managedDir, AssetManager assets);
    private static native String nativeRun(String argument);
    private static native String nativeCompile(String output, String files, String references);
    private static native String nativeExecute(String assembly, String type, String method, String argument);
    private static native String nativeStatus();
}
