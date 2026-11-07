package com.lumina.engine.ui.uimaker;

import com.lumina.engine.R;
import com.lumina.engine.runtime.ui.UINode;

/** Editor icons (drawables of the app) for the widget types, used by the palette, the tree and the properties header. */
final class UiIcons {
    private UiIcons() {}

    static int forType(String type) {
        switch (type) {
            case UINode.PANEL: return R.drawable.ic_lumina_square;
            case UINode.STACK: return R.drawable.ic_lumina_layers;
            case UINode.LINEAR: return R.drawable.ic_lumina_view_list;
            case UINode.GRID: return R.drawable.ic_lumina_view_grid;
            case UINode.SCROLL: return R.drawable.ic_lumina_sort;
            case UINode.LABEL: return R.drawable.ic_lumina_text;
            case UINode.IMAGE: return R.drawable.ic_lumina_image;
            case UINode.ICON: return R.drawable.ic_lumina_widgets;
            case UINode.BUTTON: return R.drawable.ic_lumina_touch;
            case UINode.PROGRESS: return R.drawable.ic_lumina_progress;
            case UINode.SLIDER: return R.drawable.ic_lumina_scale;
            case UINode.CHECKBOX: return R.drawable.ic_lumina_check;
            case UINode.RADIO: return R.drawable.ic_lumina_circle;
            case UINode.SWITCH: return R.drawable.ic_lumina_settings;
            case UINode.TEXTFIELD: return R.drawable.ic_lumina_keyboard;
            default: return R.drawable.ic_lumina_square;
        }
    }
}
