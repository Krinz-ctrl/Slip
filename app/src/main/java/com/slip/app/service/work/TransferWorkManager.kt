package com.slip.app.service.work

import android.content.Context
import android.util.Log
import androidx.lifecycle.asFlow
import androidx.work.*
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.data.repository.PersistentTransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Manager for WorkManager transfer operations
 */
class TransferWorkManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TransferWorkManager"
        private const val TRANSFER_WORK_TAG = "transfer_work"
        
        // Unique work names
        const val UNIQUE_TRANSFER_WORK = "unique_transfer_work"
        
        // Retry configuration
        private const val RETRY_DELAY_MINUTES = 1L
        private const val MAX_RETRY_ATTEMPTS = 3
    }
    
    private val workManager = WorkManager.getInstance(context)
    private val persistentRepository = PersistentTransferRepository.getInstance(context)
    
    /**
     * Schedule a transfer work request
     */
    suspend fun scheduleTransfer(transferSession: TransferSession): WorkInfo {
        Log.d(TAG, "Scheduling transfer work for ${transferSession.id}")
        
        // Save transfer session
        persistentRepository.saveTransferSession(transferSession)
        
        // Create work request
        val workRequest = createTransferWorkRequest(transferSession)
        
        // Enqueue unique work (replaces any existing work for this transfer)
        workManager.enqueueUniqueWork(
            UNIQUE_TRANSFER_WORK,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        
        // Get work info
        return workManager.getWorkInfoByIdLiveData(workRequest.id).value ?: createDefaultWorkInfo()
    }
    
    /**
     * Create a OneTimeWorkRequest for transfer
     */
    private fun createTransferWorkRequest(transferSession: TransferSession): OneTimeWorkRequest {
        // Build input data
        val inputData = createInputData(transferSession)
        
        // Create work request with constraints and retry policy
        return OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(inputData)
            .setConstraints(createTransferConstraints())
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                RETRY_DELAY_MINUTES,
                TimeUnit.MINUTES
            )
            .addTag(TransferWorker.TRANSFER_WORK_NAME)
            .build()
    }
    
    /**
     * Create input data for the worker
     */
    private fun createInputData(transferSession: TransferSession): Data {
        return Data.Builder()
            .putString(TransferWorker.KEY_TRANSFER_ID, transferSession.id)
            .putLong("total_size", transferSession.totalSize)
            // In a real implementation, we would serialize the entire TransferSession
            .build()
    }
    
    /**
     * Create constraints for transfer work
     */
    private fun createTransferConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Require network connectivity
            .setRequiresBatteryNotLow(true) // Don't run when battery is low
            .setRequiresStorageNotLow(true) // Don't run when storage is low
            .build()
    }
    
    /**
     * Restart a transfer (for resume/retry)
     */
    suspend fun restartTransfer(transferSession: TransferSession): WorkInfo {
        Log.d(TAG, "Restarting transfer work for ${transferSession.id}")
        
        // Update session status
        persistentRepository.saveTransferSession(transferSession)
        
        // Create new work request
        val workRequest = createTransferWorkRequest(transferSession)
        
        // Enqueue unique work
        workManager.enqueueUniqueWork(
            UNIQUE_TRANSFER_WORK,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        
        // Get work info
        return getWorkInfo(transferSession.id)
    }
    
    /**
     * Cancel a transfer
     */
    suspend fun cancelTransfer(sessionId: String) {
        Log.d(TAG, "Cancelling transfer work for $sessionId")
        
        // Cancel work
        workManager.cancelUniqueWork(UNIQUE_TRANSFER_WORK)
        
        // Update status
        persistentRepository.updateTransferStatus(sessionId, TransferStatus.CANCELLED)
    }
    
    /**
     * Get transfer work info
     */
    fun getTransferWorkInfo(): Flow<WorkInfo> {
        return workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_TRANSFER_WORK).asFlow()
    }
    
    /**
     * Get work info for specific transfer
     */
    fun getWorkInfo(sessionId: String): WorkInfo {
        // For now, return the current work info
        // In a real implementation, you'd track multiple concurrent transfers
        return workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_TRANSFER_WORK).asFlow().map { workInfos ->
            workInfos.firstOrNull()
        }.value ?: throw IllegalStateException("No work info found for $sessionId")
    }
    
    /**
     * Check if transfer work is running
     */
    fun isTransferRunning(): Boolean {
        val workInfos = workManager.getWorkInfosForUniqueWork(UNIQUE_TRANSFER_WORK).get()
        return workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }
    
    /**
     * Get transfer work progress
     */
    fun getTransferProgress(): Flow<Int> {
        return workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_TRANSFER_WORK).asFlow()
            .map { workInfos ->
                workInfos.firstOrNull()?.progress?.getInt(TransferWorker.KEY_RESULT_PROGRESS, 0) ?: 0
            }
    }
    
    /**
     * Get transfer work status
     */
    fun getTransferStatus(): Flow<TransferStatus> {
        return workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_TRANSFER_WORK).asFlow()
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                when (workInfo?.state) {
                    WorkInfo.State.ENQUEUED -> TransferStatus.PENDING
                    WorkInfo.State.RUNNING -> TransferStatus.IN_PROGRESS
                    WorkInfo.State.SUCCEEDED -> TransferStatus.COMPLETED
                    WorkInfo.State.FAILED -> TransferStatus.FAILED
                    WorkInfo.State.CANCELLED -> TransferStatus.CANCELLED
                    else -> TransferStatus.PENDING
                }
            }
    }
    
    /**
     * Clear all transfer work
     */
    fun clearAllTransferWork() {
        Log.d(TAG, "Clearing all transfer work")
        workManager.cancelAllWorkByTag(TransferWorker.TRANSFER_WORK_NAME)
    }
    
    /**
     * Get work statistics
     */
    fun getWorkStatistics(): TransferWorkStatistics {
        val workInfos = workManager.getWorkInfosForUniqueWork(UNIQUE_TRANSFER_WORK).get()
        val workInfo = workInfos.firstOrNull()
        
        return TransferWorkStatistics(
            isRunning = workInfo?.state == WorkInfo.State.RUNNING,
            isEnqueued = workInfo?.state == WorkInfo.State.ENQUEUED,
            progress = workInfo?.progress?.getInt(TransferWorker.KEY_RESULT_PROGRESS, 0) ?: 0,
            runAttemptCount = workInfo?.runAttemptCount ?: 0,
            state = workInfo?.state ?: WorkInfo.State.CANCELLED
        )
    }
    
    private fun createDefaultWorkInfo(): WorkInfo {
        // Create a default work info for cases where work hasn't been scheduled yet
        return androidx.work.WorkInfo(
            androidx.work.UUID.randomUUID(),
            WorkInfo.State.CANCELLED,
            Data.EMPTY,
            emptySet(),
            emptySet(),
            0,
            0,
            0
        )
    }
}

/**
 * Statistics for transfer work
 */
data class TransferWorkStatistics(
    val isRunning: Boolean,
    val isEnqueued: Boolean,
    val progress: Int,
    val runAttemptCount: Int,
    val state: WorkInfo.State
)
