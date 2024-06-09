package com.shio.saikyo.ui.primitives

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shio.saikyo.R

@Composable
fun NavBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = stringResource(R.string.back_nav_desc),
    color: Color =  LocalContentColor.current
) = IconButton(onClick = onClick, modifier = modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = description,
        tint = color
    )
}

@Composable
fun NavCancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = stringResource(R.string.cancel_nav_desc),
    color: Color =  LocalContentColor.current
) = IconButton(onClick = onClick, modifier = modifier) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = description,
        tint = color
    )
}

@Composable
fun ProgressNextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.progress_next_button_default_desc)
) = IconButton(onClick = onClick) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = contentDescription,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}
