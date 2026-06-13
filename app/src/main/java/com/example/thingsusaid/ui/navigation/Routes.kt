package com.example.thingsusaid.ui.navigation

object Routes {
    const val CATEGORY_LIST = "category_list"
    const val NOTE_LIST = "note_list/{categoryId}"
    const val SETTINGS = "settings"

    fun noteList(categoryId: Long) = "note_list/$categoryId"
}
