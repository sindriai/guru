/*
package com.sindriai.guru.ui.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sindriai.guru.R

*/
/**
 * Supporting models
 *//*

data class Topic(val name: String)
data class Chapter(val name: String, val topics: List<Topic>)
data class Subject(val name: String, val chapters: List<Chapter>)

*/
/**
 * Selected leaf (topic) path
 *//*

data class SelectedPathXX(
    val subject: String,
    val chapter: String,
    val topic: String
)

@Composable
fun LearningDrawerContent(
    drawerState: DrawerState,
    userName: String,
    isLoggedIn: Boolean,
    subjects: List<Subject>,
    selected: SelectedPath,
    onTopicSelected: (SelectedPath) -> Unit,
    onCloseClick: () -> Unit
) {
    val expandedSubjects = remember { mutableStateMapOf<String, Boolean>() }
    val expandedChapters = remember { mutableStateMapOf<String, Boolean>() }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight(),
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        DrawerHeader(
            userName = if (isLoggedIn) userName else "Guest",
            onCloseClick = onCloseClick
        )

        Divider(color = Color.Black.copy(alpha = 0.08f))

        Text(
            text = "Syllabus",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .padding(bottom = 8.dp)
        ) {
            items(subjects) { subject ->
                val subjectExpanded = expandedSubjects[subject.name] ?: true
                expandedSubjects[subject.name] = subjectExpanded // keep open by default

                DrawerSubjectRow(
                    title = subject.name,
                    expanded = subjectExpanded,
                    onToggle = { expandedSubjects[subject.name] = !subjectExpanded }
                )

                AnimatedVisibility(visible = subjectExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .animateContentSize()
                    ) {
                        subject.chapters.forEach { chapter ->
                            val chapterKey = "${subject.name}|${chapter.name}"
                            val chapterExpanded = expandedChapters[chapterKey] ?: true
                            expandedChapters[chapterKey] = chapterExpanded // keep open by default

                            DrawerChapterRow(
                                title = chapter.name,
                                expanded = chapterExpanded,
                                onToggle = { expandedChapters[chapterKey] = !chapterExpanded }
                            )

                            AnimatedVisibility(visible = chapterExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 18.dp, bottom = 8.dp)
                                        .animateContentSize()
                                ) {
                                    chapter.topics.forEach { topic ->
                                        DrawerTopicRow(
                                            title = topic.name,
                                            selected = selected.subject == subject.name &&
                                                    selected.chapter == chapter.name &&
                                                    selected.topic == topic.name,
                                            onClick = {
                                                onTopicSelected(
                                                    SelectedPath(
                                                        subject = subject.name,
                                                        chapter = chapter.name,
                                                        topic = topic.name
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(
                    color = Color.Black.copy(alpha = 0.06f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    userName: String,
    onCloseClick: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFAFA))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_account_user),
                        contentDescription = "Profile avatar",
                        tint = Color.Black.copy(alpha = 0.55f)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Class 10 • NCERT",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Close drawer"
                )
            }
        }
    }
}

@Composable
private fun DrawerSubjectRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = if (expanded) painterResource(R.drawable.ic_arrow_down) else painterResource(R.drawable.ic_arrow_right),
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = Color.Black.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun DrawerChapterRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = Color.Black.copy(alpha = 0.78f)
        )
        Icon(
            painter = if (expanded) painterResource(R.drawable.ic_arrow_down) else painterResource(R.drawable.ic_arrow_right),
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = Color.Black.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun DrawerTopicRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color.Black.copy(alpha = 0.06f) else Color.Transparent
    val fg = if (selected) Color.Black else Color.Black.copy(alpha = 0.72f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•  $title",
            style = MaterialTheme.typography.bodyMedium,
            color = fg
        )
    }
}

*/
/**
 * Only one chapter: "2D and 3D Graphs"
 * Two topics under it:
 *  - 2D Graphs
 *  - 3D Graphs
 *//*

fun ncertClass10Subjects(): List<Subject> = listOf(
    Subject(
        name = "Maths",
        chapters = listOf(
            Chapter(
                name = "2D and 3D Graphs",
                topics = listOf(
                    Topic("2D Graphs in an Isomorphic Geometry in String Theory"),
                    Topic("3D Graphs")
                )
            )
        )
    )
)*/
