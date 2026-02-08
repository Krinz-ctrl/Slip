package com.slip.app.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.slip.app.R
import com.slip.app.databinding.DialogStorageSettingsBinding
import com.slip.app.ui.settings.SettingsManager

/**
 * Dialog for storage settings
 */
class StorageSettingsDialog : DialogFragment() {
    
    private var _binding: DialogStorageSettingsBinding? = null
    private val binding get() = _binding!!
    
    private var currentSettings: AppSettings? = null
    private var onSettingsUpdated: ((AppSettings) -> Unit)? = null
    
    companion object {
        private const val ARG_SETTINGS = "settings"
        
        fun newInstance(
            currentSettings: AppSettings,
            onSettingsUpdated: ((AppSettings) -> Unit)? = null
        ): StorageSettingsDialog {
            return StorageSettingsDialog().apply {
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
            setTitle("Storage Settings")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStorageSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        currentSettings?.let { settings ->
            setupStorageSettings(settings)
        }
        
        setupButtons()
    }
    
    private fun setupStorageSettings(settings: AppSettings) {
        // Save location
        binding.editTextSaveLocation.setText(settings.saveLocation)
        
        // Max concurrent transfers
        binding.seekBarMaxConcurrent.progress = settings.maxConcurrentTransfers
        binding.seekBarMaxConcurrent.max = 10
        binding.textViewMaxConcurrentValue.text = settings.maxConcurrentTransfers.toString()
        binding.seekBarMaxConcurrent.setOnSeekBarChangeListener { _, progress, _ ->
            binding.textViewMaxConcurrentValue.text = progress.toString()
            val updatedSettings = currentSettings?.copy(maxConcurrentTransfers = progress)
            currentSettings = updatedSettings
        }
        
        // Cleanup old days
        binding.seekBarCleanupDays.progress = settings.cleanupOldDays
        binding.seekBarCleanupDays.max = 30
        binding.textViewCleanupDaysValue.text = settings.cleanupOldDays.toString()
        binding.seekBarCleanupDays.setOnSeekBarChangeListener { _, progress, _ ->
            binding.textViewCleanupDaysValue.text = progress.toString()
            val updatedSettings = currentSettings?.copy(cleanupOldDays = progress)
            currentSettings = updatedSettings
        }
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonSave.setOnClickListener {
            currentSettings?.let { settings ->
                val updatedSettings = settings.copy(
                    saveLocation = binding.editTextSaveLocation.text.toString(),
                    maxConcurrentTransfers = binding.seekBarMaxConcurrent.progress,
                    cleanupOldDays = binding.seekBarCleanupDays.progress
                )
                onSettingsUpdated?.invoke(updatedSettings)
            }
            dismiss()
        }
        
        binding.buttonBrowseLocation.setOnClickListener {
            // TODO: Implement folder picker
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
