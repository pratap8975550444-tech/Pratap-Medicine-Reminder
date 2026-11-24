package com.pratap.medicinereminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("pratap_korake_med_app_v2", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("med_list", "[]"))
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (!obj.optBoolean("enabled", true)) continue

            val id = obj.getInt("id")
            val med = obj.getString("medicine")
            val mg = obj.optString("mg", "-")
            val note = obj.optString("note", "")
            val fullDose = if (mg != "-") "$mg - $note" else note
            val hour = obj.getInt("hour")
            val minute = obj.getInt("minute")

            val alarmIntent = Intent(context, MedicineAlarmReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("medicine", med)
                putExtra("dose", fullDose)
                putExtra("hour", hour)
                putExtra("minute", minute)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, id, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}
