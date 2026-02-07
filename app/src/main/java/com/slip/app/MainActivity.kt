package com.slip.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.slip.app.databinding.ActivityMainBinding
import com.slip.app.ui.main.MainViewModel

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private lateinit var filePickerLauncher: ActivityResultLauncher<Bundle>
    private lateinit var folderPickerLauncher: ActivityResultLauncher<android.content.Intent>
    
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
                } else {
                    Log.w(TAG, "No files selected")
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
                }
            } else {
                Log.w(TAG, "Folder picker cancelled or failed")
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSendFiles.setOnClickListener {
            showSendOptionsDialog()
        }
        
        binding.btnReceiveFiles.setOnClickListener {
            viewModel.onReceiveFilesClicked()
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
        }
    }
}
