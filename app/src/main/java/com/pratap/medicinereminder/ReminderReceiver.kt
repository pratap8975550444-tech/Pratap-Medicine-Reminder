package com.pratap.medicinereminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicineName = intent.getStringExtra("MEDICINE_NAME") ?: "औषध"
        val hour = intent.getIntExtra("HOUR", -1)
        val minute = intent.getIntExtra("MINUTE", -1)
        val requestCode = intent.getIntExtra("REQUEST_CODE", 0)

        val channelId = "medicine_alarm_channel_v2"
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Medicine Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "औषधे घेण्याच्या वेळेचा अलार्म"
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("औषध घेण्याची वेळ झाली आहे!")
            .setContentText("कृपया तुमचे औषध घ्या: $medicineName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(requestCode, notification)

        if (hour != -1 && minute != -1) {
            scheduleNextDayAlarm(context, medicineName, hour, minute, requestCode)
        }
    }

    private fun scheduleNextDayAlarm(
        context: Context,
        medicineName: String,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DATE, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nextIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("MEDICINE_NAME", medicineName)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
            putExtra("REQUEST_CODE", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}
