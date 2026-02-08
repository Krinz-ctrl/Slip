package com.slip.app.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.databinding.ItemSettingBinding

/**
 * Adapter for displaying settings items
 */
class SettingsAdapter(
    private val onSettingClick: (SettingItem) -> Unit
) : ListAdapter<SettingItem, SettingsAdapter.SettingViewHolder>(SettingDiffCallback()) {
    
    private val settings = mutableListOf<SettingItem>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
        val binding = ItemSettingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SettingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
        holder.bind(settings[position])
    }
    
    override fun getItemCount(): Int {
        return settings.size
    }
    
    fun updateSettings(newSettings: List<SettingItem>) {
        settings.clear()
        settings.addAll(newSettings)
        notifyDataSetChanged()
    }
    
    fun updateSetting(setting: SettingItem) {
        val index = settings.indexOfFirst { it.type == setting.type }
        if (index != -1) {
            settings[index] = setting
            notifyItemChanged(index)
        }
    }
    
    inner class SettingViewHolder(
        private val binding: ItemSettingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(setting: SettingItem) {
            binding.textViewTitle.text = setting.title
            binding.textViewValue.text = setting.value
            binding.textViewSubtitle.text = setting.subtitle
            
            // Set icon based on setting type
            val iconRes = when (setting.type) {
                SettingType.TRANSFER_SPEED -> com.slip.app.R.drawable.ic_speed
                SettingType.CHUNK_SIZE -> com.slip.app.R.drawable.ic_chunk
                SettingType.AUTO_RESUME -> com.slip.app.R.drawable.ic_resume
                SettingType.NOTIFICATIONS -> com.slip.app.R.drawable.ic_notifications
                SettingType.STORAGE -> com.slip.app.R.drawable.ic_storage
                SettingType.ADVANCED -> com.slip.app.R.drawable.ic_advanced
            }
            
            binding.imageViewIcon.setImageResource(iconRes)
            
            // Set click listener
            binding.root.setOnClickListener {
                onSettingClick(setting)
            }
        }
    }
    
    private class SettingDiffCallback : DiffUtil.ItemCallback<SettingItem>() {
        override fun areItemsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return oldItem.type == newItem.type
        }
        
        override fun areContentsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Setting item data class
 */
data class SettingItem(
    val type: SettingType,
    val title: String,
    val value: String,
    val subtitle: String
)

/**
 * Setting types
 */
enum class SettingType {
    TRANSFER_SPEED,
    CHUNK_SIZE,
    AUTO_RESUME,
    NOTIFICATIONS,
    STORAGE,
    ADVANCED
}
