package com.slip.app

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.slip.app.databinding.ActivityMainBinding
import com.slip.app.ui.main.MainViewModel
import com.slip.app.ui.main.PermissionManager

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private lateinit var filePickerLauncher: ActivityResultLauncher<android.content.Intent>
    private lateinit var folderPickerLauncher: ActivityResultLauncher<android.content.Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupActivityResultLaunchers()
        setupClickListeners()
    }
    
    private fun setupActivityResultLaunchers() {
        // Permission launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            
            if (allGranted) {
                Log.d(TAG, "All storage permissions granted")
                // Show file picker after permissions are granted
                showSendOptionsDialog()
            } else {
                Log.w(TAG, "Some storage permissions denied")
                handlePermissionDenied()
            }
        }
        
        // File picker launcher for multiple files
        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val clipData = result.data?.clipData
                val dataUri = result.data?.data
                
                val uris = mutableListOf<Uri>()
                
                // Handle multiple files
                clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i).uri?.let { uri ->
                            uris.add(uri)
                            // Take persistent permission
                            viewModel.takePersistableUriPermission(contentResolver, uri)
                        }
                    }
                }
                
                // Handle single file
                dataUri?.let { uri ->
                    uris.add(uri)
                    // Take persistent permission
                    viewModel.takePersistableUriPermission(contentResolver, uri)
                }
                
                if (uris.isNotEmpty()) {
                    viewModel.onFilesSelected(uris)
                    Log.d(TAG, "Selected ${uris.size} files")
                    Toast.makeText(this, "Selected ${uris.size} files", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "No files selected")
                    Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "File picker cancelled or failed")
            }
        }
        
        // Folder picker launcher
        folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    // Take persistent permission for folder access
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    
                    viewModel.onFolderSelected(uri)
                    Log.d(TAG, "Selected folder: $uri")
                    Toast.makeText(this, "Folder selected for transfer", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "Folder picker cancelled or failed")
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSendFiles.setOnClickListener {
            checkStoragePermissionsAndProceed()
        }
        
        binding.btnReceiveFiles.setOnClickListener {
            viewModel.onReceiveFilesClicked()
        }
    }
    
    private fun checkStoragePermissionsAndProceed() {
        if (PermissionManager.hasStoragePermissions(this)) {
            Log.d(TAG, "Storage permissions already granted")
            showSendOptionsDialog()
        } else {
            Log.d(TAG, "Requesting storage permissions")
            requestStoragePermissions()
        }
    }
    
    private fun requestStoragePermissions() {
        val permissions = PermissionManager.getStoragePermissions()
        
        // Check if we should show rationale
        val shouldShowRationale = permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
        
        if (shouldShowRationale) {
            showPermissionRationaleDialog()
        } else {
            permissionLauncher.launch(permissions)
        }
    }
    
    private fun showPermissionRationaleDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage(PermissionManager.getPermissionRationaleMessage())
            .setPositiveButton("Grant Permission") { _, _ ->
                permissionLauncher.launch(PermissionManager.getStoragePermissions())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handlePermissionDenied() {
        val shouldShowRationale = PermissionManager.getStoragePermissions().any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
        
        if (shouldShowRationale) {
            // Permission was denied, show rationale
            Toast.makeText(this, PermissionManager.getPermissionDeniedMessage(), Toast.LENGTH_LONG).show()
        } else {
            // Permission was permanently denied
            showPermissionPermanentlyDeniedDialog()
        }
    }
    
    private fun showPermissionPermanentlyDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(PermissionManager.getPermissionPermanentlyDeniedMessage())
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openAppSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            Toast.makeText(this, "Failed to open settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showSendOptionsDialog() {
        val options = arrayOf("Send Files", "Send Folder")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose what to send")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Send Files
                        openFilePicker()
                    }
                    1 -> {
                        // Send Folder
                        openFolderPicker()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openFilePicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        
        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch file picker", e)
            Toast.makeText(this, "Failed to open file picker", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openFolderPicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                     android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        
        try {
            folderPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch folder picker", e)
            Toast.makeText(this, "Failed to open folder picker", Toast.LENGTH_SHORT).show()
        }
    }
}
