package com.forgeport.android.data

import java.io.File

/** Chooses a project root from safely extracted ZIP contents before publishing. */
internal object ArchiveLayout {
    private const val STAGING_METADATA = ".forgeport-project.json"
    private val IGNORED_ARCHIVE_ARTIFACTS = setOf("__MACOSX", ".DS_Store", ".git", ".hf_uploader_meta.json", STAGING_METADATA)

    // These are normally meaningful repository paths rather than names of ZIP wrappers.
    // A ZIP containing only src/ or .github/ should not lose that directory on upload.
    private val PROJECT_DIRECTORIES = setOf(
        ".github", ".gitlab", ".vscode", "src", "app", "lib", "assets", "res", "resources",
        "public", "static", "templates", "images", "docs", "doc", "test", "tests", "android",
        "ios", "web", "windows", "linux", "macos", "gradle", "scripts", "config", "data",
        "build", "dist", "META-INF", "node_modules", "vendor", "bin", "include",
    )

    fun detectPublishRoot(stagedProject: File): File {
        require(stagedProject.isDirectory) { "Staged project was not found." }
        val entries = stagedProject.listFiles().orEmpty().filterNot { it.name in IGNORED_ARCHIVE_ARTIFACTS }
        require(entries.isNotEmpty()) { "The staged project is empty." }

        val soleDirectory = entries.singleOrNull()?.takeIf { it.isDirectory }
        val result = if (soleDirectory != null && soleDirectory.name !in PROJECT_DIRECTORIES) {
            soleDirectory // Exactly one outer wrapper folder; remove only this level.
        } else {
            stagedProject // Mixed files or conventional root folder(s): preserve layout.
        }
        require(result.walkTopDown().any { it.isFile && it.name !in IGNORED_ARCHIVE_ARTIFACTS }) {
            "The staged project contains no publishable files."
        }
        return result
    }
}
