package com.android.shaftschematic.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The share intents, and in particular the one behind Settings → Data → "Share diagnostic logs".
 *
 * That button is reached only after something has already gone wrong, so a crash in building its
 * intent is the worst possible failure: it takes the app down on the screen a stuck tester was
 * sent to, and it destroys the evidence rather than mailing it. Attaching the logs went through
 * `ClipData.newUri(null, …)`, which dereferences its `ContentResolver` for every `content://`
 * URI — and a FileProvider attachment is always `content://`, so every tap was an NPE.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackIntentFactoryTest {

    private val attachments = listOf(
        Uri.parse("content://com.android.shaftschematic.fileprovider/internal/logs/log.0.txt"),
        Uri.parse("content://com.android.shaftschematic.fileprovider/internal/logs/log.1.txt"),
    )

    @Test
    fun `attaching FileProvider logs builds an intent instead of throwing`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        val intent = FeedbackIntentFactory.createDiagnostics(
            context = ctx,
            attachedFileNames = listOf(AppLog.CURRENT_NAME, AppLog.PREVIOUS_NAME),
            attachments = attachments,
        )

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        val streams = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(attachments, streams?.toList())
        assertEquals(FeedbackIntentFactory.DIAGNOSTICS_SUBJECT, intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    /**
     * The clip carries every attachment, because that is what propagates the read grant to
     * whichever app the chooser picks — an attachment left out of the clip can arrive unreadable.
     */
    @Test
    fun `every attachment rides the clip under the intent's own mime type`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        val intent = FeedbackIntentFactory.createDiagnostics(
            context = ctx,
            attachedFileNames = listOf(AppLog.CURRENT_NAME, AppLog.PREVIOUS_NAME),
            attachments = attachments,
        )

        val clip = requireNotNull(intent.clipData) { "the grant flag needs a clip to travel on" }
        assertEquals(attachments.size, clip.itemCount)
        assertEquals(attachments, (0 until clip.itemCount).map { clip.getItemAt(it).uri })
        // The mime type comes off the intent, never from a resolver lookup on the URIs.
        assertEquals(intent.type, clip.description.getMimeType(0))
        assertTrue(
            "the chosen app has to be granted read on the logs",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    /**
     * The same NPE reached a second button: Open drawing → ⋮ → "Send Feedback" attaches the
     * `.shaft` file itself, so it takes the same attachment branch. It looked like a different
     * feature and failed for identical reasons — worth its own pin, since a future change to
     * `create` could reopen it without touching `createDiagnostics`.
     */
    @Test
    fun `attaching a saved document to feedback builds an intent instead of throwing`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        val intent = FeedbackIntentFactory.create(
            context = ctx,
            screen = "Open/Saved",
            unit = UnitSystem.INCHES,
            selectedSaveName = "job 1138",
            attachments = listOf(attachments.first()),
        )

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals(intent.type, intent.clipData?.description?.getMimeType(0))
        assertEquals(attachments.first(), intent.clipData?.getItemAt(0)?.uri)
    }

    /** No attachments means a plain mailto: — no clip, and nothing to resolve. */
    @Test
    fun `an unattached share stays a mailto intent`() {
        val intent = FeedbackIntentFactory.createRaw(
            toEmail = FeedbackIntentFactory.FEEDBACK_EMAIL,
            subject = FeedbackIntentFactory.SUBJECT,
            body = "body",
            attachments = emptyList(),
        )

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals(null, intent.clipData)
    }
}
