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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun S23WaveWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF060A12), Color(0xFF0B1528), Color(0xFF05070B))
            )
        )

        val wavePath = Path().apply {
            moveTo(0f, h * 0.58f)
            cubicTo(
                w * 0.28f, h * 0.44f,
                w * 0.62f, h * 0.24f,
                w * 1.05f, h * 0.14f
            )
            lineTo(w, h * 0.65f)
            cubicTo(
                w * 0.65f, h * 0.52f,
                w * 0.30f, h * 0.68f,
                0f, h * 0.78f
            )
            close()
        }

        drawPath(
            path = wavePath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1E40AF).copy(alpha = 0.85f),
                    Color(0xFF3B82F6).copy(alpha = 0.55f),
                    Color(0xFF0F172A).copy(alpha = 0.9f)
                ),
                start = Offset(w * 0.85f, h * 0.18f),
                end = Offset(w * 0.1f, h * 0.72f)
            )
        )

        val highlightCurve = Path().apply {
            moveTo(0f, h * 0.58f)
            cubicTo(
                w * 0.28f, h * 0.44f,
                w * 0.62f, h * 0.24f,
                w * 1.05f, h * 0.14f
            )
        }
        drawPath(
            path = highlightCurve,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0xFF93C5FD).copy(alpha = 0.65f), Color(0xFF60A5FA))
            ),
            style = Stroke(width = 4.dp.toPx())
        )
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
    var showGesturesShowcase by remember { mutableStateOf(false) }
    var showDynamicIconsShowcase by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppMenu by remember { mutableStateOf<AppItem?>(null) }
    var openedFolder by remember { mutableStateOf<FolderItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    val animDuration = if (settings.respectReducedMotion && SystemHelper.isSystemReducedMotion(context)) 0 else 260

    BackHandler(enabled = true) {
        when {
            selectedAppMenu != null -> selectedAppMenu = null
            openedFolder != null -> openedFolder = null
            showGesturesShowcase -> showGesturesShowcase = false
            showDynamicIconsShowcase -> showDynamicIconsShowcase = false
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
                val preferredKeywords = listOf(
                    "vending", "youtube", "gm", "apps.photos",
                    "chrome", "spotify", "whatsapp", "telegram"
                )
                val matched = preferredKeywords.mapNotNull { key ->
                    list.firstOrNull { it.packageName.contains(key, ignoreCase = true) }?.packageName
                }
                val finalFavs = (matched + list.map { it.packageName }).distinct().take(16)
                onUpdateSettings(
                    settings.copy(
                        dynamicIconsGlobal = false,
                        favoritePackages = finalFavs,
                        dockPackages = list.take(2).map { it.packageName }
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
            .pointerInput(settings.swipeUpAction, settings.swipeDownAction) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (dragAccumulator < -90f) executeGesture(settings.swipeUpAction)
                        else if (dragAccumulator > 90f) executeGesture(settings.swipeDownAction)
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
        S23WaveWallpaper()

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
                onCreateFolder = { showCreateFolderDialog = true },
                onOpenGesturesScreen = { showGesturesShowcase = true },
                onOpenDynamicIconsScreen = { showDynamicIconsShowcase = true }
            )
        }

        AnimatedVisibility(
            visible = showGesturesShowcase,
            enter = fadeIn(tween(animDuration)),
            exit = fadeOut(tween(animDuration))
        ) {
            NativeGesturesShowcaseScreen(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onClose = { showGesturesShowcase = false }
            )
        }

        AnimatedVisibility(
            visible = showDynamicIconsShowcase,
            enter = fadeIn(tween(animDuration)),
            exit = fadeOut(tween(animDuration))
        ) {
            DynamicIconsShowcaseScreen(
                settings = settings,
                allApps = allApps,
                onUpdateSettings = onUpdateSettings,
                onClose = { showDynamicIconsShowcase = false }
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
    var currentTime by remember { mutableStateOf("19:27") }
    var currentDate by remember { mutableStateOf("Sáb., 1 de Nov.") }

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
        val favs = settings.favoritePackages.mapNotNull { map[it] }
        if (favs.isNotEmpty()) favs else allApps.take(16)
    }

    val pageCapacity = 8
    val pages = remember(favoriteApps) {
        if (favoriteApps.isEmpty()) listOf(emptyList(), emptyList())
        else {
            val chunked = favoriteApps.chunked(pageCapacity)
            if (chunked.size == 1) listOf(chunked[0], emptyList()) else chunked
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(2) })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        // Relógio Superior à Esquerda
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = currentDate,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = currentTime,
                color = Color.White,
                fontSize = 58.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-1).sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Widget de Meteorologia Alinhado à Direita (22°C Cachoeiro de Itapemirim)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFFFCA28),
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = (-5).dp, y = (-4).dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .offset(x = 4.dp, y = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "22°C",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = settings.locationLabel,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Barra de Pesquisa Google (Cápsula Translúcida Escura com G, Microfone e Lens)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(28.dp)
                )
                .clickable { onOpenDrawer() },
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF232B3A).copy(alpha = 0.78f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GoogleGLogo(modifier = Modifier.size(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Pesquisa por voz",
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Google Lens",
                        tint = Color(0xFFFBBC05),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (settings.appWidgetIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            settings.appWidgetIds.take(1).forEach { widgetId ->
                val info = remember(widgetId) { appWidgetManager.getAppWidgetInfo(widgetId) }
                if (info != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .combinedClickable(onClick = {}, onLongClick = { onRemoveWidget(widgetId) }),
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
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                settings.folders.take(settings.homeColumns).forEach { folder ->
                    FolderIconView(folder = folder, onClick = { onFolderClick(folder) })
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grelha Principal (2 linhas de 4 ícones Squircle igual à imagem)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val pageApps = pages.getOrNull(pageIndex).orEmpty()
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.homeColumns),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(pageApps, key = { it.packageName }) { app ->
                    LauncherAppIcon(
                        app = app,
                        settings = settings,
                        onClick = {
                            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                context.startActivity(it)
                            }
                        },
                        onLongClick = { onAppLongClick(app) }
                    )
                }
            }
        }

        // Pontos Indicadores de Página
        if (settings.indicatorStyle != IndicatorStyle.HIDDEN) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalDots = pages.size.coerceAtLeast(2)
                for (i in 0 until totalDots) {
                    val selected = i == pagerState.currentPage
                    val width = if (settings.indicatorStyle == IndicatorStyle.PILL && selected) 18.dp else 7.dp
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(7.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }

        // Dock Inferior com os 4 Ícones Squircle Coloridos (Telefone, Mensagens, Gaveta/App, Configurações)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Telefone (Squircle Azul)
            DockSquircleButton(
                background = Brush.linearGradient(listOf(Color(0xFF2B86FF), Color(0xFF1A66E5))),
                icon = Icons.Default.Call,
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    }
                }
            )

            // 2. Mensagens (Squircle Azul Claro)
            DockSquircleButton(
                background = Brush.linearGradient(listOf(Color(0xFF4296FF), Color(0xFF2575FC))),
                icon = Icons.Default.Chat,
                onClick = {
                    runCatching {
                        val smsIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_MESSAGING)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(smsIntent)
                    }.onFailure { onOpenDrawer() }
                }
            )

            // 3. Gaveta de Aplicações / Assistente (Squircle Roxo Vibrante)
            DockSquircleButton(
                background = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))),
                icon = Icons.Default.Apps,
                onClick = onOpenDrawer
            )

            // 4. Configurações do Launcher (Squircle Cinza-Azulado Escuro)
            DockSquircleButton(
                background = Brush.linearGradient(listOf(Color(0xFF4B5563), Color(0xFF374151))),
                icon = Icons.Default.Settings,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
fun GoogleGLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.width * 0.2f
        val diameter = size.minDimension - stroke
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

        drawArc(Color(0xFFEA4335), -45f, -90f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(Color(0xFFFBBC05), 135f, 90f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(Color(0xFF34A853), 45f, 90f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(Color(0xFF4285F4), -45f, 90f, false, topLeft, arcSize, style = Stroke(stroke))
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.5f, size.height * 0.5f),
            end = Offset(size.width * 0.92f, size.height * 0.5f),
            strokeWidth = stroke
        )
    }
}

@Composable
fun DockSquircleButton(
    background: Brush,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppIcon(
    app: AppItem,
    settings: LauncherSettings,
    showLabel: Boolean = true,
    forceDynamic: Boolean? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val useDynamicForThisApp = forceDynamic ?: (settings.dynamicIconsGlobal && !settings.keepOriginalIconPackages.contains(app.packageName))
    val tintArgb = Color(settings.palettePreset.primaryHex).toArgb()
    val containerArgb = Color(settings.palettePreset.containerHex).toArgb()

    var iconBitmap by remember(app.packageName, useDynamicForThisApp, tintArgb, containerArgb) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(app.packageName, useDynamicForThisApp, tintArgb, containerArgb) {
        withContext(Dispatchers.IO) {
            iconBitmap = IconResolver.loadIcon(context.packageManager, app.packageName, useDynamicForThisApp, tintArgb, containerArgb)
        }
    }

    val baseSize: Dp = (60f * (settings.iconScalePercent / 100f)).dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 2.dp, horizontal = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(baseSize)
                .clip(RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = iconBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E293B), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FolderIconView(folder: FolderItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = folder.name, tint = Color(0xFF60A5FA), modifier = Modifier.size(30.dp))
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

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF07090E)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Pesquisar apps", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.White)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF171B24),
                    unfocusedContainerColor = Color(0xFF171B24),
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(22.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    onCreateFolder: () -> Unit,
    onOpenGesturesScreen: () -> Unit,
    onOpenDynamicIconsScreen: () -> Unit
) {
    val context = LocalContext.current
    val isDefault = remember { SystemHelper.isDefaultLauncher(context) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0D14)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White)
                }
                Text("Configurações do Launcher", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Cartão Superior: Aparência / Personalize seu launcher
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDynamicIconsScreen() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2338))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFF6366F1), Color(0xFF4ADE80), Color(0xFFFACC15))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Aparência", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Personalize seu launcher", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Cartão Principal com os 8 Itens Idênticos à Imagem de Referência
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722))
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    SettingsSwitchRow(
                        icon = Icons.Default.Layers,
                        iconTint = Color(0xFF38BDF8),
                        title = "Tema do sistema",
                        subtitle = "Seguir o tema do Android",
                        checked = settings.themeMode == ThemeMode.SYSTEM,
                        onCheckedChange = { follow ->
                            onUpdateSettings(settings.copy(themeMode = if (follow) ThemeMode.SYSTEM else ThemeMode.DARK))
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    SettingsSwitchRow(
                        icon = Icons.Default.FilterNone,
                        iconTint = Color(0xFF38BDF8),
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
                        icon = Icons.Default.Palette,
                        iconTint = Color(0xFF818CF8),
                        title = "Pacote de ícones",
                        subtitle = "Material You (Dinâmico)",
                        onClick = onOpenDynamicIconsScreen
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    SettingsClickRow(
                        icon = Icons.Default.GridView,
                        iconTint = Color(0xFF60A5FA),
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
                        iconTint = Color(0xFF60A5FA),
                        title = "Grade da gaveta de apps",
                        subtitle = "${settings.drawerColumns} x ${settings.drawerRows}",
                        onClick = {
                            val nextCols = if (settings.drawerColumns == 5) 4 else 5
                            onUpdateSettings(settings.copy(drawerColumns = nextCols))
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    // Tamanho dos ícones com barra roxa e percentagem à direita
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null, tint = Color(0xFFA855F7))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Tamanho dos ícones", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Slider(
                                        value = settings.iconScalePercent.toFloat(),
                                        onValueChange = { onUpdateSettings(settings.copy(iconScalePercent = it.toInt())) },
                                        valueRange = 80f..120f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF8B5CF6),
                                            activeTrackColor = Color(0xFF8B5CF6),
                                            inactiveTrackColor = Color(0xFF2E364F)
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${settings.iconScalePercent}%", color = Color.White, fontSize = 14.sp)
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    SettingsClickRow(
                        icon = Icons.Default.TouchApp,
                        iconTint = Color(0xFF2DD4BF),
                        title = "Animações e gestos",
                        subtitle = "Configurar transições e gestos",
                        onClick = onOpenGesturesScreen
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                    SettingsClickRow(
                        icon = Icons.Default.RadioButtonChecked,
                        iconTint = Color(0xFF38BDF8),
                        title = "Indicadores",
                        subtitle = "Estilo dos pontos de página",
                        onClick = {
                            val styles = IndicatorStyle.entries
                            val next = styles[(styles.indexOf(settings.indicatorStyle) + 1) % styles.size]
                            onUpdateSettings(settings.copy(indicatorStyle = next))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Ações de Pastas, Widgets e Launcher Padrão
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722))
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    SettingsClickRow(
                        icon = Icons.Default.CreateNewFolder,
                        iconTint = Color(0xFF60A5FA),
                        title = "Criar pasta na tela inicial",
                        subtitle = "${settings.folders.size} pasta(s) ativa(s)",
                        onClick = onCreateFolder
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(
                        icon = Icons.Default.Widgets,
                        iconTint = Color(0xFF818CF8),
                        title = "Adicionar Widget Android",
                        subtitle = "Vincular widget real à tela inicial",
                        onClick = onPickWidget
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    SettingsClickRow(
                        icon = Icons.Default.Home,
                        iconTint = Color(0xFF34D399),
                        title = "Definir como Launcher Padrão",
                        subtitle = if (isDefault) "Ativo como padrão no S23 Ultra" else "Selecionar Flow Nova nas configurações",
                        onClick = { SystemHelper.openDefaultLauncherSettings(context) }
                    )
                }
            }
        }
    }
}

@Composable
fun NativeGesturesShowcaseScreen(
    settings: LauncherSettings,
    onUpdateSettings: (LauncherSettings) -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF040814)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White)
                }
                Text("Animações e gestos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val w = size.width
                    val h = size.height

                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(w * 0.5f, h * 0.78f),
                            radius = w * 0.48f
                        ),
                        topLeft = Offset(w * 0.05f, h * 0.65f),
                        size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.25f)
                    )

                    val arrowPath = Path().apply {
                        moveTo(w * 0.5f, h * 0.15f)
                        lineTo(w * 0.72f, h * 0.42f)
                        lineTo(w * 0.58f, h * 0.42f)
                        lineTo(w * 0.64f, h * 0.78f)
                        lineTo(w * 0.36f, h * 0.78f)
                        lineTo(w * 0.42f, h * 0.42f)
                        lineTo(w * 0.28f, h * 0.42f)
                        close()
                    }
                    drawPath(
                        path = arrowPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color.Transparent)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Gestos nativos", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Navegação fluida com gestos\ndo Android",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = {
                        val actions = GestureAction.entries
                        val next = actions[(actions.indexOf(settings.swipeDownAction) + 1) % actions.size]
                        onUpdateSettings(settings.copy(swipeDownAction = next))
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Deslizar para baixo: ${settings.swipeDownAction.label}", color = Color.White)
                }
            }

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                for (i in 0 until 6) {
                    val active = i == 1
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (active) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicIconsShowcaseScreen(
    settings: LauncherSettings,
    allApps: List<AppItem>,
    onUpdateSettings: (LauncherSettings) -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF07090E)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", tint = Color.White)
                }
                Text("Ícones dinâmicos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = settings.dynamicIconsGlobal,
                    onCheckedChange = {
                        IconResolver.clearCache()
                        onUpdateSettings(settings.copy(dynamicIconsGlobal = it))
                    }
                )
            }

            Text(
                "As cores dos ícones se adaptam\nao seu tema",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PalettePreset.entries.forEach { preset ->
                    val selected = settings.palettePreset == preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            IconResolver.clearCache()
                            onUpdateSettings(settings.copy(palettePreset = preset, dynamicIconsGlobal = true))
                        },
                        label = { Text(preset.label.take(12), fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allApps.take(20), key = { it.packageName }) { app ->
                    LauncherAppIcon(
                        app = app,
                        settings = settings,
                        showLabel = false,
                        forceDynamic = settings.dynamicIconsGlobal,
                        onClick = {},
                        onLongClick = {}
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF6366F1)
            )
        )
    }
}

@Composable
fun SettingsClickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint)
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
