package com.myai.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class BuildActivity : AppCompatActivity() {
    companion object { var html: String = "" }   // passed in via static to avoid Intent size limits

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_build)

        val web = findViewById<WebView>(R.id.buildWeb)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

        findViewById<Button>(R.id.backBuildBtn).setOnClickListener { finish() }
        findViewById<Button>(R.id.shareBuildBtn).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_TEXT, html)
                putExtra(Intent.EXTRA_SUBJECT, "My website (made with My AI)")
            }
            startActivity(Intent.createChooser(share, "Share / Save your site"))
        }
    }
}
