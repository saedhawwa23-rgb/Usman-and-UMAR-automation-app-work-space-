# Qwen 3 local inference

OpenDroid v2.0.2-qwen3 replaces Needle 3 with Qwen 3 0.6B Q4_K_M, running locally through a CPU-only Android library module (`:llama`). The module vendors the official llama.cpp core at commit [`e60eff95fda7b6e46bf402a8e9f1e86d88ab29c8`](https://github.com/ggml-org/llama.cpp/tree/e60eff95fda7b6e46bf402a8e9f1e86d88ab29c8). The Android JNI bridge calls the current `llama_model_load_from_file`, `llama_init_from_model`, tokenizer, decode, and sampler APIs. This release is a **single universal APK** containing both ARM ABIs and the Qwen model; the model is not a separate download.

The model artifact is `Qwen3-0.6B-Q4_K_M.gguf` from [bartowski's GGUF repository](https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF). The APK asset name is `models/qwen3.gguf`; the installed app-scoped path is `filesDir/models/qwen3.gguf`. Its pinned size is **484,220,320 bytes** and SHA-256 is **`9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14`**. `app/build.gradle` downloads and verifies this exact artifact during `preBuild`, so the large model is deliberately ignored by Git but embedded in the APK. The quantization is based on [Qwen/Qwen3-0.6B](https://huggingface.co/Qwen/Qwen3-0.6B), released under Apache-2.0; the full upstream [license text](https://huggingface.co/Qwen/Qwen3-0.6B/blob/main/LICENSE) (Copyright 2024 Alibaba Cloud) is bundled as `app/src/main/assets/models/LICENSE` and packaged into the APK.

The Gradle module retains **minSdk 28**, **NDK 27.2.12479018**, **CMake 3.22.1**, and the `armeabi-v7a` and `arm64-v8a` filters. llama.cpp is built as CPU code and statically linked into `libqwenbridge.so`; Android's shared C++ runtime is packaged for the selected ABIs. The upstream llama.cpp license is also copied into the APK assets. This app uses the official llama.cpp core sources in its own Android library module rather than importing the upstream `llama.android` sample application, whose application-level requirements and ABI defaults do not match this app's Android 9/32-bit target.

Prompts use Qwen's ChatML boundaries (`<|im_start|>`, `<|im_end|>`) and `/no_think`. Tool prompts include only compact registered tool schemas and request Qwen's `<tool_call>{"name":"…","arguments":{…}}</tool_call>` format. The existing registry allowlist, approval gate, and action dispatcher remain the authorities for whether a proposed tool can run.

The context is 4,096 tokens with a 1,536-token upper generation cap for tool calls and 512 tokens for ordinary chat. The model is memory-mapped from app-private storage. Native compilation and APK ABI inspection do not prove execution on a physical device; a real ARMv7 phone smoke test is still needed to verify model load, ordinary chat, and a read-only `list_available_tools` call under the device's memory pressure.

## Upstream sources

- [llama.cpp official Android example and library](https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android)
- [llama.cpp source commit used by this release](https://github.com/ggml-org/llama.cpp/tree/e60eff95fda7b6e46bf402a8e9f1e86d88ab29c8)
- [Bartowski Qwen 3 0.6B GGUF artifact](https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF)
- [Qwen3 0.6B base model and license](https://huggingface.co/Qwen/Qwen3-0.6B)
