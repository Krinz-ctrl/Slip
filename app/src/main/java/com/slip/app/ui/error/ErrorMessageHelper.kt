package com.slip.app.ui.error

import android.content.Context
import com.slip.app.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.FileNotFoundException
import java.io.IOException
import java.security.NoSuchAlgorithmException

/**
 * Utility class for creating user-friendly error messages
 */
object ErrorMessageHelper {
    
    /**
     * Get user-friendly error message based on exception
     */
    fun getErrorMessage(exception: Throwable, context: Context): String {
        return when (exception) {
            is NetworkException -> getNetworkErrorMessage(exception, context)
            is FileException -> getFileErrorMessage(exception, context)
            is TransferException -> getTransferErrorMessage(exception, context)
            is PermissionException -> getPermissionErrorMessage(exception, context)
            else -> getGenericErrorMessage(exception, context)
        }
    }
    
    /**
     * Get network-related error messages
     */
    private fun getNetworkErrorMessage(exception: Throwable, context: Context): String {
        return when (exception) {
            is UnknownHostException -> context.getString(R.string.error_host_not_found)
            is ConnectException -> context.getString(R.string.error_connection_failed)
            is SocketTimeoutException -> context.getString(R.string.error_connection_timeout)
            else -> context.getString(R.string.error_network_generic, exception.message)
        }
    }
    
    /**
     * Get file-related error messages
     */
    private fun getFileErrorMessage(exception: Throwable, context: Context): String {
        return when (exception) {
            is FileNotFoundException -> context.getString(R.string.error_file_not_found)
            is SecurityException -> context.getString(R.string.error_file_access_denied)
            is IOException -> context.getString(R.string.error_file_io_error, exception.message)
            else -> context.getString(R.string.error_file_generic, exception.message)
        }
    }
    
    /**
     * Get transfer-related error messages
     */
    private fun getTransferErrorMessage(exception: Throwable, context: Context): String {
        return when (exception) {
            is TransferException.TransferCancelledException -> context.getString(R.string.error_transfer_cancelled)
            is TransferException.TransferPausedException -> context.getString(R.string.error_transfer_paused)
            is TransferException.TransferFailedException -> context.getString(R.string.error_transfer_failed, exception.message)
            is TransferException.CorruptedException -> context.getString(R.string.error_transfer_corrupted)
            is TransferException.InsufficientStorageException -> context.getString(R.string.error_insufficient_storage)
            else -> context.getString(R.string.error_transfer_generic, exception.message)
        }
    }
    
    /**
     * Get permission-related error messages
     */
    private fun getPermissionErrorMessage(exception: Throwable, context: Context): String {
        return when (exception) {
            is SecurityException -> context.getString(R.string.error_permission_denied)
            else -> context.getString(R.string.error_permission_generic, exception.message)
        }
    }
    
    /**
     * Get generic error message
     */
    private fun getGenericErrorMessage(exception: Throwable, context: Context): String {
        val message = exception.message ?: "Unknown error"
        return context.getString(R.string.error_generic, message)
    }
    
    /**
     * Get troubleshooting tips for the error
     */
    fun getTroubleshootingTips(exception: Throwable, context: Context): List<String> {
        return when (exception) {
            is NetworkException -> getNetworkTroubleshootingTips(context)
            is FileException -> getFileTroubleshootingTips(context)
            is TransferException -> getTransferTroubleshootingTips(exception, context)
            is PermissionException -> getPermissionTroubleshootingTips(context)
            else -> getGenericTroubleshootingTips(context)
        }
    }
    
    /**
     * Get network troubleshooting tips
     */
    private fun getNetworkTroubleshootingTips(context: Context): List<String> {
        return listOf(
            context.getString(R.string.tip_check_wifi_connection),
            context.getString(R.string.tip_verify_device_online),
            context.getString(R.string.try_different_network),
            context.getString(R.string.tip_restart_router),
            context.getString(R.string.tip_check_firewall)
        )
    }
    
    /**
     * Get file troubleshooting tips
     */
    private fun getFileTroubleshootingTips(context: Context): List<String> {
        return listOf(
            context.getString(R.string.tip_check_file_exists),
            context.getString(R.string.tip_verify_file_permissions),
            context.getString(R.string.tip_check_storage_space),
            context.getString(R.string.tip_try_different_file),
            context.getString(R.string.tip_restart_app)
        )
    }
    
    /**
     * Get transfer troubleshooting tips
     */
    private fun getTransferTroubleshootingTips(exception: Throwable, context: Context): List<String> {
        val tips = mutableListOf<String>()
        
        tips.add(context.getString(R.string.tip_check_network_stability))
        tips.add(context.getString(R.string.tip_verify_both_devices_online))
        tips.add(context.getString(R.string.tip_restart_transfer))
        
        if (exception is TransferException.CorruptedException) {
            tips.add(context.getString(R.string.tip_verify_file_integrity))
            tips.add(context.getString(R.string.tip_retransfer_file))
        }
        
        if (exception is TransferException.InsufficientStorageException) {
            tips.add(context.getString(R.string.tip_free_up_storage))
            tips.add(context.getString(R.string.tip_change_save_location))
        }
        
        return tips
    }
    
    /**
     * Get permission troubleshooting tips
     */
    private fun getPermissionTroubleshootingTips(context: Context): List<String> {
        return listOf(
            context.getString(R.string.tip_grant_permissions),
            context.getString(R.string.tip_check_app_settings),
            context.getString(R.string.tip_restart_app_after_permissions),
            context.getString(R.string.tip_check_android_permissions)
        )
    }
    
    /**
     * Get generic troubleshooting tips
     */
    private fun getGenericTroubleshootingTips(context: Context): List<String> {
        return listOf(
            context.getString(R.string.tip_restart_app),
            context.getString(R.string.tip_check_network),
            context.getString(R.string.tip_free_storage),
            context.getString(R.string.tip_update_app),
            context.getString(R.string.tip_contact_support)
        )
    }
    
    /**
     * Get suggested actions for the error
     */
    fun getSuggestedActions(exception: Throwable, context: Context): List<String> {
        return when (exception) {
            is NetworkException -> listOf(
                context.getString(R.string.action_retry_transfer),
                context.getString(R.string.action_check_network),
                context.getString(R.string.action_change_network)
            )
            is FileException -> listOf(
                context.getString(R.string.action_select_different_file),
                context.getString(R.string.action_check_permissions),
                context.getString(R.string.action_free_storage)
            )
            is TransferException -> listOf(
                context.getString(R.string.action_retry_transfer),
                context.getString(R.string.action_resume_transfer),
                context.getString(R.string.action_restart_app)
            )
            is PermissionException -> listOf(
                context.getString(R.string.action_grant_permissions),
                context.getString(R.string.action_open_settings),
                context.getString(R.string.action_restart_app)
            )
            else -> listOf(
                context.getString(R.string.action_retry),
                context.getString(R.string.action_restart_app),
                context.getString(R.string.action_contact_support)
            )
        }
    }
}

/**
 * Custom exception types for better error handling
 */
sealed class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class HostNotFoundException(message: String, cause: Throwable? = null) : NetworkException(message, cause)
    class ConnectionFailedException(message: String, cause: Throwable? = null) : NetworkException(message, cause)
    class TimeoutException(message: String, cause: Throwable? = null) : NetworkException(message, cause)
}

sealed class FileException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FileNotFoundException(message: String, cause: Throwable? = null) : FileException(message, cause)
    class AccessDeniedException(message: String, cause: Throwable? = null) : FileException(message, cause)
    class IOException(message: String, cause: Throwable? = null) : FileException(message, cause)
}

sealed class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TransferCancelledException(message: String = "Transfer was cancelled") : TransferException(message)
    class TransferPausedException(message: String = "Transfer was paused") : TransferException(message)
    class TransferFailedException(message: String, cause: Throwable? = null) : TransferException(message, cause)
    class CorruptedException(message: String, cause: Throwable? = null) : TransferException(message, cause)
    class InsufficientStorageException(message: String, cause: Throwable? = null) : TransferException(message)
}

sealed class PermissionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDeniedException(message: String, cause: Throwable? = null) : PermissionException(message)
    class PermissionRequiredException(message: String, cause: Throwable? = null) : PermissionException(message)
}
