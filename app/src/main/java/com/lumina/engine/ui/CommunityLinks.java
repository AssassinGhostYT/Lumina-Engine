package com.lumina.engine.ui;

/**
 * Single source of truth for the community / support links, shared by the home screen,
 * the support page and the periodic support dialog.
 */
public final class CommunityLinks {
    private CommunityLinks() {}

    /** {id, English title, English description, url, emoji}. */
    public static String[][] all() {
        java.util.ArrayList<String[]> l = new java.util.ArrayList<>();
        l.add(new String[]{"github", "GitHub Repository", "Lumina Engine open source code and issue tracking", "https://github.com/AssassinGhostYT/Lumina-Engine", "💻"});
        return l.toArray(new String[0][]);
    }
}
