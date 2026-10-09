package com.opendroid.ai.core.needle

import com.opendroid.ai.data.models.ChatMessage

/** Qwen 3's ChatML message envelope, kept separate from the local inference backend. */
object Qwen3PromptFormatter {
    fun chat(systemPrompt: String, messages: List<ChatMessage>): String {
        val body = messages.takeLast(8).joinToString("\n") { message ->
            val role = if (message.sender == ChatMessage.Sender.USER) "user" else "assistant"
            "<|im_start|>$role\n${escapeUntrustedText(message.text.take(512))}<|im_end|>"
        }
        return format(systemPrompt, Needle3PromptBudget.boundCompletionInput(body))
    }

    fun oneUserMessage(systemPrompt: String, userText: String): String =
        format(systemPrompt, "<|im_start|>user\n${escapeUntrustedText(userText)}<|im_end|>")

    private fun format(systemPrompt: String, body: String): String = buildString {
        append("<|im_start|>system\n")
        append(systemPrompt.trim())
        append("<|im_end|>\n")
        append(body)
        append("\n<|im_start|>assistant\n/no_think\n")
    }

    private fun escapeUntrustedText(value: String): String = value
        .replace("<|im_start|>", "< |im_start|>")
        .replace("<|im_end|>", "< |im_end|>")
        .replace("<|im_sep|>", "< |im_sep|>")
        .replace("<tool_call>", "< tool_call>")
        .replace("</tool_call>", "</ tool_call>")
}
