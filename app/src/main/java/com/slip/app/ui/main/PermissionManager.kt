package com.slip.app.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class PermissionManager {
    
    companion object {
        const val STORAGE_PERMISSION_CODE = 1001
        const val NOTIFICATION_PERMISSION_CODE = 1002
        
        /**
         * Check if storage permissions are granted
         */
        fun hasStoragePermissions(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ uses READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO
                ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // Below Android 13 uses READ_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        /**
         * Check if notification permission is granted (Android 13+)
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Notifications don't require permission on older versions
            }
        }
        
        /**
         * Get the required storage permissions based on Android version
         */
        fun getStoragePermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        /**
         * Get notification permission if needed
         */
        fun getNotificationPermission(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
        }
        
        /**
         * Check if should show permission rationale
         */
        fun shouldShowRationale(activity: androidx.appcompat.app.AppCompatActivity, permission: String): Boolean {
            return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                activity, 
                permission
            )
        }
        
        /**
         * Get user-friendly permission rationale message
         */
        fun getPermissionRationaleMessage(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "Slip needs access to your photos, videos, and audio files to enable file sharing. Without these permissions, you won't be able to select files for transfer."
            } else {
                "Slip needs access to your device storage to enable file sharing. Without this permission, you won't be able to select files for transfer."
            }
        }
        
        /**
         * Get notification permission rationale message
         */
        fun getNotificationPermissionRationaleMessage(): String {
            return "Slip needs notification permission to show transfer progress in the background. Without notifications, you won't see when transfers complete or encounter errors."
        }
        
        /**
         * Get permission denied message
         */
        fun getPermissionDeniedMessage(): String {
            return "Storage permission was denied. Slip cannot access your files without this permission. Please enable it in Settings to use file sharing features."
        }
        
        /**
         * Get notification permission denied message
         */
        fun getNotificationPermissionDeniedMessage(): String {
            return "Notification permission was denied. Slip cannot show transfer progress without notifications. Please enable it in Settings to see transfer updates."
        }
        
        /**
         * Get permission permanently denied message
         */
        fun getPermissionPermanentlyDeniedMessage(): String {
            return "Storage permission was permanently denied. Please enable it in Settings > Apps > Slip > Permissions to use file sharing features."
        }
        
        /**
         * Get notification permission permanently denied message
         */
        fun getNotificationPermissionPermanentlyDeniedMessage(): String {
            return "Notification permission was permanently denied. Please enable it in Settings > Apps > Slip > Permissions to see transfer progress."
        }
    }
}
