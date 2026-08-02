package com.sindriai.guru.data.courses

import kotlinx.serialization.Serializable

@Serializable
data class CourseWrapper(
    val course: Course
)

@Serializable
data class Course(
    val pub: String,
    val id: String,
    val name: String,
    val chapters: List<Chapter> = emptyList()
)

@Serializable
data class Chapter(
    val id: String,
    val name: String,
    val topics: List<Topic> = emptyList()
)

@Serializable
data class Topic(
    val id: String,
    val name: String
)