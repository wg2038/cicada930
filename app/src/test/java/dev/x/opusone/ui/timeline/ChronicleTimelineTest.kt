package dev.x.opusone.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ChronicleTimelineTest {

    @Test
    fun testChronicleNodesChronologicalOrder() {
        assertEquals(19, CHRONICLE_NODES.size)

        // Verify node IDs run S01 to S19 in order
        for (i in 0 until 19) {
            val expectedId = String.format("S%02d", i + 1)
            assertEquals(expectedId, CHRONICLE_NODES[i].id)
        }

        // Verify yearBc flows chronologically from ancient to later
        for (i in 0 until CHRONICLE_NODES.size - 1) {
            val curr = CHRONICLE_NODES[i]
            val next = CHRONICLE_NODES[i + 1]
            assertTrue(
                "Node ${curr.id} (${curr.yearBc} BC) should precede or match ${next.id} (${next.yearBc} BC)",
                curr.yearBc >= next.yearBc
            )
        }
    }

    @Test
    fun testChronicleNodesNoCollision() {
        // Ensure no two nodes overlap in 2D coordinate space
        for (i in CHRONICLE_NODES.indices) {
            for (j in i + 1 until CHRONICLE_NODES.size) {
                val stA = CHRONICLE_NODES[i]
                val stB = CHRONICLE_NODES[j]
                val dist = hypot(stA.x - stB.x, stA.y - stB.y)
                assertTrue(
                    "Nodes ${stA.id} and ${stB.id} are too close: dist=$dist",
                    dist >= 140f
                )
            }
        }
    }

    @Test
    fun testCalculateTimelineFitTransform() {
        val (scale, offset) = calculateTimelineFitTransform(
            nodes = CHRONICLE_NODES,
            viewportWidth = 1080f,
            viewportHeight = 1920f
        )

        assertTrue("Scale should be positive and within reasonable range", scale in 0.35f..2.2f)
        assertTrue("OffsetX should place content on screen", offset.x.isFinite())
        assertTrue("OffsetY should place content on screen", offset.y.isFinite())
    }

    @Test
    fun testChroniclePresetsAndMilestones() {
        assertEquals(1, CHRONICLE_PRESETS.size)

        // 验证统一编年长河包含全部 19 个节点及全部 4 个治乱纪元标尺
        val preset = CHRONICLE_PRESETS[0]
        assertEquals("编年长河", preset.title)
        assertEquals(19, preset.nodes.size)
        assertEquals(4, preset.milestones.size)

        val milestoneLabels = preset.milestones.map { it.label }
        assertTrue(milestoneLabels.contains("上古三代"))
        assertTrue(milestoneLabels.contains("春秋战国"))
        assertTrue(milestoneLabels.contains("大秦一统"))
        assertTrue(milestoneLabels.contains("楚汉西汉"))
    }

    @Test
    fun testChronicleEdgesIntegrity() {
        val allNodeIds = CHRONICLE_NODES.map { it.id }.toSet()

        // 验证所有历史因果脉络连线的首尾节点均在长河节点中存在
        for (edge in CHRONICLE_EDGES) {
            assertTrue("From node ${edge.fromId} must exist", allNodeIds.contains(edge.fromId))
            assertTrue("To node ${edge.toId} must exist", allNodeIds.contains(edge.toId))
            assertTrue("Edge transition label should not be blank", edge.label.isNotBlank())
        }
    }
}

