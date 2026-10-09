#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {
constexpr int32_t kDefaultContextTokens = 4096;
constexpr int32_t kBatchTokens = 128;
constexpr int32_t kMaxNewTokens = 1536;
constexpr int32_t kThreads = 2;

std::mutex g_mutex;
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;
bool g_backend_initialized = false;
int32_t g_context_tokens = kDefaultContextTokens;
std::string g_last_error;

void set_error(const std::string& message) {
    g_last_error = message;
    __android_log_print(ANDROID_LOG_ERROR, "Qwen3Native", "%s", message.c_str());
}

void clear_error() { g_last_error.clear(); }

void append_utf8(std::string& out, uint32_t cp) {
    if (cp <= 0x7f) {
        out.push_back(static_cast<char>(cp));
    } else if (cp <= 0x7ff) {
        out.push_back(static_cast<char>(0xc0 | (cp >> 6)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3f)));
    } else if (cp <= 0xffff) {
        out.push_back(static_cast<char>(0xe0 | (cp >> 12)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3f)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3f)));
    } else {
        out.push_back(static_cast<char>(0xf0 | (cp >> 18)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3f)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3f)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3f)));
    }
}

std::string from_jstring(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out;
    out.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xd800 && cp <= 0xdbff && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xdc00 && low <= 0xdfff) {
                cp = 0x10000 + ((cp - 0xd800) << 10) + (low - 0xdc00);
                ++i;
            }
        }
        append_utf8(out, cp);
    }
    env->ReleaseStringChars(value, chars);
    return out;
}

jstring to_jstring(JNIEnv* env, const std::string& value) {
    std::u16string out;
    out.reserve(value.size());
    for (size_t i = 0; i < value.size();) {
        const auto first = static_cast<uint8_t>(value[i++]);
        uint32_t cp = first;
        if ((first & 0xe0) == 0xc0 && i < value.size()) {
            const uint32_t second = static_cast<uint8_t>(value[i++]) & 0x3f;
            cp = ((first & 0x1f) << 6) | second;
        } else if ((first & 0xf0) == 0xe0 && i + 1 < value.size()) {
            const uint32_t second = static_cast<uint8_t>(value[i++]) & 0x3f;
            const uint32_t third = static_cast<uint8_t>(value[i++]) & 0x3f;
            cp = ((first & 0x0f) << 12) | (second << 6) | third;
        } else if ((first & 0xf8) == 0xf0 && i + 2 < value.size()) {
            const uint32_t second = static_cast<uint8_t>(value[i++]) & 0x3f;
            const uint32_t third = static_cast<uint8_t>(value[i++]) & 0x3f;
            const uint32_t fourth = static_cast<uint8_t>(value[i++]) & 0x3f;
            cp = ((first & 0x07) << 18) | (second << 12) | (third << 6) | fourth;
        }
        if (cp <= 0xffff) {
            out.push_back(static_cast<char16_t>(cp));
        } else {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xd800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xdc00 + (cp & 0x3ff)));
        }
    }
    return env->NewString(reinterpret_cast<const jchar*>(out.data()), static_cast<jsize>(out.size()));
}

bool decode_tokens(const std::vector<llama_token>& tokens) {
    if (g_context == nullptr || tokens.empty()) return false;
    size_t offset = 0;
    while (offset < tokens.size()) {
        const int32_t count = static_cast<int32_t>(std::min<size_t>(kBatchTokens, tokens.size() - offset));
        llama_batch batch = llama_batch_init(count, 0, 1);
        if (batch.token == nullptr || batch.pos == nullptr || batch.n_seq_id == nullptr ||
            batch.seq_id == nullptr || batch.logits == nullptr) {
            llama_batch_free(batch);
            set_error("Could not allocate a llama.cpp token batch");
            return false;
        }
        batch.n_tokens = count;
        for (int32_t i = 0; i < count; ++i) {
            batch.token[i] = tokens[offset + static_cast<size_t>(i)];
            batch.pos[i] = static_cast<llama_pos>(offset + static_cast<size_t>(i));
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = (offset + static_cast<size_t>(i) + 1 == tokens.size()) ? 1 : 0;
        }
        const int32_t result = llama_decode(g_context, batch);
        llama_batch_free(batch);
        if (result != 0) {
            set_error("llama_decode failed while evaluating the prompt (code " + std::to_string(result) + ")");
            return false;
        }
        offset += static_cast<size_t>(count);
    }
    return true;
}

bool decode_one(llama_token token, llama_pos position) {
    llama_batch batch = llama_batch_init(1, 0, 1);
    if (batch.token == nullptr || batch.pos == nullptr || batch.n_seq_id == nullptr ||
        batch.seq_id == nullptr || batch.logits == nullptr) {
        llama_batch_free(batch);
        set_error("Could not allocate a llama.cpp generation batch");
        return false;
    }
    batch.n_tokens = 1;
    batch.token[0] = token;
    batch.pos[0] = position;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;
    batch.logits[0] = 1;
    const int32_t result = llama_decode(g_context, batch);
    llama_batch_free(batch);
    if (result != 0) {
        set_error("llama_decode failed while generating (code " + std::to_string(result) + ")");
        return false;
    }
    return true;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_opendroid_llama_Qwen3Native_nativeLoadModel(JNIEnv* env, jobject, jstring model_path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string path = from_jstring(env, model_path);
    if (path.empty()) {
        set_error("Qwen 3 model path is empty");
        return JNI_FALSE;
    }
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path.c_str(), params);
    if (g_model == nullptr) {
        set_error("llama_model_load_from_file could not load the Qwen GGUF file");
        return JNI_FALSE;
    }
    clear_error();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_opendroid_llama_Qwen3Native_nativeCreateContext(JNIEnv*, jobject, jint requested_context_tokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr) {
        set_error("Load the Qwen model before creating an inference context");
        return JNI_FALSE;
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<uint32_t>(std::clamp(requested_context_tokens, 1024, 8192));
    g_context_tokens = static_cast<int32_t>(params.n_ctx);
    params.n_batch = kBatchTokens;
    params.n_ubatch = kBatchTokens;
    params.n_threads = kThreads;
    params.n_threads_batch = kThreads;
    g_context = llama_init_from_model(g_model, params);
    if (g_context == nullptr) {
        set_error("llama_init_from_model failed to create the Qwen inference context");
        return JNI_FALSE;
    }
    clear_error();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_opendroid_llama_Qwen3Native_nativeGenerate(JNIEnv* env, jobject, jstring prompt, jint requested_new_tokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr) {
        set_error("Qwen model/context is not initialized");
        return nullptr;
    }
    const std::string input = from_jstring(env, prompt);
    if (input.empty()) {
        set_error("Qwen prompt is empty");
        return nullptr;
    }

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int32_t token_count = llama_tokenize(
        vocab, input.data(), static_cast<int32_t>(input.size()), nullptr, 0, false, true);
    if (token_count < 0) token_count = -token_count;
    if (token_count <= 0) {
        set_error("Qwen prompt tokenization returned no tokens");
        return nullptr;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    token_count = llama_tokenize(
        vocab, input.data(), static_cast<int32_t>(input.size()), tokens.data(), token_count, false, true);
    if (token_count < 0) {
        set_error("Qwen prompt tokenization buffer was too small");
        return nullptr;
    }
    tokens.resize(static_cast<size_t>(token_count));

    const int32_t max_new_tokens = std::clamp(requested_new_tokens, 16, kMaxNewTokens);
    const int32_t context_tokens = g_context_tokens;
    if (token_count + max_new_tokens + 8 > context_tokens) {
        set_error("Qwen prompt exceeds the context budget; shorten the conversation or tool list");
        return nullptr;
    }

    llama_memory_clear(llama_get_memory(g_context), true);
    if (!decode_tokens(tokens)) return nullptr;

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sampler_params);
    if (sampler == nullptr) {
        set_error("Could not create the Qwen sampler");
        return nullptr;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.25f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));

    std::string output;
    output.reserve(static_cast<size_t>(max_new_tokens) * 4);
    llama_pos position = static_cast<llama_pos>(tokens.size());
    for (int32_t i = 0; i < max_new_tokens; ++i) {
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        llama_sampler_accept(sampler, token);
        if (llama_vocab_is_eog(vocab, token)) break;

        std::vector<char> piece(64);
        int32_t piece_size = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
        if (piece_size < 0) {
            piece.resize(static_cast<size_t>(-piece_size) + 1);
            piece_size = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
        }
        if (piece_size > 0) output.append(piece.data(), static_cast<size_t>(piece_size));
        if (!decode_one(token, position++)) {
            llama_sampler_free(sampler);
            return nullptr;
        }
    }
    llama_sampler_free(sampler);
    clear_error();
    return to_jstring(env, output);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_opendroid_llama_Qwen3Native_nativeLastError(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return to_jstring(env, g_last_error);
}
