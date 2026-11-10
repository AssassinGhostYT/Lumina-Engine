package com.lumina.engine.ui;

import com.badlogic.gdx.Gdx;
import com.lumina.engine.editor.LibgdxEditor;

/** Zero-dependency editor performance snapshot. Call on the GL thread. */
public final class EditorProfiler {
    private EditorProfiler() {}
    public static String snapshot(LibgdxEditor ed) {
        if (ed == null) return "No hay una escena abierta.";
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long total = rt.totalMemory() / (1024 * 1024);
        int actors = ed.getActors() == null ? 0 : ed.getActors().size;
        return "PROFILER LUMINA\n\n"
                + "FPS: " + Gdx.graphics.getFramesPerSecond() + "\n"
                + String.format(java.util.Locale.US, "Delta: %.2f ms\n", Gdx.graphics.getDeltaTime() * 1000f)
                + "Elementos: " + actors + "\n"
                + "Escena: " + ed.getScene() + (ed.is3DScene() ? " (3D)" : " (2D)") + "\n"
                + "Memoria Java: " + used + " / " + total + " MB\n"
                + "Memoria nativa: " + (android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)) + " MB\n\n"
                + "Consejo: si baja el FPS, reduce partículas, luces, sombras o elementos visibles.";
    }
}
