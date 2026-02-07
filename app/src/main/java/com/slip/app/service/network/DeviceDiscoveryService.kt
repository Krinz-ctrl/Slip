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
import java.io.IOException
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

/**
 * Service for discovering Slip devices on the local network
 */
class DeviceDiscoveryService(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceDiscoveryService"
        
        // mDNS service type and name
        private const val SERVICE_TYPE = "_slip._tcp.local."
        private const val SERVICE_NAME = "Slip"
        
        // Default port for Slip service
        private const val DEFAULT_PORT = 4242
        
        // Discovery interval
        private const val DISCOVERY_INTERVAL_MS = 5000L
        
        // Device timeout
        private const val DEVICE_TIMEOUT_MS = 15000L
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var jmdns: JmDNS? = null
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
        Log.d(TAG, "Starting device discovery")
        
        if (_isDiscovering.value) {
            Log.w(TAG, "Discovery already running")
            return
        }
        
        try {
            // Initialize mDNS
            initializeMdns()
            
            // Start discovery job
            discoveryJob = serviceScope.launch {
                _isDiscovering.value = true
                
                while (_isDiscovering.value) {
                    try {
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
            
            Log.d(TAG, "Device discovery started successfully")
            
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
        
        try {
            jmdns?.close()
            jmdns = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing mDNS", e)
        }
        
        discoveredDevices.clear()
        _devices.value = emptyList()
    }
    
    /**
     * Initialize mDNS service
     */
    private fun initializeMdns() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val lock = wifi.createMulticastLock("SlipDiscoveryLock")
            lock.acquire()
            
            jmdns = JmDNS.create()
            
            // Add service listener
            val listener = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    Log.d(TAG, "Service added: ${event.name}")
                    // Request service info
                    jmdns?.requestServiceInfo(event.type, event.name, 1)
                }
                
                override fun serviceRemoved(event: ServiceEvent) {
                    Log.d(TAG, "Service removed: ${event.name}")
                    removeDevice(event.name)
                }
                
                override fun serviceResolved(event: ServiceEvent) {
                    Log.d(TAG, "Service resolved: ${event.name}")
                    val device = createDeviceFromServiceEvent(event)
                    device?.let { addDevice(it) }
                }
            }
            
            jmdns?.addServiceListener(SERVICE_TYPE, listener)
            
            // Also register our own service
            registerOwnService()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize mDNS", e)
            throw e
        }
    }
    
    /**
     * Register this device as a Slip service
     */
    private fun registerOwnService() {
        try {
            val serviceInfo = javax.jmdns.ServiceInfo.create(
                SERVICE_TYPE,
                SERVICE_NAME,
                DEFAULT_PORT,
                "Slip file transfer service"
            )
            
            jmdns?.registerService(serviceInfo)
            Log.d(TAG, "Registered own service")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register own service", e)
        }
    }
    
    /**
     * Create DiscoveredDevice from ServiceEvent
     */
    private fun createDeviceFromServiceEvent(event: ServiceEvent): DiscoveredDevice? {
        return try {
            val info = event.info
            val inetAddress = info.inetAddresses.firstOrNull()
            
            if (inetAddress != null) {
                val deviceInfo = DeviceInfo(
                    appName = "Slip",
                    appVersion = "1.0",
                    deviceType = DeviceType.UNKNOWN
                )
                
                DiscoveredDevice(
                    id = event.name,
                    name = event.name,
                    ipAddress = inetAddress,
                    port = info.port,
                    deviceInfo = deviceInfo
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create device from service event", e)
            null
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
     * Remove discovered device
     */
    private fun removeDevice(serviceName: String) {
        val removed = discoveredDevices.remove(serviceName)
        if (removed != null) {
            Log.d(TAG, "Device removed: ${removed.name}")
        }
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
        
        // Request new service info
        jmdns?.requestServiceInfo(SERVICE_TYPE, SERVICE_NAME, 1)
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

/**
 * Discovery statistics
 */
data class DiscoveryStats(
    val isDiscovering: Boolean,
    val deviceCount: Int,
    val onlineDevices: Int
)
