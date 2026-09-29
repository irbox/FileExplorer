package com.opensource.filemanager

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import java.io.File

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
    // Start at the root of the user's internal storage
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf(emptyList<File>()) }
    
    // Check if we have permission to read files
    var hasPermission by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true // Simplified fallback for older Android versions
            }
        )
    }

    // Refresh the file list whenever the path or permissions change
    LaunchedEffect(currentPath, hasPermission) {
        if (hasPermission) {
            val fileList = currentPath.listFiles()?.toList() ?: emptyList()
            // Sort: Directories first, then alphabetical
            files = fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentPath.name.ifEmpty { "Internal Storage" }) },
                navigationIcon = {
                    // Show a Back button if we aren't at the root directory
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
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (!hasPermission) {
                // UI for requesting permissions
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
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
            } else if (files.isEmpty()) {
                Text("Folder is empty", modifier = Modifier.align(Alignment.Center))
            } else {
                // The actual File List UI
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files) { file ->
                        FileListItem(file) {
                            if (file.isDirectory) {
                                currentPath = file
                            } else {
                                // TODO: Handle file opening (Video, Audio, Text)
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
