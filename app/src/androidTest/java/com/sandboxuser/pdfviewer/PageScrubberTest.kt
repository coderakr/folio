package com.sandboxuser.pdfviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageScrubberTest {
    @get:Rule val rule = createComposeRule()
    private fun slider() = rule.onNodeWithContentDescription("Jump to page")

    @Test fun draggingPreviewsThenCommitsOnceAndTracksOtherNavigation() {
        val page = mutableIntStateOf(0)
        val destinations = mutableListOf<Int>()
        rule.setContent {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                PageScrubber(page.intValue, 100) { destinations += it; page.intValue = it }
            }
        }
        slider().performTouchInput { down(Offset(width * .1f, centerY)); moveTo(Offset(width * .25f, centerY)); moveTo(center) }
        val preview = slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("Dragging should preview a page near the middle (actual: $preview)", preview in 40f..60f)
        rule.runOnIdle { assertTrue(destinations.isEmpty()); assertEquals(0, page.intValue) }
        slider().performTouchInput { moveTo(Offset(width.toFloat(), centerY)); up() }
        rule.runOnIdle { assertEquals(listOf(99), destinations) }
        slider().performTouchInput { swipe(center, Offset(0f, centerY)) }
        rule.runOnIdle { assertEquals(listOf(99, 0), destinations) }
        slider().performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(3, destinations.size); assertTrue(page.intValue in 40..60) }
        // TalkBack/keyboard progress actions must also navigate without a touch release.
        slider().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(25f)) }
        rule.runOnIdle { assertEquals(24, page.intValue); assertEquals(4, destinations.size) }
        rule.runOnIdle { page.intValue = 74 }
        slider().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 75 of 100"))
    }

    @Test fun singlePageDocumentHasDisabledSlider() {
        rule.setContent { PageScrubber(0, 1) { fail("Single-page documents cannot navigate") } }
        slider().assertIsNotEnabled()
        slider().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 1 of 1"))
        rule.onNodeWithText("Page 1").assertExists()
    }
}
