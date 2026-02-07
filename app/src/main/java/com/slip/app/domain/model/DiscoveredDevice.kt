package com.slip.app.domain.model

import java.net.InetAddress

/**
 * Represents a discovered device on the LAN
 */
data class DiscoveredDevice(
    val id: String,
    val name: String,
    val ipAddress: InetAddress,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val deviceInfo: DeviceInfo? = null,
    val isOnline: Boolean = true
) {
    /**
     * Check if device is still considered online (within timeout)
     */
    fun isRecentlySeen(timeoutMs: Long = 5000): Boolean {
        return (System.currentTimeMillis() - lastSeen) < timeoutMs
    }
    
    /**
     * Get display address
     */
    fun getDisplayAddress(): String {
        return "${ipAddress.hostAddress}:$port"
    }
}

/**
 * Device information metadata
 */
data class DeviceInfo(
    val appName: String = "Slip",
    val appVersion: String = "1.0",
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val osVersion: String? = null,
    val supportedTransferTypes: List<TransferType> = emptyList()
)

/**
 * Device type enum
 */
enum class DeviceType {
    PHONE,
    TABLET,
    DESKTOP,
    LAPTOP,
    UNKNOWN
}

/**
 * Transfer type enum
 */
enum class TransferType {
    FILE,
    FOLDER,
    MULTIPLE_FILES
}
