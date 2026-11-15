package com.android.shaftschematic.pdf

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun sheetTodayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
