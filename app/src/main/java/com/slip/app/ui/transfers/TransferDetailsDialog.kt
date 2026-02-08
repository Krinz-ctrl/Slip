package com.slip.app.ui.transfers

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.slip.app.databinding.DialogTransferDetailsBinding
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.domain.model.TransferType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog for showing detailed transfer information
 */
class TransferDetailsDialog : DialogFragment() {
    
    private var _binding: DialogTransferDetailsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var transfer: TransferSession
    
    companion object {
        private const val ARG_TRANSFER = "transfer"
        
        fun newInstance(transfer: TransferSession): TransferDetailsDialog {
            return TransferDetailsDialog().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_TRANSFER, transfer)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transfer = requireArguments().getSerializable(ARG_TRANSFER) as TransferSession
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setTitle("Transfer Details")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTransferDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupTransferInfo()
        setupProgressInfo()
        setupTimingInfo()
        setupErrorInfo()
    }
    
    private fun setupTransferInfo() {
        // Transfer type and name
        binding.textViewTransferType.text = getTransferTypeText(transfer.type)
        binding.textViewTransferName.text = getTransferName(transfer)
        
        // File count and size
        binding.textViewFileCount.text = "${transfer.files.size} files"
        binding.textViewTotalSize.text = formatFileSize(transfer.totalSize)
        binding.textViewTransferredSize.text = formatFileSize(transfer.transferredSize)
        
        // Status
        updateStatusDisplay()
    }
    
    private fun setupProgressInfo() {
        binding.progressBarProgress.progress = (transfer.progress * 100).toInt()
        binding.textViewProgressPercentage.text = "${(transfer.progress * 100).toInt()}%"
        
        // Speed and ETA (would need to be calculated from real data)
        if (transfer.status == TransferStatus.IN_PROGRESS) {
            binding.textViewSpeed.text = "Calculating..."
            binding.textViewEta.text = "Calculating..."
        } else {
            binding.textViewSpeed.text = "N/A"
            binding.textViewEta.text = "N/A"
        }
    }
    
    private fun setupTimingInfo() {
        binding.textViewStartTime.text = formatTimestamp(transfer.startTime)
        
        transfer.endTime?.let { endTime ->
            binding.textViewEndTime.text = formatTimestamp(endTime)
            
            val duration = endTime - transfer.startTime
            binding.textViewDuration.text = formatDuration(duration)
        } ?: run {
            binding.textViewEndTime.text = "Not finished"
            binding.textViewDuration.text = formatDuration(System.currentTimeMillis() - transfer.startTime)
        }
    }
    
    private fun setupErrorInfo() {
        if (transfer.status == TransferStatus.FAILED || transfer.status == TransferStatus.ERROR) {
            binding.layoutError.visibility = View.VISIBLE
            binding.textViewErrorMessage.text = transfer.errorMessage ?: "Unknown error"
        } else {
            binding.layoutError.visibility = View.GONE
        }
    }
    
    private fun updateStatusDisplay() {
        val (statusText, statusColor) = when (transfer.status) {
            TransferStatus.PENDING -> "Pending" to android.graphics.Color.GRAY
            TransferStatus.CONNECTING -> "Connecting" to android.graphics.Color.BLUE
            TransferStatus.IN_PROGRESS -> "In Progress" to android.graphics.Color.BLUE
            TransferStatus.PAUSED -> "Paused" to android.graphics.Color.parseColor("#FF9800")
            TransferStatus.COMPLETED -> "Completed" to android.graphics.Color.parseColor("#4CAF50")
            TransferStatus.FAILED -> "Failed" to android.graphics.Color.RED
            TransferStatus.CANCELLED -> "Cancelled" to android.graphics.Color.GRAY
            TransferStatus.ERROR -> "Error" to android.graphics.Color.RED
        }
        
        binding.textViewStatus.text = statusText
        binding.textViewStatus.setTextColor(statusColor)
    }
    
    private fun getTransferTypeText(type: TransferType): String {
        return when (type) {
            TransferType.SEND_FILES -> "Send Files"
            TransferType.SEND_FOLDER -> "Send Folder"
            TransferType.RECEIVE_FILES -> "Receive Files"
            TransferType.RECEIVE_FOLDER -> "Receive Folder"
        }
    }
    
    private fun getTransferName(transfer: TransferSession): String {
        return when (transfer.type) {
            TransferType.SEND_FILES -> {
                if (transfer.files.size == 1) {
                    transfer.files.first().name
                } else {
                    "${transfer.files.size} files"
                }
            }
            TransferType.SEND_FOLDER -> transfer.rootFolder?.name ?: "Folder"
            TransferType.RECEIVE_FILES -> {
                if (transfer.files.size == 1) {
                    "Receiving ${transfer.files.first().name}"
                } else {
                    "Receiving ${transfer.files.size} files"
                }
            }
            TransferType.RECEIVE_FOLDER -> "Receiving ${transfer.rootFolder?.name ?: "Folder"}"
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> "%.1f GB".format(gb)
            mb >= 1 -> "%.1f MB".format(mb)
            kb >= 1 -> "%.1f KB".format(kb)
            else -> "$bytes B"
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
