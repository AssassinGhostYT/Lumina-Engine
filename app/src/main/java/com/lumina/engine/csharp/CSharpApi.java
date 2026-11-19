package com.lumina.engine.csharp;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lumina.engine.editor.TestApp;
import com.lumina.engine.remote.Gl;
import com.lumina.engine.runtime.Items.StageImp;
import com.lumina.engine.runtime.Utils.PlayerItem;
import com.lumina.engine.runtime.d3.B3;
import com.lumina.engine.runtime.d3.Camera3D;
import com.lumina.engine.runtime.d3.Light3D;
import com.lumina.engine.runtime.d3.Object3D;

/**
 * Java side of the C# engine API. Every call is routed from Mono through
 * {@link CSharpRuntime#onNativeCall} and runs on the GL thread, where the
 * running scene lives.
 */
public final class CSharpApi {
    private CSharpApi() {}

    public static String call(String command, String a, String b, float value) {
        if (command == null) return "ERR:comando vacio";
        try {
            switch (command) {
                case "running":
                    return Gl.run(() -> TestApp.getCurrentApp() != null
                            && TestApp.getCurrentApp().getPlayingStage() != null) ? "1" : "0";
                case "scene":
                    return Gl.run(() -> active().sceneName == null ? "" : active().sceneName);
                case "delta":
                    return Gl.run(() -> String.valueOf(active().getDelta()));
                case "find":
                    return Gl.run(() -> find(a) ? "1" : "0");
                case "changeScene":
                    return Gl.run(() -> {
                        String name = trim(a);
                        if (name.isEmpty()) throw new IllegalArgumentException("falta el nombre de la escena");
                        active().openScene(name);
                        return "OK";
                    });
                case "destroy":
                    return Gl.run(() -> {
                        item(a).destroy();
                        return "OK";
                    });
                case "playSound":
                    return Gl.run(() -> {
                        String sound = sound(a);
                        active().releaseSound(sound);
                        active().startSound(sound);
                        return "OK";
                    });
                case "stopSound":
                    return Gl.run(() -> {
                        active().releaseSound(sound(a));
                        return "OK";
                    });
                case "pauseSound":
                    return Gl.run(() -> {
                        active().pauseSound(sound(a));
                        return "OK";
                    });
                case "loopSound":
                    return Gl.run(() -> {
                        active().loopSound(sound(a), value != 0f);
                        return "OK";
                    });
                case "getFloat":
                    return Gl.run(() -> String.valueOf(getFloat(a, b)));
                case "setFloat":
                    return Gl.run(() -> {
                        setFloat(a, b, value);
                        return "OK";
                    });
                case "getBool":
                    return Gl.run(() -> getBool(a, b) ? "1" : "0");
                case "setBool":
                    return Gl.run(() -> {
                        setBool(a, b, value != 0f);
                        return "OK";
                    });
                case "key":
                    return Gl.run(() -> isKeyDown(a) ? "1" : "0");
                case "keyPressed":
                    return Gl.run(() -> isKeyJustPressed(a) ? "1" : "0");
                case "keyCode":
                    return Gl.run(() -> Gdx.input.isKeyPressed((int) value) ? "1" : "0");
                case "keyCodePressed":
                    return Gl.run(() -> Gdx.input.isKeyJustPressed((int) value) ? "1" : "0");
                case "button":
                    return Gl.run(() -> Gdx.input.isButtonPressed((int) value) ? "1" : "0");
                case "touched":
                    return Gl.run(() -> Gdx.input.isTouched() ? "1" : "0");
                case "justTouched":
                    return Gl.run(() -> Gdx.input.justTouched() ? "1" : "0");
                case "touchX":
                    return Gl.run(() -> String.valueOf(Gdx.input.getX()));
                case "touchY":
                    return Gl.run(() -> String.valueOf(Gdx.input.getY()));
                default:
                    return "ERR:comando desconocido: " + command;
            }
        } catch (Throwable t) {
            return "ERR:" + message(t);
        }
    }

    private static StageImp active() {
        TestApp app = TestApp.getCurrentApp();
        if (app == null) throw new IllegalStateException("el editor no esta listo");
        StageImp stage = app.getPlayingStage();
        if (stage == null) throw new IllegalStateException("el juego no esta en ejecucion");
        return stage.activeStage();
    }

    private static boolean find(String name) {
        String wanted = trim(name);
        return !wanted.isEmpty() && active().findItem(wanted) != null;
    }

    private static PlayerItem item(String name) {
        String wanted = trim(name);
        if (wanted.isEmpty()) throw new IllegalArgumentException("falta el nombre del elemento");
        PlayerItem item = active().findItem(wanted);
        if (item == null) throw new IllegalArgumentException("no existe el elemento: " + wanted);
        return item;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sound(String value) {
        String name = trim(value);
        if (name.isEmpty()) throw new IllegalArgumentException("falta el nombre del sonido");
        return name;
    }

    private static boolean is3D(PlayerItem item) {
        return item instanceof Object3D || item instanceof Light3D || item instanceof Camera3D;
    }

    private static Actor actor(PlayerItem item) {
        Actor actor = item.getActor();
        if (actor == null) throw new IllegalStateException("el elemento no tiene actor");
        return actor;
    }

    private static float getFloat(String name, String property) {
        PlayerItem item = item(name);
        String key = trim(property).toLowerCase();
        Actor actor = actor(item);
        boolean threeD = is3D(item);
        switch (key) {
            case "x":
                return threeD ? B3.x(item) : actor.getX();
            case "y":
                return threeD ? B3.y(item) : actor.getY();
            case "z":
                return threeD ? B3.z(item) : 0f;
            case "rotation":
                return threeD ? B3.rotY(item) : actor.getRotation();
            case "rotationx":
                return threeD ? B3.rotX(item) : 0f;
            case "rotationy":
                return threeD ? B3.rotY(item) : 0f;
            case "rotationz":
                return threeD ? B3.rotZ(item) : actor.getRotation();
            case "scale":
                return threeD ? B3.scaleX(item) : actor.getScaleX();
            case "scalex":
                return threeD ? B3.scaleX(item) : actor.getScaleX();
            case "scaley":
                return threeD ? B3.scaleY(item) : actor.getScaleY();
            case "scalez":
                return threeD ? B3.scaleZ(item) : 1f;
            case "alpha":
                return actor.getColor().a;
            case "width":
                return actor.getWidth();
            case "height":
                return actor.getHeight();
            default:
                throw new IllegalArgumentException("propiedad desconocida: " + property);
        }
    }

    private static void setFloat(String name, String property, float value) {
        PlayerItem item = item(name);
        String key = trim(property).toLowerCase();
        Actor actor = actor(item);
        boolean threeD = is3D(item);
        switch (key) {
            case "x":
                if (threeD) B3.setPosition(item, value, B3.y(item), B3.z(item));
                else actor.setX(value);
                return;
            case "y":
                if (threeD) B3.setPosition(item, B3.x(item), value, B3.z(item));
                else actor.setY(value);
                return;
            case "z":
                if (threeD) B3.setPosition(item, B3.x(item), B3.y(item), value);
                return;
            case "rotation":
                if (threeD) B3.setRotation(item, B3.rotX(item), value, B3.rotZ(item));
                else actor.setRotation(value);
                return;
            case "rotationx":
                if (threeD) B3.setRotation(item, value, B3.rotY(item), B3.rotZ(item));
                return;
            case "rotationy":
                if (threeD) B3.setRotation(item, B3.rotX(item), value, B3.rotZ(item));
                return;
            case "rotationz":
                if (threeD) B3.setRotation(item, B3.rotX(item), B3.rotY(item), value);
                else actor.setRotation(value);
                return;
            case "scale":
                if (threeD) B3.setScale(item, value, value, value);
                else actor.setScale(value);
                return;
            case "scalex":
                if (threeD) B3.setScale(item, value, B3.scaleY(item), B3.scaleZ(item));
                else actor.setScale(value, actor.getScaleY());
                return;
            case "scaley":
                if (threeD) B3.setScale(item, B3.scaleX(item), value, B3.scaleZ(item));
                else actor.setScale(actor.getScaleX(), value);
                return;
            case "scalez":
                if (threeD) B3.setScale(item, B3.scaleX(item), B3.scaleY(item), value);
                return;
            case "alpha":
                actor.getColor().a = value;
                return;
            default:
                throw new IllegalArgumentException("propiedad desconocida: " + property);
        }
    }

    private static boolean getBool(String name, String property) {
        String key = trim(property).toLowerCase();
        if ("exists".equals(key)) {
            String wanted = trim(name);
            return !wanted.isEmpty() && active().findItem(wanted) != null;
        }
        PlayerItem item = item(name);
        switch (key) {
            case "visible":
                return actor(item).isVisible();
            case "3d":
                return is3D(item);
            default:
                throw new IllegalArgumentException("propiedad desconocida: " + property);
        }
    }

    private static void setBool(String name, String property, boolean value) {
        PlayerItem item = item(name);
        String key = trim(property).toLowerCase();
        switch (key) {
            case "visible":
                actor(item).setVisible(value);
                return;
            default:
                throw new IllegalArgumentException("propiedad desconocida: " + property);
        }
    }

    private static int keyCode(String name) {
        String key = trim(name);
        if (key.isEmpty()) return -1;
        try {
            return Input.Keys.valueOf(key);
        } catch (Throwable ignored) {
            try {
                return Input.Keys.valueOf(key.toUpperCase());
            } catch (Throwable ignoredAgain) {
                return -1;
            }
        }
    }

    private static boolean isKeyDown(String name) {
        int code = keyCode(name);
        return code >= 0 && Gdx.input.isKeyPressed(code);
    }

    private static boolean isKeyJustPressed(String name) {
        int code = keyCode(name);
        return code >= 0 && Gdx.input.isKeyJustPressed(code);
    }

    private static String message(Throwable t) {
        String text = t.getMessage();
        return text == null || text.isEmpty() ? t.getClass().getSimpleName() : text;
    }
}
