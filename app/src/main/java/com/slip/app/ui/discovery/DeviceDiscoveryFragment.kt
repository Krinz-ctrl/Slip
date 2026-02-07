package com.slip.app.ui.discovery

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.R
import com.slip.app.databinding.FragmentDeviceDiscoveryBinding
import com.slip.app.domain.model.DiscoveredDevice
import com.slip.app.domain.model.FileMetadata
import com.slip.app.service.network.DeviceDiscoveryService
import com.slip.app.service.network.TransferManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Fragment for discovering and selecting devices for file transfer
 */
class DeviceDiscoveryFragment : Fragment() {
    
    private var _binding: FragmentDeviceDiscoveryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var deviceDiscoveryService: DeviceDiscoveryService
    private lateinit var transferManager: TransferManager
    private lateinit var deviceAdapter: DeviceAdapter
    
    private var selectedDevice: DiscoveredDevice? = null
    private var filesToTransfer: List<FileMetadata> = emptyList()
    
    companion object {
        private const val TAG = "DeviceDiscoveryFragment"
        private const val ARG_FILES = "files_to_transfer"
        
        fun newInstance(files: List<FileMetadata> = emptyList()): DeviceDiscoveryFragment {
            return DeviceDiscoveryFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_FILES, ArrayList(files))
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceDiscoveryService = DeviceDiscoveryService(requireContext())
        transferManager = TransferManager.getInstance(requireContext())
        
        // Get files to transfer
        filesToTransfer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_FILES, FileMetadata::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList(ARG_FILES) ?: emptyList()
        }
        
        // Start receiving mode
        transferManager.startReceiving()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceDiscoveryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        startDiscovery()
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            onDeviceSelected(device)
        }
        
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = deviceAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonRefresh.setOnClickListener {
            refreshDiscovery()
        }
        
        binding.buttonConnect.setOnClickListener {
            selectedDevice?.let { device ->
                connectToDevice(device)
            } ?: run {
                Toast.makeText(context, "Please select a device", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.buttonCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    
    private fun startDiscovery() {
        Log.d(TAG, "Starting device discovery")
        
        binding.progressBarDiscovery.visibility = View.VISIBLE
        binding.textViewStatus.text = "Discovering devices..."
        binding.buttonConnect.isEnabled = false
        
        deviceDiscoveryService.startDiscovery()
        
        // Listen to discovery updates
        viewLifecycleOwner.lifecycleScope.launch {
            deviceDiscoveryService.devices.collect { devices ->
                updateDeviceList(devices)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            deviceDiscoveryService.discoveryError.collect { error ->
                error?.let {
                    Log.e(TAG, "Discovery error: $it")
                    binding.textViewStatus.text = "Discovery error: $it"
                    binding.progressBarDiscovery.visibility = View.GONE
                }
            }
        }
    }
    
    private fun updateDeviceList(devices: List<DiscoveredDevice>) {
        Log.d(TAG, "Updating device list: ${devices.size} devices")
        
        deviceAdapter.updateDevices(devices)
        
        if (devices.isEmpty()) {
            binding.textViewStatus.text = "No devices found. Make sure Slip is running on nearby devices."
        } else {
            binding.textViewStatus.text = "Found ${devices.size} device(s)"
        }
        
        binding.progressBarDiscovery.visibility = View.GONE
    }
    
    private fun refreshDiscovery() {
        Log.d(TAG, "Refreshing discovery")
        deviceDiscoveryService.refresh()
        binding.textViewStatus.text = "Refreshing..."
        binding.progressBarDiscovery.visibility = View.VISIBLE
    }
    
    private fun onDeviceSelected(device: DiscoveredDevice) {
        Log.d(TAG, "Device selected: ${device.name}")
        selectedDevice = device
        binding.buttonConnect.isEnabled = true
        
        // Update UI to show selection
        binding.textViewStatus.text = "Selected: ${device.name} (${device.getDisplayAddress()})"
    }
    
    private fun connectToDevice(device: DiscoveredDevice) {
        Log.d(TAG, "Connecting to device: ${device.name}")
        
        if (filesToTransfer.isEmpty()) {
            Toast.makeText(context, "No files to transfer", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.buttonConnect.isEnabled = false
        binding.textViewStatus.text = "Connecting to ${device.name}..."
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val success = transferManager.sendFiles(
                    device = device,
                    files = filesToTransfer
                ) { progress ->
                    // Update progress
                    binding.textViewStatus.text = "Sending files... ${progress.toInt()}%"
                }
                
                if (success) {
                    binding.textViewStatus.text = "Files sent successfully to ${device.name}"
                    Toast.makeText(context, "Transfer completed", Toast.LENGTH_SHORT).show()
                } else {
                    binding.textViewStatus.text = "Transfer failed"
                    Toast.makeText(context, "Transfer failed", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Transfer error", e)
                binding.textViewStatus.text = "Transfer error: ${e.message}"
                Toast.makeText(context, "Transfer error", Toast.LENGTH_SHORT).show()
            } finally {
                binding.buttonConnect.isEnabled = true
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Stop discovery and transfer when fragment is destroyed
        deviceDiscoveryService.stopDiscovery()
        transferManager.stopReceiving()
        
        _binding = null
    }
}
