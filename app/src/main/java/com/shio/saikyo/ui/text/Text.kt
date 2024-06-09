package com.shio.saikyo.ui.text

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.shio.saikyo.ui.ComposeContextMenu
import com.shio.saikyo.ui.ContextMenu


@Composable
fun Text(
    text: AnnotatedString,
    contextMenu: ContextMenu,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    // TODO: figure out how to implement these (if this works)
//    overflow: TextOverflow = TextOverflow.Clip,
//    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current
) {
    val finalStyle = style.merge(
        // taken from the Text() component
        color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } },
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
    )
    val state = remember(text) { mutableStateOf(TextFieldValue(text)) }
    val toolbar = (LocalTextToolbar.current as ComposeContextMenu).apply {
        updateItems(contextMenu.items, contextMenu.maxNumVisible)
    }

    BasicTextField(
        value = state.value,
        onValueChange = {
            state.value = it
            toolbar.text = it.getSelectedText().text
        },
        readOnly = true,
        textStyle = finalStyle,
        minLines = minLines,
        maxLines = maxLines,
        modifier = modifier
    )
}

@Composable
fun Text(
    text: String,
    contextMenu: ContextMenu,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    // TODO: figure out how to implement these (if this works)
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
//    overflow: TextOverflow = TextOverflow.Clip,
//    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) = Text(
    text = AnnotatedString(text),
    contextMenu = contextMenu,
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight,
    maxLines = maxLines,
    minLines = minLines,
    style = style,
)
