package us.bergnet.oversight.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import us.bergnet.oversight.data.store.OverlayStateStore

class ZeroconfAdvertiser(private val context: Context) {

    companion object {
        private const val TAG = "ZeroconfAdvertiser"
        private const val SERVICE_TYPE = "_tvoverlay._tcp"
        private const val VERSION = "1.0.0"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var currentListener: NsdManager.RegistrationListener? = null

    fun register(deviceName: String, port: Int, deviceId: String) {
        unregister()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("deviceName", deviceName)
            setAttribute("port", port.toString())
            setAttribute("version", VERSION)
            setAttribute("deviceId", deviceId)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: errorCode=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: errorCode=$errorCode")
            }
        }

        try {
            currentListener = listener
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            Log.d(TAG, "Registering mDNS: name=$deviceName, port=$port, deviceId=$deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register mDNS service", e)
            currentListener = null
        }
    }

    fun unregister() {
        currentListener?.let { listener ->
            try {
                nsdManager.unregisterService(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister mDNS service", e)
            }
            currentListener = null
        }
    }

    /**
     * Observe settings changes and re-register when deviceName or port changes.
     */
    fun observeSettingsChanges(scope: CoroutineScope) {
        scope.launch {
            OverlayStateStore.infoValues
                .map { Pair(it.settings?.deviceName, it.settings?.remotePort) }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { (_, _) ->
                    if (currentListener != null) {
                        val name = OverlayStateStore.getDeviceName()
                        val port = OverlayStateStore.getRemotePort()
                        val id = OverlayStateStore.deviceId.value
                        Log.d(TAG, "Settings changed, re-registering: name=$name, port=$port")
                        register(name, port, id)
                    }
                }
        }
    }
}
