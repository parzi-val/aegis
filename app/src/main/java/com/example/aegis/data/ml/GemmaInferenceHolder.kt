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

    // OBB dir survives "Clear App Data" (unlike getExternalFilesDir).
    // Push once: adb push gemma4e2b.litertlm /sdcard/Android/obb/com.example.aegis/gemma4e2b.litertlm
    val modelPath: String
        get() {
            val dir = context.obbDir.also { it.mkdirs() }
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
            ExperimentalFlags.enableSpeculativeDecoding = true
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
            )
            Engine(config).also {
                it.initialize()
                engine = it
                logger.log("engine initialized")
            }
        }
    }

    // Serialises all conversation usage across the app — only one session at a time.
    // Conversation.use{} (AutoCloseable) guarantees close() is called after the block.
    suspend fun <T> withSession(temperature: Double = 1.0, block: suspend (Conversation) -> T): T {
        val eng = getOrLoad()
        return sessionMutex.withLock {
            eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = temperature),
                )
            ).use { conversation -> block(conversation) }
        }
    }

    fun reset() {
        engine?.close()
        engine = null
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma4e2b.litertlm"
    }
}
