package com.android.shaftschematic.util

import android.util.Log

@PublishedApi
internal actual fun verboseLogD(tag: String, msg: String) {
    Log.d(tag, msg)
}

@PublishedApi
internal actual fun verboseLogI(tag: String, msg: String) {
    Log.i(tag, msg)
}

@PublishedApi
internal actual fun verboseLogW(tag: String, msg: String) {
    Log.w(tag, msg)
}

@PublishedApi
internal actual fun verboseLogE(tag: String, msg: String) {
    Log.e(tag, msg)
}
