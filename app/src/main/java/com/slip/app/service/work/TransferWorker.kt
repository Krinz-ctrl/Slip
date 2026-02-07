package com.slip.app.service.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Worker for handling background file transfers
 */
class TransferWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        private const val TAG = "TransferWorker"
        
        // Input data keys
        const val KEY_TRANSFER_SESSION = "transfer_session"
        const val KEY_TRANSFER_ID = "transfer_id"
        
        // Output data keys
        const val KEY_RESULT_STATUS = "result_status"
        const val KEY_RESULT_MESSAGE = "result_message"
        const val KEY_RESULT_PROGRESS = "result_progress"
        
        // Work names
        const val TRANSFER_WORK_NAME = "transfer_work"
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "TransferWorker started")
        
        try {
            // Get transfer session from input data
            val transferSession = getTransferSessionFromInput()
            if (transferSession == null) {
                Log.e(TAG, "No transfer session provided")
                return Result.failure()
            }
            
            Log.d(TAG, "Processing transfer: ${transferSession.id}")
            
            // Simulate transfer work (will be replaced with actual transfer logic)
            val success = performTransferWork(transferSession)
            
            return if (success) {
                Log.d(TAG, "Transfer completed successfully")
                Result.success(createSuccessOutput())
            } else {
                Log.w(TAG, "Transfer failed, will retry")
                Result.retry()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Transfer worker failed", e)
            return Result.retry()
        }
    }
    
    private fun getTransferSessionFromInput(): TransferSession? {
        return try {
            // In a real implementation, we would serialize/deserialize TransferSession
            // For now, create a dummy session for testing
            TransferSession(
                id = inputData.getString(KEY_TRANSFER_ID) ?: "unknown",
                type = com.slip.app.domain.model.TransferType.SEND_FILES,
                status = TransferStatus.PENDING,
                totalSize = inputData.getLong("total_size", 0L)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse transfer session", e)
            null
        }
    }
    
    private suspend fun performTransferWork(transferSession: TransferSession): Boolean {
        Log.d(TAG, "Starting transfer work for ${transferSession.id}")
        
        // Simulate transfer progress
        var progress = 0
        val maxProgress = 100
        val stepDelay = 2000L // 2 seconds per step
        
        while (progress < maxProgress) {
            delay(stepDelay)
            progress += 10
            
            Log.d(TAG, "Transfer progress: $progress%")
            
            // Check if work should be stopped
            if (isStopped) {
                Log.d(TAG, "Work was stopped")
                return false
            }
            
            // Update progress (in a real implementation, this would update the TransferService)
            setProgressAsync(createProgressOutput(progress))
        }
        
        return true
    }
    
    private fun createSuccessOutput(): androidx.work.Data {
        return androidx.work.Data.Builder()
            .putString(KEY_RESULT_STATUS, "completed")
            .putString(KEY_RESULT_MESSAGE, "Transfer completed successfully")
            .putInt(KEY_RESULT_PROGRESS, 100)
            .build()
    }
    
    private fun createProgressOutput(progress: Int): androidx.work.Data {
        return androidx.work.Data.Builder()
            .putString(KEY_RESULT_STATUS, "in_progress")
            .putString(KEY_RESULT_MESSAGE, "Transfer in progress")
            .putInt(KEY_RESULT_PROGRESS, progress)
            .build()
    }
}
