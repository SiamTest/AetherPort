package com.forgeport.android.repo

import android.content.Context
import com.forgeport.android.data.ProjectStore
import com.forgeport.android.data.VariableStore
import com.forgeport.android.model.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RepositoryService(
    private val context: Context,
    private val projects: ProjectStore,
    private val variables: VariableStore,
) {
    private val identityClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    suspend fun publishGitHub(
        projectName: String,
        repoInput: String,
        branchInput: String,
        commitMessageInput: String,
    ): OperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val repoId = RepoParsing.normalizeGitHubRepo(repoInput)
            val branch = RepoParsing.validateBranch(branchInput)
            val tokenPair = variables.resolveGitHubToken(repoInput)
                ?: error("No matching GitHub token was found. Add ${RepoParsing.githubTokenVariableName(repoInput)} in Variables.")
            val message = commitMessageInput.trim().ifBlank { "Small bug fixes" }.take(240)
            val source = projects.pushSource(projects.resolve(projectName))
            val result = pushWithGit(
                source = source,
                cloneUrl = "https://github.com/$repoId.git",
                username = "x-access-token",
                token = tokenPair.second,
                branch = branch,
                commitMessage = message,
                identity = githubIdentity(tokenPair.second),
            )
            result.copy(message = result.message + " Token: ${tokenPair.first}.")
        }.getOrElse { OperationResult(false, it.cleanMessage()) }
    }

    suspend fun publishHuggingFace(
        projectName: String,
        repoInput: String,
        tokenVariable: String,
        branchInput: String,
        commitMessageInput: String,
    ): OperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val repoId = RepoParsing.normalizeHuggingFaceSpace(repoInput)
            val branch = RepoParsing.validateBranch(branchInput)
            val token = variables.get(tokenVariable) ?: error("Selected Hugging Face token was not found.")
            val username = repoId.substringBefore('/')
            val message = commitMessageInput.trim().ifBlank { "Small bug fixes" }.take(240)
            val source = projects.pushSource(projects.resolve(projectName))
            pushWithGit(
                source = source,
                cloneUrl = "https://huggingface.co/spaces/$repoId.git",
                username = username,
                token = token,
                branch = branch,
                commitMessage = message,
                identity = huggingFaceIdentity(token),
            )
        }.getOrElse { OperationResult(false, it.cleanMessage()) }
    }

    suspend fun downloadHuggingFaceZip(
        repoInput: String,
        tokenVariable: String,
        branchInput: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val repoId = RepoParsing.normalizeHuggingFaceSpace(repoInput)
            val branch = RepoParsing.validateBranch(branchInput)
            val token = variables.get(tokenVariable) ?: error("Selected Hugging Face token was not found.")
            val tempRoot = File(context.cacheDir, "hf-download-${UUID.randomUUID()}").apply { mkdirs() }
            val repoDir = File(tempRoot, "repo")
            try {
                val credentials = UsernamePasswordCredentialsProvider(repoId.substringBefore('/'), token)
                Git.cloneRepository()
                    .setURI("https://huggingface.co/spaces/$repoId.git")
                    .setCredentialsProvider(credentials)
                    .setBranch(branch)
                    .setDirectory(repoDir)
                    .call()
                    .use { }
                File(repoDir, ".git").deleteRecursively()
                val output = File(context.cacheDir, (repoId.substringAfter('/') + "-" + branch + ".zip").sanitizeFileName())
                if (output.exists()) output.delete()
                zipDirectory(repoDir, output)
                output
            } finally {
                tempRoot.deleteRecursively()
            }
        }
    }

    private fun pushWithGit(
        source: File,
        cloneUrl: String,
        username: String,
        token: String,
        branch: String,
        commitMessage: String,
        identity: CommitIdentity,
    ): OperationResult {
        val tempRoot = File(context.cacheDir, "forgeport-push-${UUID.randomUUID()}").apply { mkdirs() }
        val repoDir = File(tempRoot, "repo")
        val credentials = UsernamePasswordCredentialsProvider(username, token)
        try {
            Git.cloneRepository()
                .setURI(cloneUrl)
                .setCredentialsProvider(credentials)
                .setDirectory(repoDir)
                .call()
                .use { git ->
                    checkoutBranch(git, branch)
                    // Publishing always writes the detected project root to the repository root.
                    // The clone's Git directory is preserved for committing and pushing.
                    repoDir.listFiles().orEmpty().filter { it.name != ".git" }.forEach { it.deleteRecursively() }
                    copyTree(source, repoDir)
                    git.add().addFilepattern(".").call()
                    git.add().setUpdate(true).addFilepattern(".").call()
                    val status = git.status().call()
                    if (status.isClean) {
                        return OperationResult(true, "No changes found. Nothing was pushed.")
                    }
                    val commit = git.commit()
                        .setMessage(commitMessage)
                        .setAuthor(identity.name, identity.email)
                        .setCommitter(identity.name, identity.email)
                        .call()
                    git.push()
                        .setCredentialsProvider(credentials)
                        .setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
                        .call()
                    return OperationResult(
                        ok = true,
                        message = "Published successfully to $branch.",
                        log = "Commit ${commit.id.name.take(8)} • ${identity.name}",
                    )
                }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun githubIdentity(token: String): CommitIdentity {
        val user = accountProfile("https://api.github.com/user", token, "GitHub")
        check(user.optString("type") == "User") { "Use a GitHub personal account token to publish as your account." }
        return CommitIdentity.github(user.optString("login"), user.optLong("id"), user.optString("name").takeUnless { it == "null" }.orEmpty())
    }

    private fun huggingFaceIdentity(token: String): CommitIdentity {
        val user = accountProfile("https://huggingface.co/api/whoami-v2", token, "Hugging Face")
        check(user.optString("type") == "user") { "Use a Hugging Face personal account token to publish as your account." }
        return CommitIdentity.huggingFace(user.optString("name"), user.optString("fullname").takeUnless { it == "null" }.orEmpty(),
            user.optString("email"), user.optBoolean("emailVerified"))
    }

    private fun accountProfile(url: String, token: String, service: String): JSONObject {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .header("Accept", "application/json").build()
        return identityClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "$service account lookup failed (${response.code}). Check the selected token and try again." }
            JSONObject(requireNotNull(response.body) { "$service account response was empty." }.string())
        }
    }

    private fun checkoutBranch(git: Git, branch: String) {
        val repo = git.repository
        val local = repo.findRef("refs/heads/$branch")
        if (local != null) {
            git.checkout().setName(branch).call()
            return
        }
        val remote = repo.findRef("refs/remotes/origin/$branch")
        if (remote != null) {
            git.checkout()
                .setCreateBranch(true)
                .setName(branch)
                .setStartPoint("origin/$branch")
                .call()
            return
        }
        val head = repo.resolve(Constants.HEAD) ?: error("Repository has no commits to branch from.")
        git.checkout().setCreateBranch(true).setName(branch).setStartPoint(head.name).call()
    }

    private fun copyTree(source: File, destination: File) {
        destination.mkdirs()
        source.listFiles().orEmpty().forEach { item ->
            if (item.name in setOf(".git", "__MACOSX", ".DS_Store", ".hf_uploader_meta.json", ".forgeport-project.json")) return@forEach
            val target = File(destination, item.name)
            if (item.isDirectory) {
                copyTree(item, target)
            } else {
                target.parentFile?.mkdirs()
                Files.copy(item.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun zipDirectory(source: File, output: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            val root = source.canonicalFile
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative))
                BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun Throwable.cleanMessage(): String = message?.lineSequence()?.firstOrNull()?.take(500)
        ?: "Repository operation failed."

    private fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.', '_').ifBlank { "repository" }
}
