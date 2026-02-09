package us.bergnet.oversight.ui.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgDefault = Color(0xFF1A1A2E)
private val BgFocused = Color(0xFF2A2A4E)
private val AccentColor = Color(0xFF6C63FF)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFB0B0B0)

@Composable
private fun FocusableRow(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val baseMod = modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .focusable()

    Row(
        modifier = baseMod
            .background(if (focused) BgFocused else BgDefault, RoundedCornerShape(4.dp))
            .padding(start = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(if (focused) AccentColor else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(focused)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = AccentColor,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 19.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 19.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 14.sp)
        Text(text = value, color = valueColor, fontSize = 14.sp)
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    focusRequester: FocusRequester? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val baseMod = Modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .toggleable(value = checked, onValueChange = onCheckedChange)

    Row(
        modifier = baseMod
            .background(if (focused) BgFocused else BgDefault, RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(if (focused) AccentColor else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = TextPrimary, fontSize = 15.sp)
            Text(
                text = if (checked) "ON" else "OFF",
                color = if (checked) Color(0xFF4CAF50) else Color(0xFFFF5252),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String = "",
    focusRequester: FocusRequester? = null,
    onValueChange: (Int) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val baseMod = Modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.DirectionLeft -> {
                        val newVal = (value - step).coerceIn(range)
                        if (newVal != value) onValueChange(newVal)
                        true
                    }
                    Key.DirectionRight -> {
                        val newVal = (value + step).coerceIn(range)
                        if (newVal != value) onValueChange(newVal)
                        true
                    }
                    else -> false
                }
            } else false
        }
        .focusable()

    Row(
        modifier = baseMod
            .background(if (focused) BgFocused else BgDefault, RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(if (focused) AccentColor else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = TextPrimary, fontSize = 15.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Visual bar
                val fraction = if (range.last > range.first) {
                    (value - range.first).toFloat() / (range.last - range.first)
                } else 0f
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(6.dp)
                        .background(Color(0xFF333355), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .background(AccentColor, RoundedCornerShape(3.dp))
                    )
                }
                Text(
                    text = "$value$suffix",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SelectorRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    focusRequester: FocusRequester? = null,
    onSelectionChange: (Int) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val baseMod = Modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.DirectionLeft -> {
                        val newIdx = if (selectedIndex > 0) selectedIndex - 1 else options.lastIndex
                        onSelectionChange(newIdx)
                        true
                    }
                    Key.DirectionRight -> {
                        val newIdx = if (selectedIndex < options.lastIndex) selectedIndex + 1 else 0
                        onSelectionChange(newIdx)
                        true
                    }
                    else -> false
                }
            } else false
        }
        .focusable()

    Row(
        modifier = baseMod
            .background(if (focused) BgFocused else BgDefault, RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(if (focused) AccentColor else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = TextPrimary, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "◀ ", color = if (focused) AccentColor else Color.Transparent, fontSize = 12.sp)
                Text(
                    text = options.getOrElse(selectedIndex) { "" },
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(text = " ▶", color = if (focused) AccentColor else Color.Transparent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun TextInputRow(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    hint: String? = null,
    focusRequester: FocusRequester? = null,
    onValueChange: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val textFieldFocus = remember { FocusRequester() }

    if (editing) {
        var rowFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { rowFocused = it.hasFocus }
                .background(BgFocused, RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(48.dp)
                    .background(AccentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = label, color = TextSecondary, fontSize = 12.sp)
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(textFieldFocus)
                        .padding(top = 4.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(AccentColor),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onValueChange(editText)
                            editing = false
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AccentColor, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            innerTextField()
                        }
                    }
                )
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            textFieldFocus.requestFocus()
        }
    } else {
        val baseMod = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    editing = true
                    true
                } else false
            }
            .focusable()

        Row(
            modifier = baseMod
                .background(if (focused) BgFocused else BgDefault, RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(if (focused) AccentColor else Color.Transparent)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, color = TextPrimary, fontSize = 15.sp)
                    Text(text = value, color = TextSecondary, fontSize = 14.sp)
                }
                if (hint != null) {
                    Text(text = hint, color = Color(0xFF888888), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun ActionButtonRow(
    label: String,
    buttonText: String,
    buttonColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 19.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextPrimary, fontSize = 15.sp)
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                disabledContainerColor = buttonColor.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(text = buttonText, fontSize = 13.sp)
        }
    }
}
