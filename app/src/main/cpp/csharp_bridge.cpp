#include <jni.h>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#ifndef LUMINA_ANDROID_ABI
#define LUMINA_ANDROID_ABI "arm64-v8a"
#endif

namespace {

constexpr const char* TAG = "NebulonCSharp";
constexpr const char* MONO_LIB = "libmonosgen-2.0.so";
constexpr const char* HOST_DLL = "Nebulon.ScriptHost.dll";
constexpr const char* HOST_NS = "Nebulon.ScriptHost";
constexpr const char* HOST_CLASS = "ScriptHost";
constexpr const char* HOST_METHOD = "Run";
constexpr const char* HOST_COMPILE = "Compile";
constexpr const char* HOST_EXECUTE = "Execute";
constexpr const char* INTERNAL_PING =
        "Nebulon.ScriptHost.ScriptHost::NativePing";
constexpr const char* INTERNAL_LOG = "Nebulon.Engine::NativeLog";
constexpr const char* INTERNAL_CALL = "Nebulon.Engine::NativeCall";

using MonoDomain = void;
using MonoAssembly = void;
using MonoImage = void;
using MonoClass = void;
using MonoMethod = void;
using MonoString = void;
using MonoObject = void;
using MonoThread = void;
using MonoAssemblyName = void;
using MonoImageOpenStatus = int;
using MonoBool = int32_t;

using MonoPreloadFunc = MonoAssembly* (*)(MonoAssemblyName*, char**, void*);

struct MonoApi {
    void* handle = nullptr;

    void (*set_assemblies_path)(const char*) = nullptr;
    void (*install_assembly_preload_hook)(MonoPreloadFunc, void*) = nullptr;
    MonoDomain* (*jit_init)(const char*) = nullptr;
    void (*jit_cleanup)(MonoDomain*) = nullptr;
    MonoAssembly* (*domain_assembly_open)(MonoDomain*, const char*) = nullptr;
    MonoImage* (*assembly_get_image)(MonoAssembly*) = nullptr;
    MonoClass* (*class_from_name)(MonoImage*, const char*, const char*) = nullptr;
    MonoMethod* (*class_get_method_from_name)(MonoClass*, const char*, int) = nullptr;
    MonoObject* (*runtime_invoke)(MonoMethod*, void*, void**, MonoObject**) = nullptr;
    MonoString* (*string_new)(MonoDomain*, const char*) = nullptr;
    char* (*string_to_utf8)(MonoString*) = nullptr;
    MonoString* (*object_to_string)(MonoObject*, MonoObject**) = nullptr;
    void (*free_mem)(void*) = nullptr;
    void (*add_internal_call)(const char*, const void*) = nullptr;
    MonoThread* (*thread_current)() = nullptr;
    MonoThread* (*thread_attach)(MonoDomain*) = nullptr;
    void (*thread_detach)(MonoThread*) = nullptr;

    MonoAssemblyName* (*assembly_name_new)(const char*) = nullptr;
    const char* (*assembly_name_get_name)(MonoAssemblyName*) = nullptr;
    MonoImage* (*image_open_from_data_with_name)(
            char*, uint32_t, MonoBool, MonoImageOpenStatus*, MonoBool,
            const char*) = nullptr;
    MonoAssembly* (*assembly_load_from)(
            MonoImage*, const char*, MonoImageOpenStatus*) = nullptr;
    void (*image_close)(MonoImage*) = nullptr;
};

MonoApi api;
std::mutex g_mutex;
MonoDomain* g_domain = nullptr;
MonoImage* g_host_image = nullptr;
MonoMethod* g_run_method = nullptr;
MonoMethod* g_compile_method = nullptr;
MonoMethod* g_execute_method = nullptr;
AAssetManager* g_assets = nullptr;
JavaVM* g_vm = nullptr;
jclass g_runtime_class = nullptr;
jmethodID g_ping_method = nullptr;
jmethodID g_log_method = nullptr;
jmethodID g_call_method = nullptr;
bool g_initialized = false;
std::string g_last_error;

std::string jstring_to_string(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

template <typename T>
bool load_symbol(void* handle, const char* name, T* out) {
    *out = reinterpret_cast<T>(dlsym(handle, name));
    if (*out == nullptr) {
        g_last_error = std::string("simbolo Mono no encontrado: ") + name;
        return false;
    }
    return true;
}

bool load_mono() {
    if (api.handle != nullptr) return true;

    void* handle = dlopen(MONO_LIB, RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        const char* error = dlerror();
        g_last_error = error != nullptr ? error : "no se pudo cargar libmonosgen";
        return false;
    }

    bool ok = true;
#define LOAD_MONO(field, symbol) \
    ok = ok && load_symbol(handle, symbol, &api.field)
    LOAD_MONO(set_assemblies_path, "mono_set_assemblies_path");
    LOAD_MONO(install_assembly_preload_hook, "mono_install_assembly_preload_hook");
    LOAD_MONO(jit_init, "mono_jit_init");
    LOAD_MONO(jit_cleanup, "mono_jit_cleanup");
    LOAD_MONO(domain_assembly_open, "mono_domain_assembly_open");
    LOAD_MONO(assembly_get_image, "mono_assembly_get_image");
    LOAD_MONO(class_from_name, "mono_class_from_name");
    LOAD_MONO(class_get_method_from_name, "mono_class_get_method_from_name");
    LOAD_MONO(runtime_invoke, "mono_runtime_invoke");
    LOAD_MONO(string_new, "mono_string_new");
    LOAD_MONO(string_to_utf8, "mono_string_to_utf8");
    LOAD_MONO(object_to_string, "mono_object_to_string");
    LOAD_MONO(free_mem, "mono_free");
    LOAD_MONO(add_internal_call, "mono_add_internal_call");
    LOAD_MONO(thread_current, "mono_thread_current");
    LOAD_MONO(thread_attach, "mono_thread_attach");
    LOAD_MONO(thread_detach, "mono_thread_detach");
    LOAD_MONO(assembly_name_new, "mono_assembly_name_new");
    LOAD_MONO(assembly_name_get_name, "mono_assembly_name_get_name");
    LOAD_MONO(image_open_from_data_with_name,
              "mono_image_open_from_data_with_name");
    LOAD_MONO(assembly_load_from, "mono_assembly_load_from");
    LOAD_MONO(image_close, "mono_image_close");
#undef LOAD_MONO

    if (!ok) {
        dlclose(handle);
        return false;
    }
    api.handle = handle;
    return true;
}

MonoAssembly* load_asset_assembly(const std::string& path,
                                  const char* assembly_name) {
    if (g_assets == nullptr) return nullptr;

    AAsset* asset = AAssetManager_open(g_assets, path.c_str(), AASSET_MODE_BUFFER);
    if (asset == nullptr) return nullptr;

    const off_t length = AAsset_getLength(asset);
    if (length <= 0) {
        AAsset_close(asset);
        return nullptr;
    }

    std::vector<char> data(static_cast<size_t>(length));
    const int read = AAsset_read(asset, data.data(), data.size());
    AAsset_close(asset);
    if (read != static_cast<int>(data.size())) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "No se pudo leer el ensamblado %s", path.c_str());
        return nullptr;
    }

    MonoImageOpenStatus status = 0;
    MonoImage* image = api.image_open_from_data_with_name(
            data.data(), static_cast<uint32_t>(data.size()), 1, &status, 0,
            assembly_name);
    if (image == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Mono rechazo la imagen %s (estado %d)",
                            path.c_str(), status);
        return nullptr;
    }

    MonoAssembly* assembly =
            api.assembly_load_from(image, assembly_name, &status);
    if (assembly == nullptr) {
        api.image_close(image);
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Mono no pudo cargar %s (estado %d)",
                            assembly_name, status);
    }
    return assembly;
}

MonoAssembly* preload_assembly(MonoAssemblyName* name, char**, void*) {
    const char* assembly_name = api.assembly_name_get_name(name);
    if (assembly_name == nullptr) return nullptr;

    std::string path = "Runtime.Framework/";
    path += assembly_name;
    path += ".dll";
    MonoAssembly* assembly = load_asset_assembly(path, assembly_name);
    if (assembly != nullptr) return assembly;

    path = "Runtime.Framework/";
    path += LUMINA_ANDROID_ABI;
    path += "/";
    path += assembly_name;
    path += ".dll";
    return load_asset_assembly(path, assembly_name);
}

std::string take_string(MonoString* value) {
    if (value == nullptr || api.string_to_utf8 == nullptr) return {};
    char* chars = api.string_to_utf8(value);
    if (chars == nullptr) return {};
    std::string result(chars);
    api.free_mem(chars);
    return result;
}

std::string exception_text(MonoObject* exception) {
    if (exception == nullptr) return {};
    MonoString* text = api.object_to_string(exception, nullptr);
    return take_string(text);
}

// Mono keeps per-thread state. Every Java thread that calls into the runtime
// must be attached first, and detached again before it exits.
class MonoThreadScope {
public:
    MonoThreadScope() {
        if (api.thread_current == nullptr || api.thread_attach == nullptr) return;
        if (api.thread_current() == nullptr) {
            attached_ = api.thread_attach(g_domain);
            __android_log_print(ANDROID_LOG_INFO, TAG,
                                "Hilo Java adjuntado a Mono");
        }
    }

    ~MonoThreadScope() {
        if (attached_ != nullptr && api.thread_detach != nullptr) {
            api.thread_detach(attached_);
            __android_log_print(ANDROID_LOG_INFO, TAG,
                                "Hilo Java separado de Mono");
        }
    }

    MonoThreadScope(const MonoThreadScope&) = delete;
    MonoThreadScope& operator=(const MonoThreadScope&) = delete;

private:
    MonoThread* attached_ = nullptr;
};

std::string invoke_string(MonoMethod* method, const std::vector<std::string>& arguments) {
    if (!g_initialized || method == nullptr) {
        return "Error: el runtime C# no esta iniciado";
    }

    MonoThreadScope thread_scope;
    if (api.thread_current() == nullptr) {
        return "Error: no se pudo adjuntar el hilo a Mono";
    }

    std::vector<MonoString*> strings;
    strings.reserve(arguments.size());
    for (const std::string& argument : arguments) {
        MonoString* value = api.string_new(g_domain, argument.c_str());
        if (value == nullptr) return "Error: no se pudo crear un argumento C#";
        strings.push_back(value);
    }

    void* params[4] = {nullptr, nullptr, nullptr, nullptr};
    for (size_t i = 0; i < strings.size() && i < 4; ++i) {
        params[i] = strings[i];
    }

    MonoObject* exception = nullptr;
    MonoObject* result = api.runtime_invoke(method, nullptr, params, &exception);
    if (exception != nullptr) {
        const std::string message = exception_text(exception);
        return message.empty() ? "Error: excepcion C#" : "Error C#: " + message;
    }
    if (result == nullptr) return "Error: C# no devolvio resultado";
    return take_string(static_cast<MonoString*>(result));
}

}  // namespace

extern "C" void NebulonCSharpPing(MonoString* message) {
    if (g_vm == nullptr || g_runtime_class == nullptr || g_ping_method == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }

    const std::string text = take_string(message);
    jstring value = env->NewStringUTF(text.c_str());
    env->CallStaticVoidMethod(g_runtime_class, g_ping_method, value);
    env->DeleteLocalRef(value);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if (attached) g_vm->DetachCurrentThread();
}

extern "C" void NebulonCSharpLog(MonoString* level, MonoString* message) {
    if (g_vm == nullptr || g_runtime_class == nullptr || g_log_method == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }

    const std::string level_text = take_string(level);
    const std::string message_text = take_string(message);
    jstring level_value = env->NewStringUTF(level_text.c_str());
    jstring message_value = env->NewStringUTF(message_text.c_str());
    env->CallStaticVoidMethod(g_runtime_class, g_log_method, level_value, message_value);
    env->DeleteLocalRef(level_value);
    env->DeleteLocalRef(message_value);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if (attached) g_vm->DetachCurrentThread();
}

extern "C" MonoString* NebulonCSharpCall(
        MonoString* command, MonoString* a, MonoString* b, float value) {
    if (!g_initialized || api.string_new == nullptr || g_domain == nullptr) {
        return nullptr;
    }
    if (g_vm == nullptr || g_runtime_class == nullptr || g_call_method == nullptr) {
        return api.string_new(g_domain, "ERR:callback Java no disponible");
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return api.string_new(g_domain, "ERR:no se pudo adjuntar el hilo Java");
        }
        attached = true;
    }

    const std::string command_text = take_string(command);
    const std::string a_text = take_string(a);
    const std::string b_text = take_string(b);
    jstring command_value = env->NewStringUTF(command_text.c_str());
    jstring a_value = env->NewStringUTF(a_text.c_str());
    jstring b_value = env->NewStringUTF(b_text.c_str());
    jvalue args[4];
    args[0].l = command_value;
    args[1].l = a_value;
    args[2].l = b_value;
    args[3].f = value;
    jstring result_value = static_cast<jstring>(
            env->CallStaticObjectMethodA(g_runtime_class, g_call_method, args));

    std::string result_text;
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        result_text = "ERR:excepcion en la API Java";
    } else if (result_value != nullptr) {
        result_text = jstring_to_string(env, result_value);
        env->DeleteLocalRef(result_value);
    } else {
        result_text = "ERR:la API Java devolvio null";
    }
    env->DeleteLocalRef(command_value);
    env->DeleteLocalRef(a_value);
    env->DeleteLocalRef(b_value);
    if (attached) g_vm->DetachCurrentThread();

    return api.string_new(g_domain, result_text.c_str());
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lumina_engine_csharp_CSharpRuntime_nativeIsAvailable(JNIEnv*, jclass) {
    // Do not dlopen Mono here: this runs on the UI thread when a .cs file is
    // opened. The real load and mono_jit_init happen on the :csharp process
    // worker, so a slow or broken runtime can never freeze the editor.
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_csharp_CSharpRuntime_nativeInit(
        JNIEnv* env, jclass, jstring managed_dir, jobject asset_manager) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_initialized) {
        return env->NewStringUTF("OK:ya iniciado");
    }

    g_last_error.clear();
    g_assets = AAssetManager_fromJava(env, asset_manager);
    if (g_assets == nullptr) {
        return env->NewStringUTF("Error: AssetManager nulo");
    }

    jclass local_class =
            env->FindClass("com/lumina/engine/csharp/CSharpRuntime");
    if (local_class == nullptr) {
        env->ExceptionClear();
        return env->NewStringUTF("Error: clase CSharpRuntime no encontrada");
    }
    g_runtime_class = static_cast<jclass>(env->NewGlobalRef(local_class));
    env->DeleteLocalRef(local_class);
    g_ping_method = env->GetStaticMethodID(
            g_runtime_class, "onNativePing", "(Ljava/lang/String;)V");
    if (g_ping_method == nullptr) {
        env->ExceptionClear();
        return env->NewStringUTF("Error: callback onNativePing no encontrado");
    }
    g_log_method = env->GetStaticMethodID(
            g_runtime_class, "onNativeLog", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (g_log_method == nullptr) {
        env->ExceptionClear();
        return env->NewStringUTF("Error: callback onNativeLog no encontrado");
    }
    g_call_method = env->GetStaticMethodID(
            g_runtime_class, "onNativeCall",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;F)Ljava/lang/String;");
    if (g_call_method == nullptr) {
        env->ExceptionClear();
        return env->NewStringUTF("Error: callback onNativeCall no encontrado");
    }

    if (!load_mono()) {
        return env->NewStringUTF(("Error: " + g_last_error).c_str());
    }

    const std::string managed = jstring_to_string(env, managed_dir);
    if (managed.empty()) {
        return env->NewStringUTF("Error: ruta de ensamblados vacia");
    }

    api.set_assemblies_path(managed.c_str());
    api.install_assembly_preload_hook(preload_assembly, nullptr);

    g_domain = api.jit_init("Nebulon");
    if (g_domain == nullptr) {
        return env->NewStringUTF("Error: mono_jit_init devolvio null");
    }

    // Keep the initializing thread attached for the lifetime of the runtime;
    // CSharpRuntime serializes all calls on one dedicated worker thread.
    api.add_internal_call(INTERNAL_PING,
                          reinterpret_cast<const void*>(&NebulonCSharpPing));
    api.add_internal_call(INTERNAL_LOG,
                          reinterpret_cast<const void*>(&NebulonCSharpLog));
    api.add_internal_call(INTERNAL_CALL,
                          reinterpret_cast<const void*>(&NebulonCSharpCall));

    const std::string host_path = managed + "/" + HOST_DLL;
    MonoAssembly* host = api.domain_assembly_open(g_domain, host_path.c_str());
    if (host == nullptr) {
        g_last_error = "no se pudo abrir ";
        g_last_error += host_path;
        return env->NewStringUTF(("Error: " + g_last_error).c_str());
    }

    g_host_image = api.assembly_get_image(host);
    if (g_host_image == nullptr) {
        return env->NewStringUTF("Error: ensamblado C# sin imagen");
    }

    MonoClass* host_class =
            api.class_from_name(g_host_image, HOST_NS, HOST_CLASS);
    if (host_class == nullptr) {
        return env->NewStringUTF("Error: clase Nebulon.ScriptHost.ScriptHost no encontrada");
    }

    g_run_method =
            api.class_get_method_from_name(host_class, HOST_METHOD, 1);
    g_compile_method =
            api.class_get_method_from_name(host_class, HOST_COMPILE, 3);
    g_execute_method =
            api.class_get_method_from_name(host_class, HOST_EXECUTE, 4);
    if (g_run_method == nullptr || g_compile_method == nullptr ||
        g_execute_method == nullptr) {
        return env->NewStringUTF("Error: metodos C# del host no encontrados");
    }

    g_initialized = true;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Runtime Mono y host C# listos");
    return env->NewStringUTF("OK:Mono y C# listos");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_csharp_CSharpRuntime_nativeRun(
        JNIEnv* env, jclass, jstring argument) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string text = jstring_to_string(env, argument);
    const std::string out = invoke_string(g_run_method, {text});
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_csharp_CSharpRuntime_nativeCompile(
        JNIEnv* env, jclass, jstring output, jstring files, jstring references) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string output_path = jstring_to_string(env, output);
    const std::string file_list = jstring_to_string(env, files);
    const std::string reference_dir = jstring_to_string(env, references);
    const std::string out = invoke_string(
            g_compile_method, {output_path, file_list, reference_dir});
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_csharp_CSharpRuntime_nativeExecute(
        JNIEnv* env, jclass, jstring assembly, jstring type, jstring method,
        jstring argument) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string assembly_path = jstring_to_string(env, assembly);
    const std::string type_name = jstring_to_string(env, type);
    const std::string method_name = jstring_to_string(env, method);
    const std::string text = jstring_to_string(env, argument);
    const std::string out = invoke_string(
            g_execute_method, {assembly_path, type_name, method_name, text});
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_csharp_CSharpRuntime_nativeStatus(JNIEnv* env, jclass) {
    if (!g_initialized) {
        const std::string text =
                g_last_error.empty() ? "Mono sin iniciar" : g_last_error;
        return env->NewStringUTF(text.c_str());
    }
    return env->NewStringUTF("Mono activo; host C# con compilador Roslyn");
}
