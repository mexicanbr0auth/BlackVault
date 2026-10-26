package com.blackvault.wallet

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import java.util.UUID
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var usb: UsbManager
    private lateinit var connection: TextView
    private lateinit var usbInfo: TextView
    private val permissionAction = "com.blackvault.wallet.USB_PERMISSION"
    private val serviceUuid = UUID.fromString("7b5a0001-6f2d-4a91-9c7e-42564c564c54")
    private val rxUuid = UUID.fromString("7b5a0002-6f2d-4a91-9c7e-42564c564c54")
    private val txUuid = UUID.fromString("7b5a0003-6f2d-4a91-9c7e-42564c564c54")
    private var gatt: BluetoothGatt? = null
    private var bleScanCallback: ScanCallback? = null
    private var bleStarted = false
    private val bg = Color.rgb(11, 13, 15)
    private val cardBg = Color.rgb(23, 26, 30)
    private val white = Color.rgb(244, 246, 248)
    private val muted = Color.rgb(151, 158, 167)
    private val blue = Color.rgb(92, 153, 255)
    private val green = Color.rgb(67, 207, 139)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                permissionAction -> {
                    val d = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) else @Suppress("DEPRECATION") (i.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice)
                    if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && d != null) identify(d) else disconnected("Permissão USB não concedida")
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> scan()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = getSystemService(USB_SERVICE) as UsbManager
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(20), dp(22), dp(18)) }

        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val logo = TextView(this).apply { text = "⬡"; textSize = 30f; setTextColor(white); gravity = Gravity.CENTER; background = shape(cardBg, 14); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)) }
        brandRow.addView(logo)
        val brandTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        brandTexts.addView(text("BLACKVAULT", 18, white, true))
        brandTexts.addView(text("HARDWARE WALLET", 10, muted, true).apply { letterSpacing = .13f })
        brandRow.addView(brandTexts, LinearLayout.LayoutParams(0, -2, 1f))
        val menu = TextView(this).apply { text = "⋯"; textSize = 27f; setTextColor(white); gravity = Gravity.CENTER; background = shape(cardBg, 13); layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)) }
        brandRow.addView(menu)
        content.addView(brandRow)

        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(22), 0, dp(18)) }
        val dot = TextView(this).apply { text = "●"; textSize = 11f; setTextColor(muted) }
        statusRow.addView(dot)
        connection = text("  Dispositivo desconectado", 13, muted)
        statusRow.addView(connection)
        statusRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        statusRow.addView(text("USB-C / OTG", 11, muted))
        content.addView(statusRow)

        val balance = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); background = shape(cardBg, 22) }
        val balHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        balHead.addView(text("PATRIMÔNIO TOTAL", 11, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        balHead.addView(text("◈  MULTICHAIN", 10, green, true))
        balance.addView(balHead)
        balance.addView(text("— — —", 34, white, true).apply { setPadding(0, dp(14), 0, dp(2)) })
        balance.addView(text("Conecte seu dispositivo para começar", 13, muted))
        val divider = View(this).apply { setBackgroundColor(Color.rgb(44, 48, 54)) }
        balance.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(20); bottomMargin = dp(15) })
        val chainRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chainRow.addView(text("₿  Bitcoin", 12, white), LinearLayout.LayoutParams(0, -2, 1f))
        chainRow.addView(text("◆  Ethereum / EVM", 12, white))
        balance.addView(chainRow)
        content.addView(balance)

        content.addView(sectionTitle("Saldos EVM", "MULTICHAIN"))
        content.addView(text("Consulte saldos nativos do mesmo endereço em Ethereum, Polygon, BNB Chain, Arbitrum, Optimism, Base e Avalanche. Use somente seu endereço público 0x…; nunca informe seed ou chave privada.", 12, muted).apply { setPadding(0, 0, 0, dp(12)) })
        val evmAddress = EditText(this).apply {
            hint = "Endereço público 0x…"
            setHintTextColor(muted); setTextColor(white); textSize = 14f; setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = shape(cardBg, 12)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        content.addView(evmAddress, LinearLayout.LayoutParams(-1, dp(52)))
        val evmResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8)); background = shape(cardBg, 16) }
        val evmStatus = text("Informe um endereço para carregar os saldos.", 13, muted)
        evmResults.addView(evmStatus)
        content.addView(evmResults, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val evmButton = Button(this).apply {
            text = "Consultar todas as redes EVM"; setTextColor(white)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(48, 91, 160)))
            setOnClickListener {
                val address = evmAddress.text.toString().trim()
                if (!address.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                    evmStatus.text = "Endereço inválido. Precisa ter 42 caracteres e começar por 0x."
                    evmStatus.setTextColor(Color.rgb(255, 170, 90))
                } else {
                    evmResults.removeAllViews()
                    evmStatus.text = "Consultando RPCs públicos…"
                    evmStatus.setTextColor(muted); evmResults.addView(evmStatus)
                    val networks = listOf(
                        Triple("Ethereum", "https://cloudflare-eth.com", 18),
                        Triple("Polygon", "https://polygon-bor-rpc.publicnode.com", 18),
                        Triple("BNB Chain", "https://bsc-rpc.publicnode.com", 18),
                        Triple("Arbitrum One", "https://arb1.arbitrum.io/rpc", 18),
                        Triple("Optimism", "https://mainnet.optimism.io", 18),
                        Triple("Base", "https://mainnet.base.org", 18),
                        Triple("Avalanche C-Chain", "https://api.avax.network/ext/bc/C/rpc", 18)
                    )
                    val rows = java.util.concurrent.ConcurrentHashMap<String, String>()
                    val pool = java.util.concurrent.Executors.newFixedThreadPool(7)
                    networks.forEach { net -> pool.execute {
                        var conn: HttpURLConnection? = null
                        val value = try {
                            conn = (URL(net.second).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"; connectTimeout = 9000; readTimeout = 9000; doOutput = true
                                setRequestProperty("Content-Type", "application/json")
                            }
                            val payload = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBalance\",\"params\":[\"$address\",\"latest\"],\"id\":1}"
                            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
                            val response = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            if (response.has("error")) throw Exception("RPC error")
                            val hex = response.getString("result").removePrefix("0x").ifEmpty { "0" }
                            val wei = java.math.BigInteger(hex, 16)
                            val amount = java.math.BigDecimal(wei).divide(java.math.BigDecimal.TEN.pow(net.third)).stripTrailingZeros().toPlainString()
                            "$amount ${if (net.first == "Avalanche C-Chain") "AVAX" else if (net.first == "BNB Chain") "BNB" else if (net.first == "Polygon") "POL/MATIC" else if (net.first == "Ethereum") "ETH" else "ETH"}"
                        } catch (e: Exception) { "Indisponível (${e.message ?: "falha de rede"})" }
                        finally { conn?.disconnect() }
                        rows[net.first] = value
                        runOnUiThread {
                            evmResults.removeAllViews()
                            networks.forEach { n ->
                                val line = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(9), 0, dp(9)) }
                                line.addView(text(n.first, 13, white, true), LinearLayout.LayoutParams(0, -2, 1f))
                                line.addView(text(rows[n.first] ?: "Consultando…", 12, if (rows[n.first]?.startsWith("Indisponível") == true) Color.rgb(255,170,90) else green))
                                evmResults.addView(line)
                                if (n != networks.last()) evmResults.addView(View(this@MainActivity).apply { setBackgroundColor(Color.rgb(44,48,54)) }, LinearLayout.LayoutParams(-1, dp(1)))
                            }
                        }
                    } }
                    pool.shutdown()
                }
            }
        }
        content.addView(evmButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })

        content.addView(sectionTitle("Acesso rápido", "AÇÕES"))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; }
        actions.addView(actionTile("↑", "Enviar"), LinearLayout.LayoutParams(0, dp(92), 1f).apply { rightMargin = dp(7) })
        actions.addView(actionTile("↓", "Receber"), LinearLayout.LayoutParams(0, dp(92), 1f).apply { leftMargin = dp(3); rightMargin = dp(4) })
        actions.addView(actionTile("◈", "Ativos"), LinearLayout.LayoutParams(0, dp(92), 1f).apply { leftMargin = dp(4); rightMargin = dp(3) })
        actions.addView(actionTile("⚙", "Ajustes"), LinearLayout.LayoutParams(0, dp(92), 1f).apply { leftMargin = dp(7) })
        content.addView(actions)

        content.addView(sectionTitle("Atividade recente", "HISTÓRICO"))
        val activity = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(15), dp(15), dp(15), dp(15)); background = shape(cardBg, 18) }
        val activityIcon = TextView(this).apply { text = "↔"; textSize = 22f; setTextColor(blue); gravity = Gravity.CENTER; background = shape(Color.rgb(28, 39, 57), 13); layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)) }
        activity.addView(activityIcon)
        val activityText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        activityText.addView(text("Tudo tranquilo por aqui", 14, white, true))
        activityText.addView(text("As transações aparecerão aqui", 12, muted).apply { setPadding(0, dp(4), 0, 0) })
        activity.addView(activityText)
        content.addView(activity)

        val deviceCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); background = shape(Color.rgb(17, 20, 23), 16) }
        deviceCard.addView(text("STATUS DO DISPOSITIVO", 10, muted, true).apply { letterSpacing = .08f })
        usbInfo = text("Nenhum USB detectado. Conecte o ESP32-C3 via OTG.", 12, muted)
        deviceCard.addView(usbInfo.apply { setPadding(0, dp(8), 0, 0) })
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        content.addView(deviceCard, lp)
        content.addView(text("As chaves e assinaturas ainda não estão implementadas nesta versão.", 11, muted).apply { gravity = Gravity.CENTER; setPadding(dp(4), dp(18), dp(4), dp(8)) })

        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(9), dp(8), dp(9)); background = shape(Color.rgb(16, 18, 21), 0) }
        nav.addView(navItem("⌂", "Início", true), LinearLayout.LayoutParams(0, dp(52), 1f))
        nav.addView(navItem("◈", "Carteira", false), LinearLayout.LayoutParams(0, dp(52), 1f))
        nav.addView(navItem("◎", "Redes", false), LinearLayout.LayoutParams(0, dp(52), 1f))
        nav.addView(navItem("☰", "Mais", false), LinearLayout.LayoutParams(0, dp(52), 1f))
        page.addView(nav)
        setContentView(page)

        val f = IntentFilter().apply { addAction(permissionAction); addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, f)
        scan()
        startBleDiscovery()
    }

    private fun actionTile(symbol: String, caption: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = shape(cardBg, 17); isClickable = true
        addView(TextView(this@MainActivity).apply { text = symbol; textSize = 23f; setTextColor(blue); gravity = Gravity.CENTER })
        addView(text(caption, 11, white, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
    }
    private fun navItem(symbol: String, caption: String, selected: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(TextView(this@MainActivity).apply { text = symbol; textSize = 20f; setTextColor(if (selected) blue else muted); gravity = Gravity.CENTER })
        addView(text(caption, 10, if (selected) blue else muted, selected).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
    }
    private fun sectionTitle(title: String, eyebrow: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setPadding(0, dp(25), 0, dp(12))
        addView(text(title, 18, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        addView(text(eyebrow, 9, muted, true).apply { letterSpacing = .08f })
    }
    private fun text(s: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun shape(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun hasBlePermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startBleDiscovery() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            usbInfo.text = "BLE indisponível/desativado. Conecte por USB OTG ou ative o Bluetooth."
            return
        }
        if (!hasBlePermissions()) {
            requestPermissions(if (Build.VERSION.SDK_INT >= 31) arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 701)
            return
        }
        if (bleStarted) return
        val scanner = adapter.bluetoothLeScanner ?: return
        bleStarted = true
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: try { result.device.name } catch (_: SecurityException) { null }
                if (name == "BlackVault" || result.scanRecord?.serviceUuids?.any { it.uuid == serviceUuid } == true) {
                    try { scanner.stopScan(this) } catch (_: Exception) {}
                    bleScanCallback = null
                    runOnUiThread {
                        connection.text = "  BlackVault BLE encontrado · conectando"
                        connection.setTextColor(blue)
                        usbInfo.text = "Dispositivo BLE encontrado; verificando o serviço GATT."
                    }
                    try { gatt?.close(); gatt = result.device.connectGatt(this@MainActivity, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE) }
                    catch (_: SecurityException) { bleStarted = false }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                bleStarted = false
                runOnUiThread { usbInfo.text = "Busca BLE falhou ($errorCode). USB OTG continua disponível." }
            }
        }
        bleScanCallback = callback
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            usbInfo.text = "Buscando BlackVault por BLE automaticamente… USB OTG também é compatível."
        } catch (_: SecurityException) { bleStarted = false; usbInfo.text = "Permissão Bluetooth necessária para busca automática." }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { connection.text = "  BLE conectado · verificando serviço"; connection.setTextColor(blue) }
                try { g.discoverServices() } catch (_: SecurityException) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                g.close()
                if (gatt === g) gatt = null
                bleStarted = false
                runOnUiThread { connection.text = "  Dispositivo desconectado"; connection.setTextColor(muted) }
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(serviceUuid)
            if (status == BluetoothGatt.GATT_SUCCESS && service?.getCharacteristic(rxUuid) != null && service.getCharacteristic(txUuid) != null) {
                runOnUiThread {
                    connection.text = "  BlackVault BLE conectado · iniciando handshake"
                    connection.setTextColor(blue)
                }
                val tx = service.getCharacteristic(txUuid)!!
                val rx = service.getCharacteristic(rxUuid)!!
                try {
                    g.setCharacteristicNotification(tx, true)
                    val cccd = tx.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (cccd != null) {
                        if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        else { @Suppress("DEPRECATION") cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; @Suppress("DEPRECATION") g.writeDescriptor(cccd) }
                    } else sendHello(g, rx)
                } catch (_: SecurityException) { runOnUiThread { usbInfo.text = "Permissão GATT insuficiente." } }
            } else {
                runOnUiThread {
                    connection.text = "  BLE incompatível"
                    connection.setTextColor(muted)
                    usbInfo.text = "Serviço BlackVault não encontrado; nenhuma operação liberada."
                }
                g.disconnect()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") && status == BluetoothGatt.GATT_SUCCESS) {
                val rx = g.getService(serviceUuid)?.getCharacteristic(rxUuid) ?: return
                sendHello(g, rx)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == txUuid) processDeviceResponse(value.toString(Charsets.UTF_8))
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) processDeviceResponse(characteristic.value?.toString(Charsets.UTF_8) ?: "")
        }
    }

    private fun processDeviceResponse(response: String) {
        runOnUiThread {
            usbInfo.text = "Resposta do ESP32-C3: $response"
            if (response.contains("\"ok\":true") && response.contains("\"device\":\"BlackVault\"")) {
                connection.text = "  BlackVault conectado · dispositivo reconhecido"
                connection.setTextColor(green)
                showWalletSetupDialog()
            }
        }
    }

    private fun showWalletSetupDialog() {
        if (isFinishing || isDestroyed) return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Configurar carteira")
            .setMessage("O ESP32-C3 foi encontrado. Deseja criar uma nova carteira ou importar uma seed existente?\n\nAviso: o canal atual ainda não possui autenticação criptográfica. Não envie nem digite sua seed nesta versão.")
            .setPositiveButton("Criar carteira") { _, _ ->
                android.app.AlertDialog.Builder(this)
                    .setTitle("Criação ainda bloqueada")
                    .setMessage("A criação segura exige geração BIP39, armazenamento protegido e protocolo autenticado entre app e ESP32. Esses componentes ainda não estão prontos; não será gerada uma seed falsa.")
                    .setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Importar seed") { _, _ ->
                android.app.AlertDialog.Builder(this)
                    .setTitle("Importação ainda bloqueada")
                    .setMessage("Por segurança, não cole uma seed real enquanto a conexão não estiver autenticada. A importação será habilitada após implementar o canal seguro.")
                    .setPositiveButton("Entendi", null).show()
            }
            .setNeutralButton("Depois", null)
            .create()
        dialog.show()
    }

    private fun sendHello(g: BluetoothGatt, rx: BluetoothGattCharacteristic) {
        val payload = "{\"v\":0,\"id\":1,\"cmd\":\"hello\"}".toByteArray(Charsets.UTF_8)
        try {
            if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(rx, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            else { @Suppress("DEPRECATION") rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; @Suppress("DEPRECATION") rx.value = payload; @Suppress("DEPRECATION") g.writeCharacteristic(rx) }
        } catch (_: SecurityException) { usbInfo.text = "Sem permissão para enviar handshake BLE." }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startBleDiscovery()
        else if (requestCode == 701) usbInfo.text = "Permissão BLE negada. USB OTG permanece disponível."
    }

    private fun scan() {
        val devices = usb.deviceList.values
        if (devices.isEmpty()) { disconnected("Nenhum USB detectado. Conecte o ESP32-C3 via OTG."); return }
        val d = devices.firstOrNull { x -> (0 until x.interfaceCount).any { x.getInterface(it).interfaceClass == 2 || x.getInterface(it).interfaceClass == 10 } } ?: devices.first()
        if (!usb.hasPermission(d)) {
            val p = PendingIntent.getBroadcast(this, 0, Intent(permissionAction).setPackage(packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            usb.requestPermission(d, p)
        } else identify(d)
    }
    private fun identify(d: UsbDevice) {
        connection.text = "  Dispositivo USB detectado"; connection.setTextColor(green)
        usbInfo.text = "VID ${d.vendorId}  ·  PID ${d.productId}\nDispositivo encontrado; comunicação serial e autenticação ainda precisam ser implementadas."
    }
    private fun disconnected(s: String) { connection.text = "  Dispositivo desconectado"; connection.setTextColor(muted); usbInfo.text = s }
    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        try { if (hasBlePermissions()) (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner?.let { scanner -> bleScanCallback?.let { scanner.stopScan(it) } } } catch (_: Exception) {}
        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
