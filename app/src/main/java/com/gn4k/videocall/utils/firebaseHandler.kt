package com.gn4k.videocall.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gn4k.videocall.models.callModel
import com.gn4k.videocall.services.mainService
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SurfaceViewRenderer


class firebaseHandler {

    private lateinit var context: Context;
    private lateinit var currentUser: String;
    private lateinit var currentUserName: String;
    private lateinit var dbRef: DatabaseReference;
    private lateinit var gsonObject: Gson;
    private var acceptCall = true;
    var target = "";
    private lateinit var valueEventListenr: Any;


    constructor(
        context: Context,
        dbRef: DatabaseReference,
        currentUser: String,
        currentUserName: String
    ) {
        this.context = context
        this.dbRef = dbRef
        gsonObject = Gson();
        this.currentUser = currentUser;
        this.currentUserName = currentUserName;
    }

    constructor()

    //    fun setAcceptCall(acceptCall : Boolean) {
//        this.acceptCall = acceptCall;
//    }
    fun checkIncomingCall(respond: callHandler) {

        dbRef.child(helper().cleanWord(currentUser))
            .child("latestEvents").addValueEventListener(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (acceptCall) {
                            try {
                                var event =
                                    gsonObject.fromJson(
                                        snapshot.value.toString(),
                                        callModel::class.java
                                    )

                                val count = 0

                                when (event.callType) {

                                    callTypes.Offer.name -> {
                                        respond.onInitOffer(event);
                                    }

                                    callTypes.Answer.name -> {
                                        respond.onCallAccepted(event);
                                    }

                                    callTypes.FinalAnswer.name -> {
                                        respond.finalCallAccepted(event);
                                    }

                                    callTypes.EndCall.name -> {
                                        respond.onCallCut(event);
                                    }

                                    callTypes.Reject.name -> {
                                        respond.onCallRejected(event);
                                    }

                                    callTypes.ICECandidate.name -> {
                                        respond.onUserAdded(event);

                                    }

                                    callTypes.StartedAudioCall.name -> {
                                        respond.onCallReceived(event);
//                                                respond.onInitOffer(event);

                                    }

                                    callTypes.StartedVideoCall.name -> {
                                        respond.onCallReceived(event);
//                                                respond.onInitOffer(event);

                                    }
                                }


                            } catch (e: Exception) {
                                Log.d("err", e.message.toString());
                            }
                        }

                    }


                    override fun onCancelled(error: DatabaseError) {
                        Log.d("Error receiving updates", error.message.toString());
                    }
                }
            )


    }


    fun callUser(message: callModel): Boolean {
        var success = false;
        val serMessage = gsonObject.toJson(message);
        Log.d("fbCall", serMessage.toString());
//        var newSerMessage =
        valueEventListenr = dbRef.child(helper().cleanWord(message.targetEmail!!))
            .child("latestEvents").setValue(serMessage)
            .addOnSuccessListener({
                success = true;
                //   target = message.targetEmail!!;

            })
            .addOnFailureListener({
                success = false;
            })

        Log.d("isFirebaseCall", success.toString());
        return success;

    }

    public fun updateNotification(
        token: String,
        context: Context,
        dbRef: DatabaseReference,
        currentUser: String
    ) {
        dbRef.child(helper().cleanWord(currentUser)).child("pushToken")
            .setValue(token)
        Log.d("valueUpdated", "yes");

    }

    fun changeMyStatus(status: String) {
        dbRef.child(helper().cleanWord(currentUser)).child("status").setValue(status);
    }

    public fun answerUser(message: callModel): Boolean {
        var success = false;
        val serMessage = gsonObject.toJson(message);
        dbRef.child(helper().cleanWord(message.targetEmail!!))
            .child("latestEvents").setValue(serMessage)
            .addOnSuccessListener({
                success = true;

//                changeMyStatus("OnCall")

            })
            .addOnFailureListener({
                success = false;
            })

        return success;

    }

    public fun rejectUser(message: callModel): Boolean {
        var success = false;
        val serMessage = gsonObject.toJson(message);
        dbRef.child(helper().cleanWord(message.targetEmail!!))
            .child("latestEvents").setValue(serMessage)
            .addOnSuccessListener({
                success = true;

//                changeMyStatus("OnCall")

            })
            .addOnFailureListener({
                success = false;
            })

        return success;

    }

}

class firebaseWebRTCHandler {

    private lateinit var context: Context;
    private lateinit var currentUser: String;
    private lateinit var currentUserName: String;
    private lateinit var dbRef: DatabaseReference;
    private lateinit var gsonObject: Gson;
    private var acceptCall = true;
    lateinit var webRTCHandler: webRTCHandler;
    private lateinit var firebaseHandler: firebaseHandler;
    private lateinit var remoteView: SurfaceViewRenderer;
    private lateinit var target: String;
    private var isPreviousStateVideo = true;
    private lateinit var instance: firebaseWebRTCHandler;
    fun getInstance() {
        if (instance != null) {

        }
    }

    constructor(
        context: Context,
        dbRef: DatabaseReference,
        currentUser: String,
        currentUserName: String,
        firebaseHandler: firebaseHandler
    ) {
        this.context = context
        this.dbRef = dbRef
        gsonObject = Gson();
        this.currentUser = currentUser;
        this.currentUserName = currentUserName;
        this.firebaseHandler = firebaseHandler;
    }

    constructor()

    companion object {
        var screenPermissionIntent: Intent? = null;
    }

    fun setTarget(target: String) {
        firebaseHandler.target = target;
        this.target = target;
    }

    fun initWebRTCClient(email: String) {
        webRTCHandler = webRTCHandler(context, gsonObject, firebaseHandler);
        webRTCHandler.initializeWebRTCClient(email, object : myPeerObserver() {
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                super.onIceGatheringChange(p0)
                Log.e("iceGather", "iceGather: $p0")
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                super.onAddTrack(p0, p1)

                try {
                    Log.e("addTrack", "onAddTrack: ${p0!!.parameters.rtcp.toString()}")
                    Log.e("addTrack2", "onAddTrack2: ${p1!!.size}")
//

//                    p1?.get(0)?.videoTracks?.get(0)?.addSink(remoteView)
                } catch (e: Exception) {
                    Log.d("errorAddTrack", e.message.toString());
                }
            }

            override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {

                super.onStandardizedIceConnectionChange(newState)
                Log.d("iceNewState", newState.toString())
                if (newState == PeerConnection.IceConnectionState.DISCONNECTED ||
                    newState == PeerConnection.IceConnectionState.FAILED
                ) {
                    endCall();
                    firebaseHandler.answerUser(
                        callModel(
                            currentUser, currentUserName, currentUser, "",
                            callTypes.EndCall.name
                        )
                    )
                }
            }

            override fun onDataChannel(p0: DataChannel?) {
                super.onDataChannel(p0)
                Log.d("datachannel", p0?.label().toString())
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {
                super.onIceConnectionReceivingChange(p0)
                Log.d("icereceivingchanges", p0.toString())
            }

            override fun onRenegotiationNeeded() {
                super.onRenegotiationNeeded()
                Log.d("RenegotiationNeeded", "yes")
//                webRTCHandler.call()
            }

            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                super.onIceConnectionChange(p0)
                Log.d("iceChanged", p0.toString());
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                super.onSignalingChange(p0)
                Log.d("signalChanged", p0.toString());

            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
//                Log.d("addstream",p0!!.videoTracks.size.toString());
                try {
                    Log.e("addstream", "onAddStream: ${p0}")
                    p0?.videoTracks?.get(0)?.addSink(remoteView)

                } catch (e: Exception) {
                    Log.d("errorAddStream", e.message.toString());
                }
            }

            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0.let {
                    Log.d("ice__", it!!.sdp.toString());
                    webRTCHandler.sendIceCandidate(target, it);
                }

            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                super.onConnectionChange(newState)
                Log.d("newState", newState.toString());
                if (newState!!.equals(PeerConnection.PeerConnectionState.CONNECTED)) {
                    dbRef.child(helper().cleanWord(target)).child("status").setValue("OnCall");
                    dbRef.child(helper().cleanWord(currentUser)).child("latestEvents")
                        .setValue(null)
                } else if (newState.equals(PeerConnection.PeerConnectionState.CLOSED)) {
//                    dbRef.child(helper().cleanWord(target)).child("status").setValue("OnCall");
//                    dbRef.child(helper().cleanWord(currentUser)).child("latestEvents").setValue("EndCall")
                }
            }
        }, currentUserName);

    }

    fun initLocalSurfaceView(view: SurfaceViewRenderer, isVideoCall: Boolean) {
        webRTCHandler.initLocalSurfaceView(view, true);
    }

    fun initRemoteSurfaceView(view: SurfaceViewRenderer) {
        this.remoteView = view;
        webRTCHandler.initRemoteSurfaceView(view);
    }

    fun startCall(target: String, callType: String?) {
        webRTCHandler.call(target, callType!!);
        firebaseHandler.target = target;
    }

    fun acceptCall(target: String) {
        webRTCHandler.answer(target);
    }

//    fun pickCall(target:String) {
//        firebaseHandler.answerUser(  callModel(
//            currentUser, currentUser, target,
//            callTypes.ICECandidate.name));
//    }

    fun endCall() {
        webRTCHandler.closeConnection();
        firebaseHandler.changeMyStatus("Online");
        dbRef.child(helper().cleanWord(firebaseHandler.target))
            .child("latestEvents").setValue(
                gsonObject.toJson(
                    callModel(
                        currentUser, currentUserName, firebaseHandler.target, null,
                        callTypes.EndCall.name
                    )
                )
            );

        dbRef.child(helper().cleanWord(firebaseHandler.target))
            .child("latestEvents").setValue(null);

        dbRef.child(helper().cleanWord(currentUser))
            .child("latestEvents").setValue(null);
    }

    fun toggleVideo(shouldHide: Boolean) {
        webRTCHandler.toggleVideo(shouldHide);
        isPreviousStateVideo = !shouldHide
    }


    fun toggleAudio(shouldBeMuted: Boolean) {
        webRTCHandler.toggleAudio(shouldBeMuted);
    }

    fun switchCamera() {
        webRTCHandler.switchCamera();
    }

    fun setScreenCapturer(screenPermissionIntent: Intent) {
        webRTCHandler.setScreenCapturer(mainService.screenPermissionIntent!!)
    }

    fun toggleScreenShare(isStarting: Boolean) {

        if (isStarting) {
            webRTCHandler.startScreenCapturing()
        } else {
            webRTCHandler.startScreenCapturing()
        }
    }


}

