package com.slip.app.ui.preview

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.R
import com.slip.app.databinding.FragmentTransferPreviewBinding
import com.slip.app.domain.model.FileMetadata
import com.slip.app.domain.model.FolderNode
import com.slip.app.service.FileScannerService
import com.slip.app.service.ScanResult
import com.slip.app.service.ScanStatus
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TransferPreviewFragment : Fragment() {
    
    private var _binding: FragmentTransferPreviewBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var fileScannerService: FileScannerService
    private lateinit var previewAdapter: TransferPreviewAdapter
    
    private var selectedUris: List<android.net.Uri> = emptyList()
    private var scanResult: ScanResult? = null
    
    companion object {
        private const val TAG = "TransferPreviewFragment"
        private const val ARG_URIS = "selected_uris"
        
        fun newInstance(uris: List<android.net.Uri>): TransferPreviewFragment {
            return TransferPreviewFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_URIS, ArrayList(uris))
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileScannerService = FileScannerService()
        
        selectedUris = arguments?.getParcelableArrayList<android.net.Uri>(ARG_URIS) ?: emptyList()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        startScanning()
    }
    
    private fun setupRecyclerView() {
        previewAdapter = TransferPreviewAdapter()
        binding.recyclerViewPreview.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = previewAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonStartTransfer.setOnClickListener {
            scanResult?.let { result ->
                if (result.isComplete()) {
                    startTransfer(result)
                }
            }
        }
        
        binding.buttonCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    
    private fun startScanning() {
        binding.progressBarScanning.visibility = View.VISIBLE
        binding.textViewScanningStatus.text = "Scanning files..."
        binding.buttonStartTransfer.isEnabled = false
        
        viewLifecycleOwner.lifecycleScope.launch {
            fileScannerService.scanUris(requireContext().contentResolver, selectedUris)
                .collect { result ->
                    updateUI(result)
                }
        }
    }
    
    private fun updateUI(result: ScanResult) {
        scanResult = result
        
        when (result.status) {
            ScanStatus.SCANNING -> {
                binding.progressBarScanning.visibility = View.VISIBLE
                binding.textViewScanningStatus.text = "Scanning... ${result.processedCount}/${result.totalCount}"
                binding.progressBarScanning.progress = result.progress.toInt()
            }
            
            ScanStatus.COMPLETED -> {
                binding.progressBarScanning.visibility = View.GONE
                binding.textViewScanningStatus.text = "Scanning complete"
                binding.buttonStartTransfer.isEnabled = true
                
                // Update summary
                binding.textViewSummary.text = buildSummaryText(result)
                
                // Update preview list
                previewAdapter.updateData(result.files, result.folders)
            }
            
            ScanStatus.FAILED -> {
                binding.progressBarScanning.visibility = View.GONE
                binding.textViewScanningStatus.text = "Scanning failed"
                binding.textViewSummary.text = result.errorMessage ?: "Unknown error"
            }
            
            ScanStatus.PENDING -> {
                // Initial state
            }
        }
    }
    
    private fun buildSummaryText(result: ScanResult): String {
        val fileCount = result.getTotalFileCount()
        val folderCount = result.getTotalFolderCount()
        val totalSize = result.getFormattedTotalSize()
        
        return when {
            fileCount > 0 && folderCount > 0 -> "$fileCount files, $folderCount folders ($totalSize)"
            fileCount > 0 -> "$fileCount files ($totalSize)"
            folderCount > 0 -> "$folderCount folders ($totalSize)"
            else -> "No items selected"
        }
    }
    
    private fun startTransfer(result: ScanResult) {
        Log.d(TAG, "Starting transfer with ${result.files.size} files")
        // TODO: Navigate to transfer screen or start transfer process
        // This will be implemented in later phases
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
