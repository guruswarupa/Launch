package com.guruswarupa.launch.models

import java.io.Serializable

data class WebAppEntry(
    val id: String,
    val name: String,
    val url: String,
    val blockRedirects: Boolean = false
) : Serializable
