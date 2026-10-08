package com.forgeport.android.ui

import com.forgeport.android.ui.theme.ExpressiveButton as Button
import com.forgeport.android.ui.theme.ExpressiveTonalButton as FilledTonalButton
import com.forgeport.android.ui.theme.ExpressiveOutlinedButton as OutlinedButton
import com.forgeport.android.ui.theme.ExpressiveTextButton as TextButton
import com.forgeport.android.ui.theme.ExpressiveIconButton as IconButton
import com.forgeport.android.ui.theme.ExpressiveFilterChip as FilterChip
import com.forgeport.android.ui.theme.ExpressiveDialog as AlertDialog
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgeport.android.AetherPortViewModel
import com.forgeport.android.model.PublishProject
import com.forgeport.android.model.selectedArchiveId
import com.forgeport.android.model.SavedRepository
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ProjectFolderSettings(vm: AetherPortViewModel, dismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.clearStatus() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::configureZipFolder) }
    val context = LocalContext.current
    val allFiles = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.hasDownloadAccess()) vm.useDownloadFolder() else vm.reportDownloadPermissionFailure()
    }
    val legacyStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && vm.hasDownloadAccess()) vm.useDownloadFolder() else vm.reportDownloadPermissionFailure()
    }
    fun useDownload() {
        if (vm.hasDownloadAccess()) vm.useDownloadFolder()
        else if (Build.VERSION.SDK_INT >= 30) {
            val intents = listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
            if (intents.none { intent -> runCatching { allFiles.launch(intent) }.isSuccess }) {
                vm.reportDownloadPermissionFailure("Open Android Settings, search for All files access, and enable AetherPort. Then tap Use Download again.")
            }
        } else legacyStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    AlertDialog(
        onDismissRequest = dismiss, title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Project ZIP folder", style = MaterialTheme.typography.titleMedium)
                Text(vm.zipFolderName.ifBlank { "No folder selected" })
                Text("ZIPs in the selected folder appear in GitHub and Hugging Face, newest first.")
                if (Build.VERSION.SDK_INT >= 30) Text("Using Download directly requires Android's All files access. This permission allows access across shared storage; AetherPort uses it to read ZIP files directly in Download. Root is not required.")
                else Text("Using Download directly requires storage permission. AetherPort reads its ZIP files without changing the originals.")
                FilledTonalButton(onClick = ::useDownload, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.downloadFolderEnabled && !vm.hasDownloadAccess()) "Allow Download access" else "Use Download")
                }
                OutlinedButton(onClick = { picker.launch(vm.zipFolderUri?.let(Uri::parse)) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.zipFolderUri == null) "Choose folder" else "Change folder")
                }
                if (vm.zipFolderUri != null || vm.downloadFolderEnabled) TextButton(onClick = vm::clearZipFolder, enabled = !vm.busy) { Text("Disconnect folder") }
                vm.archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                vm.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
internal fun ProjectSourcePicker(vm: AetherPortViewModel, selected: String, select: (String) -> Unit) {
    var chooser by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    if (settings) ProjectFolderSettings(vm) { settings = false }
    if (chooser) {
        DisposableEffect(vm) {
            vm.setArchivePickerVisible(true)
            onDispose { vm.setArchivePickerVisible(false) }
        }
        ArchiveChooserDialog(
            vm = vm,
            selected = selected,
            select = { id -> select(id); chooser = false },
            dismiss = { chooser = false },
            openSettings = { chooser = false; settings = true },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.refreshArchives(); chooser = true }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                Text(vm.publishProjects.firstOrNull { it.id == selected }?.name ?: "Select project ZIP")
            }
            IconButton(onClick = vm::refreshArchives, enabled = !vm.archivesLoading && !vm.busy) { Icon(Icons.Filled.Refresh, "Refresh project ZIPs") }
        }
        if (vm.archivesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.zipFolderUri == null && !vm.downloadFolderEnabled) TextButton(onClick = { settings = true }) { Text("Set ZIP folder in Settings") }
        else Text("${vm.zipFolderName} • newest ZIPs first", style = MaterialTheme.typography.bodySmall)
    }
}

/** A bounded modal: only the virtualized ZIP list scrolls; the header and actions stay fixed.
 * Using a direct Dialog avoids nesting LazyColumn inside ExpressiveDialog's scrollable text slot.
 */
@Composable
private fun ArchiveChooserDialog(
    vm: AetherPortViewModel,
    selected: String,
    select: (String) -> Unit,
    dismiss: () -> Unit,
    openSettings: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance() }
    val scroll = rememberLazyListState()
    val dialogHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    Dialog(onDismissRequest = dismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).heightIn(max = dialogHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Choose project ZIP", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Select a ZIP to publish. Newest ZIPs appear first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vm.archivesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (vm.publishProjects.isEmpty()) {
                    Text(if (vm.archivesLoading) "Refreshing ZIP folder…" else "No ZIPs found.")
                } else {
                    LazyColumn(
                        state = scroll,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (selected != vm.publishProjects.firstOrNull()?.id) {
                            item(key = "automatic-newest") {
                                Surface(
                                    onClick = { select(vm.publishProjects.first().id) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    Text("Use newest ZIP automatically", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        items(vm.publishProjects, key = { it.id }, contentType = { "archive" }) { project ->
                            // Lightweight, stable rows: no per-row animated card or layout morph.
                            Surface(
                                onClick = { select(project.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = if (project.id == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                                    if (project.timestamp > 0) Text(
                                        remember(project.timestamp) { dateFormat.format(Date(project.timestamp)) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                vm.archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = dismiss) { Text("Close") }
                    TextButton(onClick = openSettings) { Text("ZIP folder Settings") }
                }
            }
        }
    }
}

@Composable
internal fun RepositoryChooser(
    repositories: List<SavedRepository>, huggingFace: Boolean, action: String, dismiss: () -> Unit,
    manage: () -> Unit, select: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss, title = { Text(if (huggingFace) "Choose Hugging Face Space" else "Choose GitHub repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(action)
                if (repositories.isEmpty()) Text("Save repositories in Variables to use them here.")
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(repositories, key = { it.variableName }) { repository ->
                        TextButton(onClick = { select(repository.repository) }, modifier = Modifier.fillMaxWidth()) {
                            Text(repository.repository, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = manage) { Text("Manage in Variables") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SavedRepositoriesEditor(vm: AetherPortViewModel) {
    var huggingFace by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf("") }
    var repository by rememberSaveable { mutableStateOf("") }
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    Text("Saved repositories", style = MaterialTheme.typography.headlineSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(!huggingFace, onClick = { huggingFace = false; editingName = null; label = ""; repository = "" }, label = { Text("GitHub") })
        FilterChip(huggingFace, onClick = { huggingFace = true; editingName = null; label = ""; repository = "" }, label = { Text("Hugging Face") })
    }
    OutlinedTextField(label, { label = it }, label = { Text("Repository label") }, placeholder = { Text("My project") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(repository, { repository = it }, label = { Text(if (huggingFace) "Space repository" else "GitHub repository") },
        placeholder = { Text("owner/repository or URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Saved once as a repository variable. Choose it after tapping Publish or preparing a repository download.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = { vm.saveRepository(label, repository, huggingFace, editingName) }, enabled = label.isNotBlank() && repository.isNotBlank() && !vm.busy,
        modifier = Modifier.fillMaxWidth()) { Text("Save repository") }
    if (editingName != null) TextButton(onClick = { editingName = null; label = ""; repository = "" }) { Text("Add another repository") }
    val entries = vm.savedRepositories.filter { it.huggingFace == huggingFace }
    if (entries.isEmpty()) Text("No saved ${if (huggingFace) "Hugging Face" else "GitHub"} repositories.")
    entries.forEach { item ->
        ElevatedCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.repository, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    editingName = item.variableName
                    label = item.variableName.removePrefix("GITHUB_REPOSITORY_").removePrefix("HF_REPOSITORY_").removePrefix("HUGGINGFACE_REPOSITORY_")
                    repository = item.repository
                }) { Icon(Icons.Filled.Edit, "Edit saved repository") }
                IconButton(onClick = { vm.deleteVariable(item.variableName); if (editingName == item.variableName) { editingName = null; label = ""; repository = "" } }) { Icon(Icons.Filled.Delete, "Delete saved repository") }
            }
        }
    }
}

/** Empty manual selection follows the newest ZIP; explicit older choices remain usable. */
@Composable
internal fun rememberArchiveSelection(archives: List<PublishProject>): Pair<String, (String) -> Unit> {
    var manualId by rememberSaveable { mutableStateOf("") }
    val selected = selectedArchiveId(archives, manualId)
    LaunchedEffect(archives) {
        if (archives.none { it.id == manualId }) manualId = ""
    }
    return selected to { id: String -> manualId = if (id == archives.firstOrNull()?.id) "" else id }
}
