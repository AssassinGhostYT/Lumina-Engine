package com.lumina.engine.store;

import java.util.ArrayList;
import java.util.List;

/** Public metadata of an asset published in the Lumina Store. */
public class StoreAsset {
    public String id = "";
    public String name = "";
    public String type = "";
    public String description = "";
    public String author = "";
    public String version = "";
    public String price = "Free";
    public String video = "";
    public String download = "";
    public String license = "";
    public long size = 0L;
    public long downloads = 0L;
    public long updated = 0L;
    public List<String> previews = new ArrayList<>();
    public List<String> tags = new ArrayList<>();

    public String typeLabel() {
        if (type == null) return "";
        switch (type) {
            case "model3d": return "3D model";
            case "vehicle": return "Vehicle";
            case "character": return "Character";
            case "map": return "Map";
            case "sound": return "Sound";
            case "sprite2d": return "2D art";
            case "prefab": return "Prefab";
            default: return type;
        }
    }

    public String sizeLabel() {
        if (size <= 0) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", size / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
    }

    public String firstPreview() {
        return previews == null || previews.isEmpty() ? "" : previews.get(0);
    }
}
