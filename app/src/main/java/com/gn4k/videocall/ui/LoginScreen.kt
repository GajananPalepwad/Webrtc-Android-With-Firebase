package com.gn4k.videocall.ui

import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import com.gn4k.videocall.R
import com.gn4k.videocall.databinding.ActivityLoginScreenBinding
import com.gn4k.videocall.model.userModel
import com.gn4k.videocall.utils.firebaseHandler
import com.gn4k.videocall.utils.helper
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

class LoginScreen : AppCompatActivity() {
    private lateinit var binding: ActivityLoginScreenBinding;
    private lateinit var userRef : DatabaseReference;
    private var prefs: SharedPreferences? = null;
    private var pushtToken: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginScreenBinding.inflate(layoutInflater);
        setContentView(binding.root);
        val database = FirebaseDatabase.getInstance("https://wallsplash-bce7e-default-rtdb.firebaseio.com/")
        userRef = database.getReference("Users");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        manageLogic();
        getPushToken()

    }

    private fun getPushToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(ContentValues.TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            pushtToken = token;
            Log.d("push noti token", pushtToken);
        })
    }

    private fun manageLogic() {

        binding.signupTxt.setOnClickListener {
            startActivity(Intent(this@LoginScreen, SignupScreen::class.java))
            finish()
        }

        binding.loginBtn.setOnClickListener {

            if (binding.emailTxt.text.trim().toString().isEmpty()) {
                binding.emailTxt.setError("Enter email");
            } else if (binding.passTxt.toString().isEmpty()) {
                binding.passTxt.setError("Enter password");
            } else {

                // create a fireba  se user;

                val dialog = ProgressDialog.show(
                    this@LoginScreen, "",
                    "Logging...", true
                )

               var login = userRef.child(helper().cleanWord(binding.emailTxt.text.toString())).get().addOnSuccessListener {
                    Log.i("firebase", "Got value ${it.value}")
                   dialog.cancel();
                    if(it.value!=null) {
                        var user : userModel? = it.getValue(userModel::class.java);

                        if(user!!.password.equals(binding.passTxt.text.toString())) {
                            Toast.makeText(this@LoginScreen, "Login successful", Toast.LENGTH_SHORT).show();
                            val editor = prefs!!.edit()
                            firebaseHandler().updateNotification(pushtToken, this@LoginScreen, userRef,
                                binding.emailTxt.text.toString());
                            editor.putBoolean("loggedStatus", true);
                            editor.putString("userEmail", binding.emailTxt.text.toString())
                            editor.putString("userName", user!!.username)
                            editor.apply()
                            editor.commit()
                            startActivity(Intent(this@LoginScreen, mainActivity::class.java))
                            finish()
                        }
                        else {
                            Toast.makeText(this@LoginScreen, "Incorrect password", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else {
                        Toast.makeText(this@LoginScreen, "No such account exists", Toast.LENGTH_SHORT).show();
                    }

                }.addOnFailureListener{
                    dialog.cancel();
                   Toast.makeText(this@LoginScreen, it.message.toString(), Toast.LENGTH_SHORT).show();
                    Log.e("firebase", "Error getting data", it)
                }

            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_left)
    }
}