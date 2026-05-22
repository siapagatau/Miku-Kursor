package com.farel.animecursor

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class CursorOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences

    // Cursor overlay view
    private lateinit var cursorView: ImageView
    private lateinit var cursorParams: WindowManager.LayoutParams

    // Action panel overlay view
    private lateinit var panelView: View
    private lateinit var panelParams: WindowManager.LayoutParams

    private var cursorX = 100f
    private var cursorY = 100f

    // Panel state
    private var panelMinimized = false
    private var panelOffsetX = 0
    private var panelOffsetY = 200

    // Drag state for action panel
    private var panelDragStartX = 0f
    private var panelDragStartY = 0f
    private var panelInitX = 0
    private var panelInitY = 0

    // For cursor drag action
    private var isDragMode = false
    private var dragStartX = 0f
    private var dragStartY = 0f

    companion object {
        const val CHANNEL_ID = "cursor_overlay_channel"
        const val NOTIF_ID = 1001
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("cursor_prefs", MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        setupCursorView()
        setupActionPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { windowManager.removeView(cursorView) } catch (e: Exception) {}
        try { windowManager.removeView(panelView) } catch (e: Exception) {}
    }

    // ─────────────────────────────────────────────────
    // CURSOR VIEW
    // ─────────────────────────────────────────────────

    private fun setupCursorView() {
        cursorView = ImageView(this)
        loadCursorImage()

        val size = prefs.getInt("cursor_size", 120)
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        cursorParams = WindowManager.LayoutParams(
            size, size,
            cursorX.toInt(), cursorY.toInt(),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cursorX.toInt()
            y = cursorY.toInt()
        }

        windowManager.addView(cursorView, cursorParams)
    }

    fun loadCursorImage() {
        val uriString = prefs.getString("cursor_image_uri", null)
        val opacity = prefs.getInt("cursor_opacity", 255)
        val size = prefs.getInt("cursor_size", 120)

        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
                    cursorView.setImageBitmap(scaled)
                    cursorView.imageAlpha = opacity
                }
            } catch (e: Exception) {
                setDefaultCursor()
            }
        } else {
            setDefaultCursor()
        }

        val lp = cursorView.layoutParams as? WindowManager.LayoutParams
        lp?.width = size
        lp?.height = size
        try { windowManager.updateViewLayout(cursorView, lp) } catch (e: Exception) {}
    }

    private fun setDefaultCursor() {
        // Draw a cute anime-style arrow cursor programmatically
        val size = prefs.getInt("cursor_size", 120)
        val opacity = prefs.getInt("cursor_opacity", 255)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Main arrow path
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val scale = size / 120f

        // Shadow
        paint.color = Color.argb(80, 0, 0, 0)
        paint.style = Paint.Style.FILL
        val shadowPath = Path().apply {
            moveTo(8f * scale, 8f * scale)
            lineTo(8f * scale, 80f * scale)
            lineTo(30f * scale, 58f * scale)
            lineTo(50f * scale, 100f * scale)
            lineTo(60f * scale, 96f * scale)
            lineTo(40f * scale, 54f * scale)
            lineTo(68f * scale, 54f * scale)
            close()
        }
        canvas.drawPath(shadowPath, paint)

        // White fill
        paint.color = Color.WHITE
        val arrowPath = Path().apply {
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

        // Black outline
        paint.color = Color.argb(200, 20, 20, 40)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * scale
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(arrowPath, paint)

        // Glowing tip
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(180, 150, 120, 255)
        canvas.drawCircle(7f * scale, 7f * scale, 6f * scale, paint)

        cursorView.setImageBitmap(bmp)
        cursorView.imageAlpha = opacity
    }

    fun moveCursor(x: Float, y: Float) {
        cursorX = x
        cursorY = y
        cursorParams.x = x.toInt()
        cursorParams.y = y.toInt()
        try { windowManager.updateViewLayout(cursorView, cursorParams) } catch (e: Exception) {}
    }

    // ─────────────────────────────────────────────────
    // ACTION PANEL
    // ─────────────────────────────────────────────────

    private fun setupActionPanel() {
        val inflater = LayoutInflater.from(this)
        panelView = inflater.inflate(R.layout.layout_cursor_panel, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelOffsetX
            y = panelOffsetY
        }

        windowManager.addView(panelView, panelParams)
        setupPanelButtons()
        setupPanelDrag()
    }

    private fun setupPanelButtons() {
        // MOVE cursor (joystick simulation)
        val btnUp = panelView.findViewById<ImageButton>(R.id.btn_up)
        val btnDown = panelView.findViewById<ImageButton>(R.id.btn_down)
        val btnLeft = panelView.findViewById<ImageButton>(R.id.btn_left)
        val btnRight = panelView.findViewById<ImageButton>(R.id.btn_right)

        val moveStep = 20f
        val handler = Handler(Looper.getMainLooper())

        fun makeRepeatingMover(dx: Float, dy: Float): Runnable {
            var r: Runnable? = null
            r = Runnable {
                moveCursor(cursorX + dx, cursorY + dy)
                handler.postDelayed(r!!, 50)
            }
            return r
        }

        val movers = mapOf(
            btnUp to makeRepeatingMover(0f, -moveStep),
            btnDown to makeRepeatingMover(0f, moveStep),
            btnLeft to makeRepeatingMover(-moveStep, 0f),
            btnRight to makeRepeatingMover(moveStep, 0f)
        )

        for ((btn, mover) in movers) {
            btn.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> handler.post(mover)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(mover)
                }
                true
            }
        }

        // CLICK
        panelView.findViewById<Button>(R.id.btn_click).setOnClickListener {
            CursorAccessibilityService.performClick(
                cursorX + prefs.getInt("cursor_size", 120) * 0.04f,
                cursorY + prefs.getInt("cursor_size", 120) * 0.04f
            )
        }

        // DOUBLE CLICK
        panelView.findViewById<Button>(R.id.btn_double_click).setOnClickListener {
            CursorAccessibilityService.performDoubleClick(
                cursorX + prefs.getInt("cursor_size", 120) * 0.04f,
                cursorY + prefs.getInt("cursor_size", 120) * 0.04f
            )
        }

        // LONG PRESS
        panelView.findViewById<Button>(R.id.btn_long_press).setOnClickListener {
            CursorAccessibilityService.performLongPress(
                cursorX + prefs.getInt("cursor_size", 120) * 0.04f,
                cursorY + prefs.getInt("cursor_size", 120) * 0.04f
            )
        }

        // DRAG - double-click and hold to mark start, then move cursor, press drag to confirm
        var dragModeActive = false
        val btnDrag = panelView.findViewById<Button>(R.id.btn_drag)
        btnDrag.setOnClickListener {
            if (!dragModeActive) {
                dragModeActive = true
                dragStartX = cursorX + prefs.getInt("cursor_size", 120) * 0.04f
                dragStartY = cursorY + prefs.getInt("cursor_size", 120) * 0.04f
                btnDrag.text = "✓ Drop"
                btnDrag.alpha = 1f
                // Pulse animation to indicate drag mode
                ObjectAnimator.ofFloat(btnDrag, "scaleX", 1f, 1.1f, 1f).apply {
                    duration = 600
                    repeatCount = ObjectAnimator.INFINITE
                    start()
                }
            } else {
                // Execute drag from start to current cursor
                dragModeActive = false
                btnDrag.text = "⇥ Drag"
                btnDrag.clearAnimation()
                btnDrag.scaleX = 1f
                CursorAccessibilityService.performDrag(
                    dragStartX, dragStartY,
                    cursorX + prefs.getInt("cursor_size", 120) * 0.04f,
                    cursorY + prefs.getInt("cursor_size", 120) * 0.04f
                )
            }
        }

        // SCROLL UP
        panelView.findViewById<Button>(R.id.btn_scroll_up).setOnClickListener {
            CursorAccessibilityService.performScroll(cursorX, cursorY, -1)
        }

        // SCROLL DOWN
        panelView.findViewById<Button>(R.id.btn_scroll_down).setOnClickListener {
            CursorAccessibilityService.performScroll(cursorX, cursorY, 1)
        }

        // MINIMIZE / EXPAND
        val btnMinimize = panelView.findViewById<ImageButton>(R.id.btn_minimize)
        val panelContent = panelView.findViewById<View>(R.id.panel_content)
        val panelHeader = panelView.findViewById<View>(R.id.panel_header)

        btnMinimize.setOnClickListener {
            panelMinimized = !panelMinimized
            if (panelMinimized) {
                panelContent.visibility = View.GONE
                btnMinimize.setImageResource(android.R.drawable.arrow_down_float)
                panelParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                panelParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                panelContent.visibility = View.VISIBLE
                btnMinimize.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                panelParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                panelParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            try { windowManager.updateViewLayout(panelView, panelParams) } catch (e: Exception) {}
        }
    }

    private fun setupPanelDrag() {
        val header = panelView.findViewById<View>(R.id.panel_header)

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    panelDragStartX = event.rawX
                    panelDragStartY = event.rawY
                    panelInitX = panelParams.x
                    panelInitY = panelParams.y
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = panelInitX + (event.rawX - panelDragStartX).toInt()
                    panelParams.y = panelInitY + (event.rawY - panelDragStartY).toInt()
                    try { windowManager.updateViewLayout(panelView, panelParams) } catch (e: Exception) {}
                }
            }
            true
        }
    }

    // ─────────────────────────────────────────────────
    // NOTIFICATION
    // ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Anime Cursor Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Cursor overlay service is running" }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🖱️ Anime Cursor Active")
            .setContentText("Tap to open settings")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
