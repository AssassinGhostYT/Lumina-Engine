package com.lumina.engine.Helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Prepares the C++ sources of a Lumina project for native export. */
public final class CppProjectSupport {
    private CppProjectSupport() {}

    public static boolean hasSources(File root) {
        return !collect(root, root, false, new ArrayList<String>()).isEmpty();
    }

    public static void prepare(File root) throws Exception {
        List<String> sources = collect(root, root, false, new ArrayList<String>());
        List<String> headers = collect(root, root, true, new ArrayList<String>());
        File cppDir = new File(root, "cpp");
        if (!cppDir.exists() && !cppDir.mkdirs()) throw new Exception("Cannot create cpp directory");

        StringBuilder manifest = new StringBuilder("{\n  \"version\": 1,\n  \"runtime\": \"lumina\",\n  \"sources\": [");
        appendJsonArray(manifest, sources);
        manifest.append("],\n  \"headers\": [");
        appendJsonArray(manifest, headers);
        manifest.append("]\n}\n");
        write(new File(cppDir, "lumina_project.json"), manifest.toString());

        StringBuilder cmake = new StringBuilder();
        cmake.append("cmake_minimum_required(VERSION 3.22)\n")
             .append("project(lumina_user_game LANGUAGES CXX)\n")
             .append("set(CMAKE_CXX_STANDARD 17)\n")
             .append("set(CMAKE_CXX_STANDARD_REQUIRED ON)\n\n");
        if (sources.isEmpty()) {
            cmake.append("add_library(lumina_user_game SHARED lumina_placeholder.cpp)\n");
            write(new File(cppDir, "lumina_placeholder.cpp"),
                    "#include <jni.h>\nextern \"C\" JNIEXPORT void JNICALL lumina_project_placeholder() {}\n");
        } else {
            cmake.append("add_library(lumina_user_game SHARED\n");
            for (String source : sources) cmake.append("    ").append(toCmakePath(source)).append("\n");
            cmake.append(")\n");
        }
        cmake.append("target_include_directories(lumina_user_game PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})\n")
             .append("# Link this target with the Lumina native runtime in the host export.\n");
        write(new File(cppDir, "CMakeLists.txt"), cmake.toString());
    }

    private static List<String> collect(File root, File base, boolean headers, List<String> result) {
        if (root == null || !root.isDirectory()) return result;
        File[] files = root.listFiles();
        if (files == null) return result;
        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory()) {
                if (!name.equals(".git") && !name.equals("build") && !name.equals(".gradle"))
                    collect(file, base, headers, result);
                continue;
            }
            String lower = name.toLowerCase();
            boolean isHeader = lower.endsWith(".h") || lower.endsWith(".hpp") || lower.endsWith(".hh");
            boolean isSource = lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx");
            if ((headers && isHeader) || (!headers && isSource))
                result.add(relative(base, file));
        }
        Collections.sort(result);
        return result;
    }

    private static String relative(File root, File file) {
        return file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1).replace('\\', '/');
    }

    private static String toCmakePath(String path) {
        String cmakePath = path.startsWith("cpp/") ? path.substring(4) : "../" + path;
        return cmakePath.replace("\\", "/").replace("\"", "\\\"");
    }

    private static void appendJsonArray(StringBuilder out, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append("\"").append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
               .append("\"");
        }
    }

    private static void write(File file, String value) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
