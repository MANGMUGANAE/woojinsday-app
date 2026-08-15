package com.daejin.woojintoday.data.model

data class Notice(
    val title: String,
    val writer: String,
    val date: String,
    val views: String,
    val hasAttachment: Boolean,
    val isNew: Boolean,
    /** Relative path, e.g. "/bbs/daejin/188/461679/artclView.do" — resolve against the site's base URL. */
    val detailPath: String
)
