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
- `libneedlebridge.so` links against `libc++_shared.so`; bundle the NDK-matched shared C++ runtime under `app/src/main/jniLibs/armeabi-v7a/` and `app/src/main/jniLibs/arm64-v8a/`. Verify each packaged ELF ABI and all `DT_NEEDED` dependencies in release artifacts. A successful static/package audit is not a substitute for loading the library on a physical device of the target ABI.
- Keep Needle's native static-prefix text and compacted tool JSON at or below 3,000 UTF-8 bytes, reserving context for the native template. Bound completion input to 2,500 UTF-8 bytes; cap ordinary chat at 512 output tokens and tool planning at 1,536. Connector schemas use concise aliases/descriptions and omit empty parameter blocks while preserving executable parameter types, required fields, and enum values.
- In chat mode, require natural-language text and normalize text/result envelopes instead of displaying raw JSON. Parse registered `type: call`/function-call envelopes only for action-plan requests, then route them through the existing validation, approval, and execution pipeline. Show a provider-specific thinking label while Needle inference is running.
- Return a deterministic natural-language response for simple greetings before invoking the model. If inference returns malformed/truncated structured output, never expose the raw fragment in chat; use a user-friendly fallback instead.
- Map unique model-facing connector aliases back to canonical qualified registry names before dispatch. Recover a complete allowlisted `"tool"` name from truncated JSON only as a zero-argument call; all registry lookup, argument validation, connector policy, and approval checks still apply. The `list_available_tools` request has a compact example and deterministic routing to the read-only local registry tool.
