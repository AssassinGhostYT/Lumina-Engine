package com.lumina.engine.ui.uimaker;

/** The app side of {@link UiKit.Icons}: library icons rendered into the project's images folder (Android). */
public final class UiKitIcons {
    private UiKitIcons() {}

    /** Library icons for the UI skills: rendered once into images/ui_<id>.png (white, tinted by the widgets). */
    public static UiKit.Icons of(final java.io.File projectDir) {
        return id -> {
            String clean = "ui_" + id.replace('-', '_');
            if (com.lumina.engine.icons.IconImport.exists(projectDir, clean)) return "/" + clean + ".png";
            com.lumina.engine.icons.IconCatalog cat = com.lumina.engine.icons.IconCatalog.get();
            com.lumina.engine.icons.IconCatalog.Icon ic = cat == null ? null : cat.byId(id);
            if (ic == null) return "";
            try {
                com.lumina.engine.icons.IconRenderer.Options o = new com.lumina.engine.icons.IconRenderer.Options();
                o.size = 128;
                o.color = 0xFFFFFFFF;
                return com.lumina.engine.icons.IconImport.importIcon(projectDir, ic, clean, o, true).useAs;
            } catch (Throwable t) {
                return "";
            }
        };
    }

}
