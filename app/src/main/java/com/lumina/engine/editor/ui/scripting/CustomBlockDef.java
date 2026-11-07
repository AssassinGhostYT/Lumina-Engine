package com.lumina.engine.editor.ui.scripting;

public class CustomBlockDef {

    public String name = "";
    public String title = "";
    public String code = "";

    public CustomBlockDef() {
    }

    public CustomBlockDef(String name, String title, String code) {
        this.name = name;
        this.title = title;
        this.code = code;
    }
}
