package com.android.shaftschematic.pdf

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source scan behind [sheetTextPaint]: no file under `pdf/` may build a FRESH text paint by
 * hand.
 *
 * A fresh `Paint(...)` whose `apply` block sets a text property (`textSize`, `textAlign`,
 * `typeface`) is a text paint, and one that does not take the Output font prints its values in
 * the platform sans beside the chosen face — the rail values and Ø callouts did exactly that
 * (on-device report). Copies (`Paint(text)`) inherit the face and are not flagged.
 */
class SheetTextPaintSourceTest {

    private val pdfDir: File = run {
        var dir: File? = File(System.getProperty("user.dir") ?: error("Missing system property: user.dir"))
        val rel = "app/src/main/java/com/android/shaftschematic/pdf"
        while (dir != null && !File(dir, rel).isDirectory) dir = dir.parentFile
        File(dir ?: error("$rel not found above ${System.getProperty("user.dir")}"), rel)
    }

    /** A fresh paint: `Paint()` or `Paint(Paint.ANTI_ALIAS_FLAG)`, optionally fully qualified. */
    private val freshPaint = Regex(
        """(?:android\.graphics\.)?Paint\(\s*(?:(?:android\.graphics\.)?Paint\.ANTI_ALIAS_FLAG)?\s*\)\s*\.apply\s*\{""",
    )
    private val textProps = Regex("""\b(textSize|textAlign|typeface)\b""")

    @Test
    fun `every fresh text paint in pdf goes through sheetTextPaint`() {
        val sources = pdfDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("no sources found under $pdfDir", sources.isNotEmpty())

        val offenders = mutableListOf<String>()
        for (file in sources) {
            if (file.name == "SheetTextPaint.kt") continue
            val src = file.readText()
            for (m in freshPaint.findAll(src)) {
                val body = applyBlock(src, m.range.last)
                if (textProps.containsMatchIn(body)) {
                    val line = src.substring(0, m.range.first).count { it == '\n' } + 1
                    offenders += "${file.name}:$line"
                }
            }
        }
        assertTrue(
            "fresh text paints bypass sheetTextPaint (Output font would not reach them): $offenders",
            offenders.isEmpty(),
        )
    }

    /** The text of the `{ ... }` block whose opening brace sits at [open]. */
    private fun applyBlock(src: String, open: Int): String {
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return src.substring(open + 1, i)
            }
        }
        return src.substring(open + 1)
    }
}
