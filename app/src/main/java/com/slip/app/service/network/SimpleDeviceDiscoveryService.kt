package com.slip.app.service.network

import android.content.Context
import android.util.Log
import com.slip.app.domain.model.DiscoveredDevice
import com.slip.app.domain.model.DeviceInfo
import com.slip.app.domain.model.DeviceType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple device discovery service using network scanning
 * (Fallback when jMDNS is not available)
 */
class SimpleDeviceDiscoveryService(private val context: Context) {
    
    companion object {
        private const val TAG = "SimpleDeviceDiscovery"
        
        // Default port for Slip service
        private const val DEFAULT_PORT = 4242
        
        // Discovery interval
        private const val DISCOVERY_INTERVAL_MS = 10000L
        
        // Device timeout
        private const val DEVICE_TIMEOUT_MS = 30000L
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null
    
    // Discovered devices cache
    private val discoveredDevices = ConcurrentHashMap<String, DiscoveredDevice>()
    
    // State flows
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()
    
    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()
    
    /**
     * Start device discovery
     */
    fun startDiscovery() {
        Log.d(TAG, "Starting simple device discovery")
        
        if (_isDiscovering.value) {
            Log.w(TAG, "Discovery already running")
            return
        }
        
        try {
            // Start discovery job
            discoveryJob = serviceScope.launch {
                _isDiscovering.value = true
                
                while (_isDiscovering.value) {
                    try {
                        // Scan network for devices
                        scanNetwork()
                        
                        // Clean up stale devices
                        cleanupStaleDevices()
                        
                        // Update devices flow
                        _devices.value = discoveredDevices.values.toList()
                        
                        delay(DISCOVERY_INTERVAL_MS)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in discovery loop", e)
                        _discoveryError.value = "Discovery error: ${e.message}"
                    }
                }
            }
            
            Log.d(TAG, "Simple device discovery started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _discoveryError.value = "Failed to start discovery: ${e.message}"
            stopDiscovery()
        }
    }
    
    /**
     * Stop device discovery
     */
    fun stopDiscovery() {
        Log.d(TAG, "Stopping device discovery")
        
        _isDiscovering.value = false
        discoveryJob?.cancel()
        discoveryJob = null
        
        discoveredDevices.clear()
        _devices.value = emptyList()
    }
    
    /**
     * Scan network for devices
     */
    private suspend fun scanNetwork() {
        try {
            // Get local network interfaces
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { !it.isLoopback && it.isUp }
                .toList()
            
            for (networkInterface in networkInterfaces) {
                try {
                    // Get IP addresses for this interface
                    val addresses = networkInterface.inetAddresses
                        .asSequence()
                        .filter { !it.isLoopback && it is Inet4Address }
                        .toList()
                    
                    for (address in addresses) {
                        val subnet = getSubnet(address)
                        if (subnet != null) {
                            scanSubnet(subnet)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning interface ${networkInterface.name}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning network", e)
        }
    }
    
    /**
     * Get subnet for an IP address
     */
    private fun getSubnet(address: InetAddress): String? {
        val hostAddress = address.hostAddress ?: return null
        val parts = hostAddress.split(".")
        if (parts.size != 4) return null
        
        // Assume /24 subnet (most common)
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }
    
    /**
     * Scan a subnet for active devices
     */
    private suspend fun scanSubnet(subnet: String) {
        val scanJobs = mutableListOf<Job>()
        
        // Scan common IP range (1-254)
        for (i in 1..254) {
            val ip = "$subnet.$i"
            
            val job = serviceScope.async {
                try {
                    if (isDeviceReachable(ip)) {
                        checkSlipService(ip)
                    }
                } catch (e: Exception) {
                    // Ignore individual scan errors
                }
            }
            
            scanJobs.add(job)
            
            // Limit concurrent scans
            if (scanJobs.size >= 10) {
                scanJobs.awaitAll()
                scanJobs.clear()
            }
        }
        
        // Wait for remaining jobs
        scanJobs.awaitAll()
    }
    
    /**
     * Check if a device is reachable
     */
    private suspend fun isDeviceReachable(ip: String): Boolean {
        return try {
            withTimeoutOrNull(1000) {
                val address = InetAddress.getByName(ip)
                address.isReachable(1000)
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if a device is running Slip service
     */
    private suspend fun checkSlipService(ip: String) {
        try {
            withTimeoutOrNull(2000) {
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(ip, DEFAULT_PORT), 2000)
                    
                    if (socket.isConnected) {
                        // Device is running Slip service
                        val device = DiscoveredDevice(
                            id = "device_$ip",
                            name = "Slip Device ($ip)",
                            ipAddress = InetAddress.getByName(ip),
                            port = DEFAULT_PORT,
                            deviceInfo = DeviceInfo(
                                appName = "Slip",
                                appVersion = "1.0",
                                deviceType = DeviceType.UNKNOWN
                            )
                        )
                        
                        addDevice(device)
                    }
                } finally {
                    socket.close()
                }
            }
        } catch (e: Exception) {
            // Device not running Slip or not reachable
        }
    }
    
    /**
     * Add discovered device
     */
    private fun addDevice(device: DiscoveredDevice) {
        val existingDevice = discoveredDevices[device.id]
        
        if (existingDevice == null) {
            Log.d(TAG, "New device discovered: ${device.name}")
        } else {
            // Update last seen time
            device = device.copy(lastSeen = System.currentTimeMillis())
        }
        
        discoveredDevices[device.id] = device
    }
    
    /**
     * Clean up stale devices
     */
    private fun cleanupStaleDevices() {
        val now = System.currentTimeMillis()
        val staleDevices = discoveredDevices.filter { (_, device) ->
            !device.isRecentlySeen(DEVICE_TIMEOUT_MS)
        }
        
        staleDevices.forEach { (id, device) ->
            discoveredDevices.remove(id)
            Log.d(TAG, "Removed stale device: ${device.name}")
        }
    }
    
    /**
     * Get device by ID
     */
    fun getDeviceById(id: String): DiscoveredDevice? {
        return discoveredDevices[id]
    }
    
    /**
     * Refresh discovery
     */
    fun refresh() {
        Log.d(TAG, "Refreshing discovery")
        discoveredDevices.clear()
        _devices.value = emptyList()
    }
    
    /**
     * Check if discovery is active
     */
    fun isActive(): Boolean {
        return _isDiscovering.value
    }
    
    /**
     * Get discovery statistics
     */
    fun getDiscoveryStats(): DiscoveryStats {
        return DiscoveryStats(
            isDiscovering = _isDiscovering.value,
            deviceCount = discoveredDevices.size,
            onlineDevices = discoveredDevices.values.count { it.isRecentlySeen() }
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopDiscovery()
        serviceScope.cancel()
    }
}
