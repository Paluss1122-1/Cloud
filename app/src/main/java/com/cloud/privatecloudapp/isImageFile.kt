package com.cloud.privatecloudapp

fun isImageFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast(".", "").lowercase()
    return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "mov", "avi", "mkv", "3gp")
}

fun isVideoFile(fileName: String): Boolean {
    val cleanName = fileName.substringBefore("?").trim()
    val extension = cleanName.substringAfterLast(".", "").lowercase()
    return extension in setOf("mp4", "mov", "avi", "mkv", "3gp")
}