package com.opendroid.ai.core.storage

data class WorkspaceDiffPreview(
    val removedLines: Int,
    val addedLines: Int,
    val text: String,
    val truncated: Boolean
) {
    companion object {
        private const val MAX_CHANGED_LINES_PER_SIDE = 20
        private const val MAX_LINE_CHARS = 300

        fun create(before: String, after: String): WorkspaceDiffPreview {
            if (before == after) return WorkspaceDiffPreview(0, 0, "No changes.", false)

            val oldLines = before.split('\n')
            val newLines = after.split('\n')
            var prefix = 0
            while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++

            var suffix = 0
            while (
                suffix < oldLines.size - prefix &&
                suffix < newLines.size - prefix &&
                oldLines[oldLines.lastIndex - suffix] == newLines[newLines.lastIndex - suffix]
            ) suffix++

            val removed = oldLines.subList(prefix, oldLines.size - suffix)
            val added = newLines.subList(prefix, newLines.size - suffix)
            val truncated = removed.size > MAX_CHANGED_LINES_PER_SIDE || added.size > MAX_CHANGED_LINES_PER_SIDE ||
                (removed + added).any { it.length > MAX_LINE_CHARS }
            val preview = buildList {
                if (prefix > 0) add("  ${clip(oldLines[prefix - 1])}")
                removed.take(MAX_CHANGED_LINES_PER_SIDE).forEach { add("- ${clip(it)}") }
                added.take(MAX_CHANGED_LINES_PER_SIDE).forEach { add("+ ${clip(it)}") }
                if (suffix > 0) add("  ${clip(newLines[newLines.size - suffix])}")
                if (truncated) add("… preview truncated; full file changes are bounded to 100 KB")
            }.joinToString("\n")

            return WorkspaceDiffPreview(removed.size, added.size, preview, truncated)
        }

        private fun clip(line: String): String = if (line.length <= MAX_LINE_CHARS) line else line.take(MAX_LINE_CHARS) + "…"
    }
}
