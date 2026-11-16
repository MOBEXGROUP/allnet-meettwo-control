package org.roomscreen.meettwo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * Two columns side by side in landscape, stacked in portrait. Each page is laid out to fit
 * the 1280x800 dp screen without scrolling; [scrollable] only lets the columns scroll when
 * something (the soft keyboard) takes away space.
 */
@Composable
fun TwoPane(
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val spacing = Arrangement.spacedBy(12.dp)
        @Composable
        fun Modifier.scroll() = if (scrollable) verticalScroll(rememberScrollState()) else this
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).scroll(), verticalArrangement = spacing, content = left)
                Column(Modifier.weight(1f).scroll(), verticalArrangement = spacing, content = right)
            }
        } else {
            Column(Modifier.fillMaxSize().scroll(), verticalArrangement = spacing) {
                left()
                right()
            }
        }
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun StatusLine(label: String, text: String, ok: Boolean) {
    Row {
        Text("$label: ", Modifier.width(120.dp))
        Text(text, color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Slider that shows its own value while dragged and reports every change. */
@Composable
fun LabeledSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value.toFloat()) }
    val shown = if (dragging) local else value.toFloat()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${shown.toInt()}", Modifier.width(130.dp))
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                local = it
                onChange(it.toInt())
            },
            onValueChangeFinished = { dragging = false },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
    }
}
