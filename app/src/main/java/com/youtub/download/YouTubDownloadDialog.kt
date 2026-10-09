package com.youtub.download

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.youtub.domain.model.StreamItem

/**
 * Compose-based Download Dialog with format selection.
 * Inspired by PipePipe's DownloadDialog but built with Jetpack Compose.
 * 
 * Features:
 * - Video/Audio/Subtitle tab selection
 * - Quality/format picker
 * - Thread count slider (1-5)
 * - Filename editing
 */
@Composable
fun YouTubDownloadDialog(
    videoTitle: String,
    videoStreams: List<StreamItem>,
    audioStreams: List<StreamItem>,
    onDownload: (selectedStream: StreamItem, threadCount: Int, fileName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Video, 1=Audio, 2=Subtitle
    var selectedStreamIndex by remember { mutableIntStateOf(0) }
    var threadCount by remember { mutableIntStateOf(3) }
    var fileName by remember { mutableStateOf(videoTitle) }
    
    val tabs = listOf("Video", "Audio", "Subtitle")
    val currentStreams = when (selectedTab) {
        0 -> videoStreams
        1 -> audioStreams
        else -> emptyList() // Subtitles handled separately
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab selection
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { 
                                selectedTab = index
                                selectedStreamIndex = 0
                            },
                            text = { Text(title) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Stream quality selection
                if (currentStreams.isNotEmpty()) {
                    Text("Quality:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(currentStreams.indices.toList()) { index ->
                            val stream = currentStreams[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedStreamIndex == index,
                                        onClick = { selectedStreamIndex = index },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedStreamIndex == index,
                                    onClick = { selectedStreamIndex = index }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stream.qualityLabel ?: "Unknown",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "No ${tabs[selectedTab].lowercase()} streams available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Thread count slider
                Text("Threads: $threadCount", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = threadCount.toFloat(),
                    onValueChange = { threadCount = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Filename
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (currentStreams.isNotEmpty() && selectedStreamIndex < currentStreams.size) {
                        onDownload(currentStreams[selectedStreamIndex], threadCount, fileName)
                    }
                },
                enabled = currentStreams.isNotEmpty()
            ) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

/**
 * Extension property to get quality label from StreamItem
 */
private val StreamItem.qualityLabel: String?
    get() = when {
        !quality.isNullOrEmpty() -> "$quality (${format ?: "unknown"})"
        !format.isNullOrEmpty() -> format
        else -> null
    }
