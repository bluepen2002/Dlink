package com.dlink.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.dlink.app.data.ConnectionEntity
import com.dlink.app.data.DlinkDatabase
import com.dlink.app.data.ProfileEntity
import com.dlink.app.exchange.DlinkShareEnvelope
import com.dlink.app.exchange.DlinkSecureEnvelope
import com.dlink.app.exchange.NfcShareManager
import com.dlink.app.exchange.NearbyExchangeManager
import com.dlink.app.exchange.QrCodeManager
import com.dlink.app.exchange.QrScannerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var db: DlinkDatabase
    private lateinit var nearby: NearbyExchangeManager
    private lateinit var nfc: NfcShareManager
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val qrLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringExtra("dlink_payload")?.let { handleIncomingPayload(it, "QR") }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = DlinkDatabase.get(this)
        nearby = NearbyExchangeManager(this)
        nfc = NfcShareManager(this)
        nfc.setReceiver { payload -> nfc.clearPublishedPayload(); handleIncomingPayload(payload, "NFC") }
        nfc.setErrorReceiver { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        requestPermissions()
        setContent { DlinkApp(db, nearby, nfc) }
        handleNfcIntent(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleNfcIntent(intent) }
    override fun onDestroy() { nfc.disableReader(); nfc.clearPublishedPayload(); super.onDestroy() }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED || intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED || intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            // Phone-to-phone NFC uses HCE + IsoDep reader mode; this path remains for external NDEF tags.
            nfc.handleExternalNdef(intent.extras)
        }
    }

    private fun requestPermissions() {
        val p = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 31) { p += Manifest.permission.BLUETOOTH_SCAN; p += Manifest.permission.BLUETOOTH_CONNECT; p += Manifest.permission.BLUETOOTH_ADVERTISE } else p += Manifest.permission.ACCESS_FINE_LOCATION
        if (android.os.Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        p += Manifest.permission.CAMERA
        permissionLauncher.launch(p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray())
    }

    private fun handleIncomingPayload(raw: String, method: String) {
        if (raw.startsWith("dlink://secure?payload=")) {
            requestExchangeCode { code -> decodeAndSave(raw, method, code) }
        } else {
            try { decodeAndSave(raw, method, null) } catch (ex: Exception) { Toast.makeText(this, ex.message ?: "Invalid Dlink payload", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun requestExchangeCode(onSubmit: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            hint = "10-character code"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Enter Dlink exchange code")
            .setMessage("Enter the temporary code shown on the sender's phone. The encrypted profile cannot be opened without it.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("UNLOCK") { _, _ -> onSubmit(input.text.toString().trim()) }
            .show()
    }

    private fun decodeAndSave(raw: String, method: String, code: String?) {
        try {
            val e = if (code != null) DlinkSecureEnvelope.decode(raw, code) else DlinkShareEnvelope.decode(raw)
            CoroutineScope(Dispatchers.IO).launch {
                db.connectionDao().insert(ConnectionEntity(e.sessionId, e.name, e.title, e.company, method, note = listOfNotNull(e.phone, e.email, e.website).joinToString(" · ")))
            }
            runOnUiThread { Toast.makeText(this, "Received ${e.name} via $method", Toast.LENGTH_LONG).show() }
        } catch (ex: Exception) { runOnUiThread { Toast.makeText(this, ex.message ?: "Invalid or incorrect exchange code", Toast.LENGTH_SHORT).show() } }
    }

    fun openQrScanner() = qrLauncher.launch(Intent(this, QrScannerActivity::class.java))
}

@Composable
fun DlinkApp(db: DlinkDatabase, nearby: NearbyExchangeManager, nfc: NfcShareManager) {
    var tab by remember { mutableStateOf(0) }
    val profile by db.profileDao().observeDefault().collectAsState(null)
    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF2563EB))) {
        Scaffold(bottomBar = { NavigationBar { listOf("Home", "Connections", "Profiles", "Share").forEachIndexed { i, l -> NavigationBarItem(tab == i, { tab = i }, icon = { Text(listOf("⌂", "◉", "●", "↗")[i]) }, label = { Text(l) }) } } }) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> Home(profile?.name ?: "Your Dlink") { tab = 3 }
                    1 -> Connections(db)
                    2 -> Profiles(profile)
                    3 -> Share(profile, nearby, nfc)
                }
            }
        }
    }
}

@Composable
fun Home(name: String, onShare: () -> Unit) = Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("DLINK", style = MaterialTheme.typography.labelLarge); Text("Tap. Share.\nConnect.", style = MaterialTheme.typography.displaySmall); Text("Offline-first digital identity and contact exchange.")
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(name, style = MaterialTheme.typography.titleLarge); Text("Your digital identity") } }
    Button(onShare, Modifier.fillMaxWidth().height(58.dp)) { Text("SHARE MY DLINK") }
    OutlinedButton(onShare, Modifier.fillMaxWidth()) { Text("RECEIVE") }
    Text("Nearby, NFC and QR exchange can work without mobile data or Wi-Fi.")
}

@Composable
fun Share(profile: ProfileEntity?, nearby: NearbyExchangeManager, nfc: NfcShareManager) {
    var showQr by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
    val ttl = 30_000L
    val payloadData = remember(profile, code) {
        if (code.isBlank()) null else {
            val envelope = DlinkShareEnvelope.create(profile?.name ?: "Dlink User", profile?.title ?: "", profile?.company ?: "", profile?.bio ?: "" , ttlMs = ttl)
            DlinkSecureEnvelope.encode(envelope, code)
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Secure Offline Share", style = MaterialTheme.typography.headlineMedium)
        Text("Encrypted profile exchange • no mobile data or Wi-Fi required")
        Button({
            code = DlinkSecureEnvelope.newCode()
            nearby.startAdvertising()
            sharing = true
        }, Modifier.fillMaxWidth()) { Text(if (sharing) "NEARBY SHARING ACTIVE" else "START SECURE NEARBY SHARE") }
        if (sharing) {
            Text("Exchange code: $code", style = MaterialTheme.typography.titleLarge)
            Text("Show this code only to the person receiving your Dlink. It expires with the 30-second session.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ nearby.stopAdvertising(); sharing = false; code = "" }, Modifier.fillMaxWidth()) { Text("STOP SHARING") }
        }
        OutlinedButton({ nearby.startDiscovery() }, Modifier.fillMaxWidth()) { Text("SEARCH FOR DLINK DEVICES") }
        Divider()
        Text("NFC", style = MaterialTheme.typography.titleMedium)
        Button({
            code = DlinkSecureEnvelope.newCode()
            val envelope = DlinkShareEnvelope.create(profile?.name ?: "Dlink User", profile?.title ?: "", profile?.company ?: "", profile?.bio ?: "" , ttlMs = ttl)
            nfc.publish(DlinkSecureEnvelope.encode(envelope, code))
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ nfc.clearPublishedPayload() }, ttl)
            Toast.makeText(activity, "NFC ready. Exchange code: $code", Toast.LENGTH_LONG).show()
        }, enabled = nfc.supported, Modifier.fillMaxWidth()) { Text(if (nfc.supported) "ENABLE SECURE NFC TAP SHARE" else "NFC NOT SUPPORTED") }
        OutlinedButton({ nfc.enableReader(); Toast.makeText(activity, "Hold phones back-to-back; enter the sender code when prompted", Toast.LENGTH_LONG).show() }, enabled = nfc.supported, Modifier.fillMaxWidth()) { Text("RECEIVE VIA NFC") }
        Divider()
        Text("QR", style = MaterialTheme.typography.titleMedium)
        OutlinedButton({
            val c = DlinkSecureEnvelope.newCode()
            code = c
            val envelope = DlinkShareEnvelope.create(profile?.name ?: "Dlink User", profile?.title ?: "", profile?.company ?: "", profile?.bio ?: "" , ttlMs = ttl)
            qrBitmap = QrCodeManager.generate(DlinkSecureEnvelope.encode(envelope, c)); showQr = true
        }, Modifier.fillMaxWidth()) { Text("SHOW SECURE DLINK QR") }
        OutlinedButton({ activity.openQrScanner() }, Modifier.fillMaxWidth()) { Text("SCAN DLINK QR") }
        if (code.isNotBlank()) Text("Current exchange code: $code", style = MaterialTheme.typography.titleMedium)
        Text("QR/NFC data is AES-GCM encrypted and authenticated. The receiver must enter the temporary exchange code. Sessions expire after 30 seconds.", style = MaterialTheme.typography.bodySmall)
    }
    if (showQr && qrBitmap != null) AlertDialog(onDismissRequest = { showQr = false }, title = { Text("Secure Dlink QR") }, text = { Column { Image(qrBitmap!!.asImageBitmap(), "Dlink QR", Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text("Exchange code: $code", style = MaterialTheme.typography.titleLarge); Text("Give this code to the intended recipient.") } }, confirmButton = { TextButton({ showQr = false }) { Text("DONE") } })
}

@Composable
fun Connections(db: DlinkDatabase) { val xs by db.connectionDao().observeAll().collectAsState(emptyList()); LazyColumn(Modifier.fillMaxSize().padding(20.dp)) { item { Text("Connections", style = MaterialTheme.typography.headlineMedium) }; items(xs.size) { i -> ListItem({ Text(xs[i].name) }, { Text("${xs[i].title} · ${xs[i].company}") }, trailingContent = { Text(xs[i].method) }) } } }

@Composable
fun Profiles(p: ProfileEntity?) { Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Profiles", style = MaterialTheme.typography.headlineMedium); Text(p?.name ?: "Create your first Dlink profile"); Text("Profile data is stored locally first and can be synchronized later.") } }
