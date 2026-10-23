package com.imagegrabber.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    Search("بحث"), Favorites("المفضلة"), Downloads("التنزيلات"), Settings("الإعدادات")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { App() }
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.Search) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        vm.onCsvTarget(uri)
    }

    LaunchedEffect(tab) { if (tab != Tab.Settings) vm.lockAdmin() }

    LaunchedEffect(Unit) {
        vm.uiEvents.collect { e ->
            when (e) {
                is UiEvent.Msg -> scope.launch { snack.showSnackbar(e.text) }
                is UiEvent.PickFolder -> folderLauncher.launch(null)
                is UiEvent.SaveCsv -> csvLauncher.launch("images.csv")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.Search -> Icons.Default.Search
                                    Tab.Favorites -> Icons.Default.Favorite
                                    Tab.Downloads -> Icons.Default.Check
                                    Tab.Settings -> Icons.Default.Settings
                                }, null
                            )
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Search -> SearchScreen(vm)
                Tab.Favorites -> FavoritesScreen(vm)
                Tab.Downloads -> DownloadsScreen(vm)
                Tab.Settings -> if (vm.adminOpen) AdminScreen(vm) else SettingsScreen(vm)
            }
        }
    }

    if (vm.showAdminLogin) AdminLoginDialog(vm)
    vm.detail?.let { DetailSheet(vm, it) }
    vm.pendingDownload?.let { DownloadConfirmDialog(vm, it) }
}
