package com.opensource.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opensource.filemanager.server.PcShareServer
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileManagerScreen(
                        onRequestPermission = { requestStoragePermission() }
                    )
                }
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf(emptyList<File>()) }
    
    var isServerRunning by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf<PcShareServer?>(null) }
    val localIpAddress = remember { getLocalIpAddress(context) }

    var hasPermission by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true 
            }
        )
    }

    // Safely load files with crash-prevention try-catch
    LaunchedEffect(currentPath, hasPermission) {
        if (hasPermission) {
            files = try {
                val fileList = currentPath.listFiles()?.toList() ?: emptyList()
                fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            server?.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentPath.name.ifEmpty { "Internal Storage" }) },
                navigationIcon = {
                    if (currentPath.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                        Button(
                            onClick = { currentPath = currentPath.parentFile ?: currentPath },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Back")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            
            if (hasPermission) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Access from PC", style = MaterialTheme.typography.titleMedium)
                            if (isServerRunning) {
                                Text("http://$localIpAddress:8080", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Server stopped", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Switch(
                            checked = isServerRunning,
                            onCheckedChange = { start ->
                                if (start) {
                                    server = PcShareServer(Environment.getExternalStorageDirectory())
                                    server?.start()
                                    isServerRunning = true
                                } else {
                                    server?.stop()
                                    server = null
                                    isServerRunning = false
                                }
                            }
                        )
                    }
                }
                HorizontalDivider()
            }

            if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Storage permission is required.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { 
                        onRequestPermission()
                        hasPermission = true 
                    }) {
                        Text("Grant Permission")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files) { file ->
                        FileListItem(file) {
                            if (file.isDirectory) {
                                currentPath = file
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun FileListItem(file: File, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (file.isDirectory) "📁" else "📄",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
            if (!file.isDirectory) {
                Text(
                    text = "${file.length() / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun getLocalIpAddress(context: Context): String {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            Locale.ROOT,
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    } catch (e: Exception) {
        return "127.0.0.1"
    }
}
