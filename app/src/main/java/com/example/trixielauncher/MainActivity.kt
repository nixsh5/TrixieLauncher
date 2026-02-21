package com.example.trixielauncher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trixielauncher.ui.theme.TrixieLauncherTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            TrixieLauncherTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF000000)
                ) { innerPadding ->
                    TrixieHome(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrixieHome(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trixie_prefs", Context.MODE_PRIVATE) }

    var showAllApps by remember { mutableStateOf(false) }
    var isEditingFavoriteIndex by remember { mutableStateOf<Int?>(null) }
    var favorites by remember { mutableStateOf(loadFavorites(prefs)) }

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            currentDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now)
            kotlinx.coroutines.delay(1000)
        }
    }

    // Fix: Only intercept back when NOT on the main home screen
    BackHandler(enabled = showAllApps || isEditingFavoriteIndex != null) {
        if (isEditingFavoriteIndex != null) {
            isEditingFavoriteIndex = null
        } else {
            showAllApps = false
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        // 1. Time & Date Header
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Text(text = currentTime, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraLight)
            Text(text = currentDate, color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        }

        // 2. Main List - Perfectly Centered
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isEditingFavoriteIndex == null && !showAllApps) {
                // HOME VIEW
                favorites.forEachIndexed { index, app ->
                    Text(
                        text = app.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.combinedClickable(
                            onClick = { if (app.packageName.isNotEmpty()) launchApp(context, app.packageName) },
                            onLongClick = { isEditingFavoriteIndex = index }
                        )
                    )
                }
                Text(
                    text = "All Apps",
                    color = Color.DarkGray,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 10.dp).clickable { showAllApps = true }
                )
            } else {
                // ALL APPS / PICKER VIEW
                val allApps = remember { getInstalledApps(context) }

                Text(
                    text = if (isEditingFavoriteIndex != null) "select for slot ${isEditingFavoriteIndex!! + 1}" else "All Apps",
                    color = Color(0xFF00FF00),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Fix: Scrollable and Centered List
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(0.7f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(allApps) { app ->
                        Text(
                            text = app.name,
                            color = Color.White,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isEditingFavoriteIndex != null) {
                                        favorites = favorites.toMutableList().apply { this[isEditingFavoriteIndex!!] = app }
                                        saveFavorites(prefs, favorites)
                                        isEditingFavoriteIndex = null
                                    } else {
                                        launchApp(context, app.packageName)
                                        showAllApps = false
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

// Data and Logic Helpers
data class TrixieApp(val name: String, val packageName: String)

fun loadFavorites(prefs: SharedPreferences): List<TrixieApp> {
    val list = mutableListOf<TrixieApp>()
    for (i in 0..6) {
        val name = prefs.getString("fav_name_$i", "Empty Slot") ?: "Empty Slot"
        val pkg = prefs.getString("fav_pkg_$i", "") ?: ""
        list.add(TrixieApp(name, pkg))
    }
    return list
}

fun saveFavorites(prefs: SharedPreferences, list: List<TrixieApp>) {
    prefs.edit {
        list.forEachIndexed { i, app ->
            putString("fav_name_$i", app.name)
            putString("fav_pkg_$i", app.packageName)
        }
    }
}

fun getInstalledApps(context: Context): List<TrixieApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return pm.queryIntentActivities(intent, 0).map {
        TrixieApp(it.loadLabel(pm).toString(), it.activityInfo.packageName)
    }.sortedBy { it.name}
}

fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    intent?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(it) }
}
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TrixieLauncherTheme {
        Box(modifier = Modifier.background(Color.Black).fillMaxSize()) {
            TrixieHome()
        }
    }
}