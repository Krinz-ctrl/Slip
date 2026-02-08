package com.slip.app.service.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.data.repository.PersistentTransferRepository
import com.slip.app.data.repository.ChunkRepository
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
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1000L
        
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
    
    private lateinit var persistentRepository: PersistentTransferRepository
    private lateinit var chunkRepository: ChunkRepository
    
    override suspend fun doWork(): Result {
        try {
            // Initialize repositories
            persistentRepository = PersistentTransferRepository.getInstance(applicationContext)
            chunkRepository = ChunkRepository.getInstance(applicationContext)
            
            // Get transfer session
            val transferSessionJson = inputData.getString(KEY_TRANSFER_SESSION)
                ?: return Result.failure(Exception("Transfer session not provided"))
            
            val transferSession = com.google.gson.Gson().fromJson(transferSessionJson, TransferSession::class.java)
            
            Log.d(TAG, "Starting transfer work for ${transferSession.id}")
            
            // Update status to connecting
            persistentRepository.updateTransferStatus(transferSession.id, TransferStatus.CONNECTING)
            
            // Perform transfer based on type
            val result = when (transferSession.type) {
                com.slip.app.domain.model.TransferType.SEND_FILES -> performSendTransfer(transferSession)
                com.slip.app.domain.model.TransferType.SEND_FOLDER -> performSendTransfer(transferSession)
                com.slip.app.domain.model.TransferType.RECEIVE_FILES -> performReceiveTransfer(transferSession)
                com.slip.app.domain.model.TransferType.RECEIVE_FOLDER -> performReceiveTransfer(transferSession)
            }
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Transfer work failed", e)
            
            // Update transfer status to failed
            val transferId = inputData.getString(KEY_TRANSFER_ID)
            transferId?.let {
                persistentRepository.updateTransferStatus(it, TransferStatus.FAILED, e.message)
            }
            
            return Result.failure(e)
        }
    }
    
    /**
     * Perform send transfer
     */
    private suspend fun performSendTransfer(transferSession: TransferSession): Result {
        try {
            Log.d(TAG, "Performing send transfer for ${transferSession.id}")
            
            // Update status to in progress
            persistentRepository.updateTransferStatus(transferSession.id, TransferStatus.IN_PROGRESS)
            
            // TODO: Implement actual send transfer logic using TransferManager
            // For now, simulate progress
            var progress = 0f
            while (progress < 100f) {
                progress += 10f
                delay(1000)
                
                // Update progress
                val updatedSession = transferSession.copy(progress = progress)
                persistentRepository.saveTransferSession(updatedSession)
            }
            
            // Mark as completed
            persistentRepository.updateTransferStatus(transferSession.id, TransferStatus.COMPLETED)
            
            Log.d(TAG, "Send transfer completed: ${transferSession.id}")
            return Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Send transfer failed", e)
            persistentRepository.updateTransferStatus(transferSession.id, TransferStatus.FAILED, e.message)
            return Result.failure(e)
        }
    }
    
    /**
     * Perform receive transfer
     */
    private suspend fun performReceiveTransfer(transferSession: TransferSession): Result {
        try {
            Log.d(TAG, "Performing receive transfer for ${transferSession.id}")
            
            // Update status to in progress
            persistentRepository.updateTransferStatus(transferSession.id, TransferStatus.IN_PROGRESS)
            
            // TODO: Implement actual receive transfer logic using TransferManager
            // For now, simulate progress
            var progress = 0f
            while (progress < 100f) {
                progress += 10f
                delay(1000)
                
                // Update progress
                val updatedSession = transferSession.copy(progress = progress)
                persistentRepository.saveTransferSession(updatedSession)
            }
            
            // Mark as completed
            persistentRepository.updateTransferStatus(transferSession.id, TransferStatus.COMPLETED)
            
            Log.d(TAG, "Receive transfer completed: ${transferSession.id}")
            return Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Receive transfer failed", e)
            persistentRepository.updateTransferStatus(transferSession.id, TransferStatus.FAILED, e.message)
            return Result.failure(e)
        }
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
