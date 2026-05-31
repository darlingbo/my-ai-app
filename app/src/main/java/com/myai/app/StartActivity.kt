package com.myai.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StartActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("myai", Context.MODE_PRIVATE)

        // Must be logged in first
        if (prefs.getString("user_id", null).isNullOrEmpty()) {
            startActivity(Intent(this, AuthActivity::class.java)); finish(); return
        }
        // Creator (admin account) can use all platforms — skip the lock-in chooser
        if (prefs.getBoolean("is_creator", false) || prefs.getBoolean("chosen", false)) {
            openChat(); return
        }

        setContentView(R.layout.activity_start)
        val hi = findViewById<TextView>(R.id.creatorLink)
        hi.text = "Logged in as ${prefs.getString("name", "you")}"
        hi.setOnClickListener(null)
        findViewById<Button>(R.id.pickStudent).setOnClickListener { choose("student") }
        findViewById<Button>(R.id.pickBusiness).setOnClickListener { choose("business") }
        findViewById<Button>(R.id.pickDaily).setOnClickListener { choose("daily") }
    }

    private fun choose(platform: String) {
        prefs.edit().putString("platform", platform).putBoolean("chosen", true).apply()
        openChat()
    }

    private fun openChat() {
        startActivity(Intent(this, ChatActivity::class.java)); finish()
    }
}
