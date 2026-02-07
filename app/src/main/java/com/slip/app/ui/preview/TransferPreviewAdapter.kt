package com.slip.app.ui.preview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.slip.app.R
import com.slip.app.domain.model.FileMetadata
import com.slip.app.domain.model.FolderNode

/**
 * Adapter for displaying transfer preview items
 */
class TransferPreviewAdapter : ListAdapter<PreviewItem, TransferPreviewAdapter.PreviewViewHolder>(DiffCallback()) {
    
    private var files: List<FileMetadata> = emptyList()
    private var folders: List<FolderNode> = emptyList()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transfer_preview, parent, false)
        return PreviewViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
    
    override fun getItemCount(): Int {
        return files.size + folders.size
    }
    
    fun updateData(files: List<FileMetadata>, folders: List<FolderNode>) {
        this.files = files
        this.folders = folders
        
        val items = mutableListOf<PreviewItem>()
        
        // Add folders first
        folders.forEach { folder ->
            items.add(PreviewItem.FolderItem(folder))
        }
        
        // Add files
        files.forEach { file ->
            items.add(PreviewItem.FileItem(file))
        }
        
        submitList(items)
    }
    
    class PreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.imageViewIcon)
        private val nameView: TextView = itemView.findViewById(R.id.textViewName)
        private val sizeView: TextView = itemView.findViewById(R.id.textViewSize)
        private val pathView: TextView = itemView.findViewById(R.id.textViewPath)
        
        fun bind(item: PreviewItem) {
            when (item) {
                is PreviewItem.FolderItem -> {
                    bindFolder(item.folder)
                }
                is PreviewItem.FileItem -> {
                    bindFile(item.file)
                }
            }
        }
        
        private fun bindFolder(folder: FolderNode) {
            iconView.setImageResource(R.drawable.ic_folder)
            nameView.text = folder.name
            sizeView.text = "${folder.totalFiles} files, ${folder.totalFolders} folders"
            sizeView.text = "${folder.getFormattedTotalSize()} • ${folder.totalFiles} files"
            pathView.text = folder.relativePath.ifEmpty { "/" }
            pathView.visibility = if (folder.relativePath.isNotEmpty()) View.VISIBLE else View.GONE
        }
        
        private fun bindFile(file: FileMetadata) {
            nameView.text = file.name
            sizeView.text = file.getFormattedSize()
            pathView.text = file.relativePath
            pathView.visibility = if (file.relativePath.isNotEmpty()) View.VISIBLE else View.GONE
            
            // Set icon based on MIME type
            val icon = when {
                file.mimeType.startsWith("image/") -> R.drawable.ic_image
                file.mimeType.startsWith("video/") -> R.drawable.ic_video
                file.mimeType.startsWith("audio/") -> R.drawable.ic_audio
                file.mimeType.startsWith("text/") -> R.drawable.ic_document
                file.mimeType == "application/pdf" -> R.drawable.ic_pdf
                file.mimeType.contains("zip") || file.mimeType.contains("rar") -> R.drawable.ic_archive
                else -> R.drawable.ic_file
            }
            iconView.setImageResource(icon)
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<PreviewItem>() {
        override fun areItemsTheSame(oldItem: PreviewItem, newItem: PreviewItem): Boolean {
            return when {
                oldItem is PreviewItem.FileItem && newItem is PreviewItem.FileItem -> 
                    oldItem.file.id == newItem.file.id
                oldItem is PreviewItem.FolderItem && newItem is PreviewItem.FolderItem -> 
                    oldItem.folder.id == newItem.folder.id
                else -> false
            }
        }
        
        override fun areContentsTheSame(oldItem: PreviewItem, newItem: PreviewItem): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Sealed class for preview items
 */
sealed class PreviewItem {
    data class FileItem(val file: FileMetadata) : PreviewItem()
    data class FolderItem(val folder: FolderNode) : PreviewItem()
}
