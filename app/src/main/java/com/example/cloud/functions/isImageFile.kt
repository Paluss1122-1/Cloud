package com.example.cloud.functions

fun isImageFile(fileName: String): Boolean {
    return fileName.lowercase().let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                it.endsWith(".png") || it.endsWith(".gif") ||
                it.endsWith(".webp")
    }
}