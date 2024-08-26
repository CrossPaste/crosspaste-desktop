package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.material.TextFieldDefaults.textFieldWithLabelPadding
import androidx.compose.material.TextFieldDefaults.textFieldWithoutLabelPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.devices.measureTextWidth

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape =
        MaterialTheme.shapes.small.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    contentPadding: PaddingValues =
        if (label == null) {
            textFieldWithoutLabelPadding()
        } else {
            textFieldWithLabelPadding()
        },
) {
// If color is not provided via the text style, use content color as a default
    val textColor =
        textStyle.color.takeOrElse {
            colors.textColor(enabled).value
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    @OptIn(ExperimentalMaterialApi::class)
    (
        BasicTextField(
            value = value,
            modifier =
                modifier
                    .background(colors.backgroundColor(enabled).value, shape)
                    .indicatorLine(enabled, isError, interactionSource, colors)
                    .defaultMinSize(
                        minWidth = TextFieldDefaults.MinWidth,
                        minHeight = TextFieldDefaults.MinHeight,
                    ),
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(colors.cursorColor(isError).value),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            decorationBox = @Composable { innerTextField ->
                // places leading icon, text field with label and placeholder, trailing icon
                TextFieldDefaults.TextFieldDecorationBox(
                    value = value,
                    visualTransformation = visualTransformation,
                    innerTextField = innerTextField,
                    placeholder = placeholder,
                    label = label,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    singleLine = singleLine,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    contentPadding = contentPadding,
                )
            },
        )
    )
}

@Composable
fun DefaultTextField(
    fixContentWidth: Dp? = null,
    isError: Boolean = false,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val textWidth =
        fixContentWidth
            ?: measureTextWidth(
                value,
                LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
            )

    CustomTextField(
        modifier = Modifier.width(textWidth + 16.dp).wrapContentHeight(),
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle =
            LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 10.sp,
            ),
        colors =
            TextFieldDefaults.textFieldColors(
                focusedIndicatorColor = MaterialTheme.colors.primary,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        contentPadding = PaddingValues(0.dp),
    )
}
