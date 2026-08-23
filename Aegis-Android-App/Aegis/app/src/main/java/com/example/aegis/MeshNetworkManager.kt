package com.example.aegis

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class MeshNetworkManager(
    private val context: Context,
    private val onNodeCountChanged: (Int) -> Unit,
    private val onRelayedSosReceived: (name: String, phone: String, lat: Double, lng: Double, notes: String) -> Unit,
    private val onAckReceived: (msgId: String) -> Unit
) {

    private val DASHBOARD_API_URL = "https://aegis-dashboard-t60k.onrender.com/api/sos-alert"
    private val localDeviceName = android.os.Build.MODEL.ifEmpty { "AegisNode" }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_STAR
    private val serviceId = "com.example.aegis.MESH_GRID"

    private val connectedEndpoints = mutableSetOf<String>()
    private val connectingEndpoints = mutableSetOf<String>()
    
    private val processedSosIds = mutableSetOf<String>()
    private val processedAckIds = mutableSetOf<String>()
    
    private val activeSosCache = ConcurrentHashMap<String, String>()

    fun startMesh() {
        startAdvertising()
        startDiscovering()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(localDeviceName, serviceId, connectionLifecycleCallback, options)
            .addOnSuccessListener { Log.d("AegisMesh", "Advertising initialized") }
    }

    private fun startDiscovering() {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, options)
            .addOnSuccessListener { Log.d("AegisMesh", "Discovery initialized") }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (!connectedEndpoints.contains(endpointId) && !connectingEndpoints.contains(endpointId)) {
                connectingEndpoints.add(endpointId)
                connectionsClient.requestConnection(localDeviceName, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener { connectingEndpoints.remove(endpointId) }
            }
        }
        override fun onEndpointLost(endpointId: String) {
            connectingEndpoints.remove(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                onNodeCountChanged(connectedEndpoints.size)

                if (activeSosCache.isNotEmpty()) {
                    activeSosCache.values.forEach { cachedSosJson ->
                        val payload = Payload.fromBytes(cachedSosJson.toByteArray())
                        connectionsClient.sendPayload(endpointId, payload)
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            connectingEndpoints.remove(endpointId)
            onNodeCountChanged(connectedEndpoints.size)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val jsonString = String(payload.asBytes()!!)
                parseAndProcessMeshMessage(jsonString, endpointId)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun parseAndProcessMeshMessage(jsonString: String, senderEndpointId: String?) {
        try {
            val json = JSONObject(jsonString)
            val messageType = json.optString("type", "SOS")
            val msgId = json.getString("msgId")

            if (messageType == "SOS") {
                if (processedSosIds.contains(msgId)) return

                processedSosIds.add(msgId)
                activeSosCache[msgId] = jsonString

                val name = json.getString("name")
                val phone = json.getString("phone")
                val lat = json.getDouble("lat")
                val lng = json.getDouble("lng")
                val notes = json.getString("notes")

                onRelayedSosReceived(name, phone, lat, lng, notes)
                relayPayloadToOthers(jsonString, senderEndpointId)

                uploadToCloud(jsonString,
                    onSuccess = {
                        activeSosCache.remove(msgId)
                        broadcastAckBackToMesh(msgId)
                    },
                    onFailure = { }
                )
            } else if (messageType == "ACK") {
                if (processedAckIds.contains(msgId)) return

                processedAckIds.add(msgId)
                activeSosCache.remove(msgId)

                onAckReceived(msgId)
                relayPayloadToOthers(jsonString, senderEndpointId)
            }
        } catch (e: Exception) {
            Log.e("AegisMesh", "Error parsing mesh packet", e)
        }
    }

    fun broadcastLocalSos(
        name: String,
        phone: String,
        lat: Double,
        lng: Double,
        notes: String,
        priority: Int,
        msgId: String
    ) {
        processedSosIds.add(msgId)

        val jsonPayload = JSONObject().apply {
            put("type", "SOS")
            put("msgId", msgId)
            put("priority", priority) 
            put("name", name)
            put("phone", phone)
            put("lat", lat)
            put("lng", lng)
            put("notes", notes)
            put("gatewayNode", localDeviceName)
            put("mapLink", "https://www.google.com/maps?q=$lat,$lng")
        }.toString()

        activeSosCache[msgId] = jsonPayload

        val payload = Payload.fromBytes(jsonPayload.toByteArray())
        connectedEndpoints.forEach { endpoint ->
            connectionsClient.sendPayload(endpoint, payload)
        }

        uploadToCloud(jsonPayload,
            onSuccess = {
                activeSosCache.remove(msgId)
                onAckReceived(msgId)
            },
            onFailure = { }
        )
    }

    private fun broadcastAckBackToMesh(msgId: String) {
        val ackPayload = JSONObject().apply {
            put("type", "ACK")
            put("msgId", msgId)
            put("status", "DELIVERED_TO_CLOUD")
        }.toString()

        val payload = Payload.fromBytes(ackPayload.toByteArray())
        connectedEndpoints.forEach { endpoint ->
            connectionsClient.sendPayload(endpoint, payload)
        }
    }

    private fun relayPayloadToOthers(jsonString: String, senderEndpointId: String?) {
        val payload = Payload.fromBytes(jsonString.toByteArray())
        connectedEndpoints.forEach { endpoint ->
            if (endpoint != senderEndpointId) {
                connectionsClient.sendPayload(endpoint, payload)
            }
        }
    }

    private fun uploadToCloud(jsonString: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(DASHBOARD_API_URL)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.connectTimeout = 4000

                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(jsonString)
                writer.flush()
                writer.close()

                if (connection.responseCode == 200 || connection.responseCode == 201) {
                    onSuccess()
                } else {
                    onFailure()
                }
            } catch (e: Exception) {
                onFailure()
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}