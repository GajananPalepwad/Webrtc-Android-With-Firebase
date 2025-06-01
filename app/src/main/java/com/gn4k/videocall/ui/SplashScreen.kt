package com.gn4k.videocall.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gn4k.videocall.R


class SplashScreen : AppCompatActivity() {
    lateinit var prefs: SharedPreferences
    val PERMISSION_PROJECT_MEDIA = "android.permission.PROJECT_MEDIA"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        if (Build.VERSION.SDK_INT >= 34) {
            if (ContextCompat.checkSelfPermission(this, PERMISSION_PROJECT_MEDIA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(PERMISSION_PROJECT_MEDIA), 1001)
            }
        }


        Handler().postDelayed({
            prefs = PreferenceManager.getDefaultSharedPreferences(this@SplashScreen)
            Log.d("logged", prefs.getBoolean("loggedStatus", false).toString())
            if (prefs.getBoolean("loggedStatus", false)) {
                startActivity(Intent(this@SplashScreen, mainActivity::class.java))
                finish()
            } else {
                startActivity(Intent(this@SplashScreen, LoginScreen::class.java))
                finish()
            }
        }, 1500)
    }


    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_left)
    }
}