package com.forgeport.android.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forgeport.android.BuildConfig
import com.forgeport.android.ForgePortViewModel
import com.forgeport.android.R
import com.forgeport.android.model.StagedProject
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private data class Destination(val route: String, val title: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("home", "Overview", Icons.Filled.Home),
    Destination("projects", "Projects", Icons.Filled.Folder),
    Destination("github", "GitHub", Icons.Filled.Code),
    Destination("huggingface", "Hugging Face", Icons.Filled.CloudUpload),
    Destination("download", "HF Download", Icons.Filled.CloudDownload),
    Destination("google", "Google OAuth", Icons.Filled.Key),
    Destination("variables", "Variables", Icons.Filled.Security),
    Destination("updates", "Updates", Icons.Filled.SystemUpdate),
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

    LaunchedEffect(Unit) {
        vm.checkForUpdates(silent = true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.resumePendingUpdateInstall()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_forgeport_mark),
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Column {
                        Text("ForgePort", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Local Android workspace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                destinations.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
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
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    composable("updates") { UpdatesScreen(vm) }
                }
                AnimatedVisibility(
                    visible = vm.updateDownloading || vm.updateInstalling || vm.updateInstallPending,
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 10.dp),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                ) {
                    UpdateProgressBanner(vm)
                }
                if (vm.busy) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ElevatedCard {
                                Row(
                                    Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                                    Text("Working locally…", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val promptedUpdate = vm.availableUpdate
    if (vm.showUpdatePrompt && promptedUpdate != null) {
        AlertDialog(
            onDismissRequest = vm::dismissUpdatePrompt,
            icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
            title = { Text("ForgePort ${promptedUpdate.versionName} is available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A newer ForgePort release is available on GitHub.")
                    if (promptedUpdate.notes.isNotBlank()) {
                        Text(
                            promptedUpdate.notes.take(420),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissUpdatePrompt()
                    vm.downloadUpdate()
                }) { Text("Download update") }
            },
            dismissButton = { TextButton(onClick = vm::dismissUpdatePrompt) { Text("Later") } },
        )
    }
}

@Composable
private fun UpdateProgressBanner(vm: ForgePortViewModel) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            vm.updateDownloading -> "Downloading update"
                            vm.updateInstallPending -> "Waiting for install permission"
                            else -> "Opening installer"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            vm.updateDownloading && vm.updateDownloadTotalBytes > 0L ->
                                "${humanBytes(vm.updateDownloadedBytes)} / ${humanBytes(vm.updateDownloadTotalBytes)}"
                            vm.updateInstallPending -> "Return to ForgePort after allowing update installation."
                            else -> "The verified APK is ready to install."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (vm.updateDownloading) {
                if (vm.updateDownloadTotalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { vm.updateDownloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ScreenColumn(vm: ForgePortViewModel, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vm.statusMessage?.let {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(it, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    if (vm.operationLog.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(vm.operationLog, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    TextButton(onClick = vm::clearStatus, modifier = Modifier.align(Alignment.End)) { Text("Dismiss") }
                }
            }
        }
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionHeader(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HomeScreen(vm: ForgePortViewModel, open: (String) -> Unit) {
    ScreenColumn(vm) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(vm.projects.size.toString(), "Staged projects", Icons.Filled.Folder, Modifier.weight(1f))
            StatCard(vm.variables.size.toString(), "Saved variables", Icons.Filled.Security, Modifier.weight(1f))
        }
        SectionHeader("Workspace")
        val cards = listOf(
            Triple("Projects", "Review projects already staged on this device.", "projects"),
            Triple("GitHub", "Publish with an automatically matched repository-owner token.", "github"),
            Triple("Hugging Face", "Publish a staged project directly to a Space repository.", "huggingface"),
            Triple("HF Download", "Create a clean ZIP snapshot from a Space branch.", "download"),
            Triple("Google OAuth", "Generate token.pickle and token.json locally.", "google"),
            Triple("Variables", "Keep reusable credentials protected by Android Keystore.", "variables"),
            Triple("Updates", "Check, download and install new ForgePort releases.", "updates"),
        )
        cards.forEach { (name, desc, route) ->
            val icon = destinations.first { it.route == route }.icon
            ElevatedCard(onClick = { open(route) }) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StageZipCard(vm: ForgePortViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::stageZip)
    }
    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Upload project ZIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("The archive is extracted into ForgePort's private app storage.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose ZIP")
            }
        }
    }
}

@Composable
private fun ProjectsScreen(vm: ForgePortViewModel) {
    ScreenColumn(vm) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Your staged projects", "Stored only on this device until you delete them.")
            IconButton(onClick = vm::refreshAll) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        }
        if (vm.projects.isEmpty()) {
            ElevatedCard { Text("No staged projects. Upload a ZIP from GitHub, Hugging Face, or HF Download.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            vm.projects.forEach { project -> ProjectCard(project, onDelete = { vm.deleteProject(project.name) }) }
        }
    }
}

@Composable
private fun ProjectCard(project: StagedProject, onDelete: () -> Unit) {
    ElevatedCard {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.padding(11.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${project.fileCount} files • ${humanBytes(project.totalBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Imported ${DateFormat.getDateTimeInstance().format(Date(project.createdAtEpochMs))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete project") }
        }
    }
}

@Composable
private fun ProjectPicker(projects: List<StagedProject>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.ifBlank { "Select staged project" }) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = { onSelected(project.name); expanded = false }) }
        }
    }
}

@Composable
private fun TokenPicker(names: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.ifBlank { "Select saved HF token" }) }
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
        SectionHeader("Publish to GitHub", "The repository-owner token is matched automatically from Variables.")
        ProjectPicker(vm.projects, project) { project = it }
        OutlinedTextField(repo, { repo = it }, label = { Text("GitHub repository") }, placeholder = { Text("username/repository") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commit, { commit = it }, label = { Text("Commit message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetPath, { targetPath = it }, label = { Text("Target path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CheckRow("Remove one outer wrapper folder", unwrap) { unwrap = it }
        Button(onClick = { vm.publishGitHub(project, repo, branch, commit, unwrap, targetPath) }, enabled = project.isNotBlank() && repo.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Code, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Publish to GitHub")
        }
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
        SectionHeader("Publish to Hugging Face", "Push a staged project directly to a Space repository.")
        ProjectPicker(vm.projects, project) { project = it }
        OutlinedTextField(repo, { repo = it }, label = { Text("Hugging Face Space") }, placeholder = { Text("username/repository") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TokenPicker(vm.hfTokenNames, token) { token = it }
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commit, { commit = it }, label = { Text("Commit message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetPath, { targetPath = it }, label = { Text("Target path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CheckRow("Remove one outer wrapper folder", unwrap) { unwrap = it }
        Button(onClick = { vm.publishHuggingFace(project, repo, token, branch, commit, unwrap, targetPath) }, enabled = project.isNotBlank() && repo.isNotBlank() && token.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Publish to Hugging Face")
        }
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
        if (uri != null) scope.launch { runCatching { vm.saveFile(uri, file) }; vm.clearPendingHfZip() }
    }
    ScreenColumn(vm) {
        StageZipCard(vm)
        SectionHeader("HF Download", "Create a clean ZIP snapshot from a Space branch.")
        OutlinedTextField(repo, { repo = it }, label = { Text("Hugging Face Space") }, placeholder = { Text("username/repository") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TokenPicker(vm.hfTokenNames, token) { token = it }
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.prepareHfDownload(repo, token, branch) }, enabled = repo.isNotBlank() && token.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Prepare ZIP snapshot")
        }
        vm.pendingHfZip?.let { file -> OutlinedButton(onClick = { saveLauncher.launch(file.name) }, modifier = Modifier.fillMaxWidth()) { Text("Save ${file.name}") } }
    }
}

@Composable
private fun GoogleOAuthScreen(vm: ForgePortViewModel) {
    var scopes by remember { mutableStateOf("https://www.googleapis.com/auth/drive") }
    val coroutineScope = rememberCoroutineScope()
    val credentialsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.generateGoogleTokens(it, scopes) } }
    val savePickle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = vm.googleTokenBundle?.tokenPickle ?: return@rememberLauncherForActivityResult
        uri?.let { coroutineScope.launch { vm.saveBytes(it, bytes) } }
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = vm.googleTokenBundle?.tokenJson ?: return@rememberLauncherForActivityResult
        uri?.let { coroutineScope.launch { vm.saveBytes(it, bytes) } }
    }
    ScreenColumn(vm) {
        SectionHeader("Google OAuth", "Generate Google Drive credentials locally using a Desktop OAuth client.")
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text("ForgePort opens the system browser and receives Google's callback on 127.0.0.1, matching the InstalledAppFlow-style desktop flow.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(scopes, { scopes = it }, label = { Text("Scopes") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { credentialsPicker.launch(arrayOf("application/json", "text/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Key, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Choose credentials.json and authorize")
        }
        Text("For long-lived refresh access, configure the OAuth consent screen appropriately. Google can still revoke refresh tokens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.googleTokenBundle?.let {
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Credentials ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("token.pickle stores the refresh credentials and lets google-auth refresh the access token on first use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        SectionHeader("Variables", "Values are encrypted locally with Android Keystore.")
        OutlinedTextField(name, { name = it }, label = { Text("Variable name") }, placeholder = { Text("GITHUB_TOKEN_USERNAME") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value, { value = it }, label = { Text("Value") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions.Default, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.saveVariable(name, value); value = "" }, enabled = name.isNotBlank() && value.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save variable") }
        if (vm.variables.isEmpty()) {
            ElevatedCard { Text("No saved variables.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            vm.variables.forEach { variable ->
                ElevatedCard {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(variable.name, fontWeight = FontWeight.SemiBold)
                            Text(variable.maskedValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.deleteVariable(variable.name) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete variable") }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesScreen(vm: ForgePortViewModel) {
    val update = vm.availableUpdate
    ScreenColumn(vm) {
        SectionHeader(
            "App updates",
            "ForgePort checks GitHub Releases when the app opens. Downloaded updates automatically continue to Android's installer.",
        )

        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Automatic update pop-ups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Show the update prompt automatically when ForgePort opens and a newer release is available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = vm.automaticUpdatePopups,
                        onCheckedChange = vm::updateAutomaticUpdatePopups,
                    )
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Current version", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(BuildConfig.UPDATE_GITHUB_REPOSITORY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        FilledTonalButton(
            onClick = { vm.checkForUpdates(silent = false) },
            enabled = !vm.updateChecking && !vm.updateDownloading && !vm.updateInstalling,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.updateChecking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (vm.updateChecking) "Checking…" else "Check for updates")
        }

        vm.updateMessage?.let { message ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        if (update != null) {
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Version ${update.versionName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (update.prerelease) "Prerelease" else "Stable release",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (update.apkSizeBytes > 0L) {
                            Text(humanBytes(update.apkSizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (update.notes.isNotBlank()) {
                        Text(update.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (vm.updateDownloading) {
                        if (vm.updateDownloadTotalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { vm.updateDownloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${humanBytes(vm.updateDownloadedBytes)} / ${humanBytes(vm.updateDownloadTotalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    val downloaded = vm.downloadedUpdateApk
                    if (downloaded == null) {
                        Button(
                            onClick = vm::downloadUpdate,
                            enabled = !vm.updateDownloading && !vm.updateInstalling,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (vm.updateDownloading) "Downloading…" else "Download update")
                        }
                        Text(
                            "After the APK is downloaded and verified, ForgePort opens Android's installer automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Button(
                            onClick = vm::installDownloadedUpdate,
                            enabled = !vm.updateDownloading && !vm.updateInstalling,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (vm.updateInstalling) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (vm.updateInstalling) "Opening installer…" else "Install update")
                        }
                        Text(
                            "ForgePort keeps the verified APK available as a fallback if Android's installer was cancelled. SHA-256 is verified when the release provides a checksum asset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onChecked)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) { value /= 1024.0; index++ }
    return "%.1f %s".format(value, units[index])
}
