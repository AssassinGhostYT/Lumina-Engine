package com.lumina.engine.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Keeps the last scene version before an overwrite for manual recovery. */
public final class RecoveryStore {
    private RecoveryStore() {}
    private static File backup(File project, String scene) {
        return new File(new File(project, ".lumina/recovery"), scene + ".previous");
    }
    public static void beforeSave(File project, String scene) {
        if (project == null || scene == null || scene.isEmpty()) return;
        File source = new File(new File(project, "scenes"), scene);
        if (!source.isFile() || source.length() == 0) return;
        File target = backup(project, scene);
        try {
            target.getParentFile().mkdirs();
            copy(source, target);
        } catch (Throwable ignored) {}
    }
    public static boolean hasBackup(File project, String scene) {
        File f = backup(project, scene);
        return f.isFile() && f.length() > 0;
    }
    public static boolean restore(File project, String scene) {
        if (!hasBackup(project, scene)) return false;
        try {
            File target = new File(new File(project, "scenes"), scene);
            target.getParentFile().mkdirs();
            copy(backup(project, scene), target);
            return true;
        } catch (Throwable ignored) { return false; }
    }
    private static void copy(File from, File to) throws Exception {
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
    }
}
