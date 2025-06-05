package com.gn4k.videocall.utils

import com.gn4k.videocall.models.callModel

enum class callTypes {
    StartedAudioCall,StartedVideoCall,Offer,FinalAnswer, Answer,Reject,ICECandidate,EndCall
}
interface callHandler {

    fun onCallReceived(message : callModel);

    fun onInitOffer(message : callModel);
    fun onCallAccepted(message : callModel);

    fun onCallRejected(message: callModel);

    fun onCallCut(message : callModel);

    fun onUserAdded(message : callModel);

    fun finalCallAccepted(message : callModel);


}
