package com.lumina.engine.nativecode;

/** JNI entry point for the Lumina native toolchain. */
public final class NativeBridge {
    private static final boolean LOADED;
    static {
        boolean loaded;
        try {
            System.loadLibrary("lumina_native");
            loaded = true;
        } catch (Throwable ignored) {
            loaded = false;
        }
        LOADED = loaded;
    }

    private NativeBridge() {}

    public static boolean isLoaded() {
        return LOADED && nativeIsAvailable();
    }

    public static String runtimeVersion() {
        return isLoaded() ? nativeRuntimeVersion() : "Runtime C++ no disponible";
    }

    public static void update(float deltaSeconds) {
        if (isLoaded()) nativeUpdate(deltaSeconds);
    }

    public static String status() {
        return isLoaded() ? nativeStatus() : "Puente NDK no disponible";
    }

    private static native String nativeStatus();
    private static native boolean nativeIsAvailable();
    private static native String nativeRuntimeVersion();
    private static native void nativeUpdate(float deltaSeconds);
}
