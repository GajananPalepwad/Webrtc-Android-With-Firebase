package com.gn4k.videocall.ui

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import com.gn4k.videocall.R
import com.gn4k.videocall.databinding.ActivityReceiveCallScreenBinding
import com.gn4k.videocall.models.callModel
import com.gn4k.videocall.services.mainService
import com.gn4k.videocall.utils.callHandler
import com.gn4k.videocall.utils.callTypes
import com.gn4k.videocall.utils.firebaseHandler
import com.gn4k.videocall.utils.firebaseWebRTCHandler
import com.gn4k.videocall.utils.helper
import com.gn4k.videocall.utils.webRTCHandler
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ReceiveCallScreen : AppCompatActivity(), callHandler {

    private lateinit var binding: ActivityReceiveCallScreenBinding;
    private lateinit var userRef : DatabaseReference;
    private lateinit var receiverEmail :String;
    private lateinit var receiverName :String;
    private lateinit var callType :String;
    private lateinit var email :String;
    private lateinit var userName :String;
    private var prefs: SharedPreferences? = null;
    lateinit var service: mainService;
    lateinit var firebaseWebRTCHandler: firebaseWebRTCHandler;
    lateinit var firebaseHandler: firebaseHandler;
    lateinit var rtcHandler: webRTCHandler;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveCallScreenBinding.inflate(layoutInflater);
        setContentView(binding.root);
        val database = FirebaseDatabase.getInstance("https://wallsplash-bce7e-default-rtdb.firebaseio.com/")
        userRef = database.getReference("Users");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        loadData();
        manageLogic();
    }

    private fun manageLogic() {

        binding.rejectCall.setOnClickListener{
            firebaseHandler.answerUser(
            callModel(
                email, userName, receiverEmail, null, callTypes.Reject.name
            )
            )
            userRef.child(helper().cleanWord(email.toString())).child("latestEvents").setValue(null);
            userRef.child(helper().cleanWord(email.toString())).child("status").setValue("Online");
            finish()
            overridePendingTransition(R.anim.fade_2, R.anim.fade);
        }

        binding.acceptCall.setOnClickListener {
            firebaseHandler.answerUser(
                callModel(
                    email, userName, receiverEmail, null, callTypes.Answer.name
                )
            )
            firebaseHandler.answerUser(
                callModel(
                    email, userName, email, null, "Picked"
                )
            )
            var intent = Intent(this@ReceiveCallScreen, VideoCallActivity::class.java)
            intent.putExtra("userName", receiverName);
            intent.putExtra("userEmail",  receiverEmail);
            intent.putExtra("callType",  callType);
            startActivity(intent);
            finish()

        }
    }

    private fun loadData() {
        email = prefs!!.getString("userEmail","")!!;

        receiverEmail = intent.getStringExtra("userEmail").toString();
        receiverName = intent.getStringExtra("userName").toString();
        var intent = intent;
        callType = intent.getStringExtra("callType").toString();

        if(callType.toString().lowercase().contains("video")) {
            binding.callType.setText("Video call");
        }
        else if(callType.toString().lowercase().contains("audio")) {
            binding.callType.setText("Audio call");
        }

        binding.userNameText.setText("From "+receiverName);


        email = prefs!!.getString("userEmail","")!!;
        userName = prefs!!.getString("userName","")!!;


        firebaseHandler = firebaseHandler(this, userRef, email, userName);
        firebaseWebRTCHandler = firebaseWebRTCHandler(this,userRef, email, userName
            , firebaseHandler);

        service = mainService(this, firebaseWebRTCHandler)
        service.setCallHandler(this, firebaseHandler);
        firebaseWebRTCHandler.initWebRTCClient(email);
        if(callType.toString().lowercase().contains("video")) {
            binding.callType.setText("Video");
            binding.userCamera.visibility = View.VISIBLE
            binding.userCameraOverlay.visibility = View.VISIBLE
            firebaseWebRTCHandler.initLocalSurfaceView(binding.userCamera, true);
        }
        else if(callType.toString().lowercase().contains("audio")) {
            binding.callType.setText("Audio");
            binding.userCamera.visibility = View.GONE
            binding.userCameraOverlay.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // no action here
    }

    override fun onDestroy() {
        super.onDestroy()
        userRef.child(helper().cleanWord(email)).child("status").setValue("Online");
    }

    override fun onCallReceived(message: callModel) {

    }

    override fun onInitOffer(message: callModel) {


    }

    override fun onCallAccepted(message: callModel) {

    }

    override fun onCallRejected(message: callModel) {

    }

    override fun onCallCut(message: callModel) {
    }

    override fun onUserAdded(message: callModel) {

    }

    override fun finalCallAccepted(message: callModel) {
    }
}