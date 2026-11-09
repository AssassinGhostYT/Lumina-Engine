#include <jni.h>
#include <string>
#include "lumina_game.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_nativecode_NativeBridge_nativeStatus(JNIEnv* env, jclass) {
    const std::string status = "Lumina NDK/Clang bridge activo (C++17)";
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lumina_engine_nativecode_NativeBridge_nativeIsAvailable(JNIEnv*, jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumina_engine_nativecode_NativeBridge_nativeRuntimeVersion(JNIEnv* env, jclass) {
    return env->NewStringUTF(lumina::version());
}

extern "C" JNIEXPORT void JNICALL
Java_com_lumina_engine_nativecode_NativeBridge_nativeUpdate(JNIEnv*, jclass, jfloat deltaSeconds) {
    lumina::update(deltaSeconds);
}
