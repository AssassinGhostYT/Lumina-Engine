package com.lumina.engine.runtime;

/** Optional C++ game module compiled by the project's GitHub Actions workflow. */
public final class NativeUserGame {
    private static final boolean LOADED;
    static {
        boolean loaded;
        try {
            System.loadLibrary("lumina_user_game");
            loaded = true;
        } catch (Throwable ignored) { loaded = false; }
        LOADED = loaded;
    }
    private NativeUserGame() {}
    public static boolean isLoaded() { return LOADED; }
    public static void update(float deltaSeconds) {
        if (LOADED) {
            try { nativeUpdate(deltaSeconds); } catch (Throwable ignored) { }
        }
    }
    private static native void nativeUpdate(float deltaSeconds);
}
