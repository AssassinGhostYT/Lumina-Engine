package com.lumina.engine.csharp;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;

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
    public static String test(Context context) {
        if (!AVAILABLE) {
            return "Mono no esta disponible en esta arquitectura.";
        }

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
