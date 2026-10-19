package com.forgeport.android.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
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
private fun pressStyle(modifier: Modifier, enabled: Boolean, morphShape: Boolean = true): PressStyle {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f, ExpressiveMotion.feedback(), label = "Press scale")
    val corner by animateFloatAsState(if (morphShape && pressed && enabled) 14f else 18f, ExpressiveMotion.feedback(), label = "Press shape")
    return PressStyle(source, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, RoundedCornerShape(corner.roundToInt().dp))
}

@Composable
internal fun ExpressiveButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val press = pressStyle(modifier.heightIn(min = 48.dp), enabled)
    Button(onClick, press.modifier, enabled, shape = press.shape, interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val press = pressStyle(modifier.heightIn(min = 48.dp), enabled)
    FilledTonalButton(onClick, press.modifier, enabled, shape = press.shape, interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val press = pressStyle(modifier.heightIn(min = 48.dp), enabled)
    OutlinedButton(onClick, press.modifier, enabled, shape = press.shape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val press = pressStyle(modifier.heightIn(min = 48.dp), enabled)
    TextButton(onClick, press.modifier, enabled, shape = press.shape, interactionSource = press.source, content = content)
}

@Composable
internal fun ExpressiveIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val press = pressStyle(Modifier, enabled, morphShape = false)
    IconButton(onClick, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), enabled, interactionSource = press.source) {
        Box(press.modifier, contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun ExpressiveFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val press = pressStyle(modifier.heightIn(min = 48.dp), enabled)
    FilterChip(selected, onClick, label, press.modifier, enabled, shape = MaterialTheme.shapes.small, interactionSource = press.source, colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface, selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.primary, selectedLeadingIconColor = MaterialTheme.colorScheme.primary))
}

@Composable
internal fun ExpressiveCard(modifier: Modifier = Modifier, colors: CardColors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface), content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier, shape = MaterialTheme.shapes.extraLarge, colors = colors, elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp), content = content)
}

@Composable
internal fun ExpressiveCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val press = pressStyle(modifier, true, morphShape = false)
    ElevatedCard(onClick, press.modifier, shape = MaterialTheme.shapes.medium, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp), interactionSource = press.source, content = content)
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

/** Filled, quietly outlined fields shared by forms, search and credential dialogs. */
@Composable
internal fun AetherPortTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false, minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, label = label, placeholder = placeholder, trailingIcon = trailingIcon,
        singleLine = singleLine, minLines = minLines, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent,
        ),
    )
}
