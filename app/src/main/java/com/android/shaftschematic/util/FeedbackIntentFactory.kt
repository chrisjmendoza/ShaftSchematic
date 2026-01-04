package com.android.shaftschematic.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

object FeedbackIntentFactory {
    const val FEEDBACK_EMAIL: String = "chrisjmendoza@gmail.com"

    const val SUBJECT: String = "ShaftSchematic Feedback"

    fun buildBody(
        context: Context,
        unit: UnitSystem,
        screen: String,
        selectedSaveName: String?
    ): String {
        val (versionName, versionCode) = appVersion(context)
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val sdk = Build.VERSION.SDK_INT
        val units = when (unit) {
            UnitSystem.INCHES -> "in"
            else -> "mm"
        }

        return buildString {
            appendLine("ShaftSchematic Feedback")
            appendLine()
            appendLine("App version: $versionName ($versionCode)")
            appendLine("Device: $device")
            appendLine("Android: $sdk")
            appendLine("Units: $units")
            appendLine("Screen: $screen")
            appendLine("Selected save (if any): ${selectedSaveName ?: ""}")
            appendLine()
            appendLine("Steps to reproduce:")
            appendLine("1)")
            appendLine("2)")
            appendLine("3)")
            appendLine()
            appendLine("Expected:")
            appendLine("Actual:")
            appendLine()
            appendLine("Notes:")
            appendLine()
        }
    }

    fun create(
        context: Context,
        screen: String,
        unit: UnitSystem,
        selectedSaveName: String? = null,
        attachments: List<Uri> = emptyList(),
    ): Intent {
        val body = buildBody(context = context, unit = unit, screen = screen, selectedSaveName = selectedSaveName)
        return createRaw(toEmail = FEEDBACK_EMAIL, subject = SUBJECT, body = body, attachments = attachments)
    }

    fun createRaw(
        toEmail: String,
        subject: String,
        body: String,
        attachments: List<Uri>
    ): Intent {
        return if (attachments.isEmpty()) {
            Intent(Intent.ACTION_SENDTO)
                .setData(Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body)
        } else {
            val list = ArrayList<Uri>(attachments)
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("message/rfc822")
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { setClipDataForUris(list) }
        }
    }

    fun uriForFile(context: Context, file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    /** Best-effort: returns a URI for the newest PDF in app-scoped Documents, if any. */
    fun latestPdfAttachment(context: Context): Uri? {
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        val pdf = docsDir
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        return runCatching { uriForFile(context, pdf) }.getOrNull()
    }

    private fun appVersion(context: Context): Pair<String, Long> {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val pi = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            val name = pi.versionName ?: "0"
            val code = if (Build.VERSION.SDK_INT >= 28) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
            name to code
        } catch (_: Throwable) {
            "0" to 0L
        }
    }

    private fun Intent.setClipDataForUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val first = uris.first()
        val clip = ClipData.newUri(null, "attachment", first)
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        clipData = clip
    }
}
