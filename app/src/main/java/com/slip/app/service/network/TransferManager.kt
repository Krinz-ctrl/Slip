package com.slip.app.service.network

import android.content.Context
import android.util.Log
import com.slip.app.domain.model.DiscoveredDevice
import com.slip.app.domain.model.FileMetadata
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Manager for coordinating socket transfers between devices
 */
class TransferManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TransferManager"
        
        @Volatile
        private var INSTANCE: TransferManager? = null
        
        fun getInstance(context: Context): TransferManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransferManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val socketServer = SocketTransferServer(context)
    private val socketClient = SocketTransferClient(context)
    
    // Current transfer session
    private var currentTransferSession: TransferSession? = null
    
    /**
     * Start receiving mode (server)
     */
    fun startReceiving(): Boolean {
        Log.d(TAG, "Starting receiving mode")
        return socketServer.startServer()
    }
    
    /**
     * Stop receiving mode
     */
    fun stopReceiving() {
        Log.d(TAG, "Stopping receiving mode")
        socketServer.stopServer()
    }
    
    /**
     * Send files to a device
     */
    suspend fun sendFiles(
        device: DiscoveredDevice,
        files: List<FileMetadata>,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        Log.d(TAG, "Sending ${files.size} files to ${device.name}")
        
        if (files.isEmpty()) {
            Log.w(TAG, "No files to send")
            return false
        }
        
        try {
            // Connect to device
            val success = socketClient.sendFile(
                host = device.ipAddress.hostAddress,
                port = device.port,
                fileMetadata = files.first(), // For now, send first file only
                progressCallback = onProgress
            )
            
            if (success) {
                Log.d(TAG, "Files sent successfully to ${device.name}")
            } else {
                Log.e(TAG, "Failed to send files to ${device.name}")
            }
            
            return success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending files to ${device.name}", e)
            return false
        }
    }
    
    /**
     * Get transfer progress (for sending)
     */
    fun getSendProgress(): StateFlow<Float> {
        return socketClient.transferProgress
    }
    
    /**
     * Get transfer status (for sending)
     */
    fun getSendStatus(): StateFlow<TransferStatus> {
        return socketClient.transferStatus
    }
    
    /**
     * Get receive progress
     */
    fun getReceiveProgress(): StateFlow<Float> {
        return socketServer.transferProgress
    }
    
    /**
     * Get receive status
     */
    fun getReceiveStatus(): StateFlow<TransferStatus> {
        return socketServer.transferStatus
    }
    
    /**
     * Get connected clients
     */
    fun getConnectedClients(): StateFlow<List<String>> {
        return socketServer.connectedClients
    }
    
    /**
     * Get server status
     */
    fun getServerStatus() = socketServer.getServerStatus()
    
    /**
     * Check if server is running
     */
    fun isReceiving(): Boolean {
        return socketServer.getServerStatus().isRunning
    }
    
    /**
     * Set current transfer session
     */
    fun setCurrentTransferSession(session: TransferSession) {
        currentTransferSession = session
    }
    
    /**
     * Get current transfer session
     */
    fun getCurrentTransferSession(): TransferSession? {
        return currentTransferSession
    }
    
    /**
     * Cancel current transfer
     */
    fun cancelTransfer() {
        Log.d(TAG, "Cancelling current transfer")
        
        // Stop server if running
        if (isReceiving()) {
            stopReceiving()
        }
        
        // Cancel client transfer
        socketClient.clientScope.launch {
            // Client doesn't have explicit cancel, but we can stop server
        }
        
        currentTransferSession = null
    }
    
    /**
     * Get combined transfer status
     */
    fun getCombinedTransferStatus(): StateFlow<TransferStatus> {
        return combine(
            socketServer.transferStatus,
            socketClient.transferStatus
        ) { serverStatus, clientStatus ->
            when {
                serverStatus != TransferStatus.IDLE && serverStatus != TransferStatus.LISTENING -> serverStatus
                clientStatus != TransferStatus.IDLE -> clientStatus
                else -> TransferStatus.IDLE
            }
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up transfer manager")
        stopReceiving()
        socketServer.serverScope.cancel()
        socketClient.clientScope.cancel()
    }
}
