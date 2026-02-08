package com.slip.app.ui.settings

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.R
import com.slip.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

/**
 * Settings fragment for app preferences
 */
class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var networkStatusManager: NetworkStatusManager
    private lateinit var settingsAdapter: SettingsAdapter
    
    companion object {
        private const val TAG = "SettingsFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize managers
        settingsManager = SettingsManager.getInstance(requireContext())
        networkStatusManager = NetworkStatusManager.getInstance(requireContext())
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup observers
        setupObservers()
        
        // Setup click listeners
        setupClickListeners()
        
        // Start network monitoring
        networkStatusManager.startMonitoring()
    }
    
    private fun setupRecyclerView() {
        settingsAdapter = SettingsAdapter(
            onSettingClick = { setting ->
                handleSettingClick(setting)
            }
        )
        
        binding.recyclerViewSettings.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = settingsAdapter
        }
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Network status
            networkStatusManager.isConnected.collect { isConnected ->
                updateNetworkStatus(isConnected)
            }
            
            networkStatusManager.networkType.collect { networkType ->
                updateNetworkType(networkType)
            }
            
            networkStatusManager.connectionStrength.collect { strength ->
                updateConnectionStrength(strength)
            }
        }
    }
    
    private fun setupClickListeners() {
        // Network status click
        binding.layoutNetworkStatus.setOnClickListener {
            showNetworkDetails()
        }
        
        // Storage location click
        binding.layoutStorageLocation.setOnClickListener {
            showStorageLocationDialog()
        }
        
        // Save location click
        binding.buttonChangeSaveLocation.setOnClickListener {
            showStorageLocationDialog()
        }
    }
    
    private fun updateNetworkStatus(isConnected: Boolean) {
        if (isConnected) {
            binding.textViewNetworkStatus.text = "Connected"
            binding.textViewNetworkStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            binding.imageViewNetworkStatus.setImageResource(R.drawable.ic_wifi)
        } else {
            binding.textViewNetworkStatus.text = "Disconnected"
            binding.textViewNetworkStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            binding.imageViewNetworkStatus.setImageResource(R.drawable.ic_error)
        }
    }
    
    private fun updateNetworkType(networkType: com.slip.app.ui.settings.NetworkType) {
        val typeText = when (networkType) {
            com.slip.app.ui.settings.NetworkType.WIFI -> "Wi-Fi"
            com.slip.app.ui.settings.NetworkType.CELLULAR -> "Cellular"
            com.slip.app.ui.settings.NetworkType.ETHERNET -> "Ethernet"
            com.slip.app.ui.settings.NetworkType.UNKNOWN -> "Unknown"
            com.slip.app.ui.settings.NetworkType.NONE -> "None"
        }
        binding.textViewNetworkType.text = typeText
    }
    
    private fun updateConnectionStrength(strength: com.slip.app.ui.settings.ConnectionStrength) {
        val strengthText = when (strength) {
            com.slip.app.ui.settings.ConnectionStrength.UNKNOWN -> "Unknown"
            com.slip.app.ui.settings.ConnectionStrength.POOR -> "Poor"
            com.slip.app.ui.settings.ConnectionStrength.FAIR -> "Fair"
            com.slip.app.ui.settings.ConnectionStrength.GOOD -> "Good"
            com.slip.app.ui.settings.ConnectionStrength.EXCELLENT -> "Excellent"
            com.slip.app.ui.settings.ConnectionStrength.UNMETERED -> "Unmetered"
            com.slip.app.ui.settings.ConnectionStrength.VPN -> "VPN"
        }
        binding.textViewConnectionStrength.text = strengthText
    }
    
    private fun handleSettingClick(setting: SettingItem) {
        when (setting.type) {
            SettingType.TRANSFER_SPEED -> showTransferSpeedDialog()
            SettingType.CHUNK_SIZE -> showChunkSizeDialog()
            SettingType.AUTO_RESUME -> toggleBooleanSetting(setting)
            SettingType.NOTIFICATIONS -> showNotificationSettings()
            SettingType.STORAGE -> showStorageSettings()
            SettingType.ADVANCED -> showAdvancedSettings()
        }
    }
    
    private fun showTransferSpeedDialog() {
        // Create and show transfer speed selection dialog
        val dialog = TransferSpeedDialog.newInstance(
            currentSpeed = settingsManager.transferSpeedLimit.value,
            onSpeedSelected = { speed ->
                settingsManager.setTransferSpeedLimit(speed)
                settingsAdapter.updateSetting(SettingItem(
                    type = SettingType.TRANSFER_SPEED,
                    title = "Transfer Speed Limit",
                    value = speed.displaySpeed.toString(),
                    subtitle = getSpeedSubtitle(speed)
                ))
            }
        )
        dialog.show(childFragmentManager, "TransferSpeedDialog")
    }
    
    private fun showChunkSizeDialog() {
        // Create and show chunk size selection dialog
        val dialog = ChunkSizeDialog.newInstance(
            currentSize = settingsManager.chunkSize.value,
            onSizeSelected = { size ->
                settingsManager.setChunkSize(size)
                settingsAdapter.updateSetting(SettingItem(
                    type = SettingType.CHUNK_SIZE,
                    title = "Chunk Size",
                    value = size.value,
                    subtitle = getChunkSizeSubtitle(size)
                ))
            }
        )
        dialog.show(childFragmentManager, "ChunkSizeDialog")
    }
    
    private fun toggleBooleanSetting(setting: SettingItem) {
        val currentValue = when (setting.type) {
            SettingType.AUTO_RESUME -> settingsManager.autoResume.value
            SettingType.NOTIFICATIONS -> settingsManager.notificationsEnabled.value
            else -> false
        }
        
        val newValue = !currentValue
        when (setting.type) {
            SettingType.AUTO_RESUME -> settingsManager.setAutoResume(newValue)
            SettingType.NOTIFICATIONS -> settingsManager.setNotificationsEnabled(newValue)
        }
        
        settingsAdapter.updateSetting(setting.copy(value = if (newValue) "Enabled" else "Disabled"))
    }
    
    private fun showNotificationSettings() {
        // Create notification settings dialog
        val dialog = NotificationSettingsDialog.newInstance(
            currentSettings = settingsManager.getAllSettings(),
            onSettingsUpdated = { settings ->
                // Update all notification-related settings
                settingsManager.setNotificationsEnabled(settings.notificationsEnabled)
                settingsManager.setShowProgressNotifications(settings.showProgressNotifications)
                settingsManager.setKeepScreenOn(settings.keepScreenOn)
            }
        )
        dialog.show(childFragmentManager, "NotificationSettingsDialog")
    }
    
    private fun showStorageSettings() {
        // Create storage settings dialog
        val dialog = StorageSettingsDialog.newInstance(
            currentSettings = settingsManager.getAllSettings(),
            onSettingsUpdated = { settings ->
                // Update storage-related settings
                settingsManager.setSaveLocation(settings.saveLocation)
                settingsManager.setMaxConcurrentTransfers(settings.maxConcurrentTransfers)
                settingsManager.setCleanupOldDays(settings.cleanupOldDays)
            }
        )
        dialog.show(childFragmentManager, "StorageSettingsDialog")
    }
    
    private fun showAdvancedSettings() {
        // Create advanced settings dialog
        val dialog = AdvancedSettingsDialog.newInstance(
            currentSettings = settingsManager.getAllSettings(),
            onSettingsUpdated = { settings ->
                // Update advanced settings
                settingsManager.setEnableChunkVerification(settings.enableChunkVerification)
                settingsManager.setRetryFailedTransfers(settings.retryFailedTransfers)
                settingsManager.setCompressTransfers(settings.compressTransfers)
            }
        )
        dialog.show(childFragmentManager, "AdvancedSettingsDialog")
    }
    
    private fun showNetworkDetails() {
        // Show network status details
        val status = networkStatusManager.getNetworkStatus()
        val message = """
            Network Status: ${if (status.isConnected) "Connected" else "Disconnected"}
            Type: ${status.networkType}
            Strength: ${status.connectionStrength}
            WiFi Enabled: ${status.isWifiEnabled}
            Speed: ${status.networkSpeed}
        """.trimIndent()
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Network Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showStorageLocationDialog() {
        // Show storage location picker
        val currentLocation = settingsManager.saveLocation.value
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Storage Location")
            .setMessage("Current location: $currentLocation")
            .setPositiveButton("Change") { _, _ ->
                // TODO: Implement folder picker
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getSpeedSubtitle(speed: TransferSpeedLimit): String {
        return when (speed) {
            TransferSpeedLimit.UNLIMITED -> "No speed limit"
            TransferSpeedLimit.SLOW -> "512 KB/s"
            TransferSpeedLimit.MEDIUM -> "2 MB/s"
            TransferSpeedLimit.FAST -> "10 MB/s"
            TransferSpeedLimit.CUSTOM -> "Custom speed"
        }
    }
    
    private fun getChunkSizeSubtitle(size: ChunkSize): String {
        return when (size) {
            ChunkSize.AUTO -> "Automatic"
            ChunkSize.SMALL -> "256 KB"
            ChunkSize.MEDIUM -> "1 MB"
            ChunkSize.LARGE -> "2 MB"
            ChunkSize.EXTRA_LARGE -> "4 MB"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
