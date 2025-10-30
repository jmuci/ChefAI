package com.tenmilelabs.chefai.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun FloatingActionButtonMenu(
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onCreateRecipeClick: () -> Unit,
    onImportRecipeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate the rotation of the main FAB icon
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "FAB rotation"
    )

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Menu items with animation
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Import Recipe option
                    FabMenuItem(
                        onClick = onImportRecipeClick,
                        icon = Icons.Default.Download,
                        label = "Import Recipe"
                    )

                    // Create Recipe option
                    FabMenuItem(
                        onClick = onCreateRecipeClick,
                        icon = Icons.Default.Create,
                        label = "Create Recipe"
                    )
                }
            }

            // Main FAB
            FloatingActionButton(
                onClick = {
                    onExpandedChange()
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (expanded) "Close menu" else "Open menu",
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}

@Composable
fun FabMenuItem(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Small FAB
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}


@Preview(showBackground = true, name = "FAB Menu Collapsed")
@Composable
fun FloatingActionButtonMenuCollapsedPreview() {
    var expanded by remember { mutableStateOf(false) }
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButtonMenu(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                onCreateRecipeClick = {},
                onImportRecipeClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "FAB Menu Expanded")
@Composable
fun FloatingActionButtonMenuExpandedPreview() {
    var expanded by remember { mutableStateOf(true) }
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButtonMenu(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                onCreateRecipeClick = {},
                onImportRecipeClick = {}
            )
        }
    }
}