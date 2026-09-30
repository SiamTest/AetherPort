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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ForgePortViewModel(application: Application) : AndroidViewModel(application) {
    private val projectStore = ProjectStore(application)
    private val variableStore = VariableStore(application)
    private val repositoryService = RepositoryService(application, projectStore, variableStore)
    private val googleOAuthService = GoogleOAuthService(application)

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
