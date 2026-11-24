package com.pratap.medicinereminder

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etMedicineName: EditText
    private lateinit var tvSelectedTime: TextView
    private lateinit var btnPickTime: Button
    private lateinit var btnSetReminder: Button
    private lateinit var lvReminders: ListView

    private var selectedHour: Int = -1
    private var selectedMinute: Int = -1

    private val reminderList = ArrayList<String>()
    private val requestCodeList = ArrayList<Int>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etMedicineName = findViewById(R.id.etMedicineName)
        tvSelectedTime = findViewById(R.id.tvSelectedTime)
        btnPickTime = findViewById(R.id.btnPickTime)
        btnSetReminder = findViewById(R.id.btnSetReminder)
        lvReminders = findViewById(R.id.lvReminders)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, reminderList)
        lvReminders.adapter = adapter

        loadSavedReminders()
        requestNotificationPermission()

        // वेळ निवडण्यासाठी TimePicker उघडणे
        btnPickTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                tvSelectedTime.text = String.format(
                    Locale.getDefault(),
                    "निवडलेली वेळ: %02d:%02d",
                    selectedHour,
                    selectedMinute
                )
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }

        // रिमाइंडर सेट करणे
        btnSetReminder.setOnClickListener {
            val medicineName = etMedicineName.text.toString().trim()

            if (medicineName.isEmpty()) {
                Toast.makeText(this, "कृपया औषधाचे नाव टाका", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedHour == -1 || selectedMinute == -1) {
                Toast.makeText(this, "कृपया वेळ निवडा", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scheduleMedicineReminder(medicineName, selectedHour, selectedMinute)
        }

        // यादीतील औषधावर क्लिक केल्यास रिमाइंडर डिलीट करणे
        lvReminders.setOnItemClickListener { _, _, position, _ ->
            AlertDialog.Builder(this)
                .setTitle("रिमाइंडर रद्द करा")
                .setMessage("तुम्हाला हा रिमाइंडर काढून टाकायचा आहे का?")
                .setPositiveButton("होय") { _, _ ->
                    cancelReminder(position)
                }
                .setNegativeButton("नाही", null)
                .show()
        }
    }

    private fun scheduleMedicineReminder(medicineName: String, hour: Int, minute: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(this, "कृपया Exact Alarm ची परवानगी द्या", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        val requestCode = System.currentTimeMillis().toInt()
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("MEDICINE_NAME", medicineName)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
            putExtra("REQUEST_CODE", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        val timeText = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        val displayItem = "$medicineName - दररोज $timeText वाजता"

        reminderList.add(displayItem)
        requestCodeList.add(requestCode)
        adapter.notifyDataSetChanged()
        saveReminders()

        Toast.makeText(this, "रिमाइंडर सेट झाला: $displayItem", Toast.LENGTH_SHORT).show()
        etMedicineName.text.clear()
    }

    private fun cancelReminder(position: Int) {
        val requestCode = requestCodeList[position]
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        reminderList.removeAt(position)
        requestCodeList.removeAt(position)
        adapter.notifyDataSetChanged()
        saveReminders()

        Toast.makeText(this, "रिमाइंडर रद्द केला", Toast.LENGTH_SHORT).show()
    }

    private fun saveReminders() {
        val prefs = getSharedPreferences("MedicinePrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("COUNT", reminderList.size)
        for (i in reminderList.indices) {
            editor.putString("ITEM_$i", reminderList[i])
            editor.putInt("CODE_$i", requestCodeList[i])
        }
        editor.apply()
    }

    private fun loadSavedReminders() {
        val prefs = getSharedPreferences("MedicinePrefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("COUNT", 0)
        reminderList.clear()
        requestCodeList.clear()
        for (i in 0 until count) {
            val item = prefs.getString("ITEM_$i", null)
            val code = prefs.getInt("CODE_$i", 0)
            if (item != null) {
                reminderList.add(item)
                requestCodeList.add(code)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
