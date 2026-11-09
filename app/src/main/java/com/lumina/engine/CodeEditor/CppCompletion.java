package com.lumina.engine.CodeEditor;

import com.lumina.engine.CodeEditor.smart.CompletionResult;
import com.lumina.engine.CodeEditor.smart.Item;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight offline C++ completion for the mobile editor. */
public final class CppCompletion {
    private static final String[] KEYWORDS = {
            "auto", "bool", "break", "case", "class", "const", "continue", "else", "enum",
            "false", "for", "if", "inline", "namespace", "new", "nullptr", "private", "protected",
            "public", "return", "static", "struct", "switch", "template", "this", "true", "using",
            "virtual", "void", "while", "std::string", "std::vector", "std::map", "std::unique_ptr"
    };
    private static final String[] LUMINA = {
            "lumina::update", "lumina::version", "lumina::Vector2", "lumina::Vector3",
            "lumina::Sprite", "lumina::Scene", "lumina::Entity", "lumina::log",
            "NativeBridge::runtimeVersion", "NativeBridge::update"
    };
    private static final Pattern DECLARATION = Pattern.compile(
            "(?:class|struct|enum)\\s+([A-Za-z_]\\w*)|(?:[A-Za-z_]\\w*[\\s:*&<>]*)+\\s+([A-Za-z_]\\w*)\\s*\\(");

    private CppCompletion() {}

    public static CompletionResult complete(String text, int offset) {
        CompletionResult result = new CompletionResult();
        int end = Math.max(0, Math.min(offset, text.length()));
        int start = end;
        while (start > 0 && (Character.isLetterOrDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '_')) start--;
        result.prefix = text.substring(start, end);
        result.member = start > 0 && (text.charAt(start - 1) == '.' || text.charAt(start - 1) == ':');
        HashSet<String> seen = new HashSet<>();
        for (String value : KEYWORDS) add(result, seen, value, Item.Kind.KEYWORD, "C++", value);
        for (String value : LUMINA) add(result, seen, value, Item.Kind.METHOD, "Lumina Runtime", value + "($0)");

        Matcher matcher = DECLARATION.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (name != null) add(result, seen, name, matcher.group(1) != null ? Item.Kind.CLASS : Item.Kind.METHOD, "Project", name + (matcher.group(1) != null ? "" : "($0)"));
        }
        return result;
    }

    private static void add(CompletionResult result, HashSet<String> seen, String name, Item.Kind kind, String owner, String insert) {
        String plain = name.substring(name.lastIndexOf("::") + 2);
        if (!result.prefix.isEmpty() && !name.toLowerCase().contains(result.prefix.toLowerCase()) && !plain.toLowerCase().startsWith(result.prefix.toLowerCase())) return;
        if (!seen.add(name)) return;
        Item item = new Item();
        item.kind = kind;
        item.name = name;
        item.label = kind == Item.Kind.METHOD ? name + "()" : name;
        item.insert = result.member && name.contains("::")
                ? plain + (kind == Item.Kind.METHOD ? "($0)" : "")
                : insert;
        item.owner = owner;
        item.detail = kind == Item.Kind.METHOD ? "función C++" : "palabra clave";
        result.items.add(item);
    }
}
