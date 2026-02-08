package com.slip.app.ui.transfers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.databinding.ItemTransferHistoryBinding
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.domain.model.TransferType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying transfer history
 */
class TransferHistoryAdapter(
    private val onTransferClick: (TransferSession) -> Unit,
    private val onPauseClick: (TransferSession) -> Unit,
    private val onResumeClick: (TransferSession) -> Unit,
    private val onCancelClick: (TransferSession) -> Unit,
    private val onRetryClick: (TransferSession) -> Unit
) : ListAdapter<TransferSession, TransferHistoryAdapter.TransferViewHolder>(TransferDiffCallback()) {
    
    var activeTransfers: List<TransferSession> = emptyList()
        private set
    
    var completedTransfers: List<TransferSession> = emptyList()
        private set
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferViewHolder {
        val binding = ItemTransferHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransferViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TransferViewHolder, position: Int) {
        val allTransfers = activeTransfers + completedTransfers
        val transfer = allTransfers[position]
        holder.bind(transfer)
    }
    
    override fun getItemCount(): Int {
        return activeTransfers.size + completedTransfers.size
    }
    
    fun updateActiveTransfers(transfers: List<TransferSession>) {
        activeTransfers = transfers
        notifyDataSetChanged()
    }
    
    fun updateCompletedTransfers(transfers: List<TransferSession>) {
        completedTransfers = transfers
        notifyDataSetChanged()
    }
    
    inner class TransferViewHolder(
        private val binding: ItemTransferHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(transfer: TransferSession) {
            // Basic info
            binding.textViewFileName.text = getDisplayText(transfer)
            binding.textViewFileSize.text = formatFileSize(transfer.totalSize)
            binding.textViewTime.text = formatTime(transfer.startTime)
            
            // Progress
            binding.progressBarProgress.progress = (transfer.progress * 100).toInt()
            binding.textViewProgress.text = "${(transfer.progress * 100).toInt()}%"
            
            // Status
            updateStatus(transfer)
            
            // Actions
            updateActions(transfer)
            
            // Click listeners
            binding.root.setOnClickListener {
                onTransferClick(transfer)
            }
        }
        
        private fun getDisplayText(transfer: TransferSession): String {
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
        
        private fun updateStatus(transfer: TransferSession) {
            val (statusText, statusColor, statusIcon) = when (transfer.status) {
                TransferStatus.PENDING -> "Pending" to android.graphics.Color.GRAY to R.drawable.ic_clock
                TransferStatus.CONNECTING -> "Connecting" to android.graphics.Color.BLUE to R.drawable.ic_wifi
                TransferStatus.IN_PROGRESS -> "Transferring" to android.graphics.Color.BLUE to R.drawable.ic_transfer
                TransferStatus.PAUSED -> "Paused" to android.graphics.Color.parseColor("#FF9800") to R.drawable.ic_pause
                TransferStatus.COMPLETED -> "Completed" to android.graphics.Color.parseColor("#4CAF50") to R.drawable.ic_check
                TransferStatus.FAILED -> "Failed" to android.graphics.Color.RED to R.drawable.ic_error
                TransferStatus.CANCELLED -> "Cancelled" to android.graphics.Color.GRAY to R.drawable.ic_cancel
                TransferStatus.ERROR -> "Error" to android.graphics.Color.RED to R.drawable.ic_error
            }
            
            binding.textViewStatus.text = statusText
            binding.textViewStatus.setTextColor(statusColor)
            binding.imageViewStatus.setImageResource(statusIcon)
        }
        
        private fun updateActions(transfer: TransferSession) {
            when (transfer.status) {
                TransferStatus.IN_PROGRESS -> {
                    binding.buttonPause.visibility = View.VISIBLE
                    binding.buttonResume.visibility = View.GONE
                    binding.buttonCancel.visibility = View.VISIBLE
                    binding.buttonRetry.visibility = View.GONE
                }
                TransferStatus.PAUSED -> {
                    binding.buttonPause.visibility = View.GONE
                    binding.buttonResume.visibility = View.VISIBLE
                    binding.buttonCancel.visibility = View.VISIBLE
                    binding.buttonRetry.visibility = View.GONE
                }
                TransferStatus.FAILED -> {
                    binding.buttonPause.visibility = View.GONE
                    binding.buttonResume.visibility = View.GONE
                    binding.buttonCancel.visibility = View.GONE
                    binding.buttonRetry.visibility = View.VISIBLE
                }
                else -> {
                    binding.buttonPause.visibility = View.GONE
                    binding.buttonResume.visibility = View.GONE
                    binding.buttonCancel.visibility = View.GONE
                    binding.buttonRetry.visibility = View.GONE
                }
            }
            
            // Set click listeners
            binding.buttonPause.setOnClickListener {
                onPauseClick(transfer)
            }
            binding.buttonResume.setOnClickListener {
                onResumeClick(transfer)
            }
            binding.buttonCancel.setOnClickListener {
                onCancelClick(transfer)
            }
            binding.buttonRetry.setOnClickListener {
                onRetryClick(transfer)
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
        
        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
    
    private class TransferDiffCallback : DiffUtil.ItemCallback<TransferSession>() {
        override fun areItemsTheSame(oldItem: TransferSession, newItem: TransferSession): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: TransferSession, newItem: TransferSession): Boolean {
            return oldItem == newItem
        }
    }
}
