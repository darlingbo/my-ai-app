package com.myai.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val task = intent?.getStringExtra("task") ?: "Reminder"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(context, 0, open, flags)
        val n = NotificationCompat.Builder(context, "reminders")
            .setContentTitle("⏰ Reminder")
            .setContentText(task)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(pi).setDefaults(NotificationCompat.DEFAULT_ALL).build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }
}
