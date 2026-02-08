package com.slip.app.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.slip.app.R
import com.slip.app.databinding.DialogAdvancedSettingsBinding
import com.slip.app.ui.settings.SettingsManager

/**
 * Dialog for advanced settings
 */
class AdvancedSettingsDialog : DialogFragment() {
    
    private var _binding: DialogAdvancedSettingsBinding? = null
    private val binding get() = _binding!!
    
    private var currentSettings: AppSettings? = null
    private var onSettingsUpdated: ((AppSettings) -> Unit)? = null
    
    companion object {
        private const val ARG_SETTINGS = "settings"
        
        fun newInstance(
            currentSettings: AppSettings,
            onSettingsUpdated: ((AppSettings) -> Unit)? = null
        ): AdvancedSettingsDialog {
            return AdvancedSettingsDialog().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_SETTINGS, currentSettings)
                }
                this.onSettingsUpdated = onSettingsUpdated
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSettings = requireArguments().getSerializable(ARG_SETTINGS) as? AppSettings
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setTitle("Advanced Settings")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAdvancedSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        currentSettings?.let { settings ->
            setupAdvancedSettings(settings)
        }
        
        setupButtons()
    }
    
    private fun setupAdvancedSettings(settings: AppSettings) {
        // Enable chunk verification
        binding.switchChunkVerification.isChecked = settings.enableChunkVerification
        binding.switchChunkVerification.setOnCheckedChangeListener { _, isChecked ->
            val updatedSettings = currentSettings?.copy(enableChunkVerification = isChecked)
            currentSettings = updatedSettings
        }
        
        // Retry failed transfers
        binding.switchRetryFailed.isChecked = settings.retryFailedTransfers
        binding.switchRetryFailed.setOnCheckedChangeListener { _, isChecked ->
            val updatedSettings = currentSettings?.copy(retryFailedTransfers = isChecked)
            currentSettings = updatedSettings
        }
        
        // Compress transfers
        binding.switchCompressTransfers.isChecked = settings.compressTransfers
        binding.switchCompressTransfers.setOnCheckedChangeListener { _, isChecked ->
            val updatedSettings = currentSettings?.copy(compressTransfers = isChecked)
            currentSettings = updatedSettings
        }
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonSave.setOnClickListener {
            currentSettings?.let { settings ->
                onSettingsUpdated?.invoke(settings)
            }
            dismiss()
        }
        
        binding.buttonResetDefaults.setOnClickListener {
            // Reset to default values
            val defaultSettings = AppSettings(
                transferSpeedLimit = TransferSpeedLimit.UNLIMITED,
                chunkSize = ChunkSize.AUTO,
                autoResume = true,
                notificationsEnabled = true,
                showProgressNotifications = true,
                keepScreenOn = false,
                saveLocation = "/storage/emulated/0/Download/Slip",
                maxConcurrentTransfers = 3,
                cleanupOldDays = 7,
                enableChunkVerification = true,
                retryFailedTransfers = true,
                compressTransfers = false
            )
            
            onSettingsUpdated?.invoke(defaultSettings)
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
