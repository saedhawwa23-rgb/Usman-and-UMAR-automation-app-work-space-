# Needle 3 integration notes

## Verified upstream sources

- Cactus repository: https://github.com/cactus-compute/needle
- Needle 3 model repository: https://huggingface.co/Cactus-Compute/needle3
- Supported devices guide: https://cactuscompute.com/blog/needle-supported-devices
- On-device deployment article: https://www.mindstudio.ai/blog/cactus-needle-3-on-device-tool-calling-model

## Compatibility facts verified 2026-10-01

- Android platform folders are published for `android-arm64`, `android-armv7`, and `android-riscv64`.
- This app's existing distribution ABI is `armeabi-v7a`; the Android ARMv7 Needle engine is therefore the minimum required runtime. ARM64 can be added without removing ARMv7 support.
- Official native C API: `needle_load`, `needle_init`, `needle_last_error`, `needle_complete`, `needle_embed`, and `needle_reset`.
- The official header documents a process-global, non-thread-safe model; calls must be serialized.
- Tool schemas are JSON arrays with `name`, `description`, and JSON-schema `parameters` objects.
- Completion returns JSON with `type`, `function_calls`, `reasoning`, `confidence`, and optional suppressed/validation fields. Empty `function_calls` is a refusal/unsupported request.
- Needle's official runtime is local/offline after the model and engine are present; it is not a cloud AI provider.
- Official model file: `needle3.cact`, downloaded size 35,335,380 bytes, SHA-256 `c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38`.
- Official Apache-2.0 license file SHA-256: `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
- Published native static library sizes: Android ARMv7 1,291,188 bytes; Android ARM64 1,664,680 bytes.
- Needle 3 is designed for tool calling, structured extraction, and embeddings rather than open-ended chat. The integration must use it for structured action selection and keep existing chat/provider behavior intact.
- Cactus documents confidence gating: act only above a product threshold; otherwise surface confirmation/refusal. Existing app approval and safety controls remain authoritative.

## Implementation constraints

- Preserve package/application ID `com.usmanandumar.app`, app name, existing providers, action dispatcher, approval policy, and Termux allowlist.
- Do not add Gemini, another model, or a cloud inference path for Needle 3.
- Ship the pinned `.cact` model and its Apache-2.0 license as APK assets; copy it into private app storage with visible progress and verify exact size and SHA-256 before native loading. Needle setup must not depend on a runtime model download.
- Serialize native calls because the upstream runtime is process-global and non-thread-safe.
