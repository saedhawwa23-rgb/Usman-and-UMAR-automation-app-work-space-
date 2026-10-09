package com.opendroid.llama

/** Thin JNI surface over the bundled llama.cpp CPU runtime. */
object Qwen3Native {
    val isSupported: Boolean
    private var loadError: String = ""

    init {
        isSupported = try {
            System.loadLibrary("qwenbridge")
            true
        } catch (error: Throwable) {
            loadError = "Qwen 3 native runtime unavailable: ${error.javaClass.simpleName}"
            false
        }
    }

    fun loadModel(path: String): Boolean = isSupported && nativeLoadModel(path)

    fun createContext(contextTokens: Int = DEFAULT_CONTEXT_TOKENS): Boolean =
        isSupported && nativeCreateContext(contextTokens)

    fun generate(prompt: String, maxNewTokens: Int): String? =
        if (isSupported) nativeGenerate(prompt, maxNewTokens) else null

    fun lastError(): String = if (isSupported) nativeLastError() else loadError

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeCreateContext(contextTokens: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxNewTokens: Int): String?
    private external fun nativeLastError(): String

    private const val DEFAULT_CONTEXT_TOKENS = 4096
}
