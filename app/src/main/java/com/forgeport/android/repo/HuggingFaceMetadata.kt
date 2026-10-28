package com.forgeport.android.repo

import java.io.File

/** Normalize only the known rejected star symbols in a staged Space card's emoji field. */
internal fun normalizeHuggingFaceReadme(root: File) {
    val readme = File(root, "README.md")
    if (!readme.isFile) return
    val bytes = readme.readBytes()
    val text = bytes.toString(Charsets.UTF_8)
    if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return
    val opening = Regex("\\A\\uFEFF?---\\r?\\n").find(text) ?: return
    val closing = Regex("(?m)^---[ \\t]*\\r?(?:\\n|$)").find(text, opening.range.last + 1) ?: return
    val start = opening.range.last + 1
    val metadata = text.substring(start, closing.range.first)
    val fixed = Regex("(?m)^(emoji:[ \\t]*)(['\"]?)[✦✧]\\2(?=[ \\t]*(?:#[^\\r\\n]*)?\\r?$)")
        .replace(metadata) { match ->
            match.groupValues[1] + match.groupValues[2] + "✨" + match.groupValues[2]
        }
    if (fixed != metadata) {
        readme.writeText(text.substring(0, start) + fixed + text.substring(closing.range.first), Charsets.UTF_8)
    }
}
