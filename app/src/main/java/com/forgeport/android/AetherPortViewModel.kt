package com.forgeport.android

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
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
import com.forgeport.android.model.ProjectArchive
import com.forgeport.android.model.PublishProject
import com.forgeport.android.model.SavedRepository
import com.forgeport.android.model.failureOrNull
import com.forgeport.android.repo.RepoParsing
import com.forgeport.android.oauth.GoogleOAuthService
import com.forgeport.android.repo.RepositoryService
import com.forgeport.android.update.AppUpdate
import com.forgeport.android.update.UpdateInstaller
import com.forgeport.android.update.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream

class AetherPortViewModel(application: Application) : AndroidViewModel(application) {
    private val projectStore = ProjectStore(application)
    private val variableStore = VariableStore(application)
    private val repositoryService = RepositoryService(application, projectStore, variableStore)
    private val googleOAuthService = GoogleOAuthService(application)
    private val updateService = UpdateService(application)
    private val updatePreferences = application.getSharedPreferences("forgeport_updates", Context.MODE_PRIVATE)
    private var startupUpdateCheckDone = false

    var variables by mutableStateOf<List<SecretVariable>>(emptyList())
        private set
    var credentialBackupMessage by mutableStateOf<String?>(null)
        private set
    var credentialBackupError by mutableStateOf<String?>(null)
        private set
    var hfTokenNames by mutableStateOf<List<String>>(emptyList())
        private set
    var savedRepositories by mutableStateOf<List<SavedRepository>>(emptyList())
        private set
    var projectArchives by mutableStateOf<List<ProjectArchive>>(emptyList())
        private set
    var zipFolderUri by mutableStateOf(projectStore.folderUri)
        private set
    var zipFolderName by mutableStateOf(projectStore.folderName)
        private set
    var downloadFolderEnabled by mutableStateOf(projectStore.downloadFolderEnabled)
        private set
    fun hasDownloadAccess(): Boolean = projectStore.hasDownloadAccess()
    var archiveError by mutableStateOf<String?>(null)
        private set
    var archiveDeleteError by mutableStateOf<String?>(null)
        private set
    var deletingArchiveId by mutableStateOf<String?>(null)
        private set
    var archivesLoading by mutableStateOf(false)
        private set

    /** Pause periodic disk scans while a user scrolls the ZIP chooser. */
    var archivePickerVisible by mutableStateOf(false)
        private set

    fun updateArchivePickerVisibility(visible: Boolean) { archivePickerVisible = visible }
    private var archiveRequest = 0
    private var archiveJob: Job? = null
    val publishProjects by derivedStateOf {
        projectArchives.map { PublishProject("zip:${it.uri}", it.name, it.modifiedAt, it.size) }
    }
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
    var updateRepository by mutableStateOf(updateService.repository)
        private set
    var automaticUpdatePopups by mutableStateOf(
        updatePreferences.getBoolean("automatic_update_popups", true),
    )
        private set
    var updateInstallPending by mutableStateOf(false)
        private set
    var updateInstalling by mutableStateOf(false)
        private set

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            refreshVariables()
        }
        refreshArchives()
    }

    fun configureZipFolder(uri: Uri) {
        runBusy {
            projectStore.configureFolder(uri)
            zipFolderUri = projectStore.folderUri
            zipFolderName = projectStore.folderName
            downloadFolderEnabled = projectStore.downloadFolderEnabled
            projectArchives = emptyList()
            refreshArchives()
        }
    }

    fun useDownloadFolder() {
        runBusy {
            projectStore.configureDownloadFolder()
            zipFolderUri = projectStore.folderUri
            zipFolderName = projectStore.folderName
            downloadFolderEnabled = projectStore.downloadFolderEnabled
            projectArchives = emptyList()
            refreshArchives()
        }
    }

    fun reportDownloadPermissionFailure(message: String = "Storage access was not granted. Your previous ZIP folder is unchanged.") {
        statusMessage = message
    }

    fun clearZipFolder() {
        runBusy {
            projectStore.clearFolder()
            zipFolderUri = null
            zipFolderName = ""
            downloadFolderEnabled = false
            projectArchives = emptyList()
            refreshArchives()
        }
    }

    fun refreshArchives(silent: Boolean = false) {
        if (silent && archiveJob?.isActive == true) return
        val request = ++archiveRequest
        archiveJob?.cancel()
        archiveJob = viewModelScope.launch {
            if (!silent) archivesLoading = true
            archiveError = null
            try {
                val archives = projectStore.listArchives()
                if (request == archiveRequest) projectArchives = archives
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == archiveRequest) { projectArchives = emptyList(); archiveError = failure.message ?: "Could not read the ZIP folder." }
            } finally { if (request == archiveRequest) archivesLoading = false }
        }
    }

    /** Delete from storage and refresh so the selected ZIP falls back to the newest survivor. */
    fun deleteArchive(id: String) {
        if (busy || deletingArchiveId != null) return
        val existing = publishProjects.firstOrNull { it.id == id } ?: run {
            archiveDeleteError = "ZIP is no longer available. Refresh the list."
            return
        }
        if (!existing.id.startsWith("zip:")) return
        deletingArchiveId = id
        busy = true
        archiveDeleteError = null
        viewModelScope.launch {
            try {
                val uri = existing.id.removePrefix("zip:")
                projectStore.deleteArchive(uri)
                // Prevent an in-flight scan from repopulating a deleted entry.
                archiveRequest++
                archiveJob?.cancel()
                projectArchives = projectArchives.filterNot { it.uri == uri }
                refreshArchives()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                archiveDeleteError = failure.message ?: "Unable to delete the ZIP from storage."
                refreshArchives()
            } finally {
                deletingArchiveId = null
                busy = false
            }
        }
    }

    fun clearArchiveDeleteError() { archiveDeleteError = null }

    fun saveVariable(name: String, value: String) {
        runBusy {
            withContext(Dispatchers.IO) { variableStore.put(name, value) }
            refreshVariables()
        }
    }

    fun deleteVariable(name: String) {
        runBusy {
            withContext(Dispatchers.IO) { variableStore.delete(name) }
            refreshVariables()
        }
    }

    fun clearCredentialBackupStatus() {
        credentialBackupMessage = null
        credentialBackupError = null
    }

    /** Uses Android's document picker; no storage permission or device-specific key export. */
    fun exportCredentials(uri: Uri, password: String) {
        if (busy) return
        clearCredentialBackupStatus()
        runBusy {
            val secret = password.toCharArray()
            try {
                withContext(Dispatchers.IO) {
                    val bytes = try { variableStore.exportPortableBackup(secret) } finally { secret.fill('\u0000') }
                    try {
                        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                            stream.write(bytes)
                            stream.flush()
                        } ?: error("Could not open the selected backup destination.")
                    } finally { bytes.fill(0) }
                }
                credentialBackupMessage = "Encrypted credential backup exported. Keep your password safe."
            } catch (failure: Exception) {
                credentialBackupError = "Export failed: ${failure.message ?: "Could not save backup."}"
            } finally { secret.fill('\u0000') }
        }
    }

    fun importCredentials(uri: Uri, password: String, replaceExisting: Boolean) {
        if (busy) return
        clearCredentialBackupStatus()
        runBusy {
            val secret = password.toCharArray()
            try {
                val report = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            require(output.size() + count <= 2 * 1024 * 1024) { "Backup file is too large." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("Could not read the selected backup file.")
                    try { variableStore.importPortableBackup(bytes, secret, replaceExisting) }
                    finally { secret.fill('\u0000'); bytes.fill(0) }
                }
                refreshVariables()
                credentialBackupMessage = "Imported ${report.restored} variable(s); skipped ${report.skipped} existing variable(s)."
            } catch (failure: Exception) {
                credentialBackupError = "Import failed: ${failure.message ?: "Could not restore credentials."}"
            } finally { secret.fill('\u0000') }
        }
    }

    fun saveRepository(label: String, repository: String, huggingFace: Boolean, previousName: String? = null) {
        runBusy {
            withContext(Dispatchers.IO) {
                val name = RepoParsing.repositoryVariableName(label, huggingFace)
                variableStore.put(name, repository)
                if (previousName != null && previousName != name && RepoParsing.repositoryKind(previousName) != null) variableStore.delete(previousName)
            }
            refreshVariables()
        }
    }

    fun publishGitHub(
        project: String,
        repo: String,
        branch: String,
        commitMessage: String,
    ) {
        runBusy {
            val result = withPublishProject(project) { staged ->
                repositoryService.publishGitHub(staged, repo, branch, commitMessage)
            }
            applyResult(result)
        }
    }

    fun publishHuggingFace(
        project: String,
        repo: String,
        tokenVariable: String,
        branch: String,
        commitMessage: String,
    ) {
        runBusy {
            val result = withPublishProject(project) { staged -> repositoryService.publishHuggingFace(
                staged,
                repo,
                tokenVariable,
                branch,
                commitMessage,
            ) }
            applyResult(result)
        }
    }

    fun prepareHfDownload(repo: String, tokenVariable: String, branch: String) {
        runBusy {
            repositoryService.downloadHuggingFaceZip(repo, tokenVariable, branch)
                .onSuccess {
                    pendingHfZip?.delete()
                    pendingHfZip = it
                    // The Save ZIP action becomes available; no success banner is necessary.
                    clearStatus()
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
            // Save token.pickle and token.json buttons are shown by the UI.
            clearStatus()
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
        if (updateChecking || updateDownloading || updateInstalling) return
        if (silent) startupUpdateCheckDone = true
        viewModelScope.launch {
            updateChecking = true
            if (!silent) updateMessage = "Checking GitHub Releases…"
            try {
                val update = withContext(Dispatchers.IO) { updateService.checkForUpdate() }
                availableUpdate = update
                if (update == null) {
                    downloadedUpdateApk = null
                    updateInstallPending = false
                    showUpdatePrompt = false
                    if (!silent) updateMessage = "AetherPort is up to date."
                } else {
                    downloadedUpdateApk = withContext(Dispatchers.IO) {
                        updateService.downloadedFile(update).takeIf { updateService.hasCompleteDownload(update) }
                    }
                    updateMessage = "AetherPort ${update.versionName} is available."
                    showUpdatePrompt = silent && automaticUpdatePopups
                }
            } catch (t: Throwable) {
                if (!silent) updateMessage = t.message ?: "Could not check for updates."
            } finally {
                updateChecking = false
            }
        }
    }

    fun configureUpdateRepository(value: String) {
        if (updateChecking || updateDownloading || updateInstalling) return
        try {
            updateService.configureRepository(value)
            updateRepository = updateService.repository
            availableUpdate = null
            downloadedUpdateApk = null
            showUpdatePrompt = false
            updateInstallPending = false
            checkForUpdates()
        } catch (failure: IllegalArgumentException) { updateMessage = failure.message }
    }

    fun updateAutomaticUpdatePopups(enabled: Boolean) {
        automaticUpdatePopups = enabled
        updatePreferences.edit().putBoolean("automatic_update_popups", enabled).apply()
        if (!enabled) showUpdatePrompt = false
    }

    fun downloadUpdate() {
        val update = availableUpdate ?: return
        if (updateDownloading || updateInstalling) return
        if (downloadedUpdateApk?.isFile == true) {
            installDownloadedUpdate()
            return
        }
        viewModelScope.launch {
            updateDownloading = true
            updateInstallPending = false
            updateDownloadProgress = 0f
            updateDownloadedBytes = 0L
            updateDownloadTotalBytes = update.apkSizeBytes
            updateMessage = "Downloading AetherPort ${update.versionName}…"
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
                updateMessage = "Update downloaded and verified. Opening installer…"
                updateDownloading = false
                installDownloadedUpdate()
            } catch (t: Throwable) {
                downloadedUpdateApk = null
                updateMessage = t.message ?: "Update download failed."
                updateDownloading = false
            }
        }
    }

    fun installDownloadedUpdate() {
        val apk = downloadedUpdateApk ?: return
        if (updateDownloading || updateInstalling) return
        val application = getApplication<Application>()
        if (!UpdateInstaller.canInstallPackages(application)) {
            updateInstallPending = true
            updateMessage = "Allow AetherPort to install updates. Installation will continue automatically when you return."
            runCatching { UpdateInstaller.requestInstallPermission(application) }
                .onFailure { error ->
                    updateInstallPending = false
                    updateMessage = error.message ?: "Could not open the install-apps permission screen."
                }
            return
        }

        updateInstalling = true
        updateInstallPending = false
        updateMessage = "Opening Android package installer…"
        runCatching { UpdateInstaller.launchInstaller(application, apk) }
            .onFailure { error ->
                updateMessage = error.message ?: "Could not open the Android package installer."
            }
        updateInstalling = false
    }

    fun resumePendingUpdateInstall() {
        if (!updateInstallPending) return
        val application = getApplication<Application>()
        if (UpdateInstaller.canInstallPackages(application)) {
            installDownloadedUpdate()
        } else {
            updateInstallPending = false
            updateMessage = "Install permission was not granted. Tap Install update to try again."
        }
    }

    fun dismissUpdatePrompt() {
        showUpdatePrompt = false
    }

    private suspend fun refreshVariables() {
        variables = withContext(Dispatchers.IO) { variableStore.list() }
        hfTokenNames = withContext(Dispatchers.IO) { variableStore.huggingFaceTokenNames() }
        savedRepositories = withContext(Dispatchers.IO) { variableStore.savedRepositories() }
    }

    private suspend fun withPublishProject(id: String, publish: suspend (String) -> OperationResult): OperationResult = withContext(Dispatchers.IO) {
        check(publishProjects.any { it.id == id }) { "Select an available project ZIP." }
        require(id.startsWith("zip:")) { "Invalid project selection." }
        val temporary = projectStore.stageArchive(id.removePrefix("zip:"))
        try { publish(temporary.name) }
        finally { withContext(NonCancellable) { projectStore.delete(temporary.name) } }
    }

    private fun applyResult(result: OperationResult) {
        val failure = result.failureOrNull()
        statusMessage = failure?.message?.ifBlank { "Publishing failed." }
        operationLog = failure?.log.orEmpty()
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            statusMessage = null
            operationLog = ""
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                statusMessage = t.message ?: "Operation failed."
            } finally {
                busy = false
            }
        }
    }
}
