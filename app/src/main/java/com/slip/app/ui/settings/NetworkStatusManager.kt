package com.slip.app.ui.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Network status manager for monitoring network connectivity
 */
class NetworkStatusManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkStatusManager"
        
        @Volatile
        private var INSTANCE: NetworkStatusManager? = null
        
        fun getInstance(context: Context): NetworkStatusManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkStatusManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    // Network status flows
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _networkType = MutableStateFlow(NetworkType.NONE)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()
    
    private val _connectionStrength = MutableStateFlow(ConnectionStrength.UNKNOWN)
    val connectionStrength: StateFlow<ConnectionStrength> = _connectionStrength.asStateFlow()
    
    private val _isWifiEnabled = MutableStateFlow(false)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()
    
    private val _networkSpeed = MutableStateFlow(NetworkSpeed.UNKNOWN)
    val networkSpeed: StateFlow<NetworkSpeed> = _networkSpeed.asStateFlow()
    
    /**
     * Start monitoring network status
     */
    fun startMonitoring() {
        updateNetworkStatus()
    }
    
    /**
     * Update network status
     */
    fun updateNetworkStatus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork
                activeNetwork?.let { network ->
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    updateNetworkCapabilities(capabilities)
                } ?: run {
                    // No active network
                    _isConnected.value = false
                    _networkType.value = NetworkType.NONE
                    _connectionStrength.value = ConnectionStrength.UNKNOWN
                }
            } else {
                // Fallback for older Android versions
                val networkInfo = connectivityManager.activeNetworkInfo
                updateLegacyNetworkInfo(networkInfo)
            }
            
            // Update WiFi status
            updateWifiStatus()
            
        } catch (e: Exception) {
            _isConnected.value = false
            _networkType.value = NetworkType.UNKNOWN
            _connectionStrength.value = ConnectionStrength.UNKNOWN
        }
    }
    
    /**
     * Update network capabilities (Android M+)
     */
    private fun updateNetworkCapabilities(capabilities: NetworkCapabilities?) {
        capabilities?.let { caps ->
            _isConnected.value = true
            
            // Determine network type
            _networkType.value = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                else -> NetworkType.UNKNOWN
            }
            
            // Determine connection strength
            _connectionStrength.value = when {
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> ConnectionStrength.UNMETERED
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) -> ConnectionStrength.VPN
                else -> ConnectionStrength.UNKNOWN
            }
            
            // Estimate network speed based on capabilities
            _networkSpeed.value = estimateNetworkSpeed(caps)
        }
    }
    
    /**
     * Update legacy network info (pre-Android M)
     */
    private fun updateLegacyNetworkInfo(networkInfo: android.net.NetworkInfo?) {
        networkInfo?.let { info ->
            _isConnected.value = info.isConnectedOrConnecting
            
            _networkType.value = when (info.type) {
                ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                ConnectivityManager.TYPE_MOBILE -> NetworkType.CELLULAR
                ConnectivityManager.TYPE_ETHERNET -> NetworkType.ETHERNET
                else -> NetworkType.UNKNOWN
            }
            
            // Get signal strength for WiFi
            if (info.type == ConnectivityManager.TYPE_WIFI) {
                val signalStrength = WifiManager.calculateSignalLevel(info.rssi, 5)
                _connectionStrength.value = when {
                    signalStrength >= 4 -> ConnectionStrength.EXCELLENT
                    signalStrength >= 3 -> ConnectionStrength.GOOD
                    signalStrength >= 2 -> ConnectionStrength.FAIR
                    signalStrength >= 1 -> ConnectionStrength.POOR
                    else -> ConnectionStrength.UNKNOWN
                }
            } else {
                _connectionStrength.value = ConnectionStrength.UNKNOWN
            }
        } ?: run {
            _isConnected.value = false
            _networkType.value = NetworkType.NONE
            _connectionStrength.value = ConnectionStrength.UNKNOWN
        }
    }
    
    /**
     * Update WiFi status
     */
    private fun updateWifiStatus() {
        try {
            _isWifiEnabled.value = wifiManager.isWifiEnabled
        } catch (e: Exception) {
            _isWifiEnabled.value = false
        }
    }
    
    /**
     * Estimate network speed based on capabilities
     */
    private fun estimateNetworkSpeed(capabilities: NetworkCapabilities): NetworkSpeed {
        return when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkSpeed.FAST
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkSpeed.MEDIUM
            else -> NetworkSpeed.UNKNOWN
        }
    }
    
    /**
     * Get network status summary
     */
    fun getNetworkStatus(): NetworkStatus {
        return NetworkStatus(
            isConnected = _isConnected.value,
            networkType = _networkType.value,
            connectionStrength = _connectionStrength.value,
            isWifiEnabled = _isWifiEnabled.value,
            networkSpeed = _networkSpeed.value
        )
    }
}

/**
 * Network types
 */
enum class NetworkType {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET,
    UNKNOWN
}

/**
 * Connection strength levels
 */
enum class ConnectionStrength {
    UNKNOWN,
    POOR,
    FAIR,
    GOOD,
    EXCELLENT,
    UNMETERED,
    VPN
}

/**
 * Network speed estimates
 */
enum class NetworkSpeed {
    UNKNOWN,
    SLOW,
    MEDIUM,
    FAST
}

/**
 * Network status data class
 */
data class NetworkStatus(
    val isConnected: Boolean,
    val networkType: NetworkType,
    val connectionStrength: ConnectionStrength,
    val isWifiEnabled: Boolean,
    val networkSpeed: NetworkSpeed
)
