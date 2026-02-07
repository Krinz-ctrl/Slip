package com.slip.app.service

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.slip.app.domain.model.FileMetadata
import com.slip.app.domain.model.FolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Service for scanning files and folders to build metadata
 */
class FileScannerService {
    
    companion object {
        private const val TAG = "FileScannerService"
    }
    
    /**
     * Scan selected URIs and build metadata
     */
    fun scanUris(contentResolver: ContentResolver, uris: List<Uri>): Flow<ScanResult> = flow {
        emit(ScanResult(status = ScanStatus.SCANNING, progress = 0f))
        
        val allFiles = mutableListOf<FileMetadata>()
        val folders = mutableListOf<FolderNode>()
        var totalSize = 0L
        var processedCount = 0
        
        try {
            for ((index, uri) in uris.withIndex()) {
                Log.d(TAG, "Scanning URI: $uri")
                
                // Check if it's a folder or file
                val mimeType = contentResolver.getType(uri)
                
                if (mimeType == "vnd.android.document/directory" || 
                    mimeType == "resource/folder") {
                    // It's a folder
                    val folderNode = FolderNode.fromUri(contentResolver, uri)
                    folderNode?.let { folder ->
                        folders.add(folder)
                        allFiles.addAll(folder.getAllFiles())
                        totalSize += folder.totalSize
                    }
                } else {
                    // It's a file
                    val fileMetadata = FileMetadata.fromUri(contentResolver, uri)
                    fileMetadata?.let { file ->
                        allFiles.add(file)
                        totalSize += file.size
                    }
                }
                
                processedCount++
                val progress = (processedCount.toFloat() / uris.size) * 100f
                emit(ScanResult(
                    status = ScanStatus.SCANNING,
                    progress = progress,
                    files = allFiles.toList(),
                    folders = folders.toList(),
                    totalSize = totalSize,
                    processedCount = processedCount,
                    totalCount = uris.size
                ))
            }
            
            emit(ScanResult(
                status = ScanStatus.COMPLETED,
                progress = 100f,
                files = allFiles,
                folders = folders,
                totalSize = totalSize,
                processedCount = uris.size,
                totalCount = uris.size
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning URIs", e)
            emit(ScanResult(
                status = ScanStatus.FAILED,
                progress = 0f,
                errorMessage = e.message ?: "Unknown error occurred"
            ))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Scan a single folder URI
     */
    fun scanFolder(contentResolver: ContentResolver, folderUri: Uri): Flow<ScanResult> = flow {
        emit(ScanResult(status = ScanStatus.SCANNING, progress = 0f))
        
        try {
            val folderNode = FolderNode.fromUri(contentResolver, folderUri)
            
            if (folderNode != null) {
                emit(ScanResult(
                    status = ScanStatus.COMPLETED,
                    progress = 100f,
                    files = folderNode.getAllFiles(),
                    folders = listOf(folderNode),
                    totalSize = folderNode.totalSize,
                    processedCount = 1,
                    totalCount = 1
                ))
            } else {
                emit(ScanResult(
                    status = ScanStatus.FAILED,
                    progress = 0f,
                    errorMessage = "Failed to scan folder"
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder", e)
            emit(ScanResult(
                status = ScanStatus.FAILED,
                progress = 0f,
                errorMessage = e.message ?: "Unknown error occurred"
            ))
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Result of scanning operation
 */
data class ScanResult(
    val status: ScanStatus,
    val progress: Float,
    val files: List<FileMetadata> = emptyList(),
    val folders: List<FolderNode> = emptyList(),
    val totalSize: Long = 0L,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null
) {
    /**
     * Get total file count
     */
    fun getTotalFileCount(): Int {
        return files.size + folders.sumOf { it.totalFiles }
    }
    
    /**
     * Get total folder count
     */
    fun getTotalFolderCount(): Int {
        return folders.size
    }
    
    /**
     * Get formatted total size
     */
    fun getFormattedTotalSize(): String {
        return FileMetadata.formatFileSize(totalSize)
    }
    
    /**
     * Check if scan is complete
     */
    fun isComplete(): Boolean {
        return status == ScanStatus.COMPLETED
    }
    
    /**
     * Check if scan failed
     */
    fun hasError(): Boolean {
        return status == ScanStatus.FAILED
    }
}

/**
 * Scan status
 */
enum class ScanStatus {
    PENDING,
    SCANNING,
    COMPLETED,
    FAILED
}
