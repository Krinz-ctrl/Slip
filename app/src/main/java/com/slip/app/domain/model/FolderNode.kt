package com.slip.app.domain.model

import android.net.Uri
import android.provider.DocumentsContract
import android.content.ContentResolver
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a folder node in the file hierarchy
 */
data class FolderNode(
    val id: String,
    val name: String,
    val uri: Uri,
    val relativePath: String = "",
    val parentId: String? = null,
    val children: List<FolderNode> = emptyList(),
    val files: List<FileMetadata> = emptyList(),
    val totalSize: Long = 0L,
    val totalFiles: Int = 0,
    val totalFolders: Int = 0,
    val isExpanded: Boolean = false,
    val scanStatus: ScanStatus = ScanStatus.PENDING,
    val scanProgress: Float = 0f,
    val errorMessage: String? = null
) {
    /**
     * Get all files recursively (including subfolders)
     */
    fun getAllFiles(): List<FileMetadata> {
        val allFiles = files.toMutableList()
        children.forEach { child ->
            allFiles.addAll(child.getAllFiles())
        }
        return allFiles
    }
    
    /**
     * Get total file count recursively
     */
    fun getTotalFileCountRecursive(): Int {
        var count = files.size
        children.forEach { child ->
            count += child.getTotalFileCountRecursive()
        }
        return count
    }
    
    /**
     * Get total folder count recursively
     */
    fun getTotalFolderCountRecursive(): Int {
        var count = 1 // Count this folder
        children.forEach { child ->
            count += child.getTotalFolderCountRecursive()
        }
        return count
    }
    
    /**
     * Get total size recursively
     */
    fun getTotalSizeRecursive(): Long {
        var size = files.sumOf { it.size }
        children.forEach { child ->
            size += child.getTotalSizeRecursive()
        }
        return size
    }
    
    /**
     * Find child folder by ID
     */
    fun findFolderById(folderId: String): FolderNode? {
        if (id == folderId) return this
        children.forEach { child ->
            child.findFolderById(folderId)?.let { return it }
        }
        return null
    }
    
    /**
     * Find file by ID
     */
    fun findFileById(fileId: String): FileMetadata? {
        files.find { it.id == fileId }?.let { return it }
        children.forEach { child ->
            child.findFileById(fileId)?.let { return it }
        }
        return null
    }
    
    /**
     * Get formatted total size
     */
    fun getFormattedTotalSize(): String {
        return FileMetadata.formatFileSize(totalSize)
    }
    
    companion object {
        private const val TAG = "FolderNode"
        
        /**
         * Create FolderNode from URI and scan its contents
         */
        suspend fun fromUri(
            contentResolver: ContentResolver,
            uri: Uri,
            relativePath: String = ""
        ): FolderNode? {
            return withContext(Dispatchers.IO) {
                try {
                    // Get folder metadata
                    val cursor = contentResolver.query(
                        uri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        ),
                        null,
                        null,
                        null
                    )
                    
                    val folderName = cursor?.use {
                        if (it.moveToFirst()) {
                            it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                        } else {
                            uri.lastPathSegment ?: "Unknown Folder"
                        }
                    } ?: uri.lastPathSegment ?: "Unknown Folder"
                    
                    // Scan folder contents
                    val (children, files) = scanFolderContents(contentResolver, uri, relativePath)
                    val totalSize = files.sumOf { it.size } + children.sumOf { it.totalSize }
                    val totalFiles = files.size + children.sumOf { it.totalFiles }
                    val totalFolders = 1 + children.sumOf { it.totalFolders }
                    
                    FolderNode(
                        id = uri.toString(),
                        name = folderName,
                        uri = uri,
                        relativePath = relativePath,
                        children = children,
                        files = files,
                        totalSize = totalSize,
                        totalFiles = totalFiles,
                        totalFolders = totalFolders,
                        scanStatus = ScanStatus.COMPLETED
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create FolderNode from URI: $uri", e)
                    null
                }
            }
        }
        
        /**
         * Scan folder contents recursively
         */
        private suspend fun scanFolderContents(
            contentResolver: ContentResolver,
            folderUri: Uri,
            basePath: String
        ): Pair<List<FolderNode>, List<FileMetadata>> {
            return withContext(Dispatchers.IO) {
                val children = mutableListOf<FolderNode>()
                val files = mutableListOf<FileMetadata>()
                
                try {
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))
                    
                    val cursor = contentResolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        ),
                        null,
                        null,
                        null
                    )
                    
                    cursor?.use {
                        while (it.moveToNext()) {
                            val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                            val name = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                            val size = it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                            val mimeType = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                            val lastModified = it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                            
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                            val relativePath = if (basePath.isEmpty()) name else "$basePath/$name"
                            
                            if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                                // It's a folder
                                FolderNode.fromUri(contentResolver, childUri, relativePath)?.let { folder ->
                                    children.add(folder)
                                }
                            } else {
                                // It's a file
                                val fileMetadata = FileMetadata(
                                    id = childUri.toString(),
                                    name = name,
                                    size = size,
                                    mimeType = mimeType ?: "application/octet-stream",
                                    uri = childUri,
                                    lastModified = lastModified,
                                    relativePath = relativePath
                                )
                                files.add(fileMetadata)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to scan folder contents: $folderUri", e)
                }
                
                Pair(children, files)
            }
        }
    }
}

/**
 * Status of folder scanning operation
 */
enum class ScanStatus {
    PENDING,        // Waiting to scan
    SCANNING,       // Currently scanning
    COMPLETED,      // Scan completed
    FAILED          // Scan failed
}
