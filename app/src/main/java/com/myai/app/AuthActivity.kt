package com.myai.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private var signupMode = true
    private val CREATOR_EMAIL = "stephenowusuansah601@gmail.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("myai", Context.MODE_PRIVATE)

        // Already logged in? Skip to the app.
        if (!prefs.getString("user_id", null).isNullOrEmpty()) { goNext(); return }

        setContentView(R.layout.activity_auth)
        Thread { Api.wake() }.start()  // warm up backend

        val name = findViewById<EditText>(R.id.nameField)
        val email = findViewById<EditText>(R.id.emailField)
        val pass = findViewById<EditText>(R.id.passField)
        val recovery = findViewById<EditText>(R.id.recoveryField)
        val btn = findViewById<Button>(R.id.authBtn)
        val toggle = findViewById<TextView>(R.id.toggleAuth)
        val subtitle = findViewById<TextView>(R.id.authSubtitle)

        fun render() {
            name.visibility = if (signupMode) View.VISIBLE else View.GONE
            recovery.visibility = if (signupMode) View.VISIBLE else View.GONE
            btn.text = if (signupMode) "Sign Up" else "Log In"
            subtitle.text = if (signupMode) "Create your account" else "Welcome back"
            toggle.text = if (signupMode) "Already have an account? Log in" else "New here? Create an account"
        }
        render()
        toggle.setOnClickListener { signupMode = !signupMode; render() }
        findViewById<TextView>(R.id.forgotLink).setOnClickListener { forgotDialog() }

        btn.setOnClickListener {
            val e = email.text.toString().trim()
            val p = pass.text.toString()
            val n = name.text.toString().trim()
            if (e.isEmpty() || p.isEmpty() || (signupMode && n.isEmpty())) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            btn.isEnabled = false; btn.text = "Please wait…"
            val rec = recovery.text.toString().trim()
            Thread {
                val res = if (signupMode) Api.signup(n, e, p, rec) else Api.login(e, p)
                runOnUiThread {
                    btn.isEnabled = true; render()
                    if (res.optBoolean("ok", false)) {
                        prefs.edit()
                            .putString("user_id", res.optString("user_id"))
                            .putString("name", res.optString("name"))
                            .putString("email", res.optString("email"))
                            .putBoolean("is_creator", res.optString("email").equals(CREATOR_EMAIL, true))
                            .apply()
                        goNext()
                    } else {
                        Toast.makeText(this, res.optString("error", "Something went wrong"), Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    private fun forgotDialog() {
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt(); container.setPadding(pad, pad, pad, 0)
        val em = EditText(this); em.hint = "Your email"; em.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val rec = EditText(this); rec.hint = "Recovery word"
        val np = EditText(this); np.hint = "New password"; np.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        container.addView(em); container.addView(rec); container.addView(np)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reset password")
            .setMessage("Enter your email, your recovery word, and a new password.")
            .setView(container)
            .setPositiveButton("Reset") { _, _ ->
                val e = em.text.toString().trim(); val r = rec.text.toString().trim(); val p = np.text.toString()
                if (e.isEmpty() || r.isEmpty() || p.isEmpty()) { Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                Thread {
                    val res = Api.resetPassword(e, r, p)
                    runOnUiThread {
                        Toast.makeText(this, if (res.optBoolean("ok")) "✅ Password reset — log in now" else res.optString("error","Failed"), Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun goNext() {
        // If they already chose a platform, go straight to chat; else choose platform.
        val next = if (prefs.getBoolean("chosen", false)) ChatActivity::class.java else StartActivity::class.java
        startActivity(Intent(this, next)); finish()
    }
}
