package com.myai.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/** Small Messenger-style chat head. Tap → opens chat. Drag → snaps to edge. */
class BubbleService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var view: View
    private lateinit var params: WindowManager.LayoutParams
    private var screenW = 0

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        foreground()
        addBubble()
    }

    private fun foreground() {
        val ch = "bubble"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(ch, "AI Bubble", NotificationManager.IMPORTANCE_MIN))
        }
        val n = NotificationCompat.Builder(this, ch)
            .setContentTitle("Your AI is here 💬")
            .setContentText("Tap the bubble to chat anytime")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun addBubble() {
        view = LayoutInflater.from(this).inflate(R.layout.bubble, null)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START
        screenW = resources.displayMetrics.widthPixels
        params.x = screenW - 160
        params.y = resources.displayMetrics.heightPixels / 2
        wm.addView(view, params)
        setupTouch()
    }

    private var lx = 0; private var ly = 0; private var tx = 0f; private var ty = 0f; private var drag = false
    private fun setupTouch() {
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { lx = params.x; ly = params.y; tx = e.rawX; ty = e.rawY; drag = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 14 || abs(dy) > 14) drag = true
                    params.x = lx + dx; params.y = ly + dy; wm.updateViewLayout(view, params); true
                }
                MotionEvent.ACTION_UP -> {
                    if (!drag) openChat()
                    else { params.x = if (params.x + 60 < screenW / 2) 0 else screenW - 130; wm.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun openChat() {
        startActivity(Intent(this, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY
    override fun onDestroy() { super.onDestroy(); try { wm.removeView(view) } catch (_: Exception) {} }
}
