#include <jni.h>
#include <mutex>
#include <string>
#include <vector>
#include "needle.h"

namespace {
std::mutex g_mutex;
std::vector<unsigned char> g_model;
std::string g_last_error;

jstring to_jstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string jstring_to_string(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

// Needle's published Android archive was built against a libc++ revision that
// exports this internal helper. NDK 27 does not export it, but the operation is
// only used to hash byte ranges for internal unordered containers. Keep the
// ABI symbol local to this bridge and provide the same deterministic contract.
extern "C" size_t _ZNSt6__ndk113__hash_memoryEPKvj(const void* data, unsigned int size) {
    const auto* bytes = static_cast<const unsigned char*>(data);
    size_t hash = static_cast<size_t>(1469598103934665603ULL);
    for (unsigned int i = 0; i < size; ++i) {
        hash ^= bytes[i];
        hash *= static_cast<size_t>(1099511628211ULL);
    }
    return hash;
}

extern "C" size_t _ZNSt6__ndk113__hash_memoryEPKvm(const void* data, size_t size) {
    const auto* bytes = static_cast<const unsigned char*>(data);
    size_t hash = static_cast<size_t>(1469598103934665603ULL);
    for (size_t i = 0; i < size; ++i) {
        hash ^= bytes[i];
        hash *= static_cast<size_t>(1099511628211ULL);
    }
    return hash;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_opendroid_ai_core_needle_Needle3Native_nativeLoad(
    JNIEnv* env, jclass, jbyteArray model) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!model) {
        g_last_error = "Needle 3 model bytes were null";
        return JNI_FALSE;
    }
    const jsize size = env->GetArrayLength(model);
    if (size <= 0) {
        g_last_error = "Needle 3 model is empty";
        return JNI_FALSE;
    }
    g_model.resize(static_cast<size_t>(size));
    env->GetByteArrayRegion(model, 0, size, reinterpret_cast<jbyte*>(g_model.data()));
    const int result = needle_load(g_model.data(), static_cast<unsigned long long>(g_model.size()));
    if (result < 0) {
        const char* error = needle_last_error();
        g_last_error = error ? error : "needle_load failed";
        g_model.clear();
        return JNI_FALSE;
    }
    g_last_error.clear();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_opendroid_ai_core_needle_Needle3Native_nativeInit(
    JNIEnv* env, jclass, jstring system_prompt, jstring tools_json, jstring tool_index_path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string system = jstring_to_string(env, system_prompt);
    const std::string tools = jstring_to_string(env, tools_json);
    const std::string index = jstring_to_string(env, tool_index_path);
    const int result = needle_init(system.c_str(), tools.c_str(), index.empty() ? nullptr : index.c_str());
    if (result < 0) {
        const char* error = needle_last_error();
        g_last_error = error ? error : "needle_init failed";
        return JNI_FALSE;
    }
    g_last_error.clear();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_opendroid_ai_core_needle_Needle3Native_nativeComplete(
    JNIEnv* env, jclass, jstring input, jint max_new_tokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string prompt = jstring_to_string(env, input);
    std::vector<char> output(1024 * 1024);
    const int result = needle_complete(
        prompt.c_str(), max_new_tokens, output.data(), static_cast<int>(output.size()));
    if (result < 0) {
        const char* error = needle_last_error();
        g_last_error = error ? error : "needle_complete failed";
        return nullptr;
    }
    g_last_error.clear();
    return to_jstring(env, std::string(output.data(), static_cast<size_t>(result)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_opendroid_ai_core_needle_Needle3Native_nativeLastError(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return to_jstring(env, g_last_error);
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendroid_ai_core_needle_Needle3Native_nativeReset(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    needle_reset();
    g_last_error.clear();
}
