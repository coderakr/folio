package com.sandboxuser.pdfviewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandboxuser.pdfviewer.ui.theme.*
import kotlin.math.roundToInt

/** Preview while dragging; render and save only the final destination. Page indices are zero-based. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageScrubber(page: Int, pageCount: Int, modifier: Modifier = Modifier, onPageSelected: (Int) -> Unit) {
    var preview by remember(pageCount) { mutableStateOf<Float?>(null) }
    val count = pageCount.coerceAtLeast(1)
    val value = preview ?: (page + 1).coerceIn(1, count).toFloat()
    val destination = value.roundToInt().coerceIn(1, count)
    val sliderColors = SliderDefaults.colors(thumbColor = Terracotta, activeTrackColor = Terracotta, inactiveTrackColor = Line)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Slider(
            value = value,
            onValueChange = { preview = it },
            onValueChangeFinished = {
                preview?.let { onPageSelected(it.roundToInt().coerceIn(1, count) - 1) }
                preview = null
            },
            valueRange = 1f..count.coerceAtLeast(2).toFloat(),
            enabled = count > 1,
            colors = sliderColors,
            thumb = { Box(Modifier.size(14.dp).background(if(count > 1) Terracotta else Muted.copy(alpha = .35f), CircleShape)) },
            track = { state ->
                SliderDefaults.Track(state, Modifier.height(3.dp), enabled = count > 1, colors = sliderColors,
                    drawStopIndicator = null, thumbTrackGapSize = 0.dp, trackInsideCornerSize = 1.5.dp)
            },
            modifier = Modifier.weight(1f).semantics {
                contentDescription = "Jump to page"
                stateDescription = "Page $destination of $count"
            }
        )
        Text("Page $destination", color = Ink, fontSize = 11.sp)
    }
}
