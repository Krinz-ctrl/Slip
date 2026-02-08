package com.slip.app.ui.error

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.slip.app.R
import com.slip.app.databinding.DialogErrorBinding

/**
 * Dialog for displaying user-friendly error messages with troubleshooting tips
 */
class ErrorDialog : DialogFragment() {
    
    private var _binding: DialogErrorBinding? = null
    private val binding get() = _binding!!
    
    private var title: String = ""
    private var message: String = ""
    private var troubleshootingTips: List<String> = emptyList()
    private var suggestedActions: List<String> = emptyList()
    private var onActionClick: ((String) -> Unit)? = null
    
    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_TIPS = "tips"
        private const val ARG_ACTIONS = "actions"
        
        fun newInstance(
            title: String,
            message: String,
            troubleshootingTips: List<String>,
            suggestedActions: List<String>,
            onActionClick: ((String) -> Unit)? = null
        ): ErrorDialog {
            return ErrorDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_MESSAGE, message)
                    putStringArrayList(ARG_TIPS, ArrayList(troubleshootingTips))
                    putStringArrayList(ARG_ACTIONS, ArrayList(suggestedActions))
                }
                this.onActionClick = onActionClick
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        arguments?.let { args ->
            title = args.getString(ARG_TITLE) ?: ""
            message = args.getString(ARG_MESSAGE) ?: ""
            troubleshootingTips = args.getStringArrayList(ARG_TIPS) ?: emptyList()
            suggestedActions = args.getStringArrayList(ARG_ACTIONS) ?: emptyList()
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setTitle(title)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogErrorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupErrorMessage()
        setupTroubleshootingTips()
        setupSuggestedActions()
        setupButtons()
    }
    
    private fun setupErrorMessage() {
        binding.textViewErrorMessage.text = message
        
        // Set error icon based on title
        val errorIcon = when {
            title.contains("Network", ignoreCase = true) -> R.drawable.ic_wifi
            title.contains("File", ignoreCase = true) -> R.drawable.ic_file
            title.contains("Permission", ignoreCase = true) -> R.drawable.ic_error
            else -> R.drawable.ic_error
        }
        
        binding.imageViewErrorIcon.setImageResource(errorIcon)
    }
    
    private fun setupTroubleshootingTips() {
        if (troubleshootingTips.isEmpty()) {
            binding.textViewTroubleshootingTitle.visibility = View.GONE
            binding.recyclerViewTips.visibility = View.GONE
            return
        }
        
        binding.textViewTroubleshootingTitle.visibility = View.VISIBLE
        binding.recyclerViewTips.visibility = View.VISIBLE
        
        val tipsAdapter = TroubleshootingTipsAdapter(troubleshootingTips)
        binding.recyclerViewTips.adapter = tipsAdapter
    }
    
    private fun setupSuggestedActions() {
        if (suggestedActions.isEmpty()) {
            binding.layoutActions.visibility = View.GONE
            return
        }
        
        binding.layoutActions.visibility = View.VISIBLE
        
        // Clear existing buttons
        binding.layoutActionButtons.removeAllViews()
        
        // Add action buttons
        suggestedActions.forEach { action ->
            val button = com.google.android.material.button.MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = action
                setOnClickListener {
                    onActionClick?.invoke(action)
                    dismiss()
                }
            }
            
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 8, 0, 0)
            
            binding.layoutActionButtons.addView(button, params)
        }
    }
    
    private fun setupButtons() {
        binding.buttonClose.setOnClickListener {
            dismiss()
        }
        
        binding.buttonContactSupport.setOnClickListener {
            openSupport()
        }
    }
    
    private fun openSupport() {
        // Open support email or website
        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
            putExtra(android.content.Intent.EXTRA_EMAIL, "support@slip.app")
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Slip App Support")
            putExtra(android.content.Intent.EXTRA_TEXT, "I need help with: $title")
        }
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Handle case where email app is not available
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
