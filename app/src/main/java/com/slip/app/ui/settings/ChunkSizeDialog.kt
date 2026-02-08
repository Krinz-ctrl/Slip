package com.slip.app.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.slip.app.R
import com.slip.app.databinding.DialogChunkSizeBinding

/**
 * Dialog for selecting chunk size preference
 */
class ChunkSizeDialog : DialogFragment() {
    
    private var _binding: DialogChunkSizeBinding? = null
    private val binding get() = _binding!!
    
    private var currentSize: ChunkSize = ChunkSize.AUTO
    private var onSizeSelected: ((ChunkSize) -> Unit)? = null
    
    companion object {
        private const val ARG_CURRENT_SIZE = "current_size"
        
        fun newInstance(
            currentSize: ChunkSize,
            onSizeSelected: ((ChunkSize) -> Unit)? = null
        ): ChunkSizeDialog {
            return ChunkSizeDialog().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CURRENT_SIZE, currentSize)
                }
                this.onSizeSelected = onSizeSelected
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSize = requireArguments().getSerializable(ARG_CURRENT_SIZE) as? ChunkSize 
            ?: ChunkSize.AUTO
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setTitle("Chunk Size Preference")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogChunkSizeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupChunkSizeOptions()
        setupButtons()
    }
    
    private fun setupChunkSizeOptions() {
        val sizeOptions = listOf(
            ChunkSizeOption(ChunkSize.AUTO, "Automatic", "Optimal size based on file size"),
            ChunkSizeOption(ChunkSize.SMALL, "Small", "256 KB chunks (good for slow networks)"),
            ChunkSizeOption(ChunkSize.MEDIUM, "Medium", "1 MB chunks (balanced)"),
            ChunkSizeOption(ChunkSize.LARGE, "Large", "2 MB chunks (faster transfers)"),
            ChunkSizeOption(ChunkSize.EXTRA_LARGE, "Extra Large", "4 MB chunks (maximum speed)")
        )
        
        val adapter = ChunkSizeOptionAdapter(
            options = sizeOptions,
            currentSelection = currentSize,
            onOptionSelected = { size ->
                currentSize = size
            }
        )
        
        binding.recyclerViewChunkOptions.adapter = adapter
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonOk.setOnClickListener {
            onSizeSelected?.invoke(currentSize)
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Chunk size option data class
 */
data class ChunkSizeOption(
    val size: ChunkSize,
    val title: String,
    val description: String
)
