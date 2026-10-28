package com.forgeport.android.repo

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

internal const val GIT_TRANSFER_TIMEOUT_SECONDS = 120

internal fun cloneForTransfer(
    url: String,
    credentials: UsernamePasswordCredentialsProvider,
    directory: File,
    branch: String? = null,
): Git = retryGitTransfer("Clone") {
    // Only callers' disposable clone directories are passed here. A failed clone
    // must not leave a partial index or pack for the next attempt to reuse.
    check(!directory.exists() || directory.deleteRecursively()) { "Could not clear the temporary clone." }
    Git.cloneRepository()
        .setURI(url)
        .setCredentialsProvider(credentials)
        .setDirectory(directory)
        .setTimeout(GIT_TRANSFER_TIMEOUT_SECONDS)
        .apply { if (branch != null) setBranch(branch) }
        .call()
}

internal fun <T> retryGitTransfer(operation: String, transfer: () -> T): T {
    for (attempt in 0..1) {
        try {
            return transfer()
        } catch (error: Exception) {
            if (Thread.currentThread().isInterrupted || error is java.util.concurrent.CancellationException) throw error
            val interrupted = generateSequence<Throwable>(error) { it.cause }.take(12).any {
                it is EOFException || it is SocketException || it is SocketTimeoutException ||
                    // JGit's HTTP push reader can replace EOF with this transport error
                    // when the response ends and no further request is pending.
                    (it is org.eclipse.jgit.errors.TransportException &&
                        it.message.orEmpty().contains("Starting read stage without written request data pending"))
            }
            if (interrupted && attempt == 0) continue
            val hint = if (interrupted) {
                if (operation == "Push") "The server may already have accepted the commits. Retry publishing to check its current state."
                else "Check your connection and retry."
            } else ""
            throw IOException(
                "$operation failed${if (interrupted) " after one automatic retry" else ""}. $hint".trim(), error,
            )
        }
    }
    error("Unreachable transfer attempt.")
}

internal fun cleanGitError(error: Throwable): String {
    val details = generateSequence(error) { it.cause }.take(12)
        .mapNotNull { it.message?.takeIf(String::isNotBlank) }.distinct().joinToString("\n")
        .ifBlank { "Repository operation failed." }
    return details
        .replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"), "")
        .replace(Regex("(?:hf_|gh[pousr]_|github_pat_)[A-Za-z0-9_]+"), "[redacted]")
        .replace(Regex("(?i)(https?://)[^/\\s@]+@"), "$1[redacted]@")
        .replace(Regex("(?i)(Authorization:\\s*(?:Bearer|Basic)\\s+)\\S+"), "$1[redacted]")
        .take(1500)
}
