package com.aqlanlab.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {
  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  val isOnline: Flow<Boolean> = callbackFlow {
    val cm = connectivityManager
    if (cm == null) {
      trySend(true)
      awaitClose { }
      return@callbackFlow
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        trySend(true)
      }

      override fun onLost(network: Network) {
        // FIX: `onLost` fires for a single network even when another one is still up
        // (e.g. WiFi -> cellular handover). Re-query the ACTUAL state instead of
        // blindly reporting offline.
        trySend(isCurrentlyConnected())
      }

      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) {
        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        trySend(hasInternet)
      }
    }

    try {
      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

      cm.registerNetworkCallback(request, callback)
    } catch (e: Exception) {
      trySend(true)
    }

    // Initial value
    trySend(isCurrentlyConnected())

    awaitClose {
      try {
        cm.unregisterNetworkCallback(callback)
      } catch (e: Exception) {
        // Ignore
      }
    }
  }.distinctUntilChanged().conflate()

  fun isCurrentlyConnected(): Boolean {
    val cm = connectivityManager ?: return true
    return try {
      val activeNetwork = cm.activeNetwork ?: return false
      val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
      // FIX: previously any exception returned `true` (fail-open). An unreachable
      // ConnectivityManager is now reported as offline so the UI doesn't attempt
      // cloud operations that are guaranteed to fail.
      false
    }
  }
}
