package com.sindriai.guru.ui.learning.nav

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sindriai.guru.R
import com.sindriai.guru.data.courses.Course
import com.sindriai.guru.data.courses.CourseJsonParser

data class SelectedPath(
    val course: String,
    val chapter: String,
    val topic: String,
    val id: String
)

@Composable
fun LearningDrawerContent(
    context: Context,
    drawerState: DrawerState,
    userName: String,
    isLoggedIn: Boolean,
    subjects: List<Course>,
    selected: SelectedPath,
    onTopicSelected: (SelectedPath) -> Unit,
    onCloseClick: () -> Unit,
    onOpenCourseStore: () -> Unit
) {
    val expandedCourses = remember { mutableStateMapOf<String, Boolean>() }
    val languageState = remember { mutableStateOf("Hinglish") }
    val expandedChapters = remember { mutableStateMapOf<String, Boolean>() }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight(),
        drawerShape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp),
        drawerContainerColor = Color.White
    ) {

        // ---------- TOP: Guru + Premium + Language ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "Guru",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 10.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFFFFF),
                modifier = Modifier.clickable { /* TODO: handle premium click */ }
            ) {
                /*Text(
                    text = "Premium",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color(0xFFE65100)
                )*/
            }

            Spacer(modifier = Modifier.weight(1f))

            Spacer(Modifier.size(6.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFFFFF),
                modifier = Modifier.clickable {
                    languageState.value = "Hinglish"
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF1B5E20), CircleShape)
                    )

                    Spacer(Modifier.size(6.dp))

                    Text(
                        text = languageState.value,
                        fontSize = 11.sp,
                        color = Color(0xFF1B5E20)
                    )
                }
            }
        }

        Divider(thickness = 0.5.dp, color = Color.Black.copy(alpha = 0.25f))

        // ---------- SCROLLABLE MIDDLE CONTENT ----------
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            item {
                Text(
                    text = "Courses",
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp, 12.dp, 0.dp, 6.dp)
                )
            }

            items(subjects) { subject ->
                val expanded = expandedCourses[subject.id] ?: false

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedCourses[subject.id] = !expanded
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subject.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )

                    Icon(
                        painter = if (expanded) {
                            painterResource(R.drawable.ic_arrow_down)
                        } else {
                            painterResource(R.drawable.ic_arrow_right)
                        },
                        contentDescription = null
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(start = 20.dp)) {

                        subject.chapters.forEach { chapter ->
                            val chapterKey = "${subject.id}|${chapter.id}"
                            val chapterExpanded = expandedChapters[chapterKey] ?: false
                            val topics = chapter.topics.orEmpty()
                            val hasTopics = topics.isNotEmpty()

                            val isChapterSelected =
                                selected.course == subject.name &&
                                        selected.chapter == chapter.name &&
                                        selected.topic.isEmpty()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isChapterSelected) {
                                            Color.Black.copy(alpha = 0.06f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable {
                                        if (hasTopics) {
                                            expandedChapters[chapterKey] = !chapterExpanded
                                        } else {
                                            onTopicSelected(
                                                SelectedPath(
                                                    course = subject.name,
                                                    chapter = chapter.name,
                                                    topic = "",
                                                    id = chapter.id
                                                )
                                            )
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = chapter.name,
                                    modifier = Modifier.weight(1f),
                                    color = if (isChapterSelected) {
                                        Color.Black
                                    } else {
                                        Color.Black.copy(alpha = 0.85f)
                                    },
                                    fontWeight = if (isChapterSelected) {
                                        FontWeight.Medium
                                    } else {
                                        FontWeight.Normal
                                    }
                                )

                                if (hasTopics) {
                                    Icon(
                                        painter = if (chapterExpanded) {
                                            painterResource(R.drawable.ic_arrow_down)
                                        } else {
                                            painterResource(R.drawable.ic_arrow_right)
                                        },
                                        contentDescription = null
                                    )
                                }
                            }

                            AnimatedVisibility(visible = hasTopics && chapterExpanded) {
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    topics.forEach { topic ->
                                        DrawerTopicRow(
                                            title = topic.name,
                                            selected =
                                                selected.course == subject.name &&
                                                        selected.chapter == chapter.name &&
                                                        selected.topic == topic.name,
                                            onClick = {
                                                onTopicSelected(
                                                    SelectedPath(
                                                        course = subject.name,
                                                        chapter = chapter.name,
                                                        topic = topic.name,
                                                        id = topic.id
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
            }

            item {
                Spacer(Modifier.size(16.dp))
            }
        }

        Divider(thickness = 0.5.dp, color = Color.Black.copy(alpha = 0.25f))

        // ---------- USER + COURSE STORE ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoggedIn && userName.isNotEmpty()) {
                            userName.take(1).uppercase()
                        } else {
                            "G"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.size(8.dp))

                Text(
                    text = if (isLoggedIn) userName else "Guest",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2E7D32),
                modifier = Modifier.clickable { onOpenCourseStore() }
            ) {
                Text(
                    text = "Book Store",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

fun ncertClass10Subjects(context: Context): List<Course> = listOf(
    CourseJsonParser.loadCourseFromAssets(
        context,
        "course_materials/P001C005/math_class_10_biology.json"
    )
    // Add more courses here
)

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