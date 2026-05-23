package com.example.aegis.ui.screens.gemma

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.ml.GemmaInferenceHolder
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class GemmaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inferenceHolder: GemmaInferenceHolder,
) : ViewModel() {

    enum class ModelState { NOT_LOADED, LOADING, READY, ERROR }

    data class ChatMessage(val text: String, val isUser: Boolean, val imageUri: Uri? = null)

    var modelState by mutableStateOf(ModelState.NOT_LOADED)
        private set
    var errorMessage by mutableStateOf("")
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var pendingImageUri by mutableStateOf<Uri?>(null)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _partialResponse = MutableStateFlow("")
    val partialResponse: StateFlow<String> = _partialResponse.asStateFlow()

    private var conversation: Conversation? = null

    init {
        inferenceHolder.cachedEngine?.let {
            conversation = inferenceHolder.newConversation(it)
            modelState = ModelState.READY
        }
    }

    fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            modelState = ModelState.LOADING
            try {
                if (!inferenceHolder.isModelAvailable) {
                    errorMessage = "Model file not found.\n\nCopy ${GemmaInferenceHolder.MODEL_FILE_NAME} to:\n${inferenceHolder.modelPath}"
                    modelState = ModelState.ERROR
                    return@launch
                }
                val engine = inferenceHolder.getOrLoad()
                conversation = inferenceHolder.newConversation(engine)
                modelState = ModelState.READY
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load model"
                modelState = ModelState.ERROR
            }
        }
    }

    fun send(userText: String) {
        val conv = conversation ?: return
        if (isGenerating) return

        val imageUri = pendingImageUri
        pendingImageUri = null
        if (userText.isBlank() && imageUri == null) return

        _messages.update { it + ChatMessage(userText, isUser = true, imageUri = imageUri) }
        isGenerating = true
        _partialResponse.value = ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val callback = object : MessageCallback {
                    override fun onMessage(message: Message) {
                        _partialResponse.update { it + message.toString() }
                    }
                    override fun onDone() { finishResponse() }
                    override fun onError(throwable: Throwable) {
                        _messages.update { it + ChatMessage("Error: ${throwable.message}", isUser = false) }
                        _partialResponse.value = ""
                        isGenerating = false
                    }
                }

                if (imageUri != null) {
                    val bytes = loadImageAsBytes(imageUri)
                    if (bytes != null) {
                        val parts = buildList {
                            add(Content.ImageBytes(bytes))
                            if (userText.isNotBlank()) add(Content.Text(userText))
                        }
                        conv.sendMessageAsync(Contents.of(parts), callback, emptyMap())
                    } else {
                        // image load failed, fall back to text
                        conv.sendMessageAsync(userText, callback)
                    }
                } else {
                    conv.sendMessageAsync(userText, callback)
                }
            } catch (e: Exception) {
                _messages.update { it + ChatMessage("Error: ${e.message}", isUser = false) }
                _partialResponse.value = ""
                isGenerating = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _partialResponse.value = ""
        pendingImageUri = null
        inferenceHolder.cachedEngine?.let {
            conversation?.close()
            conversation = inferenceHolder.newConversation(it)
        }
    }

    private fun finishResponse() {
        val full = _partialResponse.value
        _messages.update { it + ChatMessage(full, isUser = false) }
        _partialResponse.value = ""
        isGenerating = false
    }

    private fun loadImageAsBytes(uri: Uri): ByteArray? = try {
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }

    override fun onCleared() {
        conversation?.close()
        super.onCleared()
    }
}
