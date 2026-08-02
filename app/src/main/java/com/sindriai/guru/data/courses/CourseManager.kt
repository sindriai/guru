package com.sindriai.guru.data.courses

import android.content.Context
import kotlinx.serialization.json.Json

class CourseManager {
}

object CourseJsonParser {

    fun loadCourseFromAssets(
        context: Context,
        fileName: String
    ): Course {

        val jsonString = context.assets
            .open(fileName)
            .bufferedReader()
            .use { it.readText() }

        val json = Json {
            ignoreUnknownKeys = true
        }

        val wrapper = json.decodeFromString<CourseWrapper>(jsonString)

        return wrapper.course
    }
}