package com.gn4k.videocall.ui

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.gn4k.videocall.adapter.usersAdapter
import com.gn4k.videocall.models.callModel
import com.gn4k.videocall.models.isValid
import com.gn4k.videocall.models.userPublicModel
import com.gn4k.videocall.services.mainService
import com.gn4k.videocall.services.mainServiceActions
import com.gn4k.videocall.utils.callHandler
import com.gn4k.videocall.utils.callTypes
import com.gn4k.videocall.utils.firebaseHandler
import com.gn4k.videocall.utils.firebaseWebRTCHandler
import com.gn4k.videocall.utils.helper
import com.gn4k.videocall.utils.webRTCHandler
import com.gn4k.videocall.R
import com.gn4k.videocall.databinding.ActivityMainBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class MainActivity : AppCompatActivity(), callHandler {
    lateinit var firebaseWebRTCHandler: firebaseWebRTCHandler;
    lateinit var rtcHandler: webRTCHandler;
    private lateinit var binding: ActivityMainBinding;
    private lateinit var userRef : DatabaseReference;
    private var userList : ArrayList<userPublicModel> = ArrayList();
    private lateinit var userAdapter : usersAdapter;
    private var prefs: SharedPreferences? = null;
    private var email : String? = ""
    private var username : String? = ""
    private lateinit var firebaseHandler : firebaseHandler;
    lateinit var service :mainService;
    private var target: String = ""
    private var accepted = false;
    private var targetName: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater);
        setContentView(binding.root);
        val database = FirebaseDatabase.getInstance("https://wallsplash-bce7e-default-rtdb.firebaseio.com/")
        userRef = database.getReference("Users");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        checkAndRequestPermissions()
        manageLogic();
        loadData();
    }



    private fun checkAndRequestPermissions(): Boolean {
        val noti = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NOTIFICATION_POLICY)
        }
        val camera =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audio =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        val listPermissionsNeeded: MutableList<String> = ArrayList()
        if (noti != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (camera != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA)
        }
        if (audio != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionsNeeded.toTypedArray<String>(),
                101
            )
            return false
        }
        return true
    }


    private fun loadData() {
         username = prefs!!.getString("userName","");
        email = prefs!!.getString("userEmail","");
        binding.userName.setText("Hello "+username +" !");
        firebaseHandler = firebaseHandler(this, userRef, email!!, username!!);
        firebaseWebRTCHandler = firebaseWebRTCHandler(this,userRef, email!!,
            username!! , firebaseHandler);

        firebaseWebRTCHandler.initWebRTCClient(email!!);
        service = mainService(this, firebaseWebRTCHandler)
        service.setCallHandler(this, firebaseHandler)
            service.startService(email!!, this, mainServiceActions.START_SERVICE);


        //update status of user!
        userRef.child(helper().cleanWord(email.toString())).child("status").setValue("Online");

        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("users", snapshot.value.toString());
                userList.clear();
                snapshot.children.forEach {
                    var user: userPublicModel = it.getValue(userPublicModel::class.java)!!;

                    if(!email.equals(user.email)) {
                        userList.add(user);
                    }

                }
                userAdapter.notifyDataSetChanged();
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("cancelled",error.message);
            }

        })
    }

    private fun manageLogic() {
        userAdapter = usersAdapter(userList, this);
        var llm: LinearLayoutManager = LinearLayoutManager(this);
        llm.orientation = LinearLayoutManager.VERTICAL;
        binding.userRV.layoutManager = llm;

        userAdapter.setCallActionListener(object : usersAdapter.callActionListener {
            override fun onVideoCall(position: Int) {
                checkAndRequestPermissions()
                var user = callModel(
                    email, username,
                    userList.get(position).email, null, callTypes.StartedVideoCall.name
                );

                firebaseHandler.callUser(user)


                var intent = Intent(this@MainActivity, MakeCallActivity::class.java)
                intent.putExtra("userName", userList.get(position).username);
                intent.putExtra("userEmail", userList.get(position).email);
                intent.putExtra("callType", "video");
                startActivity(intent);
                overridePendingTransition(R.anim.fade_2, R.anim.fade);

            }

            override fun onAudioCall(position: Int) {
                checkAndRequestPermissions()
                var user = callModel(
                    email, username,
                    userList.get(position).email, null, callTypes.StartedAudioCall.name
                );

                firebaseHandler.callUser(user)

                var intent = Intent(this@MainActivity, MakeCallActivity::class.java);
                intent.putExtra("userName", userList.get(position).username);
                intent.putExtra("userEmail", userList.get(position).email);
                intent.putExtra("callType", "audio");
                startActivity(intent);
                overridePendingTransition(R.anim.fade_2, R.anim.fade);
            }

        })

        binding.userRV.adapter = userAdapter;

        binding.rejectbtn.setOnClickListener {
            userRef.child(helper().cleanWord(email.toString())).child("status").setValue("Online");
            userRef.child(helper().cleanWord(email.toString())).child("latestEvents").removeValue();
            binding.callLayout.visibility = View.GONE;
            firebaseHandler.answerUser(
                callModel(
                    email, username, target, null, callTypes.Reject.name
                )
            );
        }
        binding.acceptbtns.setOnClickListener {
            firebaseWebRTCHandler.setTarget(target);

            // This creates an offer!
            firebaseHandler.answerUser(
                callModel(
                    email, username, target, null, callTypes.Answer.name
                )
            )

            var intent = Intent(this@MainActivity, VideoCallActivity::class.java)
            intent.putExtra("userName", targetName);
            intent.putExtra("userEmail", target);
            startActivity(intent);
        }

        binding.logout.setOnClickListener {

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Logout ?")
                .setMessage("Are you sure, you want to log out ?")
                .setPositiveButton("Yes", object : DialogInterface.OnClickListener {
                    override fun onClick(p0: DialogInterface?, p1: Int) {
                        val editor: SharedPreferences.Editor = prefs!!.edit()
                        editor.clear()
                        editor.apply()
                        startActivity(Intent(this@MainActivity, LoginScreen::class.java))
                        finish()
                    }

                }).setNegativeButton("No", object : DialogInterface.OnClickListener {
                    override fun onClick(p0: DialogInterface?, p1: Int) {
                    }

                }).show()

        }
    }

    override fun finish() {
        super.finish()
        userRef.child(helper().cleanWord(email.toString())).child("status").setValue("Offline");
        userRef.child(helper().cleanWord(email.toString())).child("latestEvents").removeValue();
    }

    override fun onDestroy() {
        super.onDestroy()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_left)
        userRef.child(helper().cleanWord(email.toString())).child("status").setValue("Offline");
        userRef.child(helper().cleanWord(email.toString())).child("latestEvents").removeValue();
    }

    override fun onBackPressed() {
        super.onBackPressed()

        val builder = AlertDialog.Builder(this@MainActivity)

        builder.setMessage("Do you want to exit?")

        builder.setTitle("Exit!")

        builder.setCancelable(false)


        builder.setPositiveButton("Yes",
           { dialog: DialogInterface?, which: Int ->
                finish()
            })

        builder.setNegativeButton("No",
         { dialog: DialogInterface, which: Int ->
                dialog.cancel()
            })


        val alertDialog = builder.create()
        alertDialog.show()
    }


    override fun onResume() {
        super.onResume()
        accepted = false;
        loadData();
        binding.callLayout.visibility = View.GONE

    }
    override fun onCallReceived(message: callModel) {

        if(message.isValid() && !accepted) {
            accepted = true;
            var intent = Intent(this@MainActivity, ReceiveCallScreen::class.java)
            intent.putExtra("userName", message.senderName);
            intent.putExtra("userEmail",message.senderEmail);
            if(message.callType.toString().lowercase().contains("video")) {
                intent.putExtra("callType", "video");
            }
            else {
                intent.putExtra("callType", "audio");
            }
            startActivity(intent);
            overridePendingTransition(R.anim.fade_2, R.anim.fade);
        }

    }

    override fun onInitOffer(message: callModel) {

    }

    override fun onCallAccepted(message: callModel) {

    }

    override fun onCallRejected(message: callModel) {
        userRef.child(helper().cleanWord(email.toString())).child("status").setValue("Online");
        userRef.child(helper().cleanWord(email.toString())).child("latestEvents").removeValue();
    }

    override fun onCallCut(message: callModel) {
    }

    override fun onUserAdded(message: callModel) {

    }

    override fun finalCallAccepted(message: callModel) {
    }

}