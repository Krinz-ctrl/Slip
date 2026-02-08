package com.slip.app.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.slip.app.R
import com.slip.app.databinding.DialogNotificationSettingsBinding
import com.slip.app.ui.settings.SettingsManager

/**
 * Dialog for notification settings
 */
class NotificationSettingsDialog : DialogFragment() {
    
    private var _binding: DialogNotificationSettingsBinding? = null
    private val binding get() = _binding!!
    
    private var currentSettings: AppSettings? = null
    private var onSettingsUpdated: ((AppSettings) -> Unit)? = null
    
    companion object {
        private const val ARG_SETTINGS = "settings"
        
        fun newInstance(
            currentSettings: AppSettings,
            onSettingsUpdated: ((AppSettings) -> Unit)? = null
        ): NotificationSettingsDialog {
            return NotificationSettingsDialog().apply {
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
            setTitle("Notification Settings")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        currentSettings?.let { settings ->
            setupNotificationSettings(settings)
        }
        
        setupButtons()
    }
    
    private fun setupNotificationSettings(settings: AppSettings) {
        // Enable notifications
        binding.switchNotifications.isChecked = settings.notificationsEnabled
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val updatedSettings = settings.copy(notificationsEnabled = isChecked)
            currentSettings = updatedSettings
        }
        
        // Show progress notifications
        binding.switchProgressNotifications.isChecked = settings.showProgressNotifications
        binding.switchProgressNotifications.setOnCheckedChangeListener { _, isChecked ->
            val updatedSettings = currentSettings?.copy(showProgressNotifications = isChecked)
            currentSettings = updatedSettings
        }
        
        // Keep screen on
        binding.switchKeepScreenOn.isChecked = settings.keepScreenOn
        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            val updatedSettings = currentSettings?.copy(keepScreenOn = isChecked)
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
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
