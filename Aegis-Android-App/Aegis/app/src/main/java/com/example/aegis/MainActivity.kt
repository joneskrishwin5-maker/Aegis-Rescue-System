package com.example.aegis

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var meshNetworkManager: MeshNetworkManager
    private lateinit var locationManager: LocationManager
    private lateinit var mapWebView: WebView
    private lateinit var tvCoordinates: TextView
    private lateinit var tvNodeCount: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sharedPreferences: SharedPreferences

    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    private var hasGpsLock = false
    private var activeLocalSosMsgId: String? = null

    // Background Speech Loop
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isListeningContinuous = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on for background mesh/mic persistence
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        tvCoordinates = findViewById(R.id.tvCoordinates)
        tvNodeCount = findViewById(R.id.tvNodeCount)
        mapWebView = findViewById(R.id.mapWebView)
        drawerLayout = findViewById(R.id.drawerLayout)
        
        val btnMenu = findViewById<Button>(R.id.btnMenu)
        val btnProfile = findViewById<Button>(R.id.btnProfile)
        val btnSosCard = findViewById<CardView>(R.id.btnSosCard)

        // Sidebar Buttons - Expanded 5-Point Disaster Precautions
        findViewById<Button>(R.id.btnEarthquake).setOnClickListener { 
            showCurvyPrecautionDialog("Earthquake", 
                "1. DROP down onto your hands and knees.\n\n" +
                "2. COVER your head and neck under a sturdy table.\n\n" +
                "3. HOLD ON to your shelter until shaking stops.\n\n" +
                "4. STAY AWAY from glass, windows, and heavy fixtures.\n\n" +
                "5. DO NOT use elevators. Use stairs only after shaking stops.") 
        }
        findViewById<Button>(R.id.btnFlood).setOnClickListener { 
            showCurvyPrecautionDialog("Flood", 
                "1. MOVE immediately to higher ground.\n\n" +
                "2. AVOID walking or driving through flood waters.\n\n" +
                "3. DISCONNECT electrical appliances to avoid shocks.\n\n" +
                "4. DO NOT touch wet electrical equipment.\n\n" +
                "5. LISTEN to emergency radio broadcasts for updates.") 
        }
        findViewById<Button>(R.id.btnFire).setOnClickListener { 
            showCurvyPrecautionDialog("Wildfire", 
                "1. CRAWL low under smoke to breathe safely.\n\n" +
                "2. FEEL doors with the back of your hand before opening.\n\n" +
                "3. STOP, DROP, and ROLL if clothes catch fire.\n\n" +
                "4. COVER your face with a wet cloth to filter smoke.\n\n" +
                "5. EVACUATE immediately if instructed by authorities.") 
        }
        findViewById<Button>(R.id.btnCyclone).setOnClickListener { 
            showCurvyPrecautionDialog("Cyclone", 
                "1. STAY INDOORS and away from windows.\n\n" +
                "2. TURN OFF gas and electricity main switches.\n\n" +
                "3. PREPARE an emergency kit with flashlights and water.\n\n" +
                "4. SECURE loose outdoor objects that could become projectiles.\n\n" +
                "5. AVOID going outside during the calm 'eye' of the storm.") 
        }

        sharedPreferences = getSharedPreferences("AegisPrefs", Context.MODE_PRIVATE)

        if (getSavedName().isEmpty() || getSavedPhone().isEmpty()) {
            showProfileEditDialog()
        }

        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        btnProfile.setOnClickListener { showProfileEditDialog() }

        btnSosCard.setOnClickListener {
            if (getSavedName().isEmpty() || getSavedPhone().isEmpty()) {
                Toast.makeText(this, "Save profile details first!", Toast.LENGTH_SHORT).show()
                showProfileEditDialog()
            } else {
                showTriageDialog() // Triggers the new curvy severity menu
            }
        }

        meshNetworkManager = MeshNetworkManager(this,
            onNodeCountChanged = { count ->
                runOnUiThread { tvNodeCount.text = "📡 $count Nodes Connected" }
            },
            onRelayedSosReceived = { name, phone, lat, lng, notes ->
                runOnUiThread {
                    updateMapWithVictimMarker(name, lat, lng)
                    showCurvyPrecautionDialog("🚨 RELAYING SOS", "Victim: $name\nStatus: $notes\nRouting signal through mesh...")
                }
            },
            onAckReceived = { msgId ->
                if (msgId == activeLocalSosMsgId) {
                    runOnUiThread { 
                        showCurvyPrecautionDialog("✅ GATEWAY CONFIRMED", "Reverse Proof: Your SOS signal successfully reached the Command Center!") 
                    }
                }
            }
        )

        setupMapView(mapWebView)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        requestPermissions()
    }

    private fun getSavedName(): String = sharedPreferences.getString("USER_NAME", "") ?: ""
    private fun getSavedPhone(): String = sharedPreferences.getString("USER_PHONE", "") ?: ""

    // =========================================================
    // 🎨 CUSTOM ULTRA-SMOOTH CURVY DIALOG ENGINE
    // =========================================================

    private fun showTriageDialog() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 80, 80, 60)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 70f // Main dialog curve
            }
        }

        val titleView = TextView(this).apply {
            text = if (hasGpsLock) "🚨 Triage Severity" else "⚠️ Locating..."
            textSize = 22f
            setTextColor(Color.parseColor("#1A237E"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }
        rootLayout.addView(titleView)

        val dialog = AlertDialog.Builder(this).setView(rootLayout).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Helper function to build color-coded, curvy priority buttons
        fun addTriageButton(textStr: String, bgColor: String, textColor: String, priority: Int, notes: String) {
            val btn = Button(this).apply {
                text = textStr
                setTextColor(Color.parseColor(textColor))
                isAllCaps = false
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                stateListAnimator = null
                elevation = 0f
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 24
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgColor))
                    cornerRadius = 60f // Super smooth pill button
                }
            }
            btn.setOnClickListener {
                activeLocalSosMsgId = UUID.randomUUID().toString()
                Toast.makeText(this@MainActivity, "Broadcasting SOS (Priority $priority)...", Toast.LENGTH_SHORT).show()
                meshNetworkManager.broadcastLocalSos(
                    name = getSavedName(), phone = getSavedPhone(),
                    lat = currentLatitude, lng = currentLongitude,
                    notes = notes, priority = priority, msgId = activeLocalSosMsgId!!
                )
                dialog.dismiss()
            }
            rootLayout.addView(btn)
        }

        // Add 4 severity buttons with soft background colors
        addTriageButton("CRITICAL: Injured & Bleeding", "#FFEBEE", "#D32F2F", 2, "Injured bleeding")
        addTriageButton("CRITICAL: Trapped under debris", "#FFEBEE", "#D32F2F", 2, "Trapped injured critical")
        addTriageButton("HIGH: Medical Emergency", "#FFF3E0", "#E65100", 3, "Heart medical emergency")
        addTriageButton("MEDIUM: Safe but need assist", "#E8F5E9", "#2E7D32", 4, "Safe needing transport")

        // Cancel Button
        val btnCancel = Button(this).apply {
            text = "CANCEL"
            setTextColor(Color.parseColor("#9E9E9E"))
            background = ColorDrawable(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 }
            setOnClickListener { dialog.dismiss() }
        }
        rootLayout.addView(btnCancel)

        dialog.show()
    }

    private fun showCurvyPrecautionDialog(title: String, message: String) {
        drawerLayout.closeDrawer(GravityCompat.START)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 80, 80, 80)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 70f
            }
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(Color.parseColor("#1A237E"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }

        val messageView = TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(Color.parseColor("#424242"))
            setLineSpacing(10f, 1.2f)
            setPadding(0, 0, 0, 60)
        }

        val btnGotIt = Button(this).apply {
            text = "UNDERSTOOD"
            setTextColor(Color.parseColor("#1A237E"))
            background = GradientDrawable().apply { 
                setColor(Color.parseColor("#E8EAF6")) 
                cornerRadius = 60f 
            }
            stateListAnimator = null
            elevation = 0f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130)
        }

        rootLayout.addView(titleView)
        rootLayout.addView(messageView)
        rootLayout.addView(btnGotIt)

        val dialog = AlertDialog.Builder(this).setView(rootLayout).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        btnGotIt.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showProfileEditDialog() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 80, 80, 80)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 70f
            }
        }

        val titleView = TextView(this).apply {
            text = "👤 Edit Profile"
            textSize = 24f
            setTextColor(Color.parseColor("#1A237E"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }

        val etName = EditText(this).apply { 
            hint = "Full Name"
            setText(getSavedName())
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#F4F6F8")); cornerRadius = 40f }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
        }

        val etPhone = EditText(this).apply {
            hint = "Phone Number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(getSavedPhone())
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply { setColor(Color.parseColor("#F4F6F8")); cornerRadius = 40f }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 50 }
        }

        val btnSave = Button(this).apply {
            text = "SAVE DETAILS"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.parseColor("#1A237E")); cornerRadius = 50f }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 130)
        }

        rootLayout.addView(titleView)
        rootLayout.addView(etName)
        rootLayout.addView(etPhone)
        rootLayout.addView(btnSave)

        val dialog = AlertDialog.Builder(this).setView(rootLayout).setCancelable(false).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                sharedPreferences.edit().putString("USER_NAME", name).putString("USER_PHONE", phone).apply()
                Toast.makeText(this@MainActivity, "Profile Saved!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this@MainActivity, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    // =========================================================
    // 🎙️ INVISIBLE BACKGROUND HOTWORD DETECTION
    // =========================================================
    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            
            override fun onError(error: Int) {
                if (isListeningContinuous) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        speechRecognizer?.startListening(speechIntent)
                    }, 100)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) { checkVoiceForHotword(partialResults) }
            override fun onResults(results: Bundle?) {
                checkVoiceForHotword(results)
                if (isListeningContinuous) {
                    Handler(Looper.getMainLooper()).postDelayed({ speechRecognizer?.startListening(speechIntent) }, 100)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun checkVoiceForHotword(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.joinToString(" ")?.lowercase(Locale.getDefault()) ?: ""

        if (text.contains("emergency") && isListeningContinuous) {
            isListeningContinuous = false
            speechRecognizer?.stopListening()
            
            val voiceNotesPayload = "🎙️ VOICE TRIGGER: Emergency (Critical Assist Needed)"
            activeLocalSosMsgId = UUID.randomUUID().toString()
            Toast.makeText(this, "🚨 Voice SOS Broadcasting (Priority 1)...", Toast.LENGTH_LONG).show()

            meshNetworkManager.broadcastLocalSos(
                name = getSavedName(), phone = getSavedPhone(),
                lat = currentLatitude, lng = currentLongitude,
                notes = voiceNotesPayload, priority = 1, msgId = activeLocalSosMsgId!!
            )

            Handler(Looper.getMainLooper()).postDelayed({
                isListeningContinuous = true
                speechRecognizer?.startListening(speechIntent)
            }, 10000)
        }
    }

    // ==========================================
    // MAP AND LOCATION LOGIC 
    // ==========================================
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startLocationUpdates()
        meshNetworkManager.startMesh()
        
        setupSpeechRecognizer()
        isListeningContinuous = true
        speechRecognizer?.startListening(speechIntent)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) { updateLocationUI(location) }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (hasNetwork) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 1f, locationListener)
        if (hasGps) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1f, locationListener)

        val bestLast = (if (hasGps) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null)
            ?: (if (hasNetwork) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null)
        bestLast?.let { updateLocationUI(it) }
    }

    private fun updateLocationUI(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        hasGpsLock = true
        
        tvCoordinates.text = "GPS: ${"%.4f".format(currentLatitude)}, ${"%.4f".format(currentLongitude)}"

        mapWebView.post {
            mapWebView.evaluateJavascript("""
                if (typeof updatePosition === 'function') { updatePosition($currentLatitude, $currentLongitude); }
            """.trimIndent(), null)
        }
    }

    private fun updateMapWithVictimMarker(victimName: String, lat: Double, lng: Double) {
        mapWebView.post {
            mapWebView.evaluateJavascript("""
                if (typeof map !== 'undefined') {
                    L.marker([$lat, $lng]).addTo(map).bindPopup('<b>🚨 RELAY ALERT</b><br>$victimName').openPopup();
                }
            """.trimIndent(), null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMapView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true; allowFileAccess = true
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK 
            userAgentString = "AegisRescueHackathonApp/1.0"
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val loc = Location("dummy").apply { latitude = currentLatitude; longitude = currentLongitude }
                updateLocationUI(loc)
            }
        }
        
        val mapHtml = """
            <!DOCTYPE html> <html> <head> <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" /> 
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style> body { padding: 0; margin: 0; background: #E0E0E0; } html, body, #map { height: 100%; width: 100vw; } </style> </head>
            <body> <div id="map"></div> <script>
                var map = L.map('map', { zoomControl: false }).setView([0, 0], 2);
                L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { attribution: '© CARTO', maxZoom: 19 }).addTo(map);
                
                var userMarker = L.marker([0, 0]).addTo(map).bindPopup('<b>You</b>');

                function updatePosition(lat, lng) {
                    var newLatLng = new L.LatLng(lat, lng);
                    userMarker.setLatLng(newLatLng);
                    map.flyTo(newLatLng, 17, { animate: true, duration: 1.5 });
                }
            </script> </body> </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL("https://carto.com", mapHtml, "text/html", "UTF-8", null)
    }
}