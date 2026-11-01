package com.flownova.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flownova.launcher.data.PreferenceStorage
import com.flownova.launcher.model.*
import com.flownova.launcher.ui.theme.FlowNovaTheme
import com.flownova.launcher.util.IconResolver
import com.flownova.launcher.util.SystemHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var storage: PreferenceStorage
    lateinit var appWidgetHost: AppWidgetHost
    lateinit var appWidgetManager: AppWidgetManager
    private var onWidgetBoundCallback: ((Int) -> Unit)? = null

    private val widgetPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (result.resultCode == Activity.RESULT_OK && id != -1) {
            onWidgetBoundCallback?.invoke(id)
        } else if (id != -1) {
            appWidgetHost.deleteAppWidgetId(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemHelper.enableHighRefreshRate(this)
        storage = PreferenceStorage(this)
        appWidgetHost = AppWidgetHost(applicationContext, 1024)
        appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        setContent {
            var settings by remember { mutableStateOf(storage.loadSettings()) }
            var reloadTrigger by remember { mutableIntStateOf(0) }

            val updateSettings: (LauncherSettings) -> Unit = { newSettings ->
                settings = newSettings
                storage.saveSettings(newSettings)
            }

            DisposableEffect(Unit) {
                onWidgetBoundCallback = { widgetId ->
                    if (widgetId !in settings.appWidgetIds) {
                        updateSettings(settings.copy(appWidgetIds = settings.appWidgetIds + widgetId))
                    }
                }
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        IconResolver.clearCache()
                        reloadTrigger++
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                registerReceiver(receiver, filter)
                onDispose { unregisterReceiver(receiver) }
            }

            FlowNovaTheme(settings = settings) {
                FlowNovaRoot(
                    settings = settings,
                    onUpdateSettings = updateSettings,
                    reloadTrigger = reloadTrigger,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    onPickWidget = {
                        runCatching {
                            val appWidgetId = appWidgetHost.allocateAppWidgetId()
                            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            widgetPickerLauncher.launch(pickIntent)
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { appWidgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { appWidgetHost.stopListening() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlowNovaRoot(
    settings: LauncherSettings,
    onUpdateSettings: (LauncherSettings) -> Unit,
    reloadTrigger: Int,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onPickWidget: () -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var showDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppMenu by remember { mutableStateOf<AppItem?>(null) }
    var openedFolder by remember { mutableStateOf<FolderItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    val animDuration = if (settings.respectReducedMotion && SystemHelper.isSystemReducedMotion(context)) 0 else 260

    BackHandler(enabled = true) {
        when {
            selectedAppMenu != null -> selectedAppMenu = null
            openedFolder != null -> openedFolder = null
            showSettings -> showSettings = false
            showDrawer -> {
                showDrawer = false
                searchQuery = ""
            }
        }
    }

    LaunchedEffect(reloadTrigger) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val list = pm.queryIntentActivities(intent, 0).mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                AppItem(info.loadLabel(pm).toString(), pkg, info.activityInfo.name)
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

            allApps = list

            if (settings.favoritePackages.isEmpty() && list.isNotEmpty()) {
                onUpdateSettings(
                    settings.copy(
                        favoritePackages = list.take(8).map { it.packageName },
                        dockPackages = list.take(4).map { it.packageName }
                    )
                )
            }
        }
    }

    val executeGesture: (GestureAction) -> Unit = { action ->
        when (action) {
            GestureAction.APP_DRAWER -> showDrawer = true
            GestureAction.NOTIFICATIONS -> SystemHelper.expandNotificationShade(context)
            GestureAction.SEARCH -> showDrawer = true
            GestureAction.SETTINGS -> showSettings = true
            GestureAction.NONE -> {}
        }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xCC0B132B), Color(0xBF1C2541), Color(0xE6050811))))
            .pointerInput(settings.swipeUpAction, settings.swipeDownAction) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (dragAccumulator < -95f) executeGesture(settings.swipeUpAction)
                        else if (dragAccumulator > 95f) executeGesture(settings.swipeDownAction)
                        dragAccumulator = 0f
                    }
                ) { _, dragAmount -> dragAccumulator += dragAmount }
            }
            .pointerInput(settings.doubleTapAction) {
                detectTapGestures(
                    onDoubleTap = { executeGesture(settings.doubleTapAction) },
                    onLongPress = { showSettings = true }
                )
            }
    ) {
        HomeScreenContent(
            settings = settings,
            allApps = allApps,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager,
            onOpenDrawer = { showDrawer = true },
            onOpenSettings = { showSettings = true },
            onAppLongClick = { selectedAppMenu = it },
            onFolderClick = { openedFolder = it },
            onRemoveWidget = { id ->
                appWidgetHost.deleteAppWidgetId(id)
                onUpdateSettings(settings.copy(appWidgetIds = settings.appWidgetIds - id))
            }
        )

        AnimatedVisibility(
            visible = showDrawer,
            enter = slideInVertically(tween(animDuration)) { it } + fadeIn(tween(animDuration)),
            exit = slideOutVertically(tween(animDuration)) { it } + fadeOut(tween(animDuration))
        ) {
            AppDrawerScreen(
                settings = settings,
                allApps = allApps,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onClose = { showDrawer = false; searchQuery = "" },
                onAppLongClick = { selectedAppMenu = it }
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(tween(animDuration)) { it } + fadeIn(tween(animDuration)),
            exit = slideOutHorizontally(tween(animDuration)) { it } + fadeOut(tween(animDuration))
        ) {
            LauncherSettingsScreen(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onClose = { showSettings = false },
                onPickWidget = onPickWidget,
                onCreateFolder = { showCreateFolderDialog = true }
            )
        }

        openedFolder?.let { folder ->
            FolderModalDialog(
                folder = folder,
                allApps = allApps,
                settings = settings,
                onDismiss = { openedFolder = null },
                onDeleteFolder = {
                    onUpdateSettings(settings.copy(folders = settings.folders.filterNot { it.id == folder.id }))
                    openedFolder = null
                }
            )
        }

        selectedAppMenu?.let { app ->
            AppContextMenuDialog(
                app = app,
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onDismiss = { selectedAppMenu = null }
            )
        }

        if (showCreateFolderDialog) {
            CreateFolderDialog(
                allApps = allApps,
                onDismiss = { showCreateFolderDialog = false },
                onCreate = { name, selectedPkgs ->
                    val newFolder = FolderItem(UUID.randomUUID().toString(), name, selectedPkgs)
                    onUpdateSettings(settings.copy(folders = settings.folders + newFolder))
                    showCreateFolderDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    settings: LauncherSettings,
    allApps: List<AppItem>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onAppLongClick: (AppItem) -> Unit,
    onFolderClick: (FolderItem) -> Unit,
    onRemoveWidget: (Int) -> Unit
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE., d 'de' MMM.", Locale("pt", "BR"))
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now).replaceFirstChar { it.uppercase() }
            delay(10_000)
        }
    }

    val favoriteApps = remember(allApps, settings.favoritePackages) {
        val map = allApps.associateBy { it.packageName }
        settings.favoritePackages.mapNotNull { map[it] }
    }

    val dockApps = remember(allApps, settings.dockPackages) {
        val map = allApps.associateBy { it.packageName }
        settings.dockPackages.mapNotNull { map[it] }.take(4)
    }

    val pageCapacity = (settings.homeColumns * (settings.homeRows - 2)).coerceAtLeast(4)
    val pages = remember(favoriteApps, pageCapacity) {
        if (favoriteApps.isEmpty()) listOf(emptyList()) else favoriteApps.chunked(pageCapacity)
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(currentDate, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(currentTime, color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("22°C", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(settings.locationLabel, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().height(50.dp).clickable { onOpenDrawer() },
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1F2737).copy(alpha = 0.85f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("G", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Pesquisar apps...", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (settings.appWidgetIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            settings.appWidgetIds.take(1).forEach { widgetId ->
                val info = remember(widgetId) { appWidgetManager.getAppWidgetInfo(widgetId) }
                if (info != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(110.dp).combinedClickable(onClick = {}, onLongClick = { onRemoveWidget(widgetId) }),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx -> appWidgetHost.createView(ctx, widgetId, info).apply { setAppWidget(widgetId, info) } }
                        )
                    }
                }
            }
        }

        if (settings.folders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                settings.folders.take(settings.homeColumns).forEach { folder ->
                    FolderIconView(folder = folder, onClick = { onFolderClick(folder) })
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
            val pageApps = pages.getOrNull(pageIndex).orEmpty()
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.homeColumns),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pageApps, key = { it.packageName }) { app ->
                    LauncherAppIcon(
                        app = app,
                        settings = settings,
                        onClick = { context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) } },
                        onLongClick = { onAppLongClick(app) }
                    )
                }
            }
        }

        if (settings.indicatorStyle != IndicatorStyle.HIDDEN) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val count = pages.size.coerceAtLeast(2)
                for (i in 0 until count) {
                    val selected = i == pagerState.currentPage
                    val width = if (settings.indicatorStyle == IndicatorStyle.PILL && selected) 20.dp else 7.dp
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp).height(7.dp).width(width).clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().height(78.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF141B29).copy(alpha = 0.78f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dockApps.take(3).forEach { app ->
                    LauncherAppIcon(
                        app = app,
                        settings = settings,
                        showLabel = false,
                        onClick = { context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) } },
                        onLongClick = { onAppLongClick(app) }
                    )
                }
                Box(
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).clickable { onOpenDrawer() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "Gaveta", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
                Box(
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppIcon(
    app: AppItem,
    settings: LauncherSettings,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val useDynamicForThisApp = settings.dynamicIconsGlobal && !settings.keepOriginalIconPackages.contains(app.packageName)
    val tintArgb = MaterialTheme.colorScheme.onSecondaryContainer.toArgb()
    val containerArgb = MaterialTheme.colorScheme.secondaryContainer.toArgb()

    var iconBitmap by remember(app.packageName, useDynamicForThisApp, tintArgb, containerArgb) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(app.packageName, useDynamicForThisApp, tintArgb, containerArgb) {
        withContext(Dispatchers.IO) {
            iconBitmap = IconResolver.loadIcon(context.packageManager, app.packageName, useDynamicForThisApp, tintArgb, containerArgb)
        }
    }

    val baseSize: Dp = (56f * (settings.iconScalePercent / 100f)).dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Box(modifier = Modifier.size(baseSize).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            val bmp = iconBitmap
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = app.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Text(app.label.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun FolderIconView(folder: FolderItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(6.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = folder.name, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(folder.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun AppDrawerScreen(
    settings: LauncherSettings,
    allApps: List<AppItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit,
    onAppLongClick: (AppItem) -> Unit
) {
    val context = LocalContext.current
    val filtered = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps else allApps.filter { it.label.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xF2080B12)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Pesquisar apps", color = Color.White.copy(alpha = 0.55f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
                trailingIcon = {
                    IconButton(onClick = { if (searchQuery.isNotEmpty()) onSearchChange("") else onClose() }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A2130),
                    unfocusedContainerColor = Color(0xFF151B28),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(18.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.drawerColumns),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    LauncherAppIcon(
                        app = app,
                        settings = settings,
                        onClick = {
                            onClose()
                            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) }
                        },
                        onLongClick = { onAppLongClick(app) }
                    )
                }
            }
        }
    }
}

@Composable
fun LauncherSettingsScreen(
    settings: LauncherSettings,
    onUpdateSettings: (LauncherSettings) -> Unit,
    onClose: () -> Unit,
    onPickWidget: () -> Unit,
    onCreateFolder: () -> Unit
) {
    val context = LocalContext.current
    val isDefault = remember { SystemHelper.isDefaultLauncher(context) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0E17)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White) }
                Text("Configurações do Launcher", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2235))) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF4FC3F7), Color(0xFF7C4DFF), Color(0xFF00E676)))),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Palette, contentDescription = null, tint = Color.White) }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Aparência", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Personalize seu launcher • S23 Ultra", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141923))) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsSwitchRow(
                        icon = Icons.Default.BrightnessAuto,
                        title = "Tema do sistema",
                        subtitle = "Seguir o tema do Android (${settings.themeMode.label})",
                        checked = settings.themeMode == ThemeMode.SYSTEM,
                        onCheckedChange = { follow -> onUpdateSettings(settings.copy(themeMode = if (follow) ThemeMode.SYSTEM else ThemeMode.DARK)) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsSwitchRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "Ícones dinâmicos",
                        subtitle = "Alterar a cor dos ícones globalmente",
                        checked = settings.dynamicIconsGlobal,
                        onCheckedChange = { enabled ->
                            IconResolver.clearCache()
                            onUpdateSettings(settings.copy(dynamicIconsGlobal = enabled))
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(
                        icon = Icons.Default.ColorLens,
                        title = "Pacote de ícones",
                        subtitle = if (settings.useDynamicColors) "Material You (Dinâmico)" else settings.palettePreset.label,
                        onClick = {
                            val presets = PalettePreset.entries
                            val nextPreset = presets[(presets.indexOf(settings.palettePreset) + 1) % presets.size]
                            IconResolver.clearCache()
                            onUpdateSettings(settings.copy(palettePreset = nextPreset, useDynamicColors = (nextPreset == PalettePreset.DYNAMIC)))
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(
                        icon = Icons.Default.GridView,
                        title = "Grade da tela inicial",
                        subtitle = "${settings.homeColumns} x ${settings.homeRows}",
                        onClick = {
                            val nextCols = if (settings.homeColumns == 4) 5 else 4
                            val nextRows = if (settings.homeRows == 5) 6 else 5
                            onUpdateSettings(settings.copy(homeColumns = nextCols, homeRows = nextRows))
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(
                        icon = Icons.Default.Apps,
                        title = "Grade da gaveta de apps",
                        subtitle = "${settings.drawerColumns} x ${settings.drawerRows}",
                        onClick = {
                            val nextCols = if (settings.drawerColumns == 5) 4 else 5
                            onUpdateSettings(settings.copy(drawerColumns = nextCols))
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(14.dp))
                                Text("Tamanho dos ícones", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("${settings.iconScalePercent}%", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        }
                        Slider(
                            value = settings.iconScalePercent.toFloat(),
                            onValueChange = { onUpdateSettings(settings.copy(iconScalePercent = it.toInt())) },
                            valueRange = 80f..120f,
                            steps = 7
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(
                        icon = Icons.Default.SwipeUp,
                        title = "Animações e gestos",
                        subtitle = "Cima: ${settings.swipeUpAction.label} • Baixo: ${settings.swipeDownAction.label}",
                        onClick = {
                            val actions = GestureAction.entries
                            val nextAction = actions[(actions.indexOf(settings.swipeDownAction) + 1) % actions.size]
                            onUpdateSettings(settings.copy(swipeDownAction = nextAction))
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(
                        icon = Icons.Default.MoreHoriz,
                        title = "Indicadores",
                        subtitle = settings.indicatorStyle.label,
                        onClick = {
                            val styles = IndicatorStyle.entries
                            val next = styles[(styles.indexOf(settings.indicatorStyle) + 1) % styles.size]
                            onUpdateSettings(settings.copy(indicatorStyle = next))
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141923))) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsClickRow(icon = Icons.Default.CreateNewFolder, title = "Criar pasta na tela inicial", subtitle = "${settings.folders.size} pasta(s) ativa(s)", onClick = onCreateFolder)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(icon = Icons.Default.Widgets, title = "Adicionar Widget Android real", subtitle = "Vincular widget do sistema à tela inicial", onClick = onPickWidget)
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(
                        icon = Icons.Default.Home,
                        title = "Definir como Launcher Padrão",
                        subtitle = if (isDefault) "Flow Nova já é o seu launcher padrão" else "Toque para selecionar nas configurações da One UI",
                        onClick = { SystemHelper.openDefaultLauncherSettings(context) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun AppContextMenuDialog(app: AppItem, settings: LauncherSettings, onUpdateSettings: (LauncherSettings) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isKeepOriginal = settings.keepOriginalIconPackages.contains(app.packageName)
    val isFavorite = settings.favoritePackages.contains(app.packageName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val updated = if (isKeepOriginal) settings.keepOriginalIconPackages - app.packageName else settings.keepOriginalIconPackages + app.packageName
                        IconResolver.clearCache()
                        onUpdateSettings(settings.copy(keepOriginalIconPackages = updated))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isKeepOriginal) "Usar ícone dinâmico (Material You)" else "Manter cor original deste ícone") }

                TextButton(
                    onClick = {
                        val updated = if (isFavorite) settings.favoritePackages - app.packageName else settings.favoritePackages + app.packageName
                        onUpdateSettings(settings.copy(favoritePackages = updated))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isFavorite) "Remover da tela inicial" else "Fixar na tela inicial") }

                TextButton(onClick = { SystemHelper.openAppInfo(context, app.packageName); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Informações do aplicativo")
                }

                TextButton(onClick = { SystemHelper.uninstallApp(context, app.packageName); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Desinstalar", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
fun FolderModalDialog(folder: FolderItem, allApps: List<AppItem>, settings: LauncherSettings, onDismiss: () -> Unit, onDeleteFolder: () -> Unit) {
    val context = LocalContext.current
    val folderApps = remember(allApps, folder.packages) {
        val map = allApps.associateBy { it.packageName }
        folder.packages.mapNotNull { map[it] }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name, fontWeight = FontWeight.Bold) },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folderApps, key = { it.packageName }) { app ->
                    LauncherAppIcon(
                        app = app,
                        settings = settings,
                        onClick = {
                            onDismiss()
                            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { context.startActivity(it) }
                        },
                        onLongClick = {}
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDeleteFolder) { Text("Excluir pasta", color = MaterialTheme.colorScheme.error) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
fun CreateFolderDialog(allApps: List<AppItem>, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var folderName by remember { mutableStateOf("Essenciais") }
    val selected = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Pasta") },
        text = {
            Column {
                OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Nome da pasta") }, singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.height(180.dp).verticalScroll(rememberScrollState())) {
                    allApps.take(20).forEach { app ->
                        val isChecked = app.packageName in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { if (isChecked) selected.remove(app.packageName) else selected.add(app.packageName) }.padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = { if (isChecked) selected.remove(app.packageName) else selected.add(app.packageName) })
                            Text(app.label, maxLines = 1)
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(onClick = { if (folderName.isNotBlank() && selected.isNotEmpty()) onCreate(folderName.trim(), selected.toList()) }) { Text("Criar") }
        }
    )
}
