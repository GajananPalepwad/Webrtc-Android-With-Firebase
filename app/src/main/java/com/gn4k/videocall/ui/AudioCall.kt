package com.gn4k.videocall.ui

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import com.gn4k.videocall.R
import com.gn4k.videocall.databinding.ActivityAudioCallBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class AudioCall : AppCompatActivity() {

    private lateinit var binding: ActivityAudioCallBinding;
    private lateinit var userRef : DatabaseReference;
    private var prefs: SharedPreferences? = null;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_call)
        binding = ActivityAudioCallBinding.inflate(layoutInflater);
        setContentView(binding.root);
        val database = FirebaseDatabase.getInstance("https://wallsplash-bce7e-default-rtdb.firebaseio.com/")
        userRef = database.getReference("Users");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        loadData();
    }

    private fun loadData() {

    }
}