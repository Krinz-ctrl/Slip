package com.slip.app.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings manager for app preferences
 */
class SettingsManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "slip_settings"
        private const val KEY_TRANSFER_SPEED_LIMIT = "transfer_speed_limit"
        private const val KEY_CHUNK_SIZE = "chunk_size"
        private const val KEY_AUTO_RESUME = "auto_resume"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_SHOW_PROGRESS_NOTIFICATIONS = "show_progress_notifications"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SAVE_LOCATION = "save_location"
        private const val KEY_MAX_CONCURRENT_TRANSFERS = "max_concurrent_transfers"
        private const val KEY_CLEANUP_OLD_DAYS = "cleanup_old_days"
        private const val KEY_ENABLE_CHUNK_VERIFICATION = "enable_chunk_verification"
        private const val KEY_RETRY_FAILED_TRANSFERS = "retry_failed_transfers"
        private const val KEY_COMPRESS_TRANSFERS = "compress_transfers"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences = 
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.createDeviceProtectedSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } else {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    
    // Transfer speed limit
    private val _transferSpeedLimit = MutableStateFlow(TransferSpeedLimit.UNLIMITED)
    val transferSpeedLimit: StateFlow<TransferSpeedLimit> = _transferSpeedLimit.asStateFlow()
    
    // Chunk size preference
    private val _chunkSize = MutableStateFlow(ChunkSize.AUTO)
    val chunkSize: StateFlow<ChunkSize> = _chunkSize.asStateFlow()
    
    // Auto resume setting
    private val _autoResume = MutableStateFlow(true)
    val autoResume: StateFlow<Boolean> = _autoResume.asStateFlow()
    
    // Notification settings
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()
    
    private val _showProgressNotifications = MutableStateFlow(true)
    val showProgressNotifications: StateFlow<Boolean> = _showProgressNotifications.asStateFlow()
    
    // Screen settings
    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()
    
    // Storage settings
    private val _saveLocation = MutableStateFlow("/storage/emulated/0/Download/Slip")
    val saveLocation: StateFlow<String> = _saveLocation.asStateFlow()
    
    // Advanced settings
    private val _maxConcurrentTransfers = MutableStateFlow(3)
    val maxConcurrentTransfers: StateFlow<Int> = _maxConcurrentTransfers.asStateFlow()
    
    private val _cleanupOldDays = MutableStateFlow(7)
    val cleanupOldDays: StateFlow<Int> = _cleanupOldDays.asStateFlow()
    
    private val _enableChunkVerification = MutableStateFlow(true)
    val enableChunkVerification: StateFlow<Boolean> = _enableChunkVerification.asStateFlow()
    
    private val _retryFailedTransfers = MutableStateFlow(true)
    val retryFailedTransfers: StateFlow<Boolean> = _retryFailedTransfers.asStateFlow()
    
    private val _compressTransfers = MutableStateFlow(false)
    val compressTransfers: StateFlow<Boolean> = _compressTransfers.asStateFlow()
    
    init {
        loadSettings()
    }
    
    /**
     * Load all settings from SharedPreferences
     */
    private fun loadSettings() {
        _transferSpeedLimit.value = TransferSpeedLimit.fromValue(
            sharedPreferences.getString(KEY_TRANSFER_SPEED_LIMIT, null)
        )
        
        _chunkSize.value = ChunkSize.fromValue(
            sharedPreferences.getString(KEY_CHUNK_SIZE, null)
        )
        
        _autoResume.value = sharedPreferences.getBoolean(KEY_AUTO_RESUME, true)
        _notificationsEnabled.value = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        _showProgressNotifications.value = sharedPreferences.getBoolean(KEY_SHOW_PROGRESS_NOTIFICATIONS, true)
        _keepScreenOn.value = sharedPreferences.getBoolean(KEY_KEEP_SCREEN_ON, false)
        _saveLocation.value = sharedPreferences.getString(KEY_SAVE_LOCATION, "/storage/emulated/0/Download/Slip") ?: "/storage/emulated/0/Download/Slip"
        _maxConcurrentTransfers.value = sharedPreferences.getInt(KEY_MAX_CONCURRENT_TRANSFERS, 3)
        _cleanupOldDays.value = sharedPreferences.getInt(KEY_CLEANUP_OLD_DAYS, 7)
        _enableChunkVerification.value = sharedPreferences.getBoolean(KEY_ENABLE_CHUNK_VERIFICATION, true)
        _retryFailedTransfers.value = sharedPreferences.getBoolean(KEY_RETRY_FAILED_TRANSFERS, true)
        _compressTransfers.value = sharedPreferences.getBoolean(KEY_COMPRESS_TRANSFERS, false)
    }
    
    /**
     * Save a setting
     */
    private fun saveSetting(key: String, value: Any) {
        with(sharedPreferences.edit()) {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
            }
            apply()
        }
    }
    
    /**
     * Set transfer speed limit
     */
    fun setTransferSpeedLimit(limit: TransferSpeedLimit) {
        _transferSpeedLimit.value = limit
        saveSetting(KEY_TRANSFER_SPEED_LIMIT, limit.value)
    }
    
    /**
     * Set chunk size preference
     */
    fun setChunkSize(size: ChunkSize) {
        _chunkSize.value = size
        saveSetting(KEY_CHUNK_SIZE, size.value)
    }
    
    /**
     * Set auto resume setting
     */
    fun setAutoResume(enabled: Boolean) {
        _autoResume.value = enabled
        saveSetting(KEY_AUTO_RESUME, enabled)
    }
    
    /**
     * Set notifications enabled
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        saveSetting(KEY_NOTIFICATIONS_ENABLED, enabled)
    }
    
    /**
     * Set show progress notifications
     */
    fun setShowProgressNotifications(enabled: Boolean) {
        _showProgressNotifications.value = enabled
        saveSetting(KEY_SHOW_PROGRESS_NOTIFICATIONS, enabled)
    }
    
    /**
     * Set keep screen on
     */
    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        saveSetting(KEY_KEEP_SCREEN_ON, enabled)
    }
    
    /**
     * Set save location
     */
    fun setSaveLocation(location: String) {
        _saveLocation.value = location
        saveSetting(KEY_SAVE_LOCATION, location)
    }
    
    /**
     * Set max concurrent transfers
     */
    fun setMaxConcurrentTransfers(max: Int) {
        _maxConcurrentTransfers.value = max
        saveSetting(KEY_MAX_CONCURRENT_TRANSFERS, max)
    }
    
    /**
     * Set cleanup old days
     */
    fun setCleanupOldDays(days: Int) {
        _cleanupOldDays.value = days
        saveSetting(KEY_CLEANUP_OLD_DAYS, days)
    }
    
    /**
     * Set enable chunk verification
     */
    fun setEnableChunkVerification(enabled: Boolean) {
        _enableChunkVerification.value = enabled
        saveSetting(KEY_ENABLE_CHUNK_VERIFICATION, enabled)
    }
    
    /**
     * Set retry failed transfers
     */
    fun setRetryFailedTransfers(enabled: Boolean) {
        _retryFailedTransfers.value = enabled
        saveSetting(KEY_RETRY_FAILED_TRANSFERS, enabled)
    }
    
    /**
     * Set compress transfers
     */
    fun setCompressTransfers(enabled: Boolean) {
        _compressTransfers.value = enabled
        saveSetting(KEY_COMPRESS_TRANSFERS, enabled)
    }
    
    /**
     * Get all settings as a data class
     */
    fun getAllSettings(): AppSettings {
        return AppSettings(
            transferSpeedLimit = _transferSpeedLimit.value,
            chunkSize = _chunkSize.value,
            autoResume = _autoResume.value,
            notificationsEnabled = _notificationsEnabled.value,
            showProgressNotifications = _showProgressNotifications.value,
            keepScreenOn = _keepScreenOn.value,
            saveLocation = _saveLocation.value,
            maxConcurrentTransfers = _maxConcurrentTransfers.value,
            cleanupOldDays = _cleanupOldDays.value,
            enableChunkVerification = _enableChunkVerification.value,
            retryFailedTransfers = _retryFailedTransfers.value,
            compressTransfers = _compressTransfers.value
        )
    }
}

/**
 * Transfer speed limits
 */
enum class TransferSpeedLimit(val value: String, val displaySpeed: Int) {
    UNLIMITED("unlimited", Int.MAX_VALUE),
    SLOW("slow", 512), // 512 KB/s
    MEDIUM("medium", 2048), // 2 MB/s
    FAST("fast", 10240), // 10 MB/s
    CUSTOM("custom", 0);
    
    companion object {
        fun fromValue(value: String?): TransferSpeedLimit {
            return values.find { it.value == value } ?: UNLIMITED
        }
    }
}

/**
 * Chunk size preferences
 */
enum class ChunkSize(val value: String, val sizeBytes: Int) {
    AUTO("auto", 0),
    SMALL("small", 256 * 1024), // 256 KB
    MEDIUM("medium", 1024 * 1024), // 1 MB
    LARGE("large", 2 * 1024 * 1024), // 2 MB
    EXTRA_LARGE("extra_large", 4 * 1024 * 1024); // 4 MB
    
    companion object {
        fun fromValue(value: String?): ChunkSize {
            return values.find { it.value == value } ?: AUTO
        }
    }
}

/**
 * App settings data class
 */
data class AppSettings(
    val transferSpeedLimit: TransferSpeedLimit,
    val chunkSize: ChunkSize,
    val autoResume: Boolean,
    val notificationsEnabled: Boolean,
    val showProgressNotifications: Boolean,
    val keepScreenOn: Boolean,
    val saveLocation: String,
    val maxConcurrentTransfers: Int,
    val cleanupOldDays: Int,
    val enableChunkVerification: Boolean,
    val retryFailedTransfers: Boolean,
    val compressTransfers: Boolean
)
