package com.forgeport.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forgeport.android.ForgePortViewModel
import com.forgeport.android.model.StagedProject
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private data class Destination(val route: String, val title: String)

private val destinations = listOf(
    Destination("home", "Overview"),
    Destination("projects", "Projects"),
    Destination("github", "GitHub"),
    Destination("huggingface", "Hugging Face"),
    Destination("download", "HF Download"),
    Destination("google", "Google OAuth"),
    Destination("variables", "Variables"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgePortApp(vm: ForgePortViewModel = viewModel()) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"
    val title = destinations.firstOrNull { it.route == currentRoute }?.title ?: "ForgePort"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(20.dp)) {
                    Text("ForgePort", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Local Android workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                destinations.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        TextButton(onClick = { scope.launch { drawerState.open() } }) { Text("Menu") }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(vm) { nav.navigate(it) } }
                    composable("projects") { ProjectsScreen(vm) }
                    composable("github") { GitHubScreen(vm) }
                    composable("huggingface") { HuggingFaceScreen(vm) }
                    composable("download") { HfDownloadScreen(vm) }
                    composable("google") { GoogleOAuthScreen(vm) }
                    composable("variables") { VariablesScreen(vm) }
                }
                if (vm.busy) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(28.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Working locally…")
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenColumn(vm: ForgePortViewModel, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vm.statusMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(it)
                    if (vm.operationLog.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(vm.operationLog, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = vm::clearStatus) { Text("Dismiss") }
                }
            }
        }
        content()
    }
}

@Composable
private fun HomeScreen(vm: ForgePortViewModel, open: (String) -> Unit) {
    ScreenColumn(vm) {
        Text("ForgePort now runs on this device. Projects, variables, ZIP processing, and repository operations use local Android resources.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(vm.projects.size.toString(), "Staged projects", Modifier.weight(1f))
            StatCard(vm.variables.size.toString(), "Saved variables", Modifier.weight(1f))
        }
        val cards = listOf(
            Triple("Projects", "Review locally staged ZIP projects.", "projects"),
            Triple("GitHub", "Publish a staged project with an automatically matched GitHub token.", "github"),
            Triple("Hugging Face", "Publish a staged project to a Space repository.", "huggingface"),
            Triple("HF Download", "Create a clean ZIP snapshot from a Space branch.", "download"),
            Triple("Google OAuth", "Generate token.pickle and token.json on the phone.", "google"),
            Triple("Variables", "Store reusable credentials encrypted with Android Keystore.", "variables"),
        )
        cards.forEach { (name, desc, route) ->
            Card(onClick = { open(route) }) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StageZipCard(vm: ForgePortViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::stageZip)
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Upload project ZIP", fontWeight = FontWeight.Bold)
            Text("The ZIP is extracted into ForgePort's private app storage.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                Text("Choose ZIP")
            }
        }
    }
}

@Composable
private fun ProjectsScreen(vm: ForgePortViewModel) {
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    ScreenColumn(vm) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Your staged projects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Stored only on this device until you delete them.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = vm::refreshAll) { Text("Refresh") }
        }
        if (vm.projects.isEmpty()) {
            Card { Text("No staged projects. Upload a ZIP from GitHub, Hugging Face, or HF Download.", Modifier.padding(16.dp)) }
        } else {
            vm.projects.forEach { project ->
                ProjectCard(project, onDelete = { deleteTarget = project.name })
            }
        }
    }
    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete $name?") },
            text = { Text("This removes the local staged project from this device.") },
            confirmButton = { TextButton(onClick = { vm.deleteProject(name); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectCard(project: StagedProject, onDelete: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.Bold)
                Text("${project.fileCount} files • ${humanBytes(project.totalBytes)}", style = MaterialTheme.typography.bodySmall)
                Text("Imported ${DateFormat.getDateTimeInstance().format(Date(project.createdAtEpochMs))}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun ProjectPicker(projects: List<StagedProject>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected.ifBlank { "Select staged project" }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { project ->
                DropdownMenuItem(text = { Text(project.name) }, onClick = { onSelected(project.name); expanded = false })
            }
        }
    }
}

@Composable
private fun TokenPicker(names: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.ifBlank { "Select saved HF token" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            names.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelected(name); expanded = false }) }
        }
    }
}

@Composable
private fun GitHubScreen(vm: ForgePortViewModel) {
    var project by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var commit by remember { mutableStateOf("Small bug fixes") }
    var targetPath by remember { mutableStateOf("") }
    var unwrap by remember { mutableStateOf(true) }
    LaunchedEffect(vm.projects) { if (project.isBlank()) project = vm.projects.firstOrNull()?.name.orEmpty() }

    ScreenColumn(vm) {
        StageZipCard(vm)
        Text("Publish a staged project", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ProjectPicker(vm.projects, project) { project = it }
        OutlinedTextField(repo, { repo = it }, label = { Text("GitHub repository") }, placeholder = { Text("username/repository") }, modifier = Modifier.fillMaxWidth())
        Text("Token is detected automatically from GITHUB_TOKEN_<OWNER> in Variables.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commit, { commit = it }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetPath, { targetPath = it }, label = { Text("Target path (optional)") }, modifier = Modifier.fillMaxWidth())
        CheckRow("Remove one outer wrapper folder", unwrap) { unwrap = it }
        Button(
            onClick = { vm.publishGitHub(project, repo, branch, commit, unwrap, targetPath) },
            enabled = project.isNotBlank() && repo.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Publish to GitHub") }
    }
}

@Composable
private fun HuggingFaceScreen(vm: ForgePortViewModel) {
    var project by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var commit by remember { mutableStateOf("Small bug fixes") }
    var targetPath by remember { mutableStateOf("") }
    var unwrap by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf("") }
    LaunchedEffect(vm.projects) { if (project.isBlank()) project = vm.projects.firstOrNull()?.name.orEmpty() }
    LaunchedEffect(vm.hfTokenNames) { if (token.isBlank()) token = vm.hfTokenNames.firstOrNull().orEmpty() }

    ScreenColumn(vm) {
        StageZipCard(vm)
        Text("Publish a staged project", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ProjectPicker(vm.projects, project) { project = it }
        OutlinedTextField(repo, { repo = it }, label = { Text("Hugging Face Space") }, placeholder = { Text("username/repository") }, modifier = Modifier.fillMaxWidth())
        TokenPicker(vm.hfTokenNames, token) { token = it }
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commit, { commit = it }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetPath, { targetPath = it }, label = { Text("Target path (optional)") }, modifier = Modifier.fillMaxWidth())
        CheckRow("Remove one outer wrapper folder", unwrap) { unwrap = it }
        Button(
            onClick = { vm.publishHuggingFace(project, repo, token, branch, commit, unwrap, targetPath) },
            enabled = project.isNotBlank() && repo.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Publish to Hugging Face") }
    }
}

@Composable
private fun HfDownloadScreen(vm: ForgePortViewModel) {
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var token by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm.hfTokenNames) { if (token.isBlank()) token = vm.hfTokenNames.firstOrNull().orEmpty() }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = vm.pendingHfZip ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                runCatching { vm.saveFile(uri, file) }
                vm.clearPendingHfZip()
            }
        }
    }

    ScreenColumn(vm) {
        StageZipCard(vm)
        Text("Download repository snapshot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(repo, { repo = it }, label = { Text("Hugging Face Space") }, placeholder = { Text("username/repository") }, modifier = Modifier.fillMaxWidth())
        TokenPicker(vm.hfTokenNames, token) { token = it }
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { vm.prepareHfDownload(repo, token, branch) },
            enabled = repo.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Prepare ZIP snapshot") }
        vm.pendingHfZip?.let { file ->
            Button(onClick = { saveLauncher.launch(file.name) }, modifier = Modifier.fillMaxWidth()) { Text("Save ${file.name}") }
        }
    }
}

@Composable
private fun GoogleOAuthScreen(vm: ForgePortViewModel) {
    var scopes by remember { mutableStateOf("https://www.googleapis.com/auth/drive") }
    val coroutineScope = rememberCoroutineScope()
    val credentialsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.generateGoogleTokens(it, scopes) }
    }
    val savePickle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = vm.googleTokenBundle?.tokenPickle ?: return@rememberLauncherForActivityResult
        uri?.let { coroutineScope.launch { vm.saveBytes(it, bytes) } }
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = vm.googleTokenBundle?.tokenJson ?: return@rememberLauncherForActivityResult
        uri?.let { coroutineScope.launch { vm.saveBytes(it, bytes) } }
    }

    ScreenColumn(vm) {
        Text("Generate Google Drive credentials locally", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Use credentials.json from a Google OAuth Desktop application client. ForgePort opens the system browser and receives Google's callback on 127.0.0.1, matching the InstalledAppFlow style used by generate_drive_token.py.")
        OutlinedTextField(scopes, { scopes = it }, label = { Text("Scopes") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { credentialsPicker.launch(arrayOf("application/json", "text/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Choose credentials.json and authorize")
        }
        Text("For long-term refresh tokens, publish the OAuth consent screen to Production. Google can still revoke a refresh token, so no app can guarantee permanent validity.", style = MaterialTheme.typography.bodySmall)
        vm.googleTokenBundle?.let {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Credentials ready", fontWeight = FontWeight.Bold)
                    Text("token.pickle is exported as a google.oauth2.credentials.Credentials pickle with the refresh token and forces google-auth to refresh on first use.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { savePickle.launch("token.pickle") }, modifier = Modifier.fillMaxWidth()) { Text("Save token.pickle") }
                    OutlinedButton(onClick = { saveJson.launch("token.json") }, modifier = Modifier.fillMaxWidth()) { Text("Save token.json") }
                }
            }
        }
    }
}

@Composable
private fun VariablesScreen(vm: ForgePortViewModel) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    ScreenColumn(vm) {
        Text("Encrypted local variables", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Values are encrypted with an AES key held by Android Keystore. They are not uploaded to a ForgePort server.")
        OutlinedTextField(name, { name = it }, label = { Text("Variable name") }, placeholder = { Text("GITHUB_TOKEN_USERNAME") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value,
            { value = it },
            label = { Text("Value") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.saveVariable(name, value); value = "" },
            enabled = name.isNotBlank() && value.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save variable") }
        if (vm.variables.isEmpty()) {
            Card { Text("No saved variables.", Modifier.padding(16.dp)) }
        } else {
            vm.variables.forEach { variable ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(variable.name, fontWeight = FontWeight.Bold)
                            Text(variable.maskedValue, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.deleteVariable(variable.name) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label)
    }
}

private fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return "%.1f %s".format(value, units[index])
}
