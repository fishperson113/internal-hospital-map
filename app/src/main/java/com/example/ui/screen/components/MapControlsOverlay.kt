package com.example.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun MapControlsOverlay(
    onCenterMyLocation: () -> Unit,
    onToggleLayers: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Layers Switch Button
        SmallFloatingActionButton(
            onClick = onToggleLayers,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            modifier = Modifier
                .size(46.dp)
                .shadow(4.dp, CircleShape)
                .testTag("map_layers_button")
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = stringResource(R.string.map_layers),
                modifier = Modifier.size(22.dp)
            )
        }

        // Center on My Location Button
        FloatingActionButton(
            onClick = onCenterMyLocation,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .size(52.dp)
                .testTag("my_location_button")
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = stringResource(R.string.my_location),
                modifier = Modifier.size(24.dp)
            )
        }

        // Zoom In Button (+)
        SmallFloatingActionButton(
            onClick = onZoomIn,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            modifier = Modifier
                .size(42.dp)
                .shadow(3.dp, CircleShape)
                .testTag("zoom_in_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Phóng to",
                modifier = Modifier.size(20.dp)
            )
        }

        // Zoom Out Button (-)
        SmallFloatingActionButton(
            onClick = onZoomOut,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            modifier = Modifier
                .size(42.dp)
                .shadow(3.dp, CircleShape)
                .testTag("zoom_out_button")
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Thu nhỏ",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
