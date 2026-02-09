package com.android.shaftschematic.util

import com.android.shaftschematic.model.ShaftPosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun defaultPdfFilename(
    jobNumber: String,
    customer: String,
    vessel: String,
    shaftPosition: ShaftPosition
): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    val suggested = DocumentNaming.suggestedBaseName(
        jobNumber = jobNumber,
        customer = customer,
        vessel = vessel,
        suffix = shaftPosition.printableLabelOrNull()
    )
    return (suggested ?: "Shaft_$stamp") + ".pdf"
}
