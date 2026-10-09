# Qwen 3 local inference

OpenDroid v2.0.1-qwen3 uses Qwen 3 0.6B Q4_K_M through a CPU-only Android library module (`:llama`). The module vendors the official llama.cpp core at commit [`e60eff95fda7b6e46bf402a8e9f1e86d88ab29c8`](https://github.com/ggml-org/llama.cpp/tree/e60eff95fda7b6e46bf402a8e9f1e86d88ab29c8). The Android JNI bridge uses the current llama model, context, tokenizer, decode, and sampler APIs.

The GGUF is distributed as a separate GitHub Release asset named `qwen3.gguf`; it is not an APK asset, is not fetched by Gradle, and is never automatically downloaded by the app. On the Android 9 target device, place it at **`/sdcard/Download/qwen3.gguf`**. When the Qwen 3 Local screen opens, the app checks the path and requests the already-declared `READ_EXTERNAL_STORAGE` runtime permission if needed. If the model is missing, it displays: “Please download the Qwen 3 model and place it in /sdcard/Download/”. Before loading, the runtime checks the expected size (**484,220,320 bytes**) and SHA-256 (**`9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14`**). The Qwen model is based on [Qwen/Qwen3-0.6B](https://huggingface.co/Qwen/Qwen3-0.6B), released under Apache-2.0; its license remains bundled at `app/src/main/assets/models/LICENSE`.

The Gradle module retains **minSdk 28**, **NDK 27.2.12479018**, **CMake 3.22.1**, and both `armeabi-v7a` and `arm64-v8a` ABIs. llama.cpp is built as CPU code and statically linked into `libqwenbridge.so`; the shared C++ runtime is packaged for both ABIs. The upstream llama.cpp license is included as `app/src/main/assets/models/LLAMA_CPP_LICENSE`. This app uses the official llama.cpp core sources in its own Android library module rather than importing the upstream sample application.

Prompts use Qwen ChatML boundaries (`<|im_start|>`, `<|im_end|>`) and `/no_think`. Tool prompts include compact registered schemas and request Qwen's `<tool_call>{"name":"…","arguments":{…}}</tool_call>` format. The existing registry allowlist, approval gate, and action dispatcher remain authoritative for proposed actions.

The context is 4,096 tokens, with a 1,536-token upper generation cap for tool calls and 512 tokens for ordinary chat. The model is loaded directly from the user-provided Downloads file after size/hash verification. Build and ABI checks do not prove execution on a physical device; a real ARMv7 phone smoke test is still needed to verify load, ordinary chat, and a read-only `list_available_tools` call under device memory pressure.

## Upstream sources

- [llama.cpp official Android example and library](https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android)
- [llama.cpp source commit used by this release](https://github.com/ggml-org/llama.cpp/tree/e60eff95fda7b6e46bf402a8e9f1e86d88ab29c8)
- [Qwen 3 0.6B GGUF artifact source](https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF)
- [Qwen3 0.6B base model and license](https://huggingface.co/Qwen/Qwen3-0.6B)
