package com.slip.app.ui.main

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
        const val REQUEST_CODE_SELECT_FILES = 1001
        const val REQUEST_CODE_SELECT_FOLDER = 1002
    }
    
    // Selected URIs for transfer
    private val selectedUris = mutableListOf<Uri>()
    
    fun onSendFilesClicked() {
        // File picker will be launched from MainActivity
        Log.d(TAG, "Send files clicked")
    }
    
    fun onReceiveFilesClicked() {
        // TODO: Start receiving files
        Log.d(TAG, "Receive files clicked - not implemented yet")
    }
    
    private fun openFilePicker(filePickerLauncher: ActivityResultLauncher<Bundle>) {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        
        val bundle = Bundle().apply {
            putParcelable(android.app.Activity.RESULT_OK, intent)
        }
        
        try {
            filePickerLauncher.launch(bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch file picker", e)
        }
    }
    
    fun openFolderPicker(folderPickerLauncher: ActivityResultLauncher<android.content.Intent>) {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                     android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        
        try {
            folderPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch folder picker", e)
        }
    }
    
    fun onFilesSelected(uris: List<Uri>) {
        selectedUris.clear()
        selectedUris.addAll(uris)
        Log.d(TAG, "Selected ${uris.size} files for transfer")
        
        // TODO: Navigate to transfer preview screen
    }
    
    fun onFolderSelected(uri: Uri) {
        selectedUris.clear()
        selectedUris.add(uri)
        Log.d(TAG, "Selected folder for transfer: $uri")
        
        // TODO: Navigate to transfer preview screen
    }
    
    fun getSelectedUris(): List<Uri> = selectedUris.toList()
    
    /**
     * Take persistent URI permissions for reliable file access
     */
    fun takePersistableUriPermission(
        contentResolver: android.content.ContentResolver,
        uri: Uri,
        flags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    ) {
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Taken persistent permission for URI: $uri")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to take persistent permission for URI: $uri", e)
        }
    }
    
    /**
     * Check if we have persistent permission for a URI
     */
    fun hasPersistableUriPermission(
        contentResolver: android.content.ContentResolver,
        uri: Uri
    ): Boolean {
        return try {
            contentResolver.persistedUriPermissions.any { 
                it.uri == uri && it.isReadPermission 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking persistent permission", e)
            false
        }
    }
}
