/**
 * Model conversion bridge: reads FBX / OBJ / BLEND with Assimp and writes a binary glTF (GLB) that the
 * gdx-gltf loader of the runtime already understands. Bones, skins and animation clips are kept.
 * Runs in the editor (import / export); exported games only load the generated .glb files.
 */
#include <jni.h>
#include <android/log.h>

#include <assimp/Exporter.hpp>
#include <assimp/Importer.hpp>
#include <assimp/postprocess.h>
#include <assimp/scene.h>

#include <string>

#define LOG_TAG "LuminaAssimp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::string jstringToStd(JNIEnv *env, jstring value) {
    if (value == nullptr) return std::string();
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::string();
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_runtime_d3_ModelImporter_nativeConvert(JNIEnv *env, jclass, jstring input, jstring output) {
    const std::string in = jstringToStd(env, input);
    const std::string out = jstringToStd(env, output);
    if (in.empty() || out.empty()) return env->NewStringUTF("empty input/output path");

    Assimp::Importer importer;
    importer.SetPropertyBool(AI_CONFIG_IMPORT_FBX_READ_TEXTURES, true);
    importer.SetPropertyBool(AI_CONFIG_IMPORT_FBX_PRESERVE_PIVOTS, false);
    importer.SetPropertyBool(AI_CONFIG_IMPORT_FBX_READ_ALL_GEOMETRY_LAYERS, true);

    const unsigned int flags =
            aiProcess_Triangulate |
            aiProcess_GenSmoothNormals |
            aiProcess_CalcTangentSpace |
            aiProcess_JoinIdenticalVertices |
            aiProcess_LimitBoneWeights |
            aiProcess_ImproveCacheLocality |
            aiProcess_RemoveRedundantMaterials |
            aiProcess_FindInvalidData |
            aiProcess_ValidateDataStructure;

    const aiScene *scene = importer.ReadFile(in, flags);
    if (scene == nullptr || scene->mRootNode == nullptr) {
        const char *error = importer.GetErrorString();
        LOGE("import failed: %s (%s)", error == nullptr ? "unknown" : error, in.c_str());
        return env->NewStringUTF(error == nullptr ? "Assimp could not read the file" : error);
    }

    Assimp::Exporter exporter;
    Assimp::ExportProperties properties;
    // glTF only writes node TRS when animations exist unless this is set; set it so every converted
    // file keeps the same hierarchy layout and plays clips the same way.
    properties.SetPropertyBool("GLTF2_NODE_IN_TRS", true);

    const aiReturn result = exporter.Export(scene, "glb2", out, 0, &properties);
    if (result != AI_SUCCESS) {
        const char *error = exporter.GetErrorString();
        LOGE("export failed: %s (%s)", error == nullptr ? "unknown" : error, out.c_str());
        return env->NewStringUTF(error == nullptr ? "Assimp could not write the GLB" : error);
    }

    LOGI("converted %s -> %s", in.c_str(), out.c_str());
    return nullptr;
}
