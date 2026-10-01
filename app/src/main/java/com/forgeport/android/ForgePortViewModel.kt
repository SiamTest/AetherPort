package com.forgeport.android

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgeport.android.data.ProjectStore
import com.forgeport.android.data.VariableStore
import com.forgeport.android.model.GoogleTokenBundle
import com.forgeport.android.model.OperationResult
import com.forgeport.android.model.SecretVariable
import com.forgeport.android.model.StagedProject
import com.forgeport.android.oauth.GoogleOAuthService
import com.forgeport.android.repo.RepositoryService
import com.forgeport.android.update.AppUpdate
import com.forgeport.android.update.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ForgePortViewModel(application: Application) : AndroidViewModel(application) {
    private val projectStore = ProjectStore(application)
    private val variableStore = VariableStore(application)
    private val repositoryService = RepositoryService(application, projectStore, variableStore)
    private val googleOAuthService = GoogleOAuthService(application)
    private val updateService = UpdateService(application)
    private var startupUpdateCheckDone = false

    var projects by mutableStateOf<List<StagedProject>>(emptyList())
        private set
    var variables by mutableStateOf<List<SecretVariable>>(emptyList())
        private set
    var hfTokenNames by mutableStateOf<List<String>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var operationLog by mutableStateOf("")
        private set
    var pendingHfZip by mutableStateOf<File?>(null)
        private set
    var googleTokenBundle by mutableStateOf<GoogleTokenBundle?>(null)
        private set
    var availableUpdate by mutableStateOf<AppUpdate?>(null)
        private set
    var updateChecking by mutableStateOf(false)
        private set
    var updateDownloading by mutableStateOf(false)
        private set
    var updateDownloadProgress by mutableStateOf(0f)
        private set
    var updateDownloadedBytes by mutableStateOf(0L)
        private set
    var updateDownloadTotalBytes by mutableStateOf(0L)
        private set
    var downloadedUpdateApk by mutableStateOf<File?>(null)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set
    var showUpdatePrompt by mutableStateOf(false)
        private set

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            projects = projectStore.listProjects()
            refreshVariables()
        }
    }

    fun stageZip(uri: Uri) {
        runBusy {
            val project = projectStore.stageZip(uri)
            projects = projectStore.listProjects()
            statusMessage = "${project.name} staged locally."
        }
    }

    fun deleteProject(name: String) {
        runBusy {
            projectStore.delete(name)
            projects = projectStore.listProjects()
            statusMessage = "$name deleted."
        }
    }

    fun saveVariable(name: String, value: String) {
        runBusy {
            withContext(Dispatchers.IO) { variableStore.put(name, value) }
            refreshVariables()
            statusMessage = "Variable saved."
        }
    }

    fun deleteVariable(name: String) {
        runBusy {
            withContext(Dispatchers.IO) { variableStore.delete(name) }
            refreshVariables()
            statusMessage = "$name deleted."
        }
    }

    fun publishGitHub(
        project: String,
        repo: String,
        branch: String,
        commitMessage: String,
        unwrap: Boolean,
        targetPath: String,
    ) {
        runBusy {
            val result = repositoryService.publishGitHub(project, repo, branch, commitMessage, unwrap, targetPath)
            applyResult(result)
        }
    }

    fun publishHuggingFace(
        project: String,
        repo: String,
        tokenVariable: String,
        branch: String,
        commitMessage: String,
        unwrap: Boolean,
        targetPath: String,
    ) {
        runBusy {
            val result = repositoryService.publishHuggingFace(
                project,
                repo,
                tokenVariable,
                branch,
                commitMessage,
                unwrap,
                targetPath,
            )
            applyResult(result)
        }
    }

    fun prepareHfDownload(repo: String, tokenVariable: String, branch: String) {
        runBusy {
            repositoryService.downloadHuggingFaceZip(repo, tokenVariable, branch)
                .onSuccess {
                    pendingHfZip?.delete()
                    pendingHfZip = it
                    statusMessage = "Repository ZIP is ready to save."
                    operationLog = ""
                }
                .onFailure {
                    statusMessage = it.message ?: "Hugging Face download failed."
                    operationLog = ""
                }
        }
    }

    fun clearPendingHfZip() {
        pendingHfZip?.delete()
        pendingHfZip = null
    }

    fun generateGoogleTokens(credentialsUri: Uri, scopesText: String) {
        runBusy {
            val scopes = scopesText
                .split(Regex("[\\s,]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            googleTokenBundle = googleOAuthService.authorizeWithDesktopCredentials(credentialsUri, scopes)
            statusMessage = "Google credentials generated. Save token.pickle and token.json now."
        }
    }

    suspend fun saveBytes(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: error("Could not open the selected save location.")
    }

    suspend fun saveFile(uri: Uri, file: File) = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
            out.flush()
        } ?: error("Could not open the selected save location.")
    }

    fun clearStatus() {
        statusMessage = null
        operationLog = ""
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (silent && startupUpdateCheckDone) return
        if (updateChecking || updateDownloading) return
        if (silent) startupUpdateCheckDone = true
        viewModelScope.launch {
            updateChecking = true
            if (!silent) updateMessage = "Checking GitHub Releases…"
            try {
                val update = withContext(Dispatchers.IO) { updateService.checkForUpdate() }
                availableUpdate = update
                if (update == null) {
                    downloadedUpdateApk = null
                    showUpdatePrompt = false
                    if (!silent) updateMessage = "ForgePort is up to date."
                } else {
                    downloadedUpdateApk = withContext(Dispatchers.IO) {
                        updateService.downloadedFile(update).takeIf { updateService.hasCompleteDownload(update) }
                    }
                    updateMessage = "ForgePort ${update.versionName} is available."
                    showUpdatePrompt = silent
                }
            } catch (t: Throwable) {
                if (!silent) updateMessage = t.message ?: "Could not check for updates."
            } finally {
                updateChecking = false
            }
        }
    }

    fun downloadUpdate() {
        val update = availableUpdate ?: return
        if (updateDownloading) return
        viewModelScope.launch {
            updateDownloading = true
            updateDownloadProgress = 0f
            updateDownloadedBytes = 0L
            updateDownloadTotalBytes = update.apkSizeBytes
            updateMessage = "Downloading ForgePort ${update.versionName}…"
            try {
                val file = withContext(Dispatchers.IO) {
                    updateService.download(update) { downloaded, total ->
                        viewModelScope.launch {
                            updateDownloadedBytes = downloaded
                            updateDownloadTotalBytes = total
                            updateDownloadProgress = if (total > 0L) {
                                (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        }
                    }
                }
                downloadedUpdateApk = file
                updateDownloadProgress = 1f
                updateMessage = "Update downloaded and verified. Ready to install."
            } catch (t: Throwable) {
                downloadedUpdateApk = null
                updateMessage = t.message ?: "Update download failed."
            } finally {
                updateDownloading = false
            }
        }
    }

    fun dismissUpdatePrompt() {
        showUpdatePrompt = false
    }

    fun noteInstallerPermissionRequired() {
        updateMessage = "Allow ForgePort to install unknown apps, then return and tap Install update again."
    }

    fun reportUpdateInstallError(message: String) {
        updateMessage = message.ifBlank { "Could not open the Android package installer." }
    }

    private suspend fun refreshVariables() {
        variables = withContext(Dispatchers.IO) { variableStore.list() }
        hfTokenNames = withContext(Dispatchers.IO) { variableStore.huggingFaceTokenNames() }
    }

    private fun applyResult(result: OperationResult) {
        statusMessage = result.message
        operationLog = result.log
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            statusMessage = null
            operationLog = ""
            try {
                block()
            } catch (t: Throwable) {
                statusMessage = t.message ?: "Operation failed."
            } finally {
                busy = false
            }
        }
    }
}
