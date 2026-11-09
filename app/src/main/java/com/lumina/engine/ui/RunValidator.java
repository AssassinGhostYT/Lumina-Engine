package com.lumina.engine.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lumina.engine.Helpers.PropertySet;
import com.lumina.engine.editor.LibgdxEditor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Lightweight scene checks shown before Run; runs on the GL thread. */
public final class RunValidator {
    private RunValidator() {}

    public static List<String> validate(LibgdxEditor editor) {
        ArrayList<String> out = new ArrayList<>();
        if (editor == null) { out.add("ERROR · No hay una escena abierta."); return out; }
        com.badlogic.gdx.utils.Array<Actor> actors = editor.getActors();
        if (actors == null || actors.size == 0) {
            out.add("ERROR · La escena está vacía; añade al menos un elemento antes de ejecutar.");
            return out;
        }
        boolean camera3d = false, activeCamera3d = false;
        File root = new File(editor.getProject().getPath());
        String scene = editor.getScene();
        for (Actor actor : actors) {
            PropertySet<String,Object> p;
            try { p = PropertySet.getPropertySet(actor); } catch (Throwable t) { continue; }
            if (p == null) continue;
            String name = p.getString("name");
            String kind = p.getString("TYPE");
            String body = p.getString("Body");
            String collider = p.getString("Collider");
            if ("CAMERA3D".equals(kind)) {
                camera3d = true;
                if ("true".equalsIgnoreCase(p.getString("Active"))) activeCamera3d = true;
            }
            if ("DYNAMIC".equalsIgnoreCase(body) && (collider.isEmpty() || "NONE".equalsIgnoreCase(collider)))
                out.add("ADVERTENCIA · " + name + " es DYNAMIC pero no tiene collider; no reaccionará correctamente a la física.");
            if ((name.toLowerCase().contains("button") || name.toLowerCase().contains("play") || name.toLowerCase().contains("start")) && ("BOX".equals(kind) || "TEXT".equals(kind))) {
                File event = new File(root, "Events/" + scene + "/private/" + name + "/onClick.java");
                File visual = new File(root, "Events/" + scene + "/private/" + name + "/onClick.visual");
                if (!event.isFile() && !visual.isFile()) out.add("ERROR · " + name + " parece un botón, pero no tiene evento onClick conectado.");
            }
        }
        if (camera3d && !activeCamera3d) out.add("ERROR · La escena 3D tiene cámaras, pero ninguna está marcada como Active.");
        return out;
    }
}
