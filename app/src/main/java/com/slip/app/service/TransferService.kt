package com.slip.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.slip.app.R
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.data.repository.TransferRepository
import com.slip.app.data.repository.PersistentTransferRepository
import com.slip.app.service.work.TransferWorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service for handling file transfers
 */
class TransferService : Service() {
    
    companion object {
        private const val TAG = "TransferService"
        private const val NOTIFICATION_CHANNEL_ID = "transfer_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Actions for service control
        const val ACTION_START_TRANSFER = "com.slip.app.START_TRANSFER"
        const val ACTION_PAUSE_TRANSFER = "com.slip.app.PAUSE_TRANSFER"
        const val ACTION_RESUME_TRANSFER = "com.slip.app.RESUME_TRANSFER"
        const val ACTION_CANCEL_TRANSFER = "com.slip.app.CANCEL_TRANSFER"
        
        // Extras
        const val EXTRA_TRANSFER_SESSION = "transfer_session"
        
        /**
         * Start the transfer service
         */
        fun startTransfer(context: Context, transferSession: TransferSession) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_START_TRANSFER
                putExtra(EXTRA_TRANSFER_SESSION, transferSession)
            }
            context.startService(intent)
        }
        
        /**
         * Pause transfer
         */
        fun pauseTransfer(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_PAUSE_TRANSFER
            }
            context.startService(intent)
        }
        
        /**
         * Resume transfer
         */
        fun resumeTransfer(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_RESUME_TRANSFER
            }
            context.startService(intent)
        }
        
        /**
         * Cancel transfer
         */
        fun cancelTransfer(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_CANCEL_TRANSFER
            }
            context.startService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private lateinit var transferRepository: TransferRepository
    private lateinit var persistentRepository: PersistentTransferRepository
    private lateinit var transferWorkManager: TransferWorkManager
    
    // Current transfer session
    private var currentTransferSession: TransferSession? = null
    
    // Transfer state flow
    private val _transferState = MutableStateFlow<TransferSession?>(null)
    val transferState: StateFlow<TransferSession?> = _transferState
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize dependencies
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        transferRepository = TransferRepository.getInstance(this)
        persistentRepository = PersistentTransferRepository.getInstance(this)
        transferWorkManager = TransferWorkManager(this)
        
        // Create notification channel
        createNotificationChannel()
        
        // Resume interrupted transfers
        serviceScope.launch {
            val resumableTransfers = persistentRepository.resumeInterruptedTransfers()
            if (resumableTransfers.isNotEmpty()) {
                Log.d(TAG, "Resumed ${resumableTransfers.size} interrupted transfers")
            }
        }
        
        Log.d(TAG, "TransferService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_TRANSFER -> {
                val transferSession = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_TRANSFER_SESSION, TransferSession::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_TRANSFER_SESSION) as? TransferSession
                }
                
                transferSession?.let { session ->
                    startTransfer(session)
                }
            }
            
            ACTION_PAUSE_TRANSFER -> {
                pauseTransfer()
            }
            
            ACTION_RESUME_TRANSFER -> {
                resumeTransfer()
            }
            
            ACTION_CANCEL_TRANSFER -> {
                cancelTransfer()
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of file transfers"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startTransfer(transferSession: TransferSession) {
        Log.d(TAG, "Starting transfer: ${transferSession.id}")
        
        currentTransferSession = transferSession.copy(status = TransferStatus.CONNECTING)
        _transferState.value = currentTransferSession
        
        // Update repository
        transferRepository.startTransfer(transferSession)
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification(currentTransferSession!!))
    }
    
    private fun pauseTransfer() {
        Log.d(TAG, "Pausing transfer")
        transferRepository.pauseTransfer()
        
        currentTransferSession?.let { session ->
            if (session.status == TransferStatus.IN_PROGRESS) {
                currentTransferSession = session.copy(
                    status = TransferStatus.PAUSED,
                    isPaused = true
                )
                _transferState.value = currentTransferSession
                updateNotification()
            }
        }
    }
    
    private fun resumeTransfer() {
        Log.d(TAG, "Resuming transfer")
        transferRepository.resumeTransfer()
        
        currentTransferSession?.let { session ->
            if (session.status == TransferStatus.PAUSED) {
                currentTransferSession = session.copy(
                    status = TransferStatus.IN_PROGRESS,
                    isPaused = false
                )
                _transferState.value = currentTransferSession
                updateNotification()
            }
        }
    }
    
    private fun cancelTransfer() {
        Log.d(TAG, "Cancelling transfer")
        transferRepository.cancelTransfer()
        
        currentTransferSession?.let { session ->
            currentTransferSession = session.copy(
                status = TransferStatus.CANCELLED,
                endTime = System.currentTimeMillis()
            )
            _transferState.value = currentTransferSession
            updateNotification()
            
            // Stop service after a short delay to show final notification
            serviceScope.launch {
                kotlinx.coroutines.delay(2000)
                stopForeground(true)
                stopSelf()
            }
        }
    }
    
    private fun createNotification(transferSession: TransferSession): Notification {
        val intent = Intent(this, com.slip.app.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("File Transfer")
            .setContentText(getNotificationText(transferSession))
            .setSmallIcon(R.drawable.ic_file)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        
        // Add progress bar for active transfers
        if (transferSession.status == TransferStatus.IN_PROGRESS) {
            builder.setProgress(100, transferSession.getProgressPercentage(), false)
        }
        
        // Add action buttons
        if (transferSession.isPaused) {
            builder.addAction(
                R.drawable.ic_file,
                "Resume",
                createPendingIntent(ACTION_RESUME_TRANSFER)
            )
        } else if (transferSession.status == TransferStatus.IN_PROGRESS) {
            builder.addAction(
                R.drawable.ic_file,
                "Pause",
                createPendingIntent(ACTION_PAUSE_TRANSFER)
            )
        }
        
        builder.addAction(
            R.drawable.ic_file,
            "Cancel",
            createPendingIntent(ACTION_CANCEL_TRANSFER)
        )
        
        return builder.build()
    }
    
    private fun updateNotification() {
        currentTransferSession?.let { session ->
            notificationManager.notify(NOTIFICATION_ID, createNotification(session))
        }
    }
    
    private fun getNotificationText(transferSession: TransferSession): String {
        return when (transferSession.status) {
            TransferStatus.CONNECTING -> "Connecting to receiver..."
            TransferStatus.IN_PROGRESS -> "Transferring files... ${transferSession.getProgressPercentage()}%"
            TransferStatus.PAUSED -> "Transfer paused"
            TransferStatus.COMPLETED -> "Transfer completed"
            TransferStatus.FAILED -> "Transfer failed"
            TransferStatus.CANCELLED -> "Transfer cancelled"
            else -> "Preparing transfer..."
        }
    }
    
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TransferService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun observeWorkManagerUpdates() {
        serviceScope.launch {
            // Listen to transfer progress from WorkManager
            transferRepository.getTransferProgress().collect { progress ->
                currentTransferSession?.let { session ->
                    val updatedSession = session.copy(
                        transferredSize = (session.totalSize * progress / 100).toLong(),
                        progress = progress.toFloat()
                    )
                    currentTransferSession = updatedSession
                    _transferState.value = updatedSession
                    updateNotification()
                }
            }
        }
        
        serviceScope.launch {
            // Listen to transfer status from WorkManager
            transferRepository.getTransferStatus().collect { status ->
                currentTransferSession?.let { session ->
                    val updatedSession = session.copy(status = status)
                    currentTransferSession = updatedSession
                    _transferState.value = updatedSession
                    updateNotification()
                    
                    // Handle completion
                    if (status == TransferStatus.COMPLETED) {
                        serviceScope.launch {
                            kotlinx.coroutines.delay(3000)
                            stopForeground(true)
                            stopSelf()
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TransferService destroyed")
        serviceScope.cancel()
    }
}
