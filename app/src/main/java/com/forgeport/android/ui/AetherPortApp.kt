package com.forgeport.android.ui

import com.forgeport.android.ui.theme.ExpressiveButton as Button
import com.forgeport.android.ui.theme.ExpressiveTonalButton as FilledTonalButton
import com.forgeport.android.ui.theme.ExpressiveOutlinedButton as OutlinedButton
import com.forgeport.android.ui.theme.ExpressiveTextButton as TextButton
import com.forgeport.android.ui.theme.ExpressiveIconButton as IconButton
import com.forgeport.android.ui.theme.ExpressiveFilterChip as FilterChip
import com.forgeport.android.ui.theme.ExpressiveCard as ElevatedCard
import com.forgeport.android.ui.theme.ExpressiveDialog as AlertDialog
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import com.forgeport.android.ui.theme.AdaptiveContent
import com.forgeport.android.ui.theme.ExpressiveMotion
import com.forgeport.android.ui.theme.contentColumns
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.compose.material3.ScaffoldDefaults
import com.forgeport.android.gallery.GalleryCatalogScreen
import com.forgeport.android.ui.theme.GalleryColorScheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.forgeport.android.BuildConfig
import com.forgeport.android.AetherPortViewModel
import com.forgeport.android.R
import com.forgeport.android.gallery.GalleryDetailsScreen
import com.forgeport.android.gallery.GalleryLibraryScreen
import com.forgeport.android.gallery.GalleryReaderScreen
import com.forgeport.android.gallery.GalleryViewModel
import com.forgeport.android.model.StagedProject
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private data class Destination(val route: String, val title: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("github", "GitHub", Icons.Filled.Code),
    Destination("huggingface", "Hugging Face", Icons.Filled.CloudUpload),
    Destination("google", "Google", Icons.Filled.Key),
    Destination("ehentai", "E-Hentai", Icons.Filled.Public),
    Destination("variables", "Variables", Icons.Filled.Security),
    Destination("updates", "Updates", Icons.Filled.SystemUpdate),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherPortApp(vm: AetherPortViewModel = viewModel(), galleryLibraryRequest: Int = 0) {
    val nav = rememberNavController()
    val galleryVm: GalleryViewModel = viewModel()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var settings by rememberSaveable { mutableStateOf(false) }
    var variablesShowRepositories by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"
    val readerRoute = currentRoute.startsWith("reader/")
    val galleryRoute = currentRoute.startsWith("gallery/") || currentRoute.startsWith("ehentai/web/")
    val nativeGalleryRoute = currentRoute in setOf("ehentai", "gallery-library", "gallery-downloads") || currentRoute.startsWith("gallery/")
    val galleryUiRoute = nativeGalleryRoute || readerRoute || currentRoute.startsWith("ehentai/web/")
    val view = LocalView.current
    DisposableEffect(galleryUiRoute, view) {
        val window = (view.context as? ComponentActivity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val statusAppearance = controller?.isAppearanceLightStatusBars
        val navigationAppearance = controller?.isAppearanceLightNavigationBars
        if (galleryUiRoute) {
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
        }
        onDispose {
            if (statusAppearance != null) controller?.isAppearanceLightStatusBars = statusAppearance
            if (navigationAppearance != null) controller?.isAppearanceLightNavigationBars = navigationAppearance
        }
    }
    val selectedSection = if (galleryUiRoute) "ehentai" else currentRoute
    val title = when {
        currentRoute.startsWith("ehentai/web/") -> "Account & access"
        galleryRoute -> "E-Hentai gallery"
        else -> destinations.firstOrNull { it.route == currentRoute }?.title ?: "AetherPort"
    }
    fun openGallery(url: String) { nav.navigate("gallery/${Uri.encode(url)}") { launchSingleTop = true } }
    fun readGallery(url: String, page: Int) { nav.navigate("reader/${Uri.encode(url)}/$page") }
    fun openWebsite(url: String) { nav.navigate("ehentai/web/${Uri.encode(url)}") { launchSingleTop = true } }
    fun openGallerySection(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(galleryLibraryRequest) {
        if (galleryLibraryRequest > 0) openGallerySection("gallery-downloads")
    }

    LaunchedEffect(Unit) {
        vm.checkForUpdates(silent = true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { vm.resumePendingUpdateInstall(); vm.refreshArchives() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme(colorScheme = if (galleryUiRoute) GalleryColorScheme else MaterialTheme.colorScheme) {
        if (settings) ProjectFolderSettings(vm) { settings = false }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !readerRoute,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = MaterialTheme.shapes.large,
                            color = Color.Black,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_aetherport_mark),
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                    tint = Color.Unspecified,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("AetherPort", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Create. Connect. Explore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    destinations.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.title) },
                            selected = selectedSection == item.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (item.route == "variables") variablesShowRepositories = false
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                            shape = MaterialTheme.shapes.large,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Version ${BuildConfig.VERSION_NAME} • Stable", Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = if (nativeGalleryRoute) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
                topBar = {
                    if (!readerRoute && !nativeGalleryRoute) {
                        CenterAlignedTopAppBar(
                            title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = {
                                IconButton(onClick = { if (galleryRoute) nav.popBackStack() else scope.launch { drawerState.open() } }) {
                                    if (galleryRoute) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    else Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                                }
                            },
                            actions = { if (!galleryRoute) IconButton(onClick = { settings = true }, enabled = !vm.busy) { Icon(Icons.Filled.Settings, "Settings") } },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    NavHost(
                        navController = nav, startDestination = "home",
                        enterTransition = { fadeIn(ExpressiveMotion.spatial()) + slideInHorizontally(ExpressiveMotion.spatial()) { it / 12 } },
                        exitTransition = { fadeOut(ExpressiveMotion.spatial()) + slideOutHorizontally(ExpressiveMotion.spatial()) { -it / 12 } },
                        popEnterTransition = { fadeIn(ExpressiveMotion.spatial()) + slideInHorizontally(ExpressiveMotion.spatial()) { -it / 12 } },
                        popExitTransition = { fadeOut(ExpressiveMotion.spatial()) + slideOutHorizontally(ExpressiveMotion.spatial()) { it / 12 } },
                    ) {
                        composable("home") { HomeScreen(vm) { nav.navigate(it) } }
                        composable("github") { RepositorySection(vm, huggingFace = false) { variablesShowRepositories = true; nav.navigate("variables") } }
                        composable("huggingface") { RepositorySection(vm, huggingFace = true) { variablesShowRepositories = true; nav.navigate("variables") } }
                        composable("ehentai") {
                            GalleryCatalogScreen(galleryVm, back = { nav.popBackStack() }, open = ::openGallery,
                                website = ::openWebsite, library = { openGallerySection("gallery-library") },
                                downloads = { openGallerySection("gallery-downloads") })
                        }
                        composable("gallery-library") {
                            GalleryLibraryScreen(galleryVm, ::openGallery, browse = { openGallerySection("ehentai") },
                                library = {}, downloadsPage = { openGallerySection("gallery-downloads") }, back = { nav.popBackStack() }, read = ::readGallery)
                        }
                        composable("gallery-downloads") {
                            GalleryLibraryScreen(galleryVm, ::openGallery, browse = { openGallerySection("ehentai") },
                                library = { openGallerySection("gallery-library") }, downloadsPage = {}, back = { nav.popBackStack() }, read = ::readGallery, downloadsOnly = true)
                        }
                        composable("gallery/{url}") { entry ->
                            val url = entry.arguments?.getString("url").orEmpty()
                            GalleryDetailsScreen(url, galleryVm, read = ::readGallery, website = ::openWebsite, back = { nav.popBackStack() })
                        }
                        composable("ehentai/web/{url}") { entry ->
                            EhentaiScreen(handleBack = !drawerState.isOpen, initialUrl = entry.arguments?.getString("url") ?: EhentaiNavigation.HOME)
                        }
                        composable("reader/{url}/{page}", arguments = listOf(navArgument("page") { type = NavType.IntType })) { entry ->
                            GalleryReaderScreen(entry.arguments?.getString("url").orEmpty(), entry.arguments?.getInt("page") ?: 0, galleryVm) { nav.popBackStack() }
                        }
                        composable("google") { GoogleOAuthScreen(vm) }
                        composable("variables") { VariablesScreen(vm, variablesShowRepositories) }
                        composable("updates") { UpdatesScreen(vm) }
                    }
                    AnimatedVisibility(
                        visible = vm.updateDownloading || vm.updateInstalling || vm.updateInstallPending,
                        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 10.dp).then(Modifier.widthIn(max = 840.dp)),
                        enter = slideInVertically(ExpressiveMotion.spatial(), initialOffsetY = { -it }) + fadeIn(ExpressiveMotion.spatial()),
                        exit = slideOutVertically(ExpressiveMotion.spatial(), targetOffsetY = { -it }) + fadeOut(ExpressiveMotion.spatial()),
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
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                title = { Text("AetherPort ${promptedUpdate.versionName} is available") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A newer AetherPort release is available on GitHub.")
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
}

@Composable
private fun UpdateProgressBanner(vm: AetherPortViewModel) {
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
                            vm.updateInstallPending -> "Return to AetherPort after allowing update installation."
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
private fun ScreenColumn(vm: AetherPortViewModel, content: @Composable ColumnScope.() -> Unit) {
    AdaptiveContent {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
}

@Composable
private fun SectionHeader(title: String, description: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HomeScreen(vm: AetherPortViewModel, open: (String) -> Unit) {
    ScreenColumn(vm) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = contentColumns(maxWidth.value, LocalDensity.current.fontScale, minCellDp = 164f, maxColumns = 2)
            val cellWidth = (maxWidth - 12.dp * (columns - 1)) / columns
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(vm.projects.size.toString(), "Staged projects", Icons.Filled.Folder, Modifier.width(cellWidth))
                StatCard(vm.variables.size.toString(), "Saved variables", Icons.Filled.Security, Modifier.width(cellWidth))
            }
        }
        SectionHeader("Your workspace", "Publish projects, connect accounts, and explore galleries.")
        val cards = listOf(
            Triple("GitHub", "Publish projects with your saved GitHub credentials.", "github"),
            Triple("Hugging Face", "Publish to Spaces or download a repository snapshot.", "huggingface"),
            Triple("Google", "Connect Google and generate your access tokens.", "google"),
            Triple("E-Hentai", "Browse, read, and save galleries offline.", "ehentai"),
            Triple("Variables", "Manage reusable credentials securely.", "variables"),
            Triple("Updates", "Keep AetherPort up to date.", "updates"),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = contentColumns(maxWidth.value, LocalDensity.current.fontScale, minCellDp = 320f, maxColumns = 2)
            val cellWidth = (maxWidth - 12.dp * (columns - 1)) / columns
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cards.forEach { (name, desc, route) ->
                    val icon = destinations.first { it.route == route }.icon
                    ElevatedCard(onClick = { open(route) }, modifier = Modifier.width(cellWidth)) {
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
private fun ProjectsScreen(vm: AetherPortViewModel) {
    ScreenColumn(vm) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Your staged projects", "Stored only on this device until you delete them.", Modifier.weight(1f))
            IconButton(onClick = vm::refreshAll) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        }
        if (vm.projects.isEmpty()) {
            ElevatedCard { Text("No staged projects. Choose a ZIP folder in Settings, then publish from GitHub or Hugging Face.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun RepositorySection(vm: AetherPortViewModel, huggingFace: Boolean, manageRepositories: () -> Unit) {
    val tabs = if (huggingFace) listOf("Publish", "Download", "Projects") else listOf("Publish", "Projects")
    var selectedTab by rememberSaveable(huggingFace) { mutableStateOf("Publish") }
    val tabState = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tabs.forEach { tab ->
                FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = selectedTab, label = "Repository tab",
                transitionSpec = { (fadeIn(ExpressiveMotion.spatial()) togetherWith fadeOut(ExpressiveMotion.spatial())).using(SizeTransform { _, _ -> ExpressiveMotion.spatial() }) },
            ) { tab ->
                tabState.SaveableStateProvider(tab) {
                    when (tab) {
                        "Projects" -> ProjectsScreen(vm)
                        "Download" -> HfDownloadScreen(vm, manageRepositories)
                        else -> if (huggingFace) HuggingFaceScreen(vm, manageRepositories) else GitHubScreen(vm, manageRepositories)
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubScreen(vm: AetherPortViewModel, manageRepositories: () -> Unit) {
    var project by rememberSaveable { mutableStateOf("") }
    var chooseRepository by rememberSaveable { mutableStateOf(false) }
    var branch by rememberSaveable { mutableStateOf("main") }
    var commit by rememberSaveable { mutableStateOf("Small bug fixes") }
    var targetPath by rememberSaveable { mutableStateOf("") }
    var unwrap by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(vm.publishProjects) { if (vm.publishProjects.none { it.id == project }) project = vm.publishProjects.firstOrNull()?.id.orEmpty() }
    if (chooseRepository) RepositoryChooser(vm.savedRepositories.filter { !it.huggingFace }, false,
        "Select a repository to publish the selected project to $branch.", dismiss = { chooseRepository = false },
        manage = { chooseRepository = false; manageRepositories() }, select = { repo ->
            chooseRepository = false; vm.publishGitHub(project, repo, branch, commit, unwrap, targetPath)
        })
    ScreenColumn(vm) {
        SectionHeader("Publish to GitHub", "The repository-owner token is matched from Variables. Commits are credited to that token's account with its GitHub no-reply email.")
        ProjectSourcePicker(vm, project) { project = it }
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commit, { commit = it }, label = { Text("Commit message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetPath, { targetPath = it }, label = { Text("Target path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CheckRow("Remove one outer wrapper folder", unwrap) { unwrap = it }
        Button(onClick = { chooseRepository = true }, enabled = project.isNotBlank() && !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Code, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Publish to GitHub")
        }
    }
}

@Composable
private fun HuggingFaceScreen(vm: AetherPortViewModel, manageRepositories: () -> Unit) {
    var project by rememberSaveable { mutableStateOf("") }
    var chooseRepository by rememberSaveable { mutableStateOf(false) }
    var branch by rememberSaveable { mutableStateOf("main") }
    var commit by rememberSaveable { mutableStateOf("Small bug fixes") }
    var targetPath by rememberSaveable { mutableStateOf("") }
    var unwrap by rememberSaveable { mutableStateOf(true) }
    var token by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(vm.publishProjects) { if (vm.publishProjects.none { it.id == project }) project = vm.publishProjects.firstOrNull()?.id.orEmpty() }
    LaunchedEffect(vm.hfTokenNames) { if (token !in vm.hfTokenNames) token = vm.hfTokenNames.firstOrNull().orEmpty() }
    if (chooseRepository) RepositoryChooser(vm.savedRepositories.filter { it.huggingFace }, true,
        "Select a Space to publish the selected project to $branch.", dismiss = { chooseRepository = false },
        manage = { chooseRepository = false; manageRepositories() }, select = { repo ->
            chooseRepository = false; vm.publishHuggingFace(project, repo, token, branch, commit, unwrap, targetPath)
        })
    ScreenColumn(vm) {
        SectionHeader("Publish to Hugging Face", "Push a staged project to a Space. Commits use the selected token's account and verified account email.")
        ProjectSourcePicker(vm, project) { project = it }
        TokenPicker(vm.hfTokenNames, token) { token = it }
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commit, { commit = it }, label = { Text("Commit message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetPath, { targetPath = it }, label = { Text("Target path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CheckRow("Remove one outer wrapper folder", unwrap) { unwrap = it }
        Button(onClick = { chooseRepository = true }, enabled = project.isNotBlank() && token.isNotBlank() && !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Publish to Hugging Face")
        }
    }
}

@Composable
private fun HfDownloadScreen(vm: AetherPortViewModel, manageRepositories: () -> Unit) {
    var chooseRepository by rememberSaveable { mutableStateOf(false) }
    var branch by rememberSaveable { mutableStateOf("main") }
    var token by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm.hfTokenNames) { if (token !in vm.hfTokenNames) token = vm.hfTokenNames.firstOrNull().orEmpty() }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = vm.pendingHfZip ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch { runCatching { vm.saveFile(uri, file) }; vm.clearPendingHfZip() }
    }
    if (chooseRepository) RepositoryChooser(vm.savedRepositories.filter { it.huggingFace }, true,
        "Select a Space to prepare its $branch branch as a ZIP.", dismiss = { chooseRepository = false },
        manage = { chooseRepository = false; manageRepositories() }, select = { repo ->
            chooseRepository = false; vm.prepareHfDownload(repo, token, branch)
        })
    ScreenColumn(vm) {
        SectionHeader("Download from Hugging Face", "Create a clean ZIP snapshot from a Space branch.")
        TokenPicker(vm.hfTokenNames, token) { token = it }
        OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { chooseRepository = true }, enabled = token.isNotBlank() && !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Prepare ZIP snapshot")
        }
        vm.pendingHfZip?.let { file -> OutlinedButton(onClick = { saveLauncher.launch(file.name) }, modifier = Modifier.fillMaxWidth()) { Text("Save ${file.name}") } }
    }
}

@Composable
private fun GoogleOAuthScreen(vm: AetherPortViewModel) {
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
            Text("AetherPort opens the system browser and receives Google's callback on 127.0.0.1, matching the InstalledAppFlow-style desktop flow.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun VariablesScreen(vm: AetherPortViewModel, showRepositories: Boolean) {
    var repositoriesTab by rememberSaveable { mutableStateOf(showRepositories) }
    LaunchedEffect(showRepositories) { repositoriesTab = showRepositories }
    var name by rememberSaveable { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    ScreenColumn(vm) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(!repositoriesTab, onClick = { repositoriesTab = false }, label = { Text("Credentials") })
            FilterChip(repositoriesTab, onClick = { repositoriesTab = true }, label = { Text("Repositories") })
        }
        AnimatedContent(targetState = repositoriesTab, label = "Variables tab",
            transitionSpec = { (fadeIn(ExpressiveMotion.spatial()) togetherWith fadeOut(ExpressiveMotion.spatial())).using(SizeTransform { _, _ -> ExpressiveMotion.spatial() }) },
        ) { repositories ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (repositories) SavedRepositoriesEditor(vm) else {
                    SectionHeader("Variables", "Values are encrypted locally with Android Keystore.")
                    OutlinedTextField(name, { name = it }, label = { Text("Variable name") }, placeholder = { Text("GITHUB_TOKEN_USERNAME") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value, { value = it }, label = { Text("Value") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions.Default, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.saveVariable(name, value); value = "" }, enabled = name.isNotBlank() && value.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save variable") }
                    val credentials = vm.variables.filter { variable -> vm.savedRepositories.none { it.variableName == variable.name } }
                    if (credentials.isEmpty()) {
                        ElevatedCard { Text("No saved variables.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        credentials.forEach { variable ->
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
        }
    }
}

@Composable
private fun UpdatesScreen(vm: AetherPortViewModel) {
    var editSource by remember { mutableStateOf(false) }
    var repository by remember(vm.updateRepository) { mutableStateOf(vm.updateRepository) }
    if (editSource) AlertDialog(
        onDismissRequest = { editSource = false }, title = { Text("Update repository") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = repository, onValueChange = { repository = it }, label = { Text("owner/repository") }, singleLine = true)
            Text("For private releases, save your GitHub token in Variables. AetherPort uses the matching GITHUB_TOKEN variable.")
        } },
        confirmButton = { TextButton(onClick = { editSource = false; vm.configureUpdateRepository(repository) }) { Text("Save and check") } },
        dismissButton = { TextButton(onClick = { editSource = false }) { Text("Cancel") } },
    )
    val update = vm.availableUpdate
    ScreenColumn(vm) {
        SectionHeader(
            "App updates",
            "AetherPort checks GitHub Releases when the app opens. Downloaded updates automatically continue to Android's installer.",
        )

        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Automatic update pop-ups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Show the update prompt automatically when AetherPort opens and a newer release is available.",
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
                    Text(vm.updateRepository, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { editSource = true }, enabled = !vm.updateChecking && !vm.updateDownloading && !vm.updateInstalling) { Text("Change update repository") }
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
                            "After the APK is downloaded and verified, AetherPort opens Android's installer automatically.",
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
                            "AetherPort keeps the verified APK available as a fallback if Android's installer was cancelled. SHA-256 is verified when the release provides a checksum asset.",
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
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
