package com.slip.app.service.network

import android.content.Context
import android.util.Log
import com.slip.app.domain.model.FileMetadata
import com.slip.app.domain.model.FileChunk
import com.slip.app.domain.model.ChunkStatus
import com.slip.app.domain.model.ChunkManager
import com.slip.app.data.repository.ChunkRepository
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
    private lateinit var chunkRepository: ChunkRepository
    
    // Active client connections
    private val activeConnections = ConcurrentHashMap<String, Socket>()
    
    // Transfer state
    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress.asStateFlow()
    
    private val _transferStatus = MutableStateFlow<TransferStatus>(TransferStatus.IDLE)
    val transferStatus: StateFlow<TransferStatus> = _transferStatus.asStateFlow()
    
    private val _connectedClients = MutableStateFlow<List<String>>(emptyList())
    val connectedClients: StateFlow<List<String>> = _connectedClients.asStateFlow()
    
    init {
        chunkRepository = ChunkRepository.getInstance(context)
    }
    
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
     * Receive file data from client using chunks
     */
    private suspend fun receiveFile(dataInputStream: DataInputStream, metadata: TransferMetadata) {
        _transferStatus.value = TransferStatus.RECEIVING
        _transferProgress.value = 0f
        
        val fileId = "file_${System.currentTimeMillis()}"
        val chunkSize = ChunkManager.calculateOptimalChunkSize(metadata.fileSize)
        val chunks = ChunkManager.createChunks(fileId, metadata.fileSize, chunkSize)
        val chunkMetadata = ChunkManager.createMetadata(fileId, metadata.fileName, metadata.fileSize, chunkSize)
        
        // Save chunk metadata
        chunkRepository.saveChunkMetadata(chunkMetadata)
        
        Log.d(TAG, "Receiving file: ${metadata.fileName} (${metadata.fileSize} bytes) in ${chunks.size} chunks")
        
        try {
            var totalBytesReceived = 0L
            val buffer = ByteArray(CHUNK_SIZE)
            
            for (chunkIndex in 0 until chunks.size) {
                val chunk = chunks[chunkIndex]
                
                // Update chunk status to in progress
                chunkRepository.updateChunkStatus(fileId, chunkIndex, ChunkStatus.IN_PROGRESS)
                
                // Receive chunk data
                val chunkData = receiveChunk(dataInputStream, chunk.size)
                if (chunkData != null) {
                    // Save chunk data
                    val success = chunkRepository.saveChunkData(fileId, chunkIndex, chunkData)
                    if (success) {
                        chunkRepository.updateChunkStatus(fileId, chunkIndex, ChunkStatus.COMPLETED)
                        totalBytesReceived += chunkData.size
                        
                        // Update overall progress
                        val progress = (totalBytesReceived.toFloat() / metadata.fileSize) * 100f
                        _transferProgress.value = progress
                        
                        // Send progress acknowledgment
                        dataOutputStream.writeUTF("CHUNK:$chunkIndex:COMPLETED")
                        dataOutputStream.flush()
                    } else {
                        chunkRepository.updateChunkStatus(fileId, chunkIndex, ChunkStatus.FAILED, "Failed to save chunk data")
                    }
                } else {
                    chunkRepository.updateChunkStatus(fileId, chunkIndex, ChunkStatus.FAILED, "Failed to receive chunk data")
                }
            }
            
            // Check if all chunks were received successfully
            val completedChunks = chunkRepository.getCompletedChunks(fileId)
            if (completedChunks.size == chunks.size) {
                _transferStatus.value = TransferStatus.COMPLETED
                _transferProgress.value = 100f
                
                // Verify file checksum if provided
                metadata.checksum?.let { expectedChecksum ->
                    val actualChecksum = chunkRepository.calculateFileChecksum(fileId)
                    if (actualChecksum != expectedChecksum) {
                        Log.w(TAG, "Checksum mismatch: expected $expectedChecksum, got $actualChecksum")
                    } else {
                        Log.d(TAG, "Checksum verification successful")
                    }
                }
                
                // Send completion acknowledgment
                dataOutputStream.writeUTF("COMPLETED")
                dataOutputStream.flush()
                
                Log.d(TAG, "File received successfully: ${metadata.fileName}")
            } else {
                _transferStatus.value = TransferStatus.FAILED
                Log.e(TAG, "File transfer incomplete: ${completedChunks.size}/${chunks.size} chunks")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving file", e)
            _transferStatus.value = TransferStatus.ERROR
        }
    }
    
    /**
     * Receive a single chunk
     */
    private suspend fun receiveChunk(dataInputStream: DataInputStream, chunkSize: Int): ByteArray? {
        return try {
            val chunkData = ByteArray(chunkSize)
            var totalRead = 0
            
            while (totalRead < chunkSize) {
                val bytesRead = dataInputStream.read(chunkData, totalRead, chunkSize - totalRead)
                if (bytesRead == -1) break
                totalRead += bytesRead
            }
            
            if (totalRead == chunkSize) {
                chunkData
            } else {
                // Return only the data that was actually read
                chunkData.copyOf(totalRead)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving chunk", e)
            null
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
    private lateinit var chunkRepository: ChunkRepository
    
    // Transfer state
    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress.asStateFlow()
    
    private val _transferStatus = MutableStateFlow<TransferStatus>(TransferStatus.IDLE)
    val transferStatus: StateFlow<TransferStatus> = _transferStatus.asStateFlow()
    
    init {
        chunkRepository = ChunkRepository.getInstance(context)
    }
    
    /**
     * Send file to server using chunks
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
                
                // Create chunks for the file
                val fileId = "send_${System.currentTimeMillis()}"
                val chunkSize = ChunkManager.calculateOptimalChunkSize(fileMetadata.size)
                val chunks = ChunkManager.createChunks(fileId, fileMetadata.size, chunkSize)
                val chunkMetadata = ChunkManager.createMetadata(fileId, fileMetadata.name, fileMetadata.size, chunkSize)
                
                // Save chunk metadata
                chunkRepository.saveChunkMetadata(chunkMetadata)
                
                // Send file metadata
                sendFileMetadata(dataOutputStream, fileMetadata, chunkMetadata)
                
                // Wait for ready acknowledgment
                val response = dataInputStream.readUTF()
                if (response != "READY") {
                    throw Exception("Server not ready: $response")
                }
                
                // Send file data in chunks
                _transferStatus.value = TransferStatus.SENDING
                sendFileChunks(dataOutputStream, dataInputStream, chunks, fileMetadata, progressCallback)
                
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
        fileMetadata: FileMetadata,
        chunkMetadata: com.slip.app.domain.model.ChunkMetadata
    ) {
        dataOutputStream.writeUTF(fileMetadata.name)
        dataOutputStream.writeLong(fileMetadata.size)
        dataOutputStream.writeUTF(fileMetadata.mimeType)
        dataOutputStream.writeUTF(fileMetadata.checksum ?: "")
        dataOutputStream.writeInt(chunkMetadata.totalChunks)
        dataOutputStream.writeInt(chunkMetadata.chunkSize)
        dataOutputStream.writeUTF(chunkMetadata.fileId)
        dataOutputStream.flush()
    }
    
    /**
     * Send file data in chunks
     */
    private suspend fun sendFileChunks(
        dataOutputStream: DataOutputStream,
        dataInputStream: DataInputStream,
        chunks: List<FileChunk>,
        fileMetadata: FileMetadata,
        progressCallback: (Float) -> Unit
    ) {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(fileMetadata.uri)
        
        inputStream?.use { input ->
            var totalBytesSent = 0L
            
            for ((index, chunk) in chunks.withIndex()) {
                // Update chunk status
                chunkRepository.updateChunkStatus(chunk.fileId, index, ChunkStatus.IN_PROGRESS)
                
                // Seek to chunk position
                input.skip(chunk.startPosition - totalBytesSent)
                
                // Read and send chunk data
                val chunkData = ByteArray(chunk.size.toInt())
                var bytesRead = 0
                
                while (bytesRead < chunk.size && bytesRead < chunkData.size) {
                    val read = input.read(chunkData, bytesRead, chunkData.size - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }
                
                // Send chunk
                dataOutputStream.writeUTF("CHUNK:$index:${bytesRead}")
                dataOutputStream.write(chunkData, 0, bytesRead)
                dataOutputStream.flush()
                
                // Wait for acknowledgment
                val ack = dataInputStream.readUTF()
                if (ack.startsWith("CHUNK:$index:COMPLETED")) {
                    chunkRepository.updateChunkStatus(chunk.fileId, index, ChunkStatus.COMPLETED)
                    totalBytesSent += bytesRead
                    
                    // Update progress
                    val progress = (totalBytesSent.toFloat() / chunks.sumOf { it.size }) * 100f
                    _transferProgress.value = progress
                    progressCallback(progress)
                } else {
                    chunkRepository.updateChunkStatus(chunk.fileId, index, ChunkStatus.FAILED, "Server acknowledgment failed")
                    throw Exception("Chunk transfer failed: $ack")
                }
            }
        }
        
        // Wait for completion acknowledgment
        val completionAck = dataInputStream.readUTF()
        if (completionAck == "COMPLETED") {
            _transferStatus.value = TransferStatus.COMPLETED
            _transferProgress.value = 100f
            Log.d(TAG, "File sent successfully")
        } else {
            throw Exception("Transfer not completed: $completionAck")
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

