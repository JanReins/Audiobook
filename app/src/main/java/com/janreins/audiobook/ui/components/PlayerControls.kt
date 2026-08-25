package com.janreins.audiobook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Clean Minimalism Playback controls:
 * - 4 sleek skip buttons: -1m, -15s, +15s, +1m with icons and compact tags
 * - 72dp prominent center Play / Pause button
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipBackward15s: () -> Unit,
    onSkipBackward1m: () -> Unit,
    onSkipForward15s: () -> Unit,
    onSkipForward1m: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Skip -1 Minute
        MinimalSkipButton(
            tag = "1m",
            isRewind = true,
            isLong = true,
            testTag = "skip_backward_1m_button",
            onClick = onSkipBackward1m
        )

        // Skip -15 Seconds
        MinimalSkipButton(
            tag = "15s",
            isRewind = true,
            isLong = false,
            testTag = "skip_backward_15s_button",
            onClick = onSkipBackward15s
        )

        // Center 72dp Play / Pause Button
        Surface(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlayPause
                )
                .shadow(elevation = 8.dp, shape = CircleShape)
                .testTag("play_pause_button"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause Audiobook" else "Play Audiobook",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Skip +15 Seconds
        MinimalSkipButton(
            tag = "15s",
            isRewind = false,
            isLong = false,
            testTag = "skip_forward_15s_button",
            onClick = onSkipForward15s
        )

        // Skip +1 Minute
        MinimalSkipButton(
            tag = "1m",
            isRewind = false,
            isLong = true,
            testTag = "skip_forward_1m_button",
            onClick = onSkipForward1m
        )
    }
}

/**
 * Minimalist circular skip button with directional icon and compact label underneath.
 */
@Composable
private fun MinimalSkipButton(
    tag: String,
    isRewind: Boolean,
    isLong: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.size(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isRewind) {
                    if (isLong) Icons.Default.FastRewind else Icons.Default.Replay
                } else {
                    if (isLong) Icons.Default.FastForward else Icons.Default.Update
                },
                contentDescription = if (isRewind) "Rewind $tag" else "Forward $tag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
