package com.blackvault.wallet
import android.app.*
import android.content.*
import android.hardware.usb.*
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
 private lateinit var usb: UsbManager
 private lateinit var status: TextView
 private lateinit var details: TextView
 private val permissionAction = "com.blackvault.wallet.USB_PERMISSION"
 private val receiver = object: BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) { when(i.action) {
   permissionAction -> { val d=i.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE); if(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)&&d!=null) identify(d) else disconnected("Permissão USB negada") }
   UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> scan()
  } }
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); usb=getSystemService(USB_SERVICE) as UsbManager
  window.statusBarColor=Color.rgb(11,13,15); window.navigationBarColor=Color.rgb(11,13,15)
  val root=LinearLayout(this).apply { orientation=1; setPadding(22,28,22,18); setBackgroundColor(Color.rgb(11,13,15)) }
  root.addView(label("⬡  Hardware Wallet",23,Color.WHITE)); status=label("●  Procurando USB…",14,Color.LTGRAY); root.addView(status)
  val card=LinearLayout(this).apply { orientation=1; setPadding(18,20,18,20); setBackgroundColor(Color.rgb(23,26,30)) }
  card.addView(label("◈  Multichain Wallet",18,Color.WHITE))
  card.addView(label("Bitcoin • Ethereum/EVM • outras redes planejadas",13,Color.LTGRAY))
  card.addView(label("Saldo indisponível",26,Color.WHITE))
  card.addView(label("Protótipo: sem chaves, saldo real ou assinatura",12,Color.rgb(170,175,182)))
  root.addView(card)
  root.addView(label("↑ Enviar     ↓ Receber     ◈ Ativos     ⚙ Configurar",14,Color.WHITE))
  root.addView(label("Atividade recente",18,Color.WHITE))
  details=label("Conecte o assinador ESP32-C3 por USB OTG.",14,Color.LTGRAY); root.addView(details)
  root.addView(TextView(this).apply { text="Início          Carteira          Redes          Mais"; gravity=Gravity.CENTER; setTextColor(Color.rgb(60,150,255)); setPadding(0,28,0,0) })
  setContentView(root)
  val f=IntentFilter().apply { addAction(permissionAction); addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
  registerReceiver(receiver,f,RECEIVER_NOT_EXPORTED); scan()
 }
 private fun label(s:String,size:Int,color:Int)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(color); setPadding(0,8,0,8) }
 private fun scan() {
  val ds=usb.deviceList.values
  if(ds.isEmpty()){disconnected("Nenhum USB detectado. Conecte via OTG.");return}
  val d=ds.firstOrNull { x -> (0 until x.interfaceCount).any { x.getInterface(it).interfaceClass==2 || x.getInterface(it).interfaceClass==10 } } ?: ds.first()
  if(!usb.hasPermission(d)){val p=PendingIntent.getBroadcast(this,0,Intent(permissionAction).setPackage(packageName),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);usb.requestPermission(d,p)} else identify(d)
 }
 private fun identify(d:UsbDevice) { status.text="●  USB detectado — autenticação pendente"; status.setTextColor(Color.rgb(255,190,70)); details.text="VID: ${d.vendorId} • PID: ${d.productId}\nO app detectou um dispositivo USB, mas ainda não autenticou o firmware nem estabeleceu o canal serial." }
 private fun disconnected(s:String){status.text="●  Desconectado";status.setTextColor(Color.LTGRAY);details.text=s}
 override fun onDestroy(){unregisterReceiver(receiver);super.onDestroy()}
}