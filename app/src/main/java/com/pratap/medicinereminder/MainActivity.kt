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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var selectedHour: Int = -1
    private var selectedMinute: Int = -1

    private val reminderList = ArrayList<String>()
    private val requestCodeList = ArrayList<Int>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // प्रोफाईलचे नाव वरच्या पट्टीवर दाखवा
        val profilePref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val userName = profilePref.getString("userName", "Pratap")
        val userAge = profilePref.getString("userAge", "")
        supportActionBar?.title = "$userName ($userAge वर्षे)"

        // थेट कोटलिनमधूनच तिन्ही बॉक्स आणि बटणे तयार करणे
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val etMedicineName = EditText(this).apply {
            hint = "1. औषधाचे नाव टाका (उदा. Pacimol)"
            setBackgroundResource(android.R.drawable.edit_text)
        }

        val etDosage = EditText(this).apply {
            hint = "2. डोस प्रमाण टाका (उदा. 500 mg)"
            setBackgroundResource(android.R.drawable.edit_text)
        }

        val tvSelectedTime = TextView(this).apply {
            text = "3. वेळ निवडलेली नाही"
            textSize = 18f
            setPadding(10, 20, 10, 20)
        }

        val btnPickTime = Button(this).apply {
            text = "वेळ निवडा (Select Time)"
        }

        val btnSetReminder = Button(this).apply {
            text = "औषध सेव्ह करा"
        }

        val lvReminders = ListView(this)

        mainLayout.addView(etMedicineName)
        mainLayout.addView(etDosage)
        mainLayout.addView(tvSelectedTime)
        mainLayout.addView(btnPickTime)
        mainLayout.addView(btnSetReminder)
        mainLayout.addView(lvReminders)

        setContentView(mainLayout)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, reminderList)
        lvReminders.adapter = adapter

        loadSavedReminders()
        requestNotificationPermission()

        // वेळ निवडणे
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

        // औषध सेव्ह करणे
        btnSetReminder.setOnClickListener {
            val name = etMedicineName.text.toString().trim()
            val dose = etDosage.text.toString().trim()

            if (name.isEmpty() || dose.isEmpty()) {
                Toast.makeText(this, "कृपया नाव आणि डोस दोन्ही भरा", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedHour == -1) {
                Toast.makeText(this, "कृपया वेळ निवडा", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scheduleMedicineReminder(name, dose, selectedHour, selectedMinute)
            etMedicineName.text.clear()
            etDosage.text.clear()
            tvSelectedTime.text = "3. वेळ निवडलेली नाही"
            selectedHour = -1
        }

        // यादीतील औषधावर क्लिक केल्यास Edit किंवा Delete करणे
        lvReminders.setOnItemClickListener { _, _, position, _ ->
            val options = arrayOf("Edit (बदल करा)", "Delete (डिलीट करा)")
            AlertDialog.Builder(this)
                .setTitle("पर्याय निवडा")
                .setItems(options) { _, which ->
                    if (which == 0) showEditDialog(position)
                    else cancelReminder(position)
                }
                .show()
        }
    }

    private fun showEditDialog(position: Int) {
        val currentText = reminderList.getOrElse(position) { "" }
        val mainParts = currentText.split(" | वेळ: ")
        val firstPart = mainParts.getOrElse(0) { "" }
        val nameAndDose = firstPart.split(" - ")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val inputName = EditText(this).apply { setText(nameAndDose.getOrElse(0) { "" }) }
        val inputDose = EditText(this).apply { setText(nameAndDose.getOrElse(1) { "" }) }
        val inputTime = EditText(this).apply { setText(mainParts.getOrElse(1) { "08:00" }) }

        layout.addView(inputName)
        layout.addView(inputDose)
        layout.addView(inputTime)

        AlertDialog.Builder(this)
            .setTitle("औषध बदला")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = inputName.text.toString().trim()
                val newDose = inputDose.text.toString().trim()
                val newTime = inputTime.text.toString().trim()
                if (newName.isNotEmpty() && newDose.isNotEmpty()) {
                    reminderList.set(position, "$newName - $newDose | वेळ: $newTime")
                    adapter.notifyDataSetChanged()
                    saveReminders()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scheduleMedicineReminder(name: String, dose: String, hour: Int, minute: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        val requestCode = System.currentTimeMillis().toInt()
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("MEDICINE_NAME", "$name ($dose)")
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
            putExtra("REQUEST_CODE", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

        val timeText = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        reminderList.add("$name - $dose | वेळ: $timeText")
        requestCodeList.add(requestCode)
        adapter.notifyDataSetChanged()
        saveReminders()
        Toast.makeText(this, "सेव्ह झाले!", Toast.LENGTH_SHORT).show()
    }

    private fun cancelReminder(position: Int) {
        val requestCode = requestCodeList.getOrElse(position) { 0 }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, Intent(this, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        reminderList.removeAt(position)
        requestCodeList.removeAt(position)
        adapter.notifyDataSetChanged()
        saveReminders()
        Toast.makeText(this, "डिलीट केले", Toast.LENGTH_SHORT).show()
    }

    private fun saveReminders() {
        val prefs = getSharedPreferences("MedicinePrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("COUNT", reminderList.size)
        for (i in reminderList.indices) {
            editor.putString("ITEM_$i", reminderList.getOrElse(i) { "" })
            editor.putInt("CODE_$i", requestCodeList.getOrElse(i) { 0 })
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
