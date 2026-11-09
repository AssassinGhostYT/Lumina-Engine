#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_nativecode_NativeBridge_nativeStatus(JNIEnv* env, jclass) {
    const std::string status = "Lumina NDK/Clang bridge activo (C++17)";
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lumina_engine_nativecode_NativeBridge_nativeIsAvailable(JNIEnv*, jclass) {
    return JNI_TRUE;
}
