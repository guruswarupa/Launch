package com.guruswarupa.launch.models

import java.io.Serializable

data class WebAppEntry(
    val id: String,
    val name: String,
    val url: String,
    val blockRedirects: Boolean = false,
    /** "auto" (desktop for sites that need it, such as YouTube), "mobile" or "desktop". */
    val viewMode: String = "auto"
) : Serializable
