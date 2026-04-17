package com.example.forhmdteleprompter

import android.Manifest
import android.app.DownloadManager
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.speech.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.forhmdteleprompter.databinding.ActivityMainBinding
import com.google.mlkit.common.model.*
import com.google.mlkit.nl.translate.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.*
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity(), org.vosk.android.RecognitionListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var adapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    private var gatt: BluetoothGatt? = null
    private var char: BluetoothGattCharacteristic? = null
    
    private val queue = LinkedBlockingQueue<String>()
    private var workerRunning = true
    private lateinit var sr: SpeechRecognizer
    private var isListening = false
    private var lastText = ""
    
    private val modelManager = RemoteModelManager.getInstance()
    private var translator: Translator? = null
    private var curLangs: Pair<String, String>? = null
    private var tReady = false
    private var tId = 0L

    private var vModel: Model? = null
    private var vService: SpeechService? = null
    private var curVoskLang: String? = null
    
    private val langMap = mapOf(
        "English" to ("en" to "vosk-model-small-en-us-0.15"), "Spanish" to ("es" to "vosk-model-small-es-0.42"),
        "French" to ("fr" to "vosk-model-small-fr-0.22"), "German" to ("de" to "vosk-model-small-de-0.15"),
        "Italian" to ("it" to "vosk-model-small-it-0.22"), "Portuguese" to ("pt" to "vosk-model-small-pt-0.3"),
        "Dutch" to ("nl" to "vosk-model-small-nl-0.22"), "Polish" to ("pl" to "vosk-model-small-pl-0.22"),
        "Russian" to ("ru" to "vosk-model-small-ru-0.22"), "Chinese" to ("zh" to "vosk-model-small-cn-0.22"),
        "Tamil" to ("ta" to "vosk-model-small-ta-0.42"), "Malay" to ("ms" to null)
    )
    private val sourceLangs = langMap.keys.toList()
    private val targetLangs = sourceLangs.filter { it != "Chinese" && it != "Tamil" }
    private val devices = mutableSetOf<BluetoothDevice>()

    private val cUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val sUuid = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    private val chUuid = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        checkPermissions(); setupUI(); setupSTT(); startWorker()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(btReceiver, filter, RECEIVER_EXPORTED) else registerReceiver(btReceiver, filter)
    }

    private fun setupUI() = with(binding) {
        btnConnectBt.setOnClickListener { startScan() }
        btnSendManual.setOnClickListener { etManualInput.text.toString().takeIf { it.isNotEmpty() }?.let { processInput(it); etManualInput.text.clear() } }
        btnVoiceInput.setOnClickListener { if (switchOfflineStt.isChecked) toggleOffline() else toggleOnline() }
        btnManageModels.setOnClickListener { showModels() }

        spinnerSourceLang.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, sourceLangs).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerTargetLang.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, targetLangs).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerSourceLang.setSelection(sourceLangs.indexOf("English"))
        spinnerTargetLang.setSelection(targetLangs.indexOf("Spanish"))
        
        val resetSTT = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = stopListening()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerSourceLang.onItemSelectedListener = resetSTT
        spinnerTargetLang.onItemSelectedListener = resetSTT
        switchOfflineStt.setOnCheckedChangeListener { _, _ -> stopListening() }
    }

    private fun startScan() {
        if (!hasP(Manifest.permission.BLUETOOTH_SCAN)) return checkPermissions()
        devices.clear()
        try {
            adapter?.bondedDevices?.let { devices.addAll(it) }
            adapter?.startDiscovery()
            adapter?.bluetoothLeScanner?.startScan(leCallback)
            binding.tvBtStatus.text = getString(R.string.bt_status_connecting)
        } catch (_: SecurityException) { checkPermissions() }
        Handler(Looper.getMainLooper()).postDelayed({ stopScan(); showPicker() }, 5000)
    }

    private fun stopScan() { 
        if (hasP(Manifest.permission.BLUETOOTH_SCAN)) {
            try {
                adapter?.cancelDiscovery()
                adapter?.bluetoothLeScanner?.stopScan(leCallback)
            } catch (_: SecurityException) {}
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == BluetoothDevice.ACTION_FOUND) {
                val d = if (Build.VERSION.SDK_INT >= 33) {
                    i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                d?.let { devices.add(it) }
            }
        }
    }

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(ct: Int, r: ScanResult) { devices.add(r.device) }
    }

    private fun showPicker() {
        if (!hasP(Manifest.permission.BLUETOOTH_CONNECT)) return
        val items = try {
            devices.map { "${it.name ?: it.address} (${if (it.type == 2) "BLE" else "Classic"})${if (it.bondState == 12) " (Paired)" else ""}" }.toTypedArray()
        } catch (_: SecurityException) { emptyArray<String>() }
        if (items.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Select Device").setItems(items) { _, w -> 
            val d = devices.elementAt(w); if (d.type == 2 || d.type == 3) connectBle(d) else connectClassic(d) 
        }.show()
    }

    private fun connectClassic(d: BluetoothDevice) {
        binding.tvBtStatus.text = getString(R.string.bt_status_connecting)
        Thread {
            try {
                if (!hasP(Manifest.permission.BLUETOOTH_CONNECT)) return@Thread
                closeBluetooth(); if (d.bondState != 12) d.createBond()
                socket = try {
                    d.createRfcommSocketToServiceRecord(cUuid)
                } catch (_: Exception) {
                    d.createInsecureRfcommSocketToServiceRecord(cUuid)
                }
                socket?.connect(); outStream = socket?.outputStream
                btStatus(getString(R.string.bt_status_connected, d.name ?: d.address), android.R.color.holo_green_dark)
            } catch (_: Exception) { btStatus(getString(R.string.bt_status_failed), android.R.color.holo_red_dark) }
        }.start()
    }

    private fun connectBle(d: BluetoothDevice) {
        binding.tvBtStatus.text = getString(R.string.bt_status_connecting_ble)
        if (hasP(Manifest.permission.BLUETOOTH_CONNECT)) {
            try {
                closeBluetooth()
                gatt = d.connectGatt(this, false, gCallback)
            } catch (_: SecurityException) { btStatus(getString(R.string.bt_status_failed), android.R.color.holo_red_dark) }
        }
    }

    private fun closeBluetooth() {
        if (hasP(Manifest.permission.BLUETOOTH_CONNECT)) {
            try { socket?.close(); gatt?.run { disconnect(); close() } } catch (_: Exception) {}
        }
        socket = null; outStream = null; gatt = null; char = null
    }

    private fun btStatus(m: String, c: Int) = runOnUiThread { binding.tvBtStatus.text = m; binding.tvBtStatus.setTextColor(ContextCompat.getColor(this, c)) }

    private val gCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt?, s: Int, ns: Int) {
            if (ns == 2) {
                runOnUiThread { binding.tvBtStatus.text = getString(R.string.bt_status_ble_connected) }
                if (hasP(Manifest.permission.BLUETOOTH_CONNECT)) {
                    try { g?.discoverServices() } catch (_: SecurityException) {}
                }
            } else if (ns == 0) btStatus(getString(R.string.bt_status_disconnected), android.R.color.holo_red_dark)
        }
        override fun onServicesDiscovered(g: BluetoothGatt?, s: Int) {
            if (s == 0) {
                char = g?.getService(sUuid)?.getCharacteristic(chUuid)
                runOnUiThread {
                    if (hasP(Manifest.permission.BLUETOOTH_CONNECT)) {
                        try {
                            if (char != null) btStatus(getString(R.string.bt_status_ble_ready, g?.device?.name ?: "Device"), android.R.color.holo_green_dark)
                            else binding.tvBtStatus.text = getString(R.string.bt_not_found)
                        } catch (_: SecurityException) {}
                    }
                }
            }
        }
    }

    private fun startWorker() = Thread {
        while (workerRunning) {
            try {
                val bytes = (queue.take().trim() + "\r\n").toByteArray()
                outStream?.write(bytes)
                if (gatt != null && char != null && hasP(Manifest.permission.BLUETOOTH_CONNECT)) {
                    for (i in bytes.indices step 20) {
                        val chunk = bytes.sliceArray(i until minOf(i + 20, bytes.size))
                        try {
                            char!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            if (Build.VERSION.SDK_INT >= 33) {
                                gatt!!.writeCharacteristic(char!!, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                            } else {
                                @Suppress("DEPRECATION")
                                char!!.value = chunk
                                @Suppress("DEPRECATION")
                                gatt!!.writeCharacteristic(char!!)
                            }
                        } catch (_: SecurityException) {}
                        Thread.sleep(150)
                    }
                }
            } catch (e: Exception) { if (e is InterruptedException) return@Thread }
        }
    }.start()

    private fun send(d: String) { queue.clear(); queue.offer(d) }

    private fun processInput(raw: String) {
        if (raw.isEmpty() || raw == lastText) return
        lastText = raw; binding.tvTranscript.text = getString(R.string.input_label, raw)
        if (binding.switchEnableTranslation.isChecked) translate(raw) else send(raw)
    }

    private fun translate(text: String) {
        val src = getCode(binding.spinnerSourceLang.selectedItem.toString())
        val tgt = getCode(binding.spinnerTargetLang.selectedItem.toString())
        if (src == tgt) return send(text)
        val id = ++tId
        if (translator == null || curLangs != (src to tgt)) {
            tReady = false; translator?.close(); curLangs = src to tgt
            translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()).apply {
                downloadModelIfNeeded().addOnSuccessListener { tReady = true; if (id == tId) doTranslate(text, id) }
            }
        } else if (tReady) doTranslate(text, id) else send(text)
    }

    private fun doTranslate(text: String, id: Long) = translator?.translate(text)?.addOnSuccessListener { 
        if (id == tId) { runOnUiThread { binding.tvTranslation.text = getString(R.string.translation_label, it) }; send(it) }
    }

    private fun getCode(n: String) = langMap[n]?.first ?: "en"

    private fun setupSTT() {
        sr = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) { r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { processInput(it) }; if (isListening) startOnline() else binding.btnVoiceInput.text = getString(R.string.btn_voice_start) }
                override fun onPartialResults(r: Bundle?) { r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { processInput(it) } }
                override fun onReadyForSpeech(p: Bundle?) { binding.btnVoiceInput.text = getString(R.string.btn_voice_listening) }
                override fun onError(e: Int) { if (isListening) startOnline() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
    }

    private fun startOnline() {
        if (!hasP(Manifest.permission.RECORD_AUDIO)) return
        try {
            sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, getCode(binding.spinnerSourceLang.selectedItem.toString()))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        } catch (_: SecurityException) {}
    }

    private fun toggleOnline() { if (isListening) stopListening() else { isListening = true; startOnline() } }
    
    private fun toggleOffline() {
        if (vService != null) stopVosk()
        else if (vModel != null && curVoskLang == binding.spinnerSourceLang.selectedItem.toString()) startVosk()
        else checkVosk()
    }

    private fun getVoskUrl(n: String) = langMap[n]?.second?.let { "https://alphacephei.com/vosk/models/$it.zip" }
    private fun getVoskDir(lang: String) = File(getExternalFilesDir(null), "vosk-model-$lang")

    private fun checkVosk() {
        val lang = binding.spinnerSourceLang.selectedItem.toString()
        val dir = getVoskDir(lang)
        if (dir.exists() && dir.walkTopDown().any { it.name == "am" || it.name == "conf" }) {
            binding.btnVoiceInput.text = getString(R.string.btn_voice_loading)
            Thread {
                try {
                    val modelPath = dir.walkTopDown().find { it.name == "am" }?.parentFile?.absolutePath ?: dir.absolutePath
                    vModel = Model(modelPath); curVoskLang = lang; runOnUiThread { startVosk() }
                } catch (_: Exception) {
                    dir.deleteRecursively(); runOnUiThread { getVoskUrl(lang)?.let { showVoskDl(it, lang) } ?: Toast.makeText(this, "Offline not supported", Toast.LENGTH_SHORT).show() }
                }
            }.start()
        } else getVoskUrl(lang)?.let { showVoskDl(it, lang) } ?: Toast.makeText(this, "Offline not supported", Toast.LENGTH_SHORT).show()
    }

    private fun showVoskDl(url: String, lang: String) = AlertDialog.Builder(this).setTitle("Download Vosk").setMessage("Model for $lang missing. Download?").setPositiveButton("Download") { _, _ -> dlVosk(url, lang) }.setNegativeButton("Cancel", null).show()

    private fun dlVosk(url: String, lang: String) {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val zipFile = "vosk-model-$lang.zip"
        val request = DownloadManager.Request(url.toUri())
            .setDestinationInExternalFilesDir(this, null, zipFile)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val id = dm.enqueue(request)
        val v = LayoutInflater.from(this).inflate(R.layout.download_dialog, null)
        val pb = v.findViewById<ProgressBar>(R.id.progressBar)
        val tv = v.findViewById<TextView>(R.id.tvProgress)
        val dialog = AlertDialog.Builder(this).setView(v).setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> dm.remove(id) }.show()
        
        val h = Handler(Looper.getMainLooper())
        h.post(object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                val c = dm.query(DownloadManager.Query().setFilterById(id))
                if (c != null && c.moveToFirst()) {
                    val curIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val stIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    
                    if (curIdx != -1 && totIdx != -1 && stIdx != -1) {
                        val cur = c.getLong(curIdx)
                        val tot = c.getLong(totIdx)
                        val st = c.getInt(stIdx)
                        
                        if (st == DownloadManager.STATUS_SUCCESSFUL) { dialog.dismiss(); c.close(); unzipVosk(zipFile, lang); return }
                        if (st == DownloadManager.STATUS_FAILED) { dialog.dismiss(); c.close(); Toast.makeText(this@MainActivity, getString(R.string.download_failed), Toast.LENGTH_SHORT).show(); return }

                        if (tot > 0) {
                            val progress = (cur * 100L / tot).toInt()
                            pb.isIndeterminate = false
                            pb.progress = progress
                            tv.text = getString(R.string.progress_percent, progress)
                        } else {
                            pb.isIndeterminate = true
                            tv.text = String.format(Locale.getDefault(), "%.1f MB", cur / 1048576.0)
                        }
                    }
                }
                c?.close()
                h.postDelayed(this, 500)
            }
        })
    }

    private fun unzipVosk(zipName: String, lang: String) = Thread {
        try {
            val target = getVoskDir(lang).apply { if (exists()) deleteRecursively(); mkdirs() }
            ZipInputStream(FileInputStream(File(getExternalFilesDir(null), zipName))).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val f = File(target, e.name)
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); FileOutputStream(f).use { zis.copyTo(it) } }
                    zis.closeEntry(); e = zis.nextEntry
                }
            }
            File(getExternalFilesDir(null), zipName).delete(); runOnUiThread { toggleOffline() }
        } catch (_: IOException) {}
    }.start()

    private fun startVosk() { try { vService = SpeechService(Recognizer(vModel!!, 16000f), 16000f).apply { startListening(this@MainActivity) }; binding.btnVoiceInput.text = getString(R.string.btn_voice_stop_offline_alt) } catch (_: Exception) {} }
    private fun stopVosk() { vService?.stop(); vService = null; binding.btnVoiceInput.text = getString(R.string.btn_voice_start) }
    override fun onResult(h: String?) = h?.let { try { JSONObject(it).optString("text").takeIf { t -> t.isNotEmpty() }?.let { t -> runOnUiThread { processInput(t) } } } catch (_: Exception) {} } ?: Unit
    override fun onPartialResult(h: String?) = h?.let { try { JSONObject(it).optString("partial").takeIf { t -> t.isNotEmpty() }?.let { t -> runOnUiThread { processInput(t) } } } catch (_: Exception) {} } ?: Unit
    override fun onFinalResult(p0: String?) {}
    override fun onError(p0: Exception?) = runOnUiThread { stopVosk() }
    override fun onTimeout() {}

    private fun showModels() {
        val opts = sourceLangs.map { "ML Kit $it" }.toMutableList().apply { add("Vosk (Current)"); add("Clear ML Kit") }
        AlertDialog.Builder(this).setTitle("Model Manager").setItems(opts.toTypedArray()) { _, w ->
            if (w < sourceLangs.size) dlModel(getCode(sourceLangs[w]))
            else if (w == sourceLangs.size) {
                val lang = binding.spinnerSourceLang.selectedItem.toString()
                getVoskUrl(lang)?.let { dlVosk(it, lang) } ?: Toast.makeText(this, "No Vosk model", Toast.LENGTH_SHORT).show()
            } else modelManager.getDownloadedModels(TranslateRemoteModel::class.java).addOnSuccessListener { it.forEach { m -> modelManager.deleteDownloadedModel(m) }; translator = null }
        }.show()
    }

    private fun dlModel(c: String) {
        binding.pbDownload.visibility = View.VISIBLE
        modelManager.download(TranslateRemoteModel.Builder(c).build(), DownloadConditions.Builder().requireWifi().build()).addOnSuccessListener { binding.pbDownload.visibility = View.GONE; translator = null }.addOnFailureListener { binding.pbDownload.visibility = View.GONE }
    }

    private fun hasP(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun checkPermissions() {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET, Manifest.permission.ACCESS_FINE_LOCATION).apply {
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE) }
            else { @Suppress("DEPRECATION") add(Manifest.permission.BLUETOOTH); @Suppress("DEPRECATION") add(Manifest.permission.BLUETOOTH_ADMIN) }
        }
        if (p.any { !hasP(it) }) ActivityCompat.requestPermissions(this, p.toTypedArray(), 101)
    }

    private fun stopListening() {
        isListening = false; try { sr.stopListening() } catch (_: Exception) {}
        stopVosk(); lastText = ""; vModel = null
        binding.btnVoiceInput.text = getString(R.string.btn_voice_start)
    }

    override fun onDestroy() {
        super.onDestroy()
        workerRunning = false; try { unregisterReceiver(btReceiver) } catch (_: Exception) {}
        try { sr.destroy() } catch (_: Exception) {}
        stopVosk(); closeBluetooth(); translator?.close()
    }
}
