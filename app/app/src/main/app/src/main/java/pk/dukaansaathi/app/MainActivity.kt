
package pk.dukaansaathi.app

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.*
import android.speech.RecognizerIntent
import android.view.*
import android.webkit.*
import android.widget.*
import android.database.sqlite.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

data class Receipt(val id:Long,val code:String,val kind:String,val customer:String,val phone:String,
                   val details:String,val amount:String,val created:Long,val warrantyEnd:Long)

class MainActivity:Activity() {
 private lateinit var db:StoreDb
 private lateinit var shop:EditText; private lateinit var address:EditText; private lateinit var shopPhone:EditText
 private lateinit var customer:EditText; private lateinit var customerPhone:EditText
 private lateinit var details:EditText; private lateinit var amount:EditText; private lateinit var kind:Spinner
 private lateinit var status:TextView
 private var printer:BluetoothSocket?=null
 private var speechTarget:EditText?=null
 private val speechReq=201; private val backupReq=202; private val restoreReq=203
 private val btPermReq=204
 private val kinds=listOf("مرمت کی پرچی","ایک سال کی گارنٹی","فروخت کی رسید")
 private val dateFmt=SimpleDateFormat("dd-MM-yyyy",Locale("en","PK"))

 
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
        db = StoreDb(this)
        ui()
        loadSettings()
        askPermissions()
    } catch (e: Exception) {
        android.util.Log.e("DukaanSaathi", "Startup error", e)
        android.app.AlertDialog.Builder(this)
            .setTitle("ایپ میں خرابی")
            .setMessage(e.toString())
            .setPositiveButton("ٹھیک ہے", null)
            .show()
    }
}

 private fun ui(){
  val scroll=ScrollView(this); val root=LinearLayout(this).apply{orientation=1;setPadding(16,12,16,20);setBackgroundColor(Color.rgb(246,248,251))}
  scroll.addView(root)
  root.addView(TextView(this).apply{text="🎒 دکان ساتھی\nآسان دکان مینجمنٹ";textSize=25f;gravity=Gravity.CENTER;setTextColor(Color.rgb(20,65,100));setPadding(4,18,4,16);typeface=Typeface.DEFAULT_BOLD})
  status=TextView(this).apply{text="پرنٹر منسلک نہیں";gravity=Gravity.CENTER;textSize=14f;setPadding(4,8,4,12)}
  root.addView(status)
  root.addView(head("دکان کی معلومات"))
  shop=field(root,"دکان / کمپنی کا نام")
  address=field(root,"دکان کا پتہ")
  shopPhone=field(root,"دکان کا رابطہ نمبر")
  root.addView(btn("دکان کی معلومات محفوظ کریں"){saveSettings()})
  root.addView(head("نئی رسید یا پرچی"))
  kind=Spinner(this).also{it.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,kinds);root.addView(it)}
  customer=field(root,"گاہک کا نام (اختیاری)")
  customerPhone=field(root,"گاہک کا موبائل نمبر (اختیاری)")
  details=field(root,"سامان اور کام کی تفصیل")
  amount=field(root,"رقم روپے (اختیاری)")
  root.addView(btn("🎙 تفصیل اردو میں بولیں"){speak(details)})
  root.addView(btn("🎙 گاہک کا نام بولیں"){speak(customer)})
  root.addView(btn("پرچی محفوظ کریں اور پرنٹ کریں"){saveNew(true)})
  root.addView(btn("صرف پرچی محفوظ کریں"){saveNew(false)})
  root.addView(head("پرنٹر اور ریکارڈ"))
  root.addView(btn("بلوٹوتھ پرنٹر منتخب / کنیکٹ کریں"){choosePrinter()})
  root.addView(btn("پرنٹر منقطع کریں"){disconnect()})
  root.addView(btn("پرانی رسیدیں تلاش کریں / دوبارہ پرنٹ کریں"){searchReceipts()})
  root.addView(head("بیک اپ اور بحالی"))
  root.addView(btn("ڈیٹا بیک اپ فائل بنائیں"){createBackup()})
  root.addView(btn("بیک اپ فائل سے ڈیٹا بحال کریں"){restoreBackup()})
  root.addView(TextView(this).apply{text="\nاہم شرائط:\nزپ اور کنڈے کی گارنٹی نہیں ہے\n(کپڑے اور سلائی کی گارنٹی ہے)\nخریدا ہوا مال تبدیل ہو سکتا ہے\n\nرسیدیں فون میں آف لائن محفوظ ہوتی ہیں۔ باقاعدگی سے بیک اپ بنائیں۔";textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.DKGRAY);setPadding(8,16,8,16)})
  setContentView(scroll)
 }
 private fun head(t:String)=TextView(this).apply{text=t;textSize=18f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(25,90,130));setPadding(4,18,4,8)}
 private fun field(parent:LinearLayout,h:String)=EditText(this).apply{hint=h;textSize=16f;setPadding(12,8,12,8);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=6};parent.addView(this)}
 private fun btn(t:String,action:()->Unit)=Button(this).apply{text=t;textSize=15f;layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=6};setOnClickListener{action()}}
 private fun msg(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
 private fun saveSettings(){db.set("shop",shop.text.toString());db.set("address",address.text.toString());db.set("shopPhone",shopPhone.text.toString());msg("دکان کی معلومات محفوظ ہوگئیں")}
 private fun loadSettings(){shop.setText(db.get("shop"));address.setText(db.get("address"));shopPhone.setText(db.get("shopPhone"))}
 private fun speak(target:EditText){speechTarget=target;try{startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ur-PK");putExtra(RecognizerIntent.EXTRA_PROMPT,"اردو میں بولیں")},speechReq)}catch(e:Exception){msg("وائس ٹائپنگ دستیاب نہیں، اردو کی بورڈ استعمال کریں")}}
 override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req==speechReq&&res==RESULT_OK){val spoken=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty();speechTarget?.append(if(speechTarget?.text.isNullOrEmpty())spoken else "\n$spoken")}
  if(req==backupReq&&res==RESULT_OK&&data?.data!=null)writeBackup(data.data!!)
  if(req==restoreReq&&res==RESULT_OK&&data?.data!=null)readBackup(data.data!!)
 }
 private fun askPermissions(){if(Build.VERSION.SDK_INT>=31){val p=listOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.RECORD_AUDIO).filter{checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED};if(p.isNotEmpty())requestPermissions(p.toTypedArray(),btPermReq)}else if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),btPermReq)}
 private fun choosePrinter(){
  if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){askPermissions();msg("بلوٹوتھ کی اجازت دیں، پھر دوبارہ کوشش کریں");return}
  val a=BluetoothAdapter.getDefaultAdapter()?:run{msg("فون میں بلوٹوتھ نہیں");return}
  if(!a.isEnabled){startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),btPermReq);return}
  val devices=try{a.bondedDevices.toList()}catch(e:Exception){emptyList()}
  if(devices.isEmpty()){msg("پہلے موبائل کی Bluetooth Settings میں پرنٹر Pair کریں");return}
  AlertDialog.Builder(this).setTitle("Pair شدہ پرنٹر منتخب کریں").setItems(devices.map{"${it.name} (${it.address})"}.toTypedArray()){_,i->connect(devices[i])}.setNegativeButton("بند",null).show()
 }
 private fun connect(d:BluetoothDevice){thread{try{val s=d.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery();s.connect();printer=s;runOnUiThread{status.text="پرنٹر منسلک: ${d.name}";msg("پرنٹر منسلک ہوگیا")}}catch(e:Exception){runOnUiThread{msg("کنکشن ناکام۔ پرنٹر Pair کرکے دوبارہ کوشش کریں")}}}}
 private fun disconnect(){try{printer?.close()}catch(_:Exception){};printer=null;status.text="پرنٹر منقطع ہے";msg("پرنٹر منقطع ہوگیا")}
 private fun saveNew(doPrint:Boolean){
  if(shop.text.toString().isBlank()){msg("پہلے دکان کا نام درج کریں");return}
  if(details.text.toString().isBlank()){msg("سامان یا کام کی تفصیل درج کریں");return}
  val k=kinds[kind.selectedItemPosition];val now=System.currentTimeMillis()
  val end=if(k=="ایک سال کی گارنٹی")Calendar.getInstance().apply{timeInMillis=now;add(Calendar.YEAR,1)}.timeInMillis else 0L
  val code=(100000..999999).random().toString()
  val id=db.insert(code,k,customer.text.toString(),customerPhone.text.toString(),details.text.toString(),amount.text.toString(),now,end)
  val r=db.byId(id)!!
  if(doPrint)print(r) else msg("پرچی محفوظ ہوگئی۔ نمبر: $id، کوڈ: $code")
  customer.text.clear();customerPhone.text.clear();details.text.clear();amount.text.clear()
 }
 private fun html(r:Receipt):String{
  val n=esc(shop.text.toString().ifBlank{db.get("shop").ifBlank{"دکان کا نام"}})
  val a=esc(address.text.toString().ifBlank{db.get("address")});val p=esc(shopPhone.text.toString().ifBlank{db.get("shopPhone")})
  val warranty=if(r.kind=="ایک سال کی گارنٹی")"<h2>ایک سال کی گارنٹی</h2><p>مدت: ایک سال</p><p>آخری تاریخ: ${dateFmt.format(Date(r.warrantyEnd))}</p>" else ""
  val repair=if(r.kind=="مرمت کی پرچی")"<h2>مرمت / سامان وصولی پرچی</h2><p><b>وصولی کوڈ: ${r.code}</b></p>" else ""
  val sale=if(r.kind=="فروخت کی رسید")"<h2>فروخت کی رسید</h2>" else ""
  return """<!doctype html><html dir="rtl"><meta charset="utf-8"><style>body{font-family:sans-serif;color:#000;text-align:center;margin:0;padding:8px}h1{font-size:21px;margin:4px}h2{font-size:18px;border-top:1px dashed;padding:8px 0;border-bottom:1px dashed}.small{font-size:12px}.row{text-align:right;border-bottom:1px dotted #777;padding:6px 0;font-size:14px}.terms{font-weight:bold;margin-top:16px}.exchange{border-top:1px dashed;padding-top:8px;font-weight:bold}</style><body><h1>🎒 $n</h1><div class="small">$a<br/>رابطہ: $p</div><p class="small">تاریخ: ${dateFmt.format(Date(r.created))}</p><p class="small">پرچی نمبر: ${r.id}</p>$warranty$repair$sale<div class="row"><b>گاہک:</b> ${esc(r.customer.ifBlank{"واک اِن گاہک"})}</div>${if(r.phone.isNotBlank())"<div class='row'><b>موبائل:</b> ${esc(r.phone)}</div>" else ""}<div class="row"><b>تفصیل:</b> ${esc(r.details)}</div>${if(r.amount.isNotBlank())"<div class='row'><b>رقم:</b> ${esc(r.amount)} روپے</div>" else ""}<p class="terms">زپ اور کنڈے کی گارنٹی نہیں ہے<br/><small>(کپڑے اور سلائی کی گارنٹی ہے)</small></p><p class="exchange">خریدا ہوا مال تبدیل ہو سکتا ہے</p></body></html>"""
 }
 private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
 private fun print(r:Receipt){
  val s=printer;if(s==null||!s.isConnected){msg("پرچی محفوظ ہوگئی، پہلے پرنٹر کنیکٹ کریں");return}
  val w=WebView(this);w.settings.defaultTextEncodingName="UTF-8";w.setBackgroundColor(Color.WHITE)
  w.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String?){w.postDelayed({
   try{val width=384;val h=(w.contentHeight*w.scale).toInt().coerceIn(400,5000);w.measure(View.MeasureSpec.makeMeasureSpec(width,1073741824),View.MeasureSpec.makeMeasureSpec(0,0));w.layout(0,0,width,h);val b=Bitmap.createBitmap(width,h,Bitmap.Config.ARGB_8888);w.draw(Canvas(b));thread{try{s.outputStream.write(byteArrayOf(27,64));s.outputStream.write(raster(b));s.outputStream.write(byteArrayOf(27,100,3));s.outputStream.flush();runOnUiThread{msg("پرنٹ مکمل ہوگیا")}}catch(e:Exception){runOnUiThread{msg("پرنٹ ناکام، دوبارہ کوشش کریں")}}finally{b.recycle();runOnUiThread{(w.parent as? ViewGroup)?.removeView(w)}}}}catch(e:Exception){msg("پرنٹ تیار نہیں ہوسکا")}
  },600)}}
  w.visibility=View.INVISIBLE;(window.decorView as ViewGroup).addView(w,ViewGroup.LayoutParams(384,-2));w.loadDataWithBaseURL(null,html(r),"text/html","UTF-8",null)
 }
 private fun raster(b:Bitmap):ByteArray{val wb=(b.width+7)/8;val out=ByteArrayOutputStream();out.write(byteArrayOf(29,118,48,0,(wb and 255).toByte(),(wb shr 8).toByte(),(b.height and 255).toByte(),(b.height shr 8).toByte()));for(y in 0 until b.height)for(xb in 0 until wb){var v=0;for(bit in 0..7){val x=xb*8+bit;if(x<b.width){val c=b.getPixel(x,y);val g=(Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000;if(g<165)v=v or(1 shl(7-bit))}};out.write(v)};return out.toByteArray()}
 private fun searchReceipts(){val all=db.all();if(all.isEmpty()){msg("کوئی پرچی محفوظ نہیں");return};val input=EditText(this).apply{hint="نام، نمبر، تفصیل یا پرچی نمبر لکھیں"};AlertDialog.Builder(this).setTitle("پرانی رسید تلاش کریں").setView(input).setPositiveButton("تلاش"){_,_->val q=input.text.toString();val matches=all.filter{it.id.toString().contains(q)||it.customer.contains(q,true)||it.phone.contains(q)||it.details.contains(q,true)||it.code.contains(q)};showMatches(matches)}.setNegativeButton("تمام رسیدیں"){_,_->showMatches(all)}.show()}
 private fun showMatches(rows:List<Receipt>){if(rows.isEmpty()){msg("کوئی ریکارڈ نہیں ملا");return};val labels=rows.map{"#${it.id} | ${it.kind} | ${it.customer} | ${it.code}"};AlertDialog.Builder(this).setTitle("رسید منتخب کریں").setItems(labels.toTypedArray()){_,i->val r=rows[i];AlertDialog.Builder(this).setTitle("پرچی #${r.id}").setMessage("${r.kind}\nگاہک: ${r.customer}\nکوڈ: ${r.code}\nتفصیل: ${r.details}").setPositiveButton("دوبارہ پرنٹ"){_,_->print(r)}.setNegativeButton("بند",null).show()}.setNegativeButton("بند",null).show()}
 private fun createBackup(){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"dukaan-saathi-backup.json")},backupReq)}
 private fun writeBackup(uri:Uri){try{val obj=JSONObject();obj.put("settings",JSONObject(db.settings()));val arr=JSONArray();db.all().forEach{r->arr.put(JSONObject().apply{put("id",r.id);put("code",r.code);put("kind",r.kind);put("customer",r.customer);put("phone",r.phone);put("details",r.details);put("amount",r.amount);put("created",r.created);put("warrantyEnd",r.warrantyEnd)})};obj.put("receipts",arr);contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use{it?.write(obj.toString(2))};msg("بیک اپ محفوظ ہوگیا")}catch(e:Exception){msg("بیک اپ نہیں بن سکا")}}
 private fun restoreBackup(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"},restoreReq)}
 private fun readBackup(uri:Uri){try{val raw=contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()};val obj=JSONObject(raw);db.restore(obj);loadSettings();msg("بیک اپ بحال ہوگیا")}catch(e:Exception){msg("فائل درست بیک اپ نہیں ہے")}}
 override fun onDestroy(){try{printer?.close()}catch(_:Exception){};super.onDestroy()}
}

class StoreDb(ctx:Context):SQLiteOpenHelper(ctx,"dukaan_saathi.db",null,2){
 override fun onCreate(d:SQLiteDatabase){d.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");d.execSQL("CREATE TABLE receipts(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT,kind TEXT,customer TEXT,phone TEXT,details TEXT,amount TEXT,created INTEGER,warrantyEnd INTEGER)")}
 override fun onUpgrade(d:SQLiteDatabase,o:Int,n:Int){}
 fun set(k:String,v:String){writableDatabase.execSQL("INSERT OR REPLACE INTO settings(k,v) VALUES(?,?)",arrayOf(k,v))}
 fun get(k:String):String{val c=readableDatabase.rawQuery("SELECT v FROM settings WHERE k=?",arrayOf(k));val v=if(c.moveToFirst())c.getString(0) else "";c.close();return v}
 fun settings():Map<String,String>{val m=mutableMapOf<String,String>();val c=readableDatabase.rawQuery("SELECT k,v FROM settings",null);while(c.moveToNext())m[c.getString(0)]=c.getString(1);c.close();return m}
 fun insert(code:String,k:String,c:String,p:String,d:String,a:String,t:Long,end:Long):Long{val s=writableDatabase.compileStatement("INSERT INTO receipts(code,kind,customer,phone,details,amount,created,warrantyEnd) VALUES(?,?,?,?,?,?,?,?)");listOf(code,k,c,p,d,a).forEachIndexed{i,v->s.bindString(i+1,v)};s.bindLong(7,t);s.bindLong(8,end);return s.executeInsert()}
 private fun from(c:android.database.Cursor)=Receipt(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getLong(7),c.getLong(8))
 fun byId(id:Long):Receipt?{val c=readableDatabase.rawQuery("SELECT id,code,kind,customer,phone,details,amount,created,warrantyEnd FROM receipts WHERE id=?",arrayOf(id.toString()));val r=if(c.moveToFirst())from(c)else null;c.close();return r}
 fun all():List<Receipt>{val l=mutableListOf<Receipt>();val c=readableDatabase.rawQuery("SELECT id,code,kind,customer,phone,details,amount,created,warrantyEnd FROM receipts ORDER BY id DESC",null);while(c.moveToNext())l.add(from(c));c.close();return l}
 fun restore(o:JSONObject){val s=o.getJSONObject("settings");val arr=o.getJSONArray("receipts");writableDatabase.beginTransaction();try{writableDatabase.delete("settings",null,null);writableDatabase.delete("receipts",null,null);val keys=s.keys();while(keys.hasNext()){val k=keys.next();set(k,s.getString(k))};for(i in 0 until arr.length()){val r=arr.getJSONObject(i);val st=writableDatabase.compileStatement("INSERT INTO receipts(id,code,kind,customer,phone,details,amount,created,warrantyEnd) VALUES(?,?,?,?,?,?,?,?,?)");st.bindLong(1,r.getLong("id"));listOf("code","kind","customer","phone","details","amount").forEachIndexed{idx,key->st.bindString(idx+2,r.optString(key))};st.bindLong(8,r.getLong("created"));st.bindLong(9,r.optLong("warrantyEnd"));st.executeInsert()};writableDatabase.setTransactionSuccessful()}finally{writableDatabase.endTransaction()}}
}
