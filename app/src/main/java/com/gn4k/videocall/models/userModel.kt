package com.gn4k.videocall.models

class userModel {

     var username: String = "";
     var email: String = "";
     var password : String = ""

    constructor(username: String, email: String, password: String) {
        this.username = username
        this.email = email
        this.password = password
    }

    constructor()

}