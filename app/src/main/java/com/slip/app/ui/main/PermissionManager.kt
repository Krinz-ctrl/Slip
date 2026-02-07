package com.slip.app.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class PermissionManager {
    
    companion object {
        const val STORAGE_PERMISSION_CODE = 1001
        
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
         * Get permission denied message
         */
        fun getPermissionDeniedMessage(): String {
            return "Storage permission was denied. Slip cannot access your files without this permission. Please enable it in Settings to use file sharing features."
        }
        
        /**
         * Get permission permanently denied message
         */
        fun getPermissionPermanentlyDeniedMessage(): String {
            return "Storage permission was permanently denied. Please enable it in Settings > Apps > Slip > Permissions to use file sharing features."
        }
    }
}
