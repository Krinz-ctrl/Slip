package com.slip.app.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.databinding.ItemSpeedOptionBinding

/**
 * Adapter for speed options
 */
class SpeedOptionAdapter(
    private val options: List<SpeedOption>,
    private val currentSelection: TransferSpeedLimit,
    private val onOptionSelected: (TransferSpeedLimit) -> Unit
) : ListAdapter<SpeedOption, SpeedOptionAdapter.SpeedViewHolder>(SpeedDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpeedViewHolder {
        val binding = ItemSpeedOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SpeedViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: SpeedViewHolder, position: Int) {
        holder.bind(options[position], options[position].speed == currentSelection)
    }
    
    override fun getItemCount(): Int {
        return options.size
    }
    
    inner class SpeedViewHolder(
        private val binding: ItemSpeedOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(option: SpeedOption, isSelected: Boolean) {
            binding.textViewTitle.text = option.title
            binding.textViewDescription.text = option.description
            
            // Update selection state
            if (isSelected) {
                binding.radioButton.isChecked = true
                binding.root.setBackgroundColor(
                    binding.root.context.getColor(com.slip.app.R.color.primary)
                )
                binding.textViewTitle.setTextColor(
                    binding.root.context.getColor(android.R.color.white)
                )
                binding.textViewDescription.setTextColor(
                    binding.root.context.getColor(android.R.color.white)
                )
            } else {
                binding.radioButton.isChecked = false
                binding.root.setBackgroundColor(
                    binding.root.context.getColor(android.R.color.transparent)
                )
                binding.textViewTitle.setTextColor(
                    binding.root.context.getColor(android.R.color.black)
                )
                binding.textViewDescription.setTextColor(
                    binding.root.context.getColor(android.R.color.darker_gray)
                )
            }
            
            // Set click listener
            binding.root.setOnClickListener {
                // Update selection
                onOptionSelected(option.speed)
                
                // Refresh all items
                notifyDataSetChanged()
            }
        }
    }
    
    private class SpeedDiffCallback : DiffUtil.ItemCallback<SpeedOption>() {
        override fun areItemsTheSame(oldItem: SpeedOption, newItem: SpeedOption): Boolean {
            return oldItem.speed == newItem.speed
        }
        
        override fun areContentsTheSame(oldItem: SpeedOption, newItem: SpeedOption): Boolean {
            return oldItem == newItem
        }
    }
}
