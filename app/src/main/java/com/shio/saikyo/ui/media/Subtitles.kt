package com.shio.saikyo.ui.media

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.shio.saikyo.ui.ComposeContextMenu
import com.shio.saikyo.ui.ContextMenu

val subtitleStyle = TextStyle(
    color = Color.White,
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
    fontSize = 24.sp,
)

@Composable
fun InteractiveSubtitle(
    subtitle: String,
    contextMenu: ContextMenu,
    modifier: Modifier = Modifier
) {
    val state = remember(subtitle) {
        mutableStateOf(TextFieldValue(subtitle))
    }
    val toolbar = (LocalTextToolbar.current as ComposeContextMenu).apply {
        updateItems(contextMenu.items, contextMenu.maxNumVisible)
    }

    val tm = rememberTextMeasurer()

    BasicTextField(
        value = state.value,
        onValueChange = {
            state.value = it
            toolbar.text = it.getSelectedText().text
        },
        readOnly = true,
        textStyle = subtitleStyle,
        modifier = modifier.drawBehind {
            drawText(
                tm.measure(subtitle, style = subtitleStyle),
                Color.Black, drawStyle = Stroke(width = 4f)
            )
        },
    )
}