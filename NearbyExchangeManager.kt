package com.dlink.app.exchange
import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.UUID
class NearbyExchangeManager(context:Context){
 companion object { const val SERVICE_ID="com.dlink.exchange" }
 private val client=Nearby.getConnectionsClient(context); private val strategy=Strategy.P2P_STAR
 private val discovery=object:EndpointDiscoveryCallback(){override fun onEndpointFound(id:String,info:DiscoveredEndpointInfo){};override fun onEndpointLost(id:String){}}
 fun startAdvertising(){client.startAdvertising(UUID.randomUUID().toString(),SERVICE_ID,object:ConnectionLifecycleCallback(){override fun onConnectionInitiated(id:String,info:ConnectionInfo){client.acceptConnection(id,object:PayloadCallback(){override fun onPayloadReceived(e:String,p:Payload){};override fun onPayloadTransferUpdate(e:String,u:PayloadTransferUpdate){}})};override fun onConnectionResult(id:String,r:ConnectionResolution){};override fun onDisconnected(id:String){}},AdvertisingOptions.Builder().setStrategy(strategy).build())}
 fun stopAdvertising()=client.stopAdvertising(); fun startDiscovery(){client.startDiscovery(SERVICE_ID,discovery,DiscoveryOptions.Builder().setStrategy(strategy).build())}; fun stopDiscovery()=client.stopDiscovery()
}
