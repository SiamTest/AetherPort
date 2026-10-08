package com.forgeport.android.ui

import com.forgeport.android.ui.theme.ExpressiveButton as Button
import com.forgeport.android.ui.theme.ExpressiveTonalButton as FilledTonalButton
import com.forgeport.android.ui.theme.ExpressiveOutlinedButton as OutlinedButton
import com.forgeport.android.ui.theme.ExpressiveTextButton as TextButton
import com.forgeport.android.ui.theme.ExpressiveIconButton as IconButton
import com.forgeport.android.ui.theme.ExpressiveFilterChip as FilterChip
import com.forgeport.android.ui.theme.ExpressiveDialog as AlertDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgeport.android.AetherPortViewModel
import com.forgeport.android.model.SavedRepository
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ProjectFolderSettings(vm: AetherPortViewModel, dismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.clearStatus() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::configureZipFolder) }
    AlertDialog(
        onDismissRequest = dismiss, title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Project ZIP folder", style = MaterialTheme.typography.titleMedium)
                Text(vm.zipFolderName.ifBlank { "No folder selected" })
                Text("ZIPs in this folder appear in GitHub and Hugging Face, newest first. Choose a dedicated folder, such as Downloads/AetherPort.")
                FilledTonalButton(onClick = { picker.launch(vm.zipFolderUri?.let(Uri::parse)) }, enabled = !vm.busy) {
                    Text(if (vm.zipFolderUri == null) "Choose folder" else "Change folder")
                }
                if (vm.zipFolderUri != null) TextButton(onClick = vm::clearZipFolder, enabled = !vm.busy) { Text("Disconnect folder") }
                vm.archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                vm.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
internal fun ProjectSourcePicker(vm: AetherPortViewModel, selected: String, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    if (settings) ProjectFolderSettings(vm) { settings = false }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { vm.refreshArchives(); expanded = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(vm.publishProjects.firstOrNull { it.id == selected }?.name ?: "Select project ZIP")
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                    if (vm.archivesLoading) DropdownMenuItem(text = { Text("Refreshing ZIP folder…") }, enabled = false, onClick = {})
                    vm.publishProjects.forEach { project ->
                        DropdownMenuItem(text = { Column {
                            Text(project.name)
                            if (project.timestamp > 0) Text(DateFormat.getDateTimeInstance().format(Date(project.timestamp)), style = MaterialTheme.typography.bodySmall)
                        } }, onClick = { select(project.id); expanded = false })
                    }
                    if (vm.publishProjects.isEmpty() && !vm.archivesLoading) DropdownMenuItem(text = { Text("No ZIPs found") }, enabled = false, onClick = {})
                    DropdownMenuItem(text = { Text("Configure ZIP folder") }, onClick = { expanded = false; settings = true })
                }
            }
            IconButton(onClick = vm::refreshArchives, enabled = !vm.archivesLoading && !vm.busy) { Icon(Icons.Filled.Refresh, "Refresh project ZIPs") }
        }
        if (vm.archivesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.zipFolderUri == null) TextButton(onClick = { settings = true }) { Text("Set ZIP folder in Settings") }
        else Text("${vm.zipFolderName} • newest ZIPs first", style = MaterialTheme.typography.bodySmall)
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
                            Column(Modifier.fillMaxWidth()) {
                                Text(repository.repository)
                                Text(repository.variableName, style = MaterialTheme.typography.labelSmall)
                            }
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
                Column(Modifier.weight(1f)) { Text(item.repository); Text(item.variableName, style = MaterialTheme.typography.labelSmall) }
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
