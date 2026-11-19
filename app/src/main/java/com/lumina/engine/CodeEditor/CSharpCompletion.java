package com.lumina.engine.CodeEditor;

import com.lumina.engine.CodeEditor.smart.CompletionResult;
import com.lumina.engine.CodeEditor.smart.Item;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight offline C# completion for the mobile editor. */
public final class CSharpCompletion {
    private static final String[] KEYWORDS = {
            "abstract", "as", "async", "await", "base", "bool", "break", "byte", "case",
            "catch", "char", "checked", "class", "const", "continue", "decimal", "default",
            "delegate", "do", "double", "else", "enum", "event", "explicit", "extern", "false",
            "finally", "fixed", "float", "for", "foreach", "get", "global", "goto", "if",
            "implicit", "in", "int", "interface", "internal", "is", "lock", "long", "namespace",
            "new", "null", "object", "operator", "out", "override", "params", "partial",
            "private", "protected", "public", "readonly", "record", "ref", "return", "sbyte",
            "sealed", "set", "short", "sizeof", "stackalloc", "static", "string", "struct",
            "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong", "unchecked",
            "unsafe", "ushort", "using", "var", "virtual", "void", "volatile", "while", "yield"
    };
    private static final String[] UNITY = {
            "Debug.Log", "Debug.LogWarning", "Debug.LogError", "GameObject", "Transform",
            "Vector2", "Vector3", "Vector4", "Quaternion", "Color", "Time.deltaTime",
            "Time.time", "Mathf", "Input", "Input.GetKey", "Input.GetMouseButton",
            "Input.GetAxis", "MonoBehaviour", "ScriptableObject", "Instantiate", "Destroy",
            "GetComponent", "AddComponent", "Random.Range", "Coroutine", "WaitForSeconds",
            "Application.Quit", "SceneManager"
    };
    private static final String[] NEBULON = {
            "Engine", "Engine.Version", "Engine.Log", "Engine.LogWarning", "Engine.LogError",
            "Engine.IsGameRunning", "Engine.SceneName", "Engine.DeltaTime", "Engine.Find",
            "Engine.ChangeScene", "Engine.Destroy", "Engine.PlaySound", "Engine.StopSound",
            "Engine.PauseSound", "Engine.LoopSound", "Engine.Input", "Entity", "Entity.X",
            "Entity.Y", "Entity.Z", "Entity.Position", "Entity.Rotation", "Entity.Scale",
            "Entity.Visible", "Entity.Alpha", "Entity.Exists", "Entity.Destroy",
            "Vector2", "Vector3", "Input.IsKeyDown", "Input.IsKeyJustPressed",
            "Input.IsTouched", "Input.JustTouched", "Input.TouchX", "Input.TouchY"
    };
    private static final Pattern TYPE = Pattern.compile(
            "\\b(?:class|struct|interface|enum|record)\\s+([A-Za-z_]\\w*)");
    private static final Pattern MEMBER = Pattern.compile(
            "\\b(?:(?:public|private|protected|internal|static|virtual|override|async|sealed|partial|abstract|extern)\\s+)*(?:[A-Za-z_]\\w*[\\s<>,.?\\[\\]]*)+\\s+([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern VARIABLE = Pattern.compile(
            "\\b(?:bool|byte|char|decimal|double|float|int|long|object|short|string|uint|ulong|ushort|var)\\s+([A-Za-z_]\\w*)\\b(?!\\s*\\()");

    private CSharpCompletion() {}

    public static CompletionResult complete(String text, int offset) {
        CompletionResult result = new CompletionResult();
        int end = Math.max(0, Math.min(offset, text.length()));
        int start = end;
        while (start > 0 && (Character.isLetterOrDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '_')) start--;
        result.prefix = text.substring(start, end);
        result.member = start > 0 && text.charAt(start - 1) == '.';

        HashSet<String> seen = new HashSet<>();
        for (String value : KEYWORDS) add(result, seen, value, Item.Kind.KEYWORD, "C#", value);
        for (String value : UNITY) add(result, seen, value, Item.Kind.METHOD, "Unity", value + "($0)");
        for (String value : NEBULON) add(result, seen, value, Item.Kind.METHOD, "Nebulon", value + "($0)");

        Matcher type = TYPE.matcher(text);
        while (type.find()) add(result, seen, type.group(1), Item.Kind.CLASS, "Proyecto", type.group(1));
        Matcher member = MEMBER.matcher(text);
        while (member.find()) add(result, seen, member.group(1), Item.Kind.METHOD, "Proyecto", member.group(1) + "($0)");
        Matcher variable = VARIABLE.matcher(text);
        while (variable.find()) add(result, seen, variable.group(1), Item.Kind.FIELD, "Proyecto", variable.group(1));
        return result;
    }

    private static void add(CompletionResult result, HashSet<String> seen, String name, Item.Kind kind, String owner, String insert) {
        String plain = name.substring(name.lastIndexOf('.') + 1);
        if (!result.prefix.isEmpty() && !name.toLowerCase().contains(result.prefix.toLowerCase()) && !plain.toLowerCase().startsWith(result.prefix.toLowerCase())) return;
        if (!seen.add(name)) return;
        Item item = new Item();
        item.kind = kind;
        item.name = name;
        item.label = kind == Item.Kind.METHOD ? name + "()" : name;
        item.insert = result.member && name.contains(".") ? plain + (kind == Item.Kind.METHOD ? "($0)" : "") : insert;
        item.owner = owner;
        item.detail = kind == Item.Kind.METHOD ? "método C#" : (kind == Item.Kind.CLASS ? "tipo C#" : (kind == Item.Kind.FIELD ? "variable" : "palabra clave"));
        result.items.add(item);
    }
}
