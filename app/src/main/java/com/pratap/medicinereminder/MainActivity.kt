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
import android.view.View
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

    private var hour = -1
    private var minute = -1
    private val list = ArrayList<String>()
    private val codes = ArrayList<Int>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val uName = pref.getString("userName", "Pratap")
        val uAge = pref.getString("userAge", "")
        supportActionBar?.title = "$uName ($uAge)"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
        }

        val topSpace = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 160)
        }
        layout.addView(topSpace)

        val etName = EditText(this).apply {
            hint = "1. औषधाचे नाव (उदा. Pacimol)"
            setBackgroundResource(android.R.drawable.edit_text)
        }

        val etDose = EditText(this).apply {
            hint = "2. डोस प्रमाण (उदा. 500 mg)"
            setBackgroundResource(android.R.drawable.edit_text)
        }

        val tvTime = TextView(this).apply {
            text = "3. वेळ निवडलेली नाही"
            textSize = 16f
            setPadding(10, 15, 10, 15)
        }

        val btnTime = Button(this).apply { text = "वेळ निवडा (Time)" }
        val btnSave = Button(this).apply { text = "नवीन औषध सेव्ह करा" }
        val lv = ListView(this)

        layout.addView(etName)
        layout.addView(etDose)
        layout.addView(tvTime)
        layout.addView(btnTime)
        layout.addView(btnSave)
        layout.addView(lv)
        setContentView(layout)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
        lv.adapter = adapter

        loadData()
        askPerm()

        btnTime.setOnClickListener {
            val c = Calendar.getInstance()
            TimePickerDialog(this, { _, h, m ->
                hour = h
                minute = m
                tvTime.text = String.format(Locale.getDefault(), "वेळ: %02d:%02d", h, m)
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
        }

        btnSave.setOnClickListener {
            val n = etName.text.toString().trim()
            val d = etDose.text.toString().trim()
            if (n.isEmpty() || d.isEmpty() || hour == -1) {
                Toast.makeText(this, "नाव, डोस आणि वेळ तिन्ही भरा", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addAlarm(n, d, hour, minute)
            etName.text.clear()
            etDose.text.clear()
            tvTime.text = "3. वेळ निवडलेली नाही"
            hour = -1
        }

        lv.setOnItemClickListener { _, _, pos, _ ->
            val items = arrayOf("Edit (बदल करा)", "Delete (काढून टाका)")
            AlertDialog.Builder(this)
                .setTitle("पर्याय निवडा")
                .setItems(items) { _, w ->
                    if (w == 0) editItem(pos) else deleteItem(pos)
                }.show()
        }
    }

    private fun editItem(pos: Int) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val old = list.getOrElse(pos) { "" }
        val p1 = old.split(" | वेळ: ")
        val p2 = p1.getOrElse(0) { "" }.split(" - ")

        val e1 = EditText(this).apply { setText(p2.getOrElse(0) { "" }) }
        val e2 = EditText(this).apply { setText(p2.getOrElse(1) { "" }) }
        val e3 = EditText(this).apply { setText(p1.getOrElse(1) { "10:00" }) }
        box.addView(e1); box.addView(e2); box.addView(e3)

        AlertDialog.Builder(this)
            .setTitle("औषध बदला")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                list.set(pos, "${e1.text} - ${e2.text} | वेळ: ${e3.text}")
                adapter.notifyDataSetChanged()
                saveData()
            }.setNegativeButton("Cancel", null).show()
    }
    private fun addAlarm(n: String, d: String, h: Int, m: Int) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }
        val code = System.currentTimeMillis().toInt()
        val i = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("MEDICINE_NAME", "$n ($d)")
            putExtra("HOUR", h)
            putExtra("MINUTE", m)
            putExtra("REQUEST_CODE", code)
        }
        val pi = PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)

        val t = String.format(Locale.getDefault(), "%02d:%02d", h, m)
        list.add("$n - $d | वेळ: $t")
        codes.add(code)
        adapter.notifyDataSetChanged()
        saveData()
        Toast.makeText(this, "सेव्ह झाले!", Toast.LENGTH_SHORT).show()
    }

    private fun deleteItem(pos: Int) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(this, codes.getOrElse(pos) { 0 }, Intent(this, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        list.removeAt(pos)
        codes.removeAt(position = pos)
        adapter.notifyDataSetChanged()
        saveData()
    }

    private fun saveData() {
        val e = getSharedPreferences("MedicinePrefs", Context.MODE_PRIVATE).edit()
        e.putInt("COUNT", list.size)
        for (i in list.indices) {
            e.putString("ITEM_$i", list.getOrElse(i) { "" })
            e.putInt("CODE_$i", codes.getOrElse(i) { 0 })
        }
        e.apply()
    }

    private fun loadData() {
        val p = getSharedPreferences("MedicinePrefs", Context.MODE_PRIVATE)
        list.clear(); codes.clear()
        val count = p.getInt("COUNT", 0)
        if (count == 0 && !p.getBoolean("INIT_DONE", false)) {
            val defaultMeds = arrayOf(
                "PANGRAF - 4 mg | वेळ: 10:00",
                "MYCEPT - 360/720 mg | वेळ: 10:00",
                "OMNACORTIL - 10 mg | वेळ: 10:00",
                "VALGAN - 450 mg | वेळ: 10:00",
                "PAN - 40 mg | वेळ: 10:00",
                "PACIMOL - 500 mg | वेळ: 10:00",
                "STAMLO - 2.5 mg | वेळ: 10:00",
                "BACTRIM DS - 1 Tab | वेळ: 10:00",
                "SUPRACAL - 1 Tab | वेळ: 10:00",
                "ECOSPRIN - 1 Tab | वेळ: 22:00",
                "BECOSULE - 1 Cap | वेळ: 10:00",
                "CETOLAC - 1 Tab | वेळ: 10:00",
                "CANDID MOUTH WASH - Use | वेळ: 10:00"
            )
            for (med in defaultMeds) {
                list.add(med)
                codes.add(0)
            }
                        p.edit().putBoolean("INIT_DONE", true).apply()
            saveData()
        } else {
            for (i in 0 until count) {
                p.getString("ITEM_$i", null)?.let { list.add(it); codes.add(p.getInt("CODE_$i", 0)) }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun askPerm() {
        if (Build.VERSION.SDK_INT >= 33) {
            val pm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, pm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(pm), 101)
            }
        }
    }
}

