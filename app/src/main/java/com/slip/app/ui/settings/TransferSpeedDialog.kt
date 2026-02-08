package com.slip.app.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.slip.app.R
import com.slip.app.databinding.DialogTransferSpeedBinding

/**
 * Dialog for selecting transfer speed limit
 */
class TransferSpeedDialog : DialogFragment() {
    
    private var _binding: DialogTransferSpeedBinding? = null
    private val binding get() = _binding!!
    
    private var currentSpeed: TransferSpeedLimit = TransferSpeedLimit.UNLIMITED
    private var onSpeedSelected: ((TransferSpeedLimit) -> Unit)? = null
    
    companion object {
        private const val ARG_CURRENT_SPEED = "current_speed"
        
        fun newInstance(
            currentSpeed: TransferSpeedLimit,
            onSpeedSelected: ((TransferSpeedLimit) -> Unit)? = null
        ): TransferSpeedDialog {
            return TransferSpeedDialog().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CURRENT_SPEED, currentSpeed)
                }
                this.onSpeedSelected = onSpeedSelected
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSpeed = requireArguments().getSerializable(ARG_CURRENT_SPEED) as? TransferSpeedLimit 
            ?: TransferSpeedLimit.UNLIMITED
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setTitle("Transfer Speed Limit")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTransferSpeedBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSpeedOptions()
        setupButtons()
    }
    
    private fun setupSpeedOptions() {
        val speedOptions = listOf(
            SpeedOption(TransferSpeedLimit.UNLIMITED, "No Limit", "Maximum speed"),
            SpeedOption(TransferSpeedLimit.SLOW, "Slow", "512 KB/s"),
            SpeedOption(TransferSpeedLimit.MEDIUM, "Medium", "2 MB/s"),
            SpeedOption(TransferSpeedLimit.FAST, "Fast", "10 MB/s"),
            SpeedOption(TransferSpeedLimit.CUSTOM, "Custom", "Set custom speed")
        )
        
        val adapter = SpeedOptionAdapter(
            options = speedOptions,
            currentSelection = currentSpeed,
            onOptionSelected = { speed ->
                currentSpeed = speed
            }
        )
        
        binding.recyclerViewSpeedOptions.adapter = adapter
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonOk.setOnClickListener {
            onSpeedSelected?.invoke(currentSpeed)
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Speed option data class
 */
data class SpeedOption(
    val speed: TransferSpeedLimit,
    val title: String,
    val description: String
)
