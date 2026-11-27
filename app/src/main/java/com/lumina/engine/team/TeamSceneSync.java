package com.lumina.engine.team;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Timer;
import com.lumina.engine.Helpers.PropertySet;
import com.lumina.engine.Helpers.editor.Project;
import com.lumina.engine.editor.Hierarchy;
import com.lumina.engine.editor.LibgdxEditor;
import com.lumina.engine.editor.TestApp;
import com.lumina.engine.editor.items.EditorItem;
import com.lumina.engine.prefab.PrefabStore;
import com.lumina.engine.team.TeamClient.SceneSnapshot;
import com.lumina.engine.ui.Bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Mirrors live scene gestures (move / rotate / scale) between the members of a team. */
public final class TeamSceneSync implements TeamSession.Listener {
    private static final TeamSceneSync INSTANCE = new TeamSceneSync();
    private static final long FRAME_MS = 50;
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "x", "y", "z", "rotation", "width", "height", "radius",
            "Rotation X", "Rotation Y", "Rotation Z",
            "Scale X", "Scale Y", "Scale Z"
    ));

    private static final Map<String, Long> LAST_SENT = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Timer.Task> PENDING = new HashMap<>();
    private static final Map<String, Integer> LAST_SCENE = new ConcurrentHashMap<>();

    private TeamSceneSync() {}

    public static void install() {
        TeamSession.get().addListener(INSTANCE);
    }

    /** Called by the editor while an element is being moved, rotated or scaled. */
    public static void touch(LibgdxEditor editor, Actor actor, String action, boolean force) {
        if (editor == null || actor == null || !(actor instanceof EditorItem)) return;
        TeamSession session = TeamSession.get();
        if (!session.isConnected() || !session.canEdit()) return;
        String scene = editor.getScene();
        String name = actor.getName();
        if (scene == null || scene.isEmpty() || name == null || name.isEmpty()) return;
        if (PrefabStore.isTempScene(scene)) return;
        String key = scene + "\n" + name + "\n" + action;
        long now = System.currentTimeMillis();
        Long previous = LAST_SENT.get(key);
        if (!force && previous != null && now - previous < FRAME_MS) return;
        LAST_SENT.put(key, now);
        Map<String, Map<String, String>> items = new LinkedHashMap<>();
        List<Actor> actors = new ArrayList<>();
        actors.add(actor);
        actors.addAll(Hierarchy.descendantsOf(editor, name));
        for (Actor a : actors) {
            if (items.size() >= 60) break;
            if (!(a instanceof EditorItem)) continue;
            String object = a.getName();
            if (object == null || object.isEmpty()) continue;
            PropertySet<String, Object> ps = ((EditorItem) a).getPropertySet();
            if (ps == null) continue;
            Map<String, String> props = new LinkedHashMap<>();
            for (String k : KEYS) {
                if (ps.containsKey(k)) props.put(k, ps.getString(k));
            }
            if (!props.isEmpty()) {
                items.put(object, props);
                CACHE.put(scene + "\n" + object, props);
            }
        }
        if (items.isEmpty()) return;
        session.sendSceneEdits(scene, action, items, force);
    }

    /** Any undoable edit (add, delete, duplicate, property panel...) publishes the complete scene shortly after. */
    public static void markChanged(LibgdxEditor editor) {
        if (!usable(editor) || editor.isLoading()) return;
        final String scene = editor.getScene();
        Timer.Task previous = PENDING.remove(scene);
        if (previous != null) previous.cancel();
        Timer.Task task = new Timer.Task() {
            @Override
            public void run() {
                PENDING.remove(scene);
                publishNow(editor, scene);
            }
        };
        PENDING.put(scene, task);
        Timer.schedule(task, 0.35f);
    }

    private static void publishNow(LibgdxEditor editor, String scene) {
        if (!usable(editor) || editor.isLoading() || !scene.equals(editor.getScene())) return;
        String data = editor.getSaveState();
        int hash = data.hashCode();
        Integer previous = LAST_SCENE.put(scene, hash);
        if (previous != null && previous == hash) return;
        TeamSession.get().publishScene(scene, data);
    }

    private static boolean usable(LibgdxEditor editor) {
        if (editor == null) return false;
        String scene = editor.getScene();
        if (scene == null || scene.isEmpty() || PrefabStore.isTempScene(scene)) return false;
        TeamSession session = TeamSession.get();
        return session.isConnected() && session.canEdit();
    }

    @Override
    public void onScene(TeamSession session, SceneSnapshot snapshot) {
        if (snapshot == null || snapshot.scene == null || snapshot.data == null) return;
        applyRemote(snapshot.scene, snapshot.data);
    }

    private static void applyRemote(String scene, String data) {
        if (scene == null || scene.isEmpty() || data == null || PrefabStore.isTempScene(scene)) return;
        if (PENDING.containsKey(scene)) return;
        Bridge.gl(() -> {
            TestApp app = Bridge.app();
            if (app == null) return;
            LibgdxEditor open = editorFor(scene);
            LibgdxEditor current = Bridge.editor();
            Project project = open != null ? open.getProject() : (current == null ? null : current.getProject());
            if (project == null) return;
            FileHandle file = Gdx.files.absolute(project.getScenesPath() + scene);
            String local = file.exists() ? file.readString() : "";
            if (data.equals(local)) {
                LAST_SCENE.put(scene, data.hashCode());
                return;
            }
            HashMap<String, String> before = open == null ? null : app.openSceneFingerprints();
            file.writeString(data, false);
            LAST_SCENE.put(scene, data.hashCode());
            forget(scene);
            if (open != null) app.applyExternalChanges(before);
        });
    }

    private static void forget(String scene) {
        String prefix = scene + "\n";
        for (Iterator<String> it = CACHE.keySet().iterator(); it.hasNext(); ) {
            if (it.next().startsWith(prefix)) it.remove();
        }
        for (Iterator<String> it = LAST_SENT.keySet().iterator(); it.hasNext(); ) {
            if (it.next().startsWith(prefix)) it.remove();
        }
    }

    /** Apply the last known positions of a scene after it is opened (kept while the app is running). */
    public static void applyScene(String scene) {
        if (scene == null || scene.isEmpty()) return;
        Bridge.gl(() -> {
            LibgdxEditor editor = editorFor(scene);
            if (editor == null) return;
            for (Map.Entry<String, Map<String, String>> e : CACHE.entrySet()) {
                String key = e.getKey();
                int split = key.indexOf('\n');
                if (split <= 0 || !scene.equals(key.substring(0, split))) continue;
                apply(editor, key.substring(split + 1), e.getValue());
            }
        });
    }

    @Override
    public void onEdit(TeamSession session, TeamClient.SceneEdit edit) {
        if (edit == null || edit.scene == null) return;
        List<TeamClient.SceneEdit.Item> items = new ArrayList<>();
        if (edit.items != null) {
            items.addAll(edit.items);
        } else if (edit.object != null && edit.props != null) {
            TeamClient.SceneEdit.Item one = new TeamClient.SceneEdit.Item();
            one.object = edit.object;
            one.props = edit.props;
            items.add(one);
        }
        Map<String, Map<String, String>> cached = new LinkedHashMap<>();
        for (TeamClient.SceneEdit.Item item : items) {
            if (item == null || item.object == null || item.props == null || item.props.isEmpty()) continue;
            Map<String, String> props = new LinkedHashMap<>(item.props);
            CACHE.put(edit.scene + "\n" + item.object, props);
            cached.put(item.object, props);
        }
        if (cached.isEmpty()) return;
        Bridge.gl(() -> {
            LibgdxEditor editor = editorFor(edit.scene);
            if (editor == null) return;
            boolean changed = false;
            for (Map.Entry<String, Map<String, String>> e : cached.entrySet()) {
                if (apply(editor, e.getKey(), e.getValue())) changed = true;
            }
            if (changed && edit.last) {
                try {
                    editor.getProject().save(editor);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static LibgdxEditor editorFor(String scene) {
        TestApp app = Bridge.app();
        if (app == null) return null;
        for (LibgdxEditor editor : app.editors) {
            if (editor != null && scene.equals(editor.getScene())) return editor;
        }
        return null;
    }

    private static boolean apply(LibgdxEditor editor, String object, Map<String, String> props) {
        Actor actor = editor.findActor(object);
        if (!(actor instanceof EditorItem) || props == null) return false;
        PropertySet<String, Object> ps = ((EditorItem) actor).getPropertySet();
        if (ps == null) return false;
        boolean changed = false;
        for (Map.Entry<String, String> e : props.entrySet()) {
            String key = e.getKey();
            if (key == null || !KEYS.contains(key) || e.getValue() == null) continue;
            try {
                float value = Float.parseFloat(e.getValue());
                if (Float.isNaN(value) || Float.isInfinite(value)) continue;
            } catch (Throwable ignored) {
                continue;
            }
            ps.put(key, e.getValue());
            changed = true;
        }
        if (changed) {
            try {
                ((EditorItem) actor).update();
            } catch (Throwable ignored) {
            }
        }
        return changed;
    }
}
