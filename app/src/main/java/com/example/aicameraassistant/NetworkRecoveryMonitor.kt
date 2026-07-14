package com.example.aicameraassistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null
    private var lastSignature = ""

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = manager
        _networkAvailable.value = manager.activeNetwork
            ?.let(manager::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(manager, "available")
            override fun onLost(network: Network) = publish(manager, "lost")
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                publish(manager, "capabilities")
            }
        }
        networkCallback = callback
        manager.registerDefaultNetworkCallback(callback)
        initialized = true
    }

    @Synchronized
    fun shutdown() {
        debounceJob?.cancel()
        debounceJob = null
        networkCallback?.let { callback ->
            runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        connectivityManager = null
        initialized = false
        lastSignature = ""
    }

    fun notifyForeground() {
        _events.tryEmit(System.currentTimeMillis())
    }

    private fun publish(manager: ConnectivityManager, reason: String) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(750L)
            val network = manager.activeNetwork
            val capabilities = network?.let(manager::getNetworkCapabilities)
            val validated = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ) == true
            val transports = buildList {
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("wifi")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("cellular")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("ethernet")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("vpn")
            }.joinToString("+").ifBlank { "none" }
            val signature = "$validated:$transports:${network?.hashCode() ?: 0}"
            val changed = signature != lastSignature
            lastSignature = signature
            _networkAvailable.value = validated
            if (changed) _events.tryEmit(System.currentTimeMillis())
            if (BuildConfig.DEBUG && Log.isLoggable("NETWORK_RECOVERY", Log.DEBUG)) {
                Log.d("NETWORK_RECOVERY", "$reason validated=$validated transport=$transports")
            }
        }
    }
}
