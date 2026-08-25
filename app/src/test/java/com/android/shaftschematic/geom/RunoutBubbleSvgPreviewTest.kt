package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Visual preview generator for the runout bubble placement engine — not an assertion
 * suite. Runs the REAL `planRunoutBubbles`/`finish` pipeline over representative station
 * sets and writes annotated SVGs to `app/build/reports/runout-bubble-preview/` for
 * on-device-style markup review (the same posture as `WearStripWindowSvgPreviewTest`).
 * The only assertions are zero unresolved collisions — the drawings are the deliverable.
 */
class RunoutBubbleSvgPreviewTest {

    // PDF-scale geometry: letter landscape content area, the composer's real constants.
    private val geom = RunoutBubbleGeometry(
        radius = 23f,
        minGap = 5f,
        shortLeader = 18f,
        contentLeft = 36f,
        contentRight = 756f,
    )

    private fun render(name: String, title: String, stations: List<RunoutStationX>) {
        val plan = planRunoutBubbles(stations.sortedBy { it.stationX }, geom)
        val anchorY = 250f
        val result = plan.finish(anchorY) { anchorY }
        assertEquals("$name: unresolved collisions", 0, result.unresolvedCollisions)

        val sb = StringBuilder()
        val h = 480f
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 792 $h\" style=\"background:white\">\n")
        sb.append("<text x=\"36\" y=\"30\" font-size=\"14\" font-family=\"sans-serif\">$title</text>\n")
        // Content bounds + a schematic shaft band whose bottom edge is the leader anchor.
        sb.append("<rect x=\"${geom.contentLeft}\" y=\"190\" width=\"${geom.contentRight - geom.contentLeft}\" height=\"60\" fill=\"#f2f2f2\" stroke=\"#444\"/>\n")
        for (b in result.bubbles) {
            // Station tick on the shaft band.
            sb.append("<line x1=\"${b.stationX}\" y1=\"196\" x2=\"${b.stationX}\" y2=\"${b.surfaceY}\" stroke=\"#aaa\" stroke-dasharray=\"3,2\"/>\n")
            val color = if (b.leader.size == 2) "#0055cc" else "#cc2200"
            val pts = b.leader.joinToString(" ") { "${it.x},${it.y}" }
            sb.append("<polyline points=\"$pts\" fill=\"none\" stroke=\"$color\" stroke-width=\"1.1\"/>\n")
            sb.append("<circle cx=\"${b.bubbleX}\" cy=\"${b.bubbleCenterY}\" r=\"${geom.radius}\" fill=\"white\" stroke=\"black\" stroke-width=\"1.3\"/>\n")
            sb.append("<text x=\"${b.bubbleX}\" y=\"${b.bubbleCenterY + 4f}\" font-size=\"11\" text-anchor=\"middle\" font-family=\"sans-serif\">${b.stationIndex}</text>\n")
        }
        sb.append("<text x=\"36\" y=\"${h - 16f}\" font-size=\"11\" font-family=\"sans-serif\" fill=\"#555\">blue = straight leader (aims at circle center) · red = dogleg (elbow dips for slope, then drops onto the bubble top)</text>\n")
        sb.append("</svg>\n")

        val outDir = File("build/reports/runout-bubble-preview").apply { mkdirs() }
        File(outDir, "$name.svg").writeText(sb.toString())
    }

    @Test
    fun `a - clustered stations under compressed runs`() {
        // The on-device complaint case: taper + long liner + compressed body + FWD liner
        // + FWD taper — stations cluster while the page has slack. The fidelity brake
        // keeps every bubble within one same-row pitch of its station.
        render(
            "a-clustered-compressed", "Clustered stations (compressed runs) — braked spread",
            listOf(
                RunoutStationX("taperA", 0f, 106f, 0), RunoutStationX("taperA", 1f, 258f, 1),
                RunoutStationX("linerA", 2f, 284f, 0), RunoutStationX("linerA", 3f, 431f, 1),
                RunoutStationX("linerA", 4f, 578f, 2),
                RunoutStationX("body2", 5f, 606f, 0), RunoutStationX("body2", 6f, 634f, 1),
                RunoutStationX("linerF", 7f, 662f, 0), RunoutStationX("linerF", 8f, 700f, 1),
                RunoutStationX("taperF", 9f, 716f, 0), RunoutStationX("taperF", 10f, 740f, 1),
            ),
        )
    }

    @Test
    fun `b - hand-sheet spread of stations`() {
        // Stations distributed like the shop's 302in hand sheet — mostly even coverage.
        render(
            "b-hand-sheet", "Hand-sheet-like distribution — leaders mostly straight",
            listOf(
                RunoutStationX("taperA", 0f, 80f, 0), RunoutStationX("taperA", 1f, 150f, 1),
                RunoutStationX("linerA", 2f, 165f, 0), RunoutStationX("linerA", 3f, 240f, 1),
                RunoutStationX("linerA", 4f, 315f, 2),
                RunoutStationX("body2", 5f, 345f, 0), RunoutStationX("body2", 6f, 380f, 1),
                RunoutStationX("linerM", 7f, 400f, 0), RunoutStationX("linerM", 8f, 455f, 1),
                RunoutStationX("linerM", 9f, 510f, 2),
                RunoutStationX("body3", 10f, 540f, 0), RunoutStationX("body3", 11f, 570f, 1),
                RunoutStationX("linerF", 12f, 590f, 0), RunoutStationX("linerF", 13f, 640f, 1),
                RunoutStationX("taperF", 14f, 665f, 0), RunoutStationX("taperF", 15f, 725f, 1),
            ),
        )
    }

    @Test
    fun `c - sparse sheet`() {
        render(
            "c-sparse", "Sparse sheet — bubbles stay near their stations",
            listOf(
                RunoutStationX("taperA", 0f, 120f, 0), RunoutStationX("taperA", 1f, 260f, 1),
                RunoutStationX("body1", 2f, 450f, 0),
                RunoutStationX("taperF", 3f, 640f, 0), RunoutStationX("taperF", 4f, 720f, 1),
            ),
        )
    }

    @Test
    fun `d - dense sheet`() {
        val stations = buildList {
            var x = 70f
            var idx = 0
            listOf("t" to 2, "l1" to 4, "b1" to 3, "l2" to 4, "b2" to 3, "l3" to 3, "tf" to 2).forEach { (id, k) ->
                repeat(k) { i ->
                    add(RunoutStationX(id, idx.toFloat(), x, i))
                    x += 18f
                    idx++
                }
                x += 40f
            }
        }
        render("d-dense", "Dense sheet (21 stations) — doglegs carry the overflow", stations)
    }

    @Test
    fun `e - dragged stations bunched inside one liner`() {
        // Dragging is the one way a user can pack a component's stations right up against the
        // minimum axial separation. The engine's pitch floors and dogleg repair have to absorb
        // it: derived layouts never produce this, so nothing else in the suite reaches it.
        //
        // 12.7mm apart on a liner drawn ~2 pt/mm ≈ 26 pt between stations — well inside the
        // 51 pt same-row pitch, so every adjacent pair must fall to cross-row spacing.
        render(
            "e-dragged-bunched", "Dragged stations at the minimum separation — floors absorb it",
            listOf(
                RunoutStationX("taperA", 0f, 90f, 0), RunoutStationX("taperA", 1f, 150f, 1),
                RunoutStationX("linerA", 2f, 300f, 0), RunoutStationX("linerA", 3f, 326f, 1),
                RunoutStationX("linerA", 4f, 352f, 2), RunoutStationX("linerA", 5f, 378f, 3),
                RunoutStationX("linerA", 6f, 404f, 4),
                RunoutStationX("body2", 7f, 560f, 0),
                RunoutStationX("taperF", 8f, 680f, 0), RunoutStationX("taperF", 9f, 730f, 1),
            ),
        )
    }

    @Test
    fun `f - dragged stations pushed to both ends`() {
        // The other shape dragging makes reachable: a component's stations pulled apart to its
        // extremes while its neighbours stay put, so one component's span brackets another's.
        render(
            "f-dragged-spread", "Dragged stations at a component's extremes",
            listOf(
                RunoutStationX("linerA", 0f, 60f, 0), RunoutStationX("linerA", 1f, 740f, 1),
                RunoutStationX("body1", 2f, 200f, 0), RunoutStationX("body1", 3f, 240f, 1),
                RunoutStationX("body1", 4f, 280f, 2),
                RunoutStationX("taperF", 5f, 520f, 0), RunoutStationX("taperF", 6f, 560f, 1),
            ),
        )
    }
}
