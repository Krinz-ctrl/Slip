package com.slip.app.service.network

import android.content.Context
import android.util.Log
import com.slip.app.domain.model.FileMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Socket server for receiving files
 */
class SocketTransferServer(private val context: Context) {
    
    companion object {
        private const val TAG = "SocketTransferServer"
        private const val DEFAULT_PORT = 4242
        private const val CHUNK_SIZE = 8192 // 8KB chunks
        private const val TIMEOUT_MS = 30000 // 30 seconds timeout
    }
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Active client connections
    private val activeConnections = ConcurrentHashMap<String, Socket>()
    
    // Transfer state
    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress.asStateFlow()
    
    private val _transferStatus = MutableStateFlow<TransferStatus>(TransferStatus.IDLE)
    val transferStatus: StateFlow<TransferStatus> = _transferStatus.asStateFlow()
    
    private val _connectedClients = MutableStateFlow<List<String>>(emptyList())
    val connectedClients: StateFlow<List<String>> = _connectedClients.asStateFlow()
    
    /**
     * Start the socket server
     */
    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        return try {
            if (isRunning) {
                Log.w(TAG, "Server is already running")
                return true
            }
            
            serverSocket = ServerSocket(port)
            isRunning = true
            _transferStatus.value = TransferStatus.LISTENING
            
            Log.d(TAG, "Socket server started on port $port")
            
            // Start accepting connections
            serverScope.launch {
                acceptConnections()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            _transferStatus.value = TransferStatus.ERROR
            false
        }
    }
    
    /**
     * Stop the socket server
     */
    fun stopServer() {
        Log.d(TAG, "Stopping socket server")
        
        isRunning = false
        _transferStatus.value = TransferStatus.IDLE
        
        // Close all client connections
        activeConnections.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
        activeConnections.clear()
        _connectedClients.value = emptyList()
        
        // Close server socket
        serverSocket?.close()
        serverSocket = null
        
        serverScope.cancel()
    }
    
    /**
     * Accept incoming connections
     */
    private suspend fun acceptConnections() {
        while (isRunning) {
            try {
                val clientSocket = serverSocket?.accept()
                if (clientSocket != null) {
                    handleClientConnection(clientSocket)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Error accepting connection", e)
                }
            }
        }
    }
    
    /**
     * Handle individual client connection
     */
    private suspend fun handleClientConnection(socket: Socket) {
        val clientId = "${socket.inetAddress.hostAddress}:${socket.port}"
        Log.d(TAG, "Client connected: $clientId")
        
        activeConnections[clientId] = socket
        updateConnectedClients()
        
        try {
            socket.soTimeout = TIMEOUT_MS
            
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            val dataInputStream = DataInputStream(inputStream)
            val dataOutputStream = DataOutputStream(outputStream)
            
            // Read transfer metadata
            val metadata = readTransferMetadata(dataInputStream)
            
            // Send acknowledgment
            dataOutputStream.writeUTF("READY")
            dataOutputStream.flush()
            
            // Receive file data
            receiveFile(dataInputStream, metadata)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client $clientId", e)
            _transferStatus.value = TransferStatus.ERROR
        } finally {
            // Clean up connection
            activeConnections.remove(clientId)
            updateConnectedClients()
            socket.close()
            Log.d(TAG, "Client disconnected: $clientId")
        }
    }
    
    /**
     * Read transfer metadata from client
     */
    private suspend fun readTransferMetadata(dataInputStream: DataInputStream): TransferMetadata {
        val fileName = dataInputStream.readUTF()
        val fileSize = dataInputStream.readLong()
        val mimeType = dataInputStream.readUTF()
        val checksum = dataInputStream.readUTF()
        
        Log.d(TAG, "Receiving file: $fileName ($fileSize bytes)")
        
        return TransferMetadata(
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            checksum = checksum
        )
    }
    
    /**
     * Receive file data from client
     */
    private suspend fun receiveFile(dataInputStream: DataInputStream, metadata: TransferMetadata) {
        _transferStatus.value = TransferStatus.RECEIVING
        _transferProgress.value = 0f
        
        val outputDir = File(context.getExternalFilesDir(null), "received")
        outputDir.mkdirs()
        
        val outputFile = File(outputDir, metadata.fileName)
        val fileOutputStream = FileOutputStream(outputFile)
        
        var totalBytesRead = 0L
        val buffer = ByteArray(CHUNK_SIZE)
        
        try {
            while (totalBytesRead < metadata.fileSize && isRunning) {
                val bytesRead = dataInputStream.read(buffer)
                if (bytesRead == -1) break
                
                fileOutputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // Update progress
                val progress = (totalBytesRead.toFloat() / metadata.fileSize) * 100f
                _transferProgress.value = progress
                
                // Send progress acknowledgment
                dataOutputStream.writeUTF("PROGRESS:$progress")
                dataOutputStream.flush()
            }
            
            if (totalBytesRead == metadata.fileSize) {
                _transferStatus.value = TransferStatus.COMPLETED
                _transferProgress.value = 100f
                Log.d(TAG, "File received successfully: ${metadata.fileName}")
                
                // Send completion acknowledgment
                dataOutputStream.writeUTF("COMPLETED")
                dataOutputStream.flush()
            } else {
                _transferStatus.value = TransferStatus.ERROR
                Log.e(TAG, "File transfer incomplete: $totalBytesRead/${metadata.fileSize}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving file", e)
            _transferStatus.value = TransferStatus.ERROR
        } finally {
            fileOutputStream.close()
        }
    }
    
    private fun updateConnectedClients() {
        _connectedClients.value = activeConnections.keys.toList()
    }
    
    /**
     * Get server status
     */
    fun getServerStatus(): ServerStatus {
        return ServerStatus(
            isRunning = isRunning,
            port = serverSocket?.localPort ?: 0,
            connectedClients = activeConnections.size,
            status = _transferStatus.value,
            progress = _transferProgress.value
        )
    }
}

/**
 * Socket client for sending files
 */
class SocketTransferClient(private val context: Context) {
    
    companion object {
        private const val TAG = "SocketTransferClient"
        private const val CHUNK_SIZE = 8192 // 8KB chunks
        private const val TIMEOUT_MS = 30000 // 30 seconds timeout
    }
    
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Transfer state
    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress.asStateFlow()
    
    private val _transferStatus = MutableStateFlow<TransferStatus>(TransferStatus.IDLE)
    val transferStatus: StateFlow<TransferStatus> = _transferStatus.asStateFlow()
    
    /**
     * Send file to server
     */
    suspend fun sendFile(
        host: String,
        port: Int,
        fileMetadata: FileMetadata,
        progressCallback: (Float) -> Unit = {}
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var socket: Socket? = null
            
            try {
                _transferStatus.value = TransferStatus.CONNECTING
                
                // Connect to server
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                
                Log.d(TAG, "Connected to server: $host:$port")
                
                val inputStream = socket.getInputStream()
                val outputStream = socket.getOutputStream()
                val dataInputStream = DataInputStream(inputStream)
                val dataOutputStream = DataOutputStream(outputStream)
                
                // Send file metadata
                sendFileMetadata(dataOutputStream, fileMetadata)
                
                // Wait for ready acknowledgment
                val response = dataInputStream.readUTF()
                if (response != "READY") {
                    throw Exception("Server not ready: $response")
                }
                
                // Send file data
                _transferStatus.value = TransferStatus.SENDING
                sendFileData(dataOutputStream, dataInputStream, fileMetadata, progressCallback)
                
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file", e)
                _transferStatus.value = TransferStatus.ERROR
                false
            } finally {
                socket?.close()
            }
        }
    }
    
    /**
     * Send file metadata to server
     */
    private suspend fun sendFileMetadata(
        dataOutputStream: DataOutputStream,
        fileMetadata: FileMetadata
    ) {
        dataOutputStream.writeUTF(fileMetadata.name)
        dataOutputStream.writeLong(fileMetadata.size)
        dataOutputStream.writeUTF(fileMetadata.mimeType)
        dataOutputStream.writeUTF(fileMetadata.checksum ?: "")
        dataOutputStream.flush()
    }
    
    /**
     * Send file data to server
     */
    private suspend fun sendFileData(
        dataOutputStream: DataOutputStream,
        dataInputStream: DataInputStream,
        fileMetadata: FileMetadata,
        progressCallback: (Float) -> Unit
    ) {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(fileMetadata.uri)
        
        inputStream?.use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            var totalBytesSent = 0L
            
            while (totalBytesSent < fileMetadata.size) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                
                dataOutputStream.write(buffer, 0, bytesRead)
                totalBytesSent += bytesRead
                
                // Update progress
                val progress = (totalBytesSent.toFloat() / fileMetadata.size) * 100f
                _transferProgress.value = progress
                progressCallback(progress)
                
                // Check for progress acknowledgment
                if (dataInputStream.available() > 0) {
                    val ack = dataInputStream.readUTF()
                    if (ack.startsWith("PROGRESS:")) {
                        // Server acknowledged progress
                    }
                }
            }
            
            // Wait for completion acknowledgment
            val completionAck = dataInputStream.readUTF()
            if (completionAck == "COMPLETED") {
                _transferStatus.value = TransferStatus.COMPLETED
                _transferProgress.value = 100f
                Log.d(TAG, "File sent successfully: ${fileMetadata.name}")
            } else {
                throw Exception("Transfer not completed: $completionAck")
            }
        }
    }
}

/**
 * Transfer metadata
 */
data class TransferMetadata(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val checksum: String
)

/**
 * Transfer status enum
 */
enum class TransferStatus {
    IDLE,
    LISTENING,
    CONNECTING,
    SENDING,
    RECEIVING,
    COMPLETED,
    ERROR
}

/**
 * Server status
 */
data class ServerStatus(
    val isRunning: Boolean,
    val port: Int,
    val connectedClients: Int,
    val status: TransferStatus,
    val progress: Float
)

