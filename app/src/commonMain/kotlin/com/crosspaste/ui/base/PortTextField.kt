package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Keyboard_arrow_down
import com.composables.icons.materialsymbols.rounded.Keyboard_arrow_up
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.xLarge

/**
 * A Material 3 styled Port input field with increment/decrement steppers.
 * * @param value The current port string value.
 * @param onValueChange Callback when the port value changes.
 * @param modifier Modifier for the text field.
 * @param label The label of the text field, default is "Port".
 * @param range The valid range for the port, default is 0..65535.
 */
@Composable
fun PortTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = small2XRoundedCornerShape,
    label: String,
    range: IntRange = 0..65535,
    enabled: Boolean = true,
) {
    // Helper function to safe update value within range
    val updateValue = { newValue: Int ->
        if (newValue in range) {
            onValueChange(newValue.toString())
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            // Only allow digits and ensure it's within range or empty
            if (input.isEmpty()) {
                onValueChange("")
            } else if (input.all { it.isDigit() }) {
                val numericValue = input.toIntOrNull()
                if (numericValue != null && numericValue in range) {
                    onValueChange(input)
                }
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        enabled = enabled,
        trailingIcon = {
            PortStepper(
                onIncrement = {
                    val current = value.toIntOrNull() ?: 0
                    updateValue(current + 1)
                },
                onDecrement = {
                    val current = value.toIntOrNull() ?: 0
                    updateValue(current - 1)
                },
                enabled = enabled,
            )
        },
        shape = shape,
    )
}

@Composable
private fun PortStepper(
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    enabled: Boolean,
) {
    // Using a vertical column to house the up/down arrows
    Column(
        modifier =
            Modifier
                .wrapContentHeight()
                .padding(end = tiny3X),
        // Add a small gap from the right edge
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onIncrement,
            modifier = Modifier.size(xLarge),
            enabled = enabled,
            shape = RectangleShape,
        ) {
            Icon(
                MaterialSymbols.Rounded.Keyboard_arrow_up,
                contentDescription = "Increase Port",
                modifier = Modifier.size(large2X),
            )
        }

        IconButton(
            onClick = onDecrement,
            modifier = Modifier.size(xLarge),
            enabled = enabled,
            shape = RectangleShape,
        ) {
            Icon(
                MaterialSymbols.Rounded.Keyboard_arrow_down,
                contentDescription = "Decrease Port",
                modifier = Modifier.size(large2X),
            )
        }
    }
}
