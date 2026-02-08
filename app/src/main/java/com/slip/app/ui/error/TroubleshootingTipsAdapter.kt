package com.slip.app.ui.error

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.databinding.ItemTroubleshootingTipBinding

/**
 * Adapter for displaying troubleshooting tips
 */
class TroubleshootingTipsAdapter(
    private val tips: List<String>
) : RecyclerView.Adapter<TroubleshootingTipsAdapter.TipViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val binding = ItemTroubleshootingTipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TipViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        holder.bind(tips[position])
    }
    
    override fun getItemCount(): Int {
        return tips.size
    }
    
    inner class TipViewHolder(
        private val binding: ItemTroubleshootingTipBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(tip: String) {
            binding.textViewTip.text = tip
            
            // Set bullet point
            binding.textViewBullet.text = "•"
        }
    }
}
