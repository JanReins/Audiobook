package com.janreins.audiobook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janreins.audiobook.player.SpeedSteps

@Composable
fun PlaybackSpeedDialog(speed: Float, onSetSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    val current = SpeedSteps.snap(speed)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Playback speed") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSetSpeed(SpeedSteps.stepDown(current)) }, enabled = current > SpeedSteps.MIN) { Text("−") }
                    Text(SpeedSteps.format(current), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onSetSpeed(SpeedSteps.stepUp(current)) }, enabled = current < SpeedSteps.MAX) { Text("+") }
                }
                Slider(value = current, onValueChange = { onSetSpeed(SpeedSteps.snap(it)) },
                    valueRange = SpeedSteps.MIN..SpeedSteps.MAX, steps = 49)
                listOf(0.8f, 1f, 1.2f, 1.5f, 2f).chunked(3).forEach { presets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { preset ->
                            FilterChip(selected = current == preset, onClick = { onSetSpeed(preset) },
                                label = { Text(SpeedSteps.format(preset)) })
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = { onSetSpeed(1f) }) { Text("Reset (1x)") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
