package com.eventfinder.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object EventFinderSpacing {
    val PageHorizontal = 20.dp
    val SectionGap = 24.dp
    val CardGap = 14.dp
    val CardPadding = 16.dp
    val SmallGap = 8.dp
    val LargeRadius = 24.dp
    val CardRadius = 20.dp
    val ButtonRadius = 16.dp
}

@Composable
fun ResponsiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 760.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}
