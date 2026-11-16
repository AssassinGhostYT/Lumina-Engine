package com.lumina.engine.csharp;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
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
    private static final String TAG = "LuminaCSharp";
    private static final boolean AVAILABLE;

    private static volatile boolean initialized;
    private static volatile String lastPing = "";

    // Mono keeps thread-local runtime state: initialization and every run must
    // happen on the same living thread. A single worker also serializes calls.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lumina-csharp");
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

    private static String testOnWorker(Context context) {
        try {
            File managed = CSharpAssets.ensureManaged(context);
            if (!initialized) {
                String init = nativeInit(managed.getAbsolutePath(),
                        context.getAssets());
                if (init == null || !init.startsWith("OK:")) {
                    return "No se pudo iniciar C#: " + init;
                }
                initialized = true;
            }

            lastPing = "";
            String result = nativeRun("Lumina");
            if (lastPing.isEmpty()) {
                return result;
            }
            return result + "\n\nCallback Java <- C#:\n" + lastPing;
        } catch (Throwable t) {
            Log.e(TAG, "Fallo al ejecutar el host C#", t);
            return "Error C#: " + t;
        }
    }

    static void onNativePing(String message) {
        lastPing = message == null ? "" : message;
    }

    private static native boolean nativeIsAvailable();
    private static native String nativeInit(String managedDir, AssetManager assets);
    private static native String nativeRun(String argument);
    private static native String nativeStatus();
}
