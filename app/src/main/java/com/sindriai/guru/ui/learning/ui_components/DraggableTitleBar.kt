package com.sindriai.guru.ui.learning.ui_components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sindriai.guru.R
import com.sindriai.guru.ui.learning.nav.SelectedPath

@Composable
fun DraggableTitleBar(
    selectedTopic: SelectedPath,
    dividerPositionDp: Float,
    onDividerPositionChange: (Float) -> Unit,
    onMenuClick: () -> Unit,
    onShowNotesClick:(Boolean) -> Unit,
    screenHeightDp: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val currentPosition by rememberUpdatedState(dividerPositionDp)
    val currentCallback by rememberUpdatedState(onDividerPositionChange)

    val minTopDp = 80f
    val minBottomDp = 120f
    val dragSensitivity = 1f
    val titleSectionHeight = 50.dp
    val titleRowHeight = 48.dp

    val hasTopic = selectedTopic.topic.isNotBlank()

    var showNotes by rememberSaveable { mutableStateOf(false) }

    Log.d("TopicId","Topic Id changed on Draggable "+selectedTopic)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(titleSectionHeight)
            .background(Color.Black.copy(alpha = 1.0f))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmountPx ->
                    change.consume()
                    val dragDp = with(density) { dragAmountPx.toDp().value } * dragSensitivity
                    val maxTopDp = (screenHeightDp - minBottomDp - titleSectionHeight.value)
                        .coerceAtLeast(minTopDp)
                    val newPosition = (currentPosition + dragDp).coerceIn(minTopDp, maxTopDp)
                    currentCallback(newPosition)
                }
            }
    ) {
        //Divider(thickness = 0.5.dp, color = Color.Black.copy(alpha = 1.0f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleRowHeight)
                .padding(start = 0.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = "Open drawer",
                    tint = Color.Unspecified
                )
            }

            if (hasTopic) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = selectedTopic.topic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 1.0f),
                        maxLines = 1
                    )

                    Text(
                        text = "${selectedTopic.course} | ${selectedTopic.chapter}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp), // move a little upward visually
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${selectedTopic.course} | ${selectedTopic.chapter}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        color = Color.White.copy(alpha = 1.0f),
                        maxLines = 1
                    )
                }
            }

            IconButton(onClick = {
                Log.d("CLICK","Changing showNotes from $showNotes to ${!showNotes}")
                showNotes = !showNotes
                onShowNotesClick(showNotes)
            }) {
                Icon(
                    painter = painterResource(if (!showNotes) R.drawable.ic_notes_show
                    else R.drawable.ic_notes_hide),
                    contentDescription = "Open drawer",
                    tint = Color.Unspecified
                )
            }
        }

        //Divider(thickness = 0.5.dp, color = Color.Black.copy(alpha = 1.0f))
    }
}