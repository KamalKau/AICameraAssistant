package com.example.aicameraassistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkRecoveryMonitor {
    private val _events = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    private val _networkAvailable = MutableStateFlow(false)
    val networkAvailable = _networkAvailable.asStateFlow()

    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _networkAvailable.value = manager.activeNetwork
            ?.let(manager::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(true, "available")
            override fun onLost(network: Network) = publish(false, "lost")
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                publish(
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    "capabilities"
                )
            }
        })
        initialized = true
    }

    fun notifyForeground() {
        _events.tryEmit(System.currentTimeMillis())
    }

    private fun publish(available: Boolean, reason: String) {
        val changed = _networkAvailable.value != available
        _networkAvailable.value = available
        if (changed || available) _events.tryEmit(System.currentTimeMillis())
        if (Log.isLoggable("NETWORK_RECOVERY", Log.DEBUG)) {
            Log.d("NETWORK_RECOVERY", "$reason available=$available")
        }
    }
}
