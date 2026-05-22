package com.farel.animecursor

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    private lateinit var btnToggleService: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnPickCursor: Button
    private lateinit var btnResetCursor: Button
    private lateinit var seekSize: SeekBar
    private lateinit var seekOpacity: SeekBar
    private lateinit var tvSize: TextView
    private lateinit var tvOpacity: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var statusOverlay: TextView
    private lateinit var statusAccessibility: TextView
    private lateinit var statusService: TextView

    private val pickCursorLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                prefs.edit().putString("cursor_image_uri", uri.toString()).apply()
                updatePreview()
                // Notify service to reload
                if (CursorOverlayService.isRunning) {
                    stopService(Intent(this, CursorOverlayService::class.java))
                    startForegroundService(Intent(this, CursorOverlayService::class.java))
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("cursor_prefs", MODE_PRIVATE)

        initViews()
        setupListeners()
        updateStatusUI()
        updatePreview()
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    private fun initViews() {
        btnToggleService = findViewById(R.id.btn_toggle_service)
        btnGrantOverlay = findViewById(R.id.btn_grant_overlay)
        btnGrantAccessibility = findViewById(R.id.btn_grant_accessibility)
        btnPickCursor = findViewById(R.id.btn_pick_cursor)
        btnResetCursor = findViewById(R.id.btn_reset_cursor)
        seekSize = findViewById(R.id.seek_size)
        seekOpacity = findViewById(R.id.seek_opacity)
        tvSize = findViewById(R.id.tv_size)
        tvOpacity = findViewById(R.id.tv_opacity)
        ivPreview = findViewById(R.id.iv_cursor_preview)
        statusOverlay = findViewById(R.id.status_overlay)
        statusAccessibility = findViewById(R.id.status_accessibility)
        statusService = findViewById(R.id.status_service)

        seekSize.max = 280
        seekSize.progress = prefs.getInt("cursor_size", 120) - 40
        tvSize.text = "Size: ${prefs.getInt("cursor_size", 120)}px"

        seekOpacity.max = 255
        seekOpacity.progress = prefs.getInt("cursor_opacity", 255)
        tvOpacity.text = "Opacity: ${(prefs.getInt("cursor_opacity", 255) / 255f * 100).toInt()}%"
    }

    private fun setupListeners() {
        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnToggleService.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant overlay permission first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (CursorAccessibilityService.instance == null) {
                Toast.makeText(this, "Please enable Accessibility Service first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (CursorOverlayService.isRunning) {
                stopService(Intent(this, CursorOverlayService::class.java))
                Toast.makeText(this, "Cursor stopped", Toast.LENGTH_SHORT).show()
            } else {
                startForegroundService(Intent(this, CursorOverlayService::class.java))
                Toast.makeText(this, "Cursor started! 🌸", Toast.LENGTH_SHORT).show()
            }
            // Delay update for service to start/stop
            btnToggleService.postDelayed({ updateStatusUI() }, 400)
        }

        btnPickCursor.setOnClickListener {
            pickCursorLauncher.launch(arrayOf("image/*"))
        }

        btnResetCursor.setOnClickListener {
            prefs.edit().remove("cursor_image_uri").apply()
            updatePreview()
            Toast.makeText(this, "Reset to default cursor", Toast.LENGTH_SHORT).show()
        }

        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 40
                prefs.edit().putInt("cursor_size", size).apply()
                tvSize.text = "Size: ${size}px"
                updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                restartServiceIfRunning()
            }
        })

        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit().putInt("cursor_opacity", progress).apply()
                tvOpacity.text = "Opacity: ${(progress / 255f * 100).toInt()}%"
                updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                restartServiceIfRunning()
            }
        })
    }

    private fun updateStatusUI() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = CursorAccessibilityService.instance != null
        val running = CursorOverlayService.isRunning

        statusOverlay.text = if (hasOverlay) "✅ Overlay: Granted" else "❌ Overlay: Not granted"
        statusOverlay.setTextColor(if (hasOverlay) Color.parseColor("#66EE88") else Color.parseColor("#EE6666"))

        statusAccessibility.text = if (hasAccessibility) "✅ Accessibility: Active" else "❌ Accessibility: Disabled"
        statusAccessibility.setTextColor(if (hasAccessibility) Color.parseColor("#66EE88") else Color.parseColor("#EE6666"))

        statusService.text = if (running) "🟢 Service: Running" else "⚫ Service: Stopped"
        statusService.setTextColor(if (running) Color.parseColor("#66EE88") else Color.parseColor("#AAAAAA"))

        btnToggleService.text = if (running) "⏹ Stop Cursor" else "▶ Start Cursor"
    }

    private fun updatePreview() {
        val size = prefs.getInt("cursor_size", 120)
        val opacity = prefs.getInt("cursor_opacity", 255)
        val uriString = prefs.getString("cursor_image_uri", null)

        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    ivPreview.setImageBitmap(bmp)
                }
            } catch (e: Exception) {
                drawDefaultCursorPreview(size)
            }
        } else {
            drawDefaultCursorPreview(size)
        }
        ivPreview.imageAlpha = opacity
    }

    private fun drawDefaultCursorPreview(size: Int) {
        val previewSize = 200
        val bmp = android.graphics.Bitmap.createBitmap(previewSize, previewSize, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val scale = previewSize / 120f

        paint.color = Color.WHITE
        paint.style = android.graphics.Paint.Style.FILL
        val arrowPath = android.graphics.Path().apply {
            moveTo(5f * scale, 5f * scale)
            lineTo(5f * scale, 77f * scale)
            lineTo(27f * scale, 55f * scale)
            lineTo(47f * scale, 97f * scale)
            lineTo(57f * scale, 93f * scale)
            lineTo(37f * scale, 51f * scale)
            lineTo(65f * scale, 51f * scale)
            close()
        }
        canvas.drawPath(arrowPath, paint)
        paint.color = Color.argb(200, 20, 20, 40)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f * scale
        canvas.drawPath(arrowPath, paint)
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = Color.argb(180, 150, 120, 255)
        canvas.drawCircle(7f * scale, 7f * scale, 6f * scale, paint)
        ivPreview.setImageBitmap(bmp)
    }

    private fun restartServiceIfRunning() {
        if (CursorOverlayService.isRunning) {
            stopService(Intent(this, CursorOverlayService::class.java))
            startForegroundService(Intent(this, CursorOverlayService::class.java))
        }
    }
}
