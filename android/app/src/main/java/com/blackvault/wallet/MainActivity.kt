package com.blackvault.wallet

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var usb: UsbManager
    private lateinit var connection: TextView
    private lateinit var usbInfo: TextView
    private val permissionAction = "com.blackvault.wallet.USB_PERMISSION"
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
    override fun onDestroy() { try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }
}
