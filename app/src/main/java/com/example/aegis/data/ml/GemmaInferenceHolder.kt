package com.example.aegis.data.ml

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaInferenceHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    val logger: ExtractionLogger,
) {
    private var engine: Engine? = null
    private val loadMutex = Mutex()
    private val sessionMutex = Mutex()

    // External app-private storage — no permission needed, easy ADB access.
    // Push: adb push gemma4e2b.litertlm /sdcard/Android/data/com.example.aegis/files/gemma4e2b.litertlm
    val modelPath: String
        get() {
            val dir = (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }
            return File(dir, MODEL_FILE_NAME).absolutePath
        }

    val isModelAvailable: Boolean
        get() = File(modelPath).exists()

    val isLoaded: Boolean
        get() = engine != null

    @OptIn(ExperimentalApi::class)
    suspend fun getOrLoad(): Engine = loadMutex.withLock {
        engine?.also { logger.log("engine cache hit") } ?: run {
            logger.log("engine not cached — loading from $modelPath")
            try {
                ExperimentalFlags.enableSpeculativeDecoding = true
                Engine(EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                )).also {
                    it.initialize()
                    engine = it
                    logger.log("engine initialized")
                }
            } catch (e: Exception) {
                engine = null
                logger.log("engine load failed: ${e.message}")
                throw e
            }
        }
    }

    // Serialises all conversation usage across the app — only one session at a time.
    // Conversation.use{} (AutoCloseable) guarantees close() is called after the block.
    // Resets the engine on any session failure so the next caller gets a clean load.
    suspend fun <T> withSession(temperature: Double = 1.0, block: suspend (Conversation) -> T): T {
        val eng = getOrLoad()
        return sessionMutex.withLock {
            try {
                eng.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = temperature),
                    )
                ).use { conversation -> block(conversation) }
            } catch (e: Exception) {
                logger.log("session failed, resetting engine: ${e.message}")
                reset()
                throw e
            }
        }
    }

    fun reset() {
        try { engine?.close() } catch (_: Exception) {}
        engine = null
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma4e2b.litertlm"
    }
}
