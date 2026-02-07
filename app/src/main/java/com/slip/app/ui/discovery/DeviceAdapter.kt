package com.slip.app.ui.discovery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.R
import com.slip.app.domain.model.DiscoveredDevice
import com.slip.app.domain.model.DeviceType

/**
 * Adapter for displaying discovered devices
 */
class DeviceAdapter(
    private val onDeviceClick: (DiscoveredDevice) -> Unit
) : ListAdapter<DiscoveredDevice, DeviceAdapter.DeviceViewHolder>(DiffCallback()) {
    
    private var selectedDeviceId: String? = null
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device, device.id == selectedDeviceId)
    }
    
    fun updateDevices(devices: List<DiscoveredDevice>) {
        submitList(devices)
    }
    
    fun selectDevice(deviceId: String) {
        val previousSelectedId = selectedDeviceId
        selectedDeviceId = deviceId
        
        // Update previous selection
        previousSelectedId?.let { id ->
            val position = currentList.indexOfFirst { it.id == id }
            if (position != -1) {
                notifyItemChanged(position)
            }
        }
        
        // Update new selection
        val position = currentList.indexOfFirst { it.id == deviceId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }
    
    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.imageViewDeviceIcon)
        private val nameView: TextView = itemView.findViewById(R.id.textViewDeviceName)
        private val addressView: TextView = itemView.findViewById(R.id.textViewDeviceAddress)
        private val statusView: TextView = itemView.findViewById(R.id.textViewDeviceStatus)
        private val selectedIndicator: View = itemView.findViewById(R.id.viewSelectedIndicator)
        
        fun bind(device: DiscoveredDevice, isSelected: Boolean) {
            nameView.text = device.name
            addressView.text = device.getDisplayAddress()
            
            // Set status
            val statusText = if (device.isOnline()) {
                "Online"
            } else {
                "Offline"
            }
            statusView.text = statusText
            statusView.setTextColor(
                if (device.isOnline()) {
                    0xFF4CAF50.toInt() // Green
                } else {
                    0xFFF44336.toInt() // Red
                }
            )
            
            // Set icon based on device type
            val icon = when (device.deviceInfo?.deviceType) {
                DeviceType.PHONE -> R.drawable.ic_phone
                DeviceType.TABLET -> R.drawable.ic_tablet
                DeviceType.DESKTOP -> R.drawable.ic_desktop
                DeviceType.LAPTOP -> R.drawable.ic_laptop
                else -> R.drawable.ic_device_unknown
            }
            iconView.setImageResource(icon)
            
            // Show/hide selection indicator
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Set click listener
            itemView.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<DiscoveredDevice>() {
        override fun areItemsTheSame(oldItem: DiscoveredDevice, newItem: DiscoveredDevice): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: DiscoveredDevice, newItem: DiscoveredDevice): Boolean {
            return oldItem == newItem
        }
    }
}
