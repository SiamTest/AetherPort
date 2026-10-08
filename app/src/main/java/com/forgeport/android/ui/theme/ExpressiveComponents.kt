package com.forgeport.android.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Stable Compose APIs; animations inherit the system animator duration scale. */
internal object ExpressiveMotion {
    fun <T> spatial() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 450f)
    fun <T> feedback() = spring<T>(dampingRatio = 0.8f, stiffness = 600f)
}

private data class PressStyle(val source: MutableInteractionSource, val modifier: Modifier, val shape: Shape)

@Composable
private fun pressStyle(modifier: Modifier, enabled: Boolean): PressStyle {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, ExpressiveMotion.feedback(), label = "Press scale")
    val corner by animateFloatAsState(if (pressed && enabled) 28f else 50f, ExpressiveMotion.feedback(), label = "Press shape")
    return PressStyle(source, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, RoundedCornerShape(corner.roundToInt().coerceIn(0, 50)))
}

@Composable
internal fun ExpressiveButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val press = pressStyle(modifier.heightIn(min = 56.dp), enabled)
    Button(onClick, press.modifier, enabled, shape = press.shape, interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val press = pressStyle(modifier.heightIn(min = 56.dp), enabled)
    FilledTonalButton(onClick, press.modifier, enabled, shape = press.shape, interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val press = pressStyle(modifier.heightIn(min = 56.dp), enabled)
    OutlinedButton(onClick, press.modifier, enabled, shape = press.shape, interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val press = pressStyle(modifier.heightIn(min = 52.dp), enabled)
    TextButton(onClick, press.modifier, enabled, shape = press.shape, interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val press = pressStyle(Modifier, enabled)
    IconButton(onClick, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), enabled, interactionSource = press.source) {
        Box(press.modifier, contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun ExpressiveFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val press = pressStyle(modifier.heightIn(min = 52.dp), enabled)
    val radius by animateFloatAsState(if (selected) 16f else 24f, ExpressiveMotion.feedback(), label = "Selection shape")
    FilterChip(selected, onClick, label, press.modifier, enabled, shape = RoundedCornerShape(radius.dp), interactionSource = press.source, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary, selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary))
}

@Composable
internal fun ExpressiveCard(modifier: Modifier = Modifier, colors: CardColors = CardDefaults.elevatedCardColors(), content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier.animateContentSize(ExpressiveMotion.spatial()), colors = colors, content = content)
}

@Composable
internal fun ExpressiveCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val press = pressStyle(modifier, true)
    ElevatedCard(onClick, press.modifier.animateContentSize(ExpressiveMotion.spatial()), interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveDialog(
    onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(if (entered) 1f else 0.94f, ExpressiveMotion.spatial(), label = "Dialog entry")
    val opacity by animateFloatAsState(if (entered) 1f else 0f, ExpressiveMotion.spatial(), label = "Dialog opacity")
    // Keep long forms usable in landscape, split-screen, and with large text.
    val bodyHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = opacity },
        dismissButton = dismissButton, icon = icon, title = title,
        text = { if (text != null) Box(Modifier.heightIn(max = bodyHeight).verticalScroll(rememberScrollState())) { text() } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/** A bounded reading/form surface inside the available window, not the device width. */
@Composable
internal fun AdaptiveContent(modifier: Modifier = Modifier, maxWidth: Int = 840, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth.dp).fillMaxSize(), content = content)
    }
}
