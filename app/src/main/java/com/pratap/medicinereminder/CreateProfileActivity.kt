package com.pratap.medicinereminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class CreateProfileActivity : AppCompatActivity() {

    private var profileImageUri: String = ""
    private lateinit var ivProfilePic: ImageView

    // गॅलरीमधून प्रोफाईल फोटो निवडण्यासाठी
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            profileImageUri = uri.toString()
            ivProfilePic.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // १. तपासा की युजरची प्रोफाईल आधीच बनलेली आहे का?
        val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        if (sharedPref.getBoolean("isProfileCreated", false)) {
            // प्रोफाईल आधीच असेल तर थेट MainActivity उघडा
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_create_profile)

        ivProfilePic = findViewById(R.id.ivProfilePic)
        val etUserName = findViewById<EditText>(R.id.etUserName)
        val etUserAge = findViewById<EditText>(R.id.etUserAge)
        val btnSaveProfile = findViewById<Button>(R.id.btnSaveProfile)

        // फोटोवर क्लिक केल्यास गॅलरी उघडेल
        ivProfilePic.setOnClickListener {
            pickImage.launch("image/*")
        }

        // प्रोफाईल सेव्ह करा बटण
        btnSaveProfile.setOnClickListener {
            val name = etUserName.text.toString().trim()
            val age = etUserAge.text.toString().trim()

            if (name.isEmpty()) {
                etUserName.error = "कृपया तुमचे नाव टाका"
                return@setOnClickListener
            }

            if (age.isEmpty()) {
                etUserAge.error = "कृपया वय टाका"
                return@setOnClickListener
            }

            // प्रोफाईल माहिती कायमस्वरूपी सेव्ह करा
            sharedPref.edit().apply {
                putString("userName", name)
                putString("userAge", age)
                putString("profilePhoto", profileImageUri)
                putBoolean("isProfileCreated", true)
                apply()
            }

            Toast.makeText(this, "प्रोफाईल तयार झाली!", Toast.LENGTH_SHORT).show()

            // मुख्य औषधांच्या स्क्रीनवर जा
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
