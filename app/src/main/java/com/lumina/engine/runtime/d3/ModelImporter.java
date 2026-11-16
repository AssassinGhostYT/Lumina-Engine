package com.lumina.engine.runtime.d3;

import com.badlogic.gdx.files.FileHandle;

import java.io.File;

/**
 * Converts FBX / OBJ / BLEND models to GLB with Assimp (native library "lumina_assimp").
 *
 * <p>The runtime (and the exported game) only needs the generated .glb: gdx-gltf already loads it with bones, skins and
 * animation clips. Conversion happens in the editor at import time, when a raw file is dropped in "models", and before
 * an APK is exported. The native library is optional: an exported game that finds an already converted sibling .glb
 * uses it without loading Assimp.</p>
 */
public final class ModelImporter {
    private static final String[] RAW = {".fbx", ".obj", ".blend"};
    private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<String>();
    private static boolean loaded;
    private static boolean loadTried;
    private static String loadError = "";

    private ModelImporter() {
    }

    /** True for a format Assimp has to convert first. */
    public static boolean isRaw(String name) {
        if (name == null) return false;
        String low = name.toLowerCase();
        for (String ext : RAW) if (low.endsWith(ext)) return true;
        return false;
    }

    public static boolean isRaw(File file) {
        return file != null && file.isFile() && isRaw(file.getName());
    }

    public static boolean isRaw(FileHandle file) {
        return file != null && isRaw(file.name());
    }

    /** Last conversion error of this thread ("" when the last call succeeded). */
    public static String lastError() {
        String e = LAST_ERROR.get();
        return e == null ? "" : e;
    }

    /**
     * The GLB next to a raw model, converting it when missing or older than the source. Returns null on failure (see
     * {@link #lastError()}). Non-raw files are returned unchanged.
     */
    public static FileHandle convert(FileHandle source) {
        if (source == null || !isRaw(source)) return source;
        LAST_ERROR.set("");
        try {
            File out = convert(source.file());
            return out == null ? null : source.parent().child(out.getName());
        } catch (Throwable t) {
            LAST_ERROR.set(String.valueOf(t));
            return null;
        }
    }

    /** The GLB next to a raw model, converting it when missing or older than the source. */
    public static File convert(File source) {
        if (source == null || !isRaw(source)) return source;
        LAST_ERROR.set("");
        try {
            String name = source.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            File out = new File(source.getParentFile(), base + ".glb");

            if (out.isFile() && out.length() > 0 && out.lastModified() >= source.lastModified()) return out;

            if (!load()) {
                LAST_ERROR.set(loadError);
                return null;
            }
            if (out.isFile() && !out.delete()) {
                LAST_ERROR.set("cannot replace " + out.getName());
                return null;
            }
            String error = nativeConvert(source.getAbsolutePath(), out.getAbsolutePath());
            if (error != null && error.length() > 0) {
                LAST_ERROR.set(error);
                return null;
            }
            if (!out.isFile() || out.length() == 0) {
                LAST_ERROR.set("Assimp produced an empty file");
                return null;
            }
            return out;
        } catch (Throwable t) {
            LAST_ERROR.set(String.valueOf(t));
            return null;
        }
    }

    /** True when the folder (recursive) holds at least one FBX / OBJ / BLEND file. */
    public static boolean hasRaw(File folder) {
        if (folder == null || !folder.isDirectory()) return false;
        File[] files = folder.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (hasRaw(f)) return true;
            } else if (isRaw(f)) return true;
        }
        return false;
    }

    /** Converts every FBX / OBJ / BLEND under a folder (recursive). Returns the number of files converted. */
    public static int convertAll(File folder) {
        if (folder == null || !folder.isDirectory()) return 0;
        int count = 0;
        File[] files = folder.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) count += convertAll(f);
            else if (isRaw(f) && convert(f) != null) count++;
        }
        return count;
    }

    private static synchronized boolean load() {
        if (loaded) return true;
        if (loadTried) return false;
        loadTried = true;
        try {
            System.loadLibrary("lumina_assimp");
            loaded = true;
            return true;
        } catch (Throwable t) {
            loadError = "FBX/OBJ/BLEND conversion is not available in this build (" + t + ")";
            return false;
        }
    }

    /** Returns null on success or the Assimp error message. */
    private static native String nativeConvert(String input, String output);
}
