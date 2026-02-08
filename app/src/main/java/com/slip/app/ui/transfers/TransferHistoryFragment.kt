package com.slip.app.ui.transfers

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.R
import com.slip.app.databinding.FragmentTransferHistoryBinding
import com.slip.app.domain.model.TransferSession
import com.slip.app.domain.model.TransferStatus
import com.slip.app.data.repository.PersistentTransferRepository
import kotlinx.coroutines.launch

/**
 * Fragment for displaying transfer history and active transfers
 */
class TransferHistoryFragment : Fragment() {
    
    private var _binding: FragmentTransferHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var transferHistoryAdapter: TransferHistoryAdapter
    private lateinit var viewModel: TransferHistoryViewModel
    private lateinit var persistentRepository: PersistentTransferRepository
    
    companion object {
        private const val TAG = "TransferHistoryFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize repository and view model
        persistentRepository = PersistentTransferRepository.getInstance(requireContext())
        viewModel = ViewModelProvider(this)[TransferHistoryViewModel::class.java]
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup observers
        setupObservers()
        
        // Load initial data
        viewModel.loadTransfers()
        
        // Setup refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadTransfers()
        }
    }
    
    private fun setupRecyclerView() {
        transferHistoryAdapter = TransferHistoryAdapter(
            onTransferClick = { transfer ->
                // Show transfer details
                showTransferDetails(transfer)
            },
            onPauseClick = { transfer ->
                viewModel.pauseTransfer(transfer.id)
            },
            onResumeClick = { transfer ->
                viewModel.resumeTransfer(transfer.id)
            },
            onCancelClick = { transfer ->
                viewModel.cancelTransfer(transfer.id)
            },
            onRetryClick = { transfer ->
                viewModel.retryTransfer(transfer.id)
            }
        )
        
        binding.recyclerViewTransfers.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transferHistoryAdapter
        }
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeTransfers.collect { transfers ->
                transferHistoryAdapter.updateActiveTransfers(transfers)
                updateEmptyState()
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.completedTransfers.collect { transfers ->
                transferHistoryAdapter.updateCompletedTransfers(transfers)
                updateEmptyState()
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefreshLayout.isRefreshing = isLoading
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transferStats.collect { stats ->
                updateStatistics(stats)
            }
        }
    }
    
    private fun updateEmptyState() {
        val hasActiveTransfers = transferHistoryAdapter.activeTransfers.isNotEmpty()
        val hasCompletedTransfers = transferHistoryAdapter.completedTransfers.isNotEmpty()
        
        when {
            hasActiveTransfers && hasCompletedTransfers -> {
                binding.textViewEmptyState.visibility = View.GONE
                binding.recyclerViewTransfers.visibility = View.VISIBLE
            }
            hasActiveTransfers -> {
                binding.textViewEmptyState.text = "No completed transfers yet"
                binding.textViewEmptyState.visibility = View.VISIBLE
                binding.recyclerViewTransfers.visibility = View.VISIBLE
            }
            hasCompletedTransfers -> {
                binding.textViewEmptyState.text = "No active transfers"
                binding.textViewEmptyState.visibility = View.VISIBLE
                binding.recyclerViewTransfers.visibility = View.VISIBLE
            }
            else -> {
                binding.textViewEmptyState.text = "No transfers yet. Start by selecting files to send!"
                binding.textViewEmptyState.visibility = View.VISIBLE
                binding.recyclerViewTransfers.visibility = View.GONE
            }
        }
    }
    
    private fun updateStatistics(stats: TransferStats) {
        binding.textViewActiveCount.text = stats.activeTransfers.toString()
        binding.textViewCompletedCount.text = stats.completedTransfers.toString()
        binding.textViewSuccessRate.text = "${(stats.successRate * 100).toInt()}%"
        binding.textViewTotalTransferred.text = formatFileSize(stats.totalBytesTransferred)
    }
    
    private fun showTransferDetails(transfer: TransferSession) {
        // Create transfer details dialog
        val dialog = TransferDetailsDialog.newInstance(transfer)
        dialog.show(childFragmentManager, "TransferDetailsDialog")
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
    
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_transfer_history, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_completed -> {
                viewModel.clearCompletedTransfers()
                true
            }
            R.id.action_clear_failed -> {
                viewModel.clearFailedTransfers()
                true
            }
            R.id.action_cleanup_old -> {
                viewModel.cleanupOldTransfers()
                true
            }
            R.id.action_settings -> {
                // Navigate to settings
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
