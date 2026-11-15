package com.android.shaftschematic.util

// The iOS platform log for VerboseLog is the process's standard output.

@PublishedApi
internal actual fun verboseLogD(tag: String, msg: String) {
    println("D/$tag: $msg")
}

@PublishedApi
internal actual fun verboseLogI(tag: String, msg: String) {
    println("I/$tag: $msg")
}

@PublishedApi
internal actual fun verboseLogW(tag: String, msg: String) {
    println("W/$tag: $msg")
}

@PublishedApi
internal actual fun verboseLogE(tag: String, msg: String) {
    println("E/$tag: $msg")
}
