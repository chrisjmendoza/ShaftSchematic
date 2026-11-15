package com.android.shaftschematic.pdf

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun sheetTodayDate(): String =
    NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }.stringFromDate(NSDate())
