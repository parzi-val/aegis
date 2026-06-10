package com.example.aegis.ui.screens.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.aegis.data.ml.GemmaInferenceHolder
import com.example.aegis.data.worker.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val inferenceHolder: GemmaInferenceHolder,
) : ViewModel() {

    sealed class State {
        data object NotFound : State()
        data object Done : State()
        data class Downloading(val received: Long, val total: Long) : State()
        data class Error(val message: String) : State()
    }

    private val workManager = WorkManager.getInstance(context)

    val state: StateFlow<State> = workManager
        .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
        .map { infos -> toState(infos.firstOrNull()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            toState(null),
        )

    fun startDownload() {
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ModelDownloadWorker>().build(),
        )
    }

    fun retry() {
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ModelDownloadWorker>().build(),
        )
    }

    private fun toState(info: WorkInfo?): State {
        if (inferenceHolder.isModelAvailable) return State.Done
        return when (info?.state) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                val received = info.progress.getLong(ModelDownloadWorker.KEY_RECEIVED, 0L)
                val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1L)
                State.Downloading(received, total)
            }
            WorkInfo.State.SUCCEEDED -> State.Done
            WorkInfo.State.FAILED -> State.Error(
                info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed"
            )
            else -> State.NotFound
        }
    }
}
