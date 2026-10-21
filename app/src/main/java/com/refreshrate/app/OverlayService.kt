package com.refreshrate.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Mostra a taxa de atualização atual do painel numa pequena etiqueta por cima
 * de qualquer app. Arraste para mudar de lugar; a posição fica salva.
 *
 * O texto só é redesenhado quando o modo do display muda, então a sobreposição
 * não força o painel a ficar numa taxa mais alta no modo dinâmico.
 */
class OverlayService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var view: TextView? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(id: Int) { if (id == Display.DEFAULT_DISPLAY) update() }
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).overlayEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) show()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        view?.let { runCatching { wm?.removeView(it) } }
        view = null
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        val windowManager = getSystemService(WindowManager::class.java)
        wm = windowManager
        val prefs = Prefs(this)
        val dp = resources.displayMetrics.density

        val label = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(0x99000000.toInt())
            }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.overlayX.takeIf { it >= 0 } ?: (16 * dp).toInt()
            y = prefs.overlayY.takeIf { it >= 0 } ?: (64 * dp).toInt()
        }

        // Arrastar para reposicionar.
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        label.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (startX + (e.rawX - touchX)).toInt().coerceAtLeast(0)
                    lp.y = (startY + (e.rawY - touchY)).toInt().coerceAtLeast(0)
                    windowManager.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.overlayX = lp.x; prefs.overlayY = lp.y
                    true
                }
                else -> false
            }
        }

        windowManager.addView(label, lp)
        view = label
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, main)
        update()
    }

    /** Taxa do modo ativo do painel (não a taxa de renderização de um app específico). */
    private fun update() {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return
        view?.text = "${display.mode.refreshRate.roundToInt()} Hz"
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_hz)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.overlay_stop), stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.refreshrate.app.overlay.STOP"

        /** Liga ou desliga a sobreposição conforme a preferência e a permissão. */
        fun sync(ctx: Context) {
            val intent = Intent(ctx, OverlayService::class.java)
            if (Prefs(ctx).overlayEnabled && Settings.canDrawOverlays(ctx)) {
                ContextCompat.startForegroundService(ctx, intent)
            } else {
                ctx.stopService(intent)
            }
        }
    }
}
