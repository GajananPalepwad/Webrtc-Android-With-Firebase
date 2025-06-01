package com.gn4k.videocall.utils

class helper {

    fun cleanWord(word: String) : String {
        return word.toString().replace(".","").
                replace("#","").
        replace("$","").
                replace("[","").replace("]","");
    }
}