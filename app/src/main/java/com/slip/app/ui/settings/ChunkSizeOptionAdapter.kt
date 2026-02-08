package com.slip.app.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.databinding.ItemChunkSizeOptionBinding

/**
 * Adapter for chunk size options
 */
class ChunkSizeOptionAdapter(
    private val options: List<ChunkSizeOption>,
    private val currentSelection: ChunkSize,
    private val onOptionSelected: (ChunkSize) -> Unit
) : ListAdapter<ChunkSizeOption, ChunkSizeOptionAdapter.ChunkSizeViewHolder>(ChunkSizeDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChunkSizeViewHolder {
        val binding = ItemChunkSizeOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChunkSizeViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ChunkSizeViewHolder, position: Int) {
        holder.bind(options[position], options[position].size == currentSelection)
    }
    
    override fun getItemCount(): Int {
        return options.size
    }
    
    inner class ChunkSizeViewHolder(
        private val binding: ItemChunkSizeOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(option: ChunkSizeOption, isSelected: Boolean) {
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
                onOptionSelected(option.size)
                
                // Refresh all items
                notifyDataSetChanged()
            }
        }
    }
    
    private class ChunkSizeDiffCallback : DiffUtil.ItemCallback<ChunkSizeOption>() {
        override fun areItemsTheSame(oldItem: ChunkSizeOption, newItem: ChunkSizeOption): Boolean {
            return oldItem.size == newItem.size
        }
        
        override fun areContentsTheSame(oldItem: ChunkSizeOption, newItem: ChunkSizeOption): Boolean {
            return oldItem == newItem
        }
    }
}
