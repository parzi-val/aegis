@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.aegis.ui.screens.gemma

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@Composable
fun GemmaScreen(onBack: () -> Unit) {
    val vm: GemmaViewModel = hiltViewModel()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val partial by vm.partialResponse.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val imageLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { vm.pendingImageUri = it }
    }

    LaunchedEffect(messages.size, partial) {
        val total = messages.size + if (partial.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Gemma 3n PoC") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = vm::clearChat) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear chat")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when (vm.modelState) {
                GemmaViewModel.ModelState.NOT_LOADED -> NotLoadedBanner(onLoad = vm::loadModel)
                GemmaViewModel.ModelState.LOADING -> LoadingBanner()
                GemmaViewModel.ModelState.ERROR -> ErrorBanner(message = vm.errorMessage, onRetry = vm::loadModel)
                GemmaViewModel.ModelState.READY -> Unit
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(messages) { msg -> ChatBubble(msg) }
                if (partial.isNotEmpty()) {
                    item { ChatBubble(GemmaViewModel.ChatMessage(partial + " ▌", isUser = false)) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (vm.modelState == GemmaViewModel.ModelState.READY) {
                InputRow(
                    isGenerating = vm.isGenerating,
                    pendingImageUri = vm.pendingImageUri,
                    onAttachImage = { imageLauncher.launch("image/*") },
                    onClearImage = { vm.pendingImageUri = null },
                    onSend = vm::send,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: GemmaViewModel.ChatMessage) {
    val clipboard = LocalClipboardManager.current
    val bubbleColor = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (msg.isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (msg.isUser) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
            Surface(color = bubbleColor, shape = shape, modifier = Modifier.widthIn(max = 300.dp)) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (msg.imageUri != null) {
                        AsyncImage(
                            model = msg.imageUri,
                            contentDescription = null,
                            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        if (msg.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }
                    if (msg.text.isNotBlank()) {
                        Text(text = msg.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
            }
            if (!msg.isUser && msg.text.isNotBlank()) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(msg.text)) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputRow(
    isGenerating: Boolean,
    pendingImageUri: android.net.Uri?,
    onAttachImage: () -> Unit,
    onClearImage: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (pendingImageUri != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = pendingImageUri,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = "  Image attached",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClearImage, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove image", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onAttachImage, enabled = !isGenerating) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "Attach image",
                        tint = if (!isGenerating) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(if (pendingImageUri != null) "Ask about the image…" else "Ask Gemma anything…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!isGenerating && (text.isNotBlank() || pendingImageUri != null)) {
                            onSend(text); text = ""
                        }
                    }),
                    enabled = !isGenerating,
                )
                IconButton(
                    onClick = {
                        if (!isGenerating && (text.isNotBlank() || pendingImageUri != null)) {
                            onSend(text); text = ""
                        }
                    },
                    enabled = !isGenerating && (text.isNotBlank() || pendingImageUri != null),
                ) {
                    if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun NotLoadedBanner(onLoad: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Model not loaded", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Button(onClick = onLoad) { Text("Load") }
        }
    }
}

@Composable
private fun LoadingBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Loading Gemma 3n — this may take 30–60 seconds…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Error", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
