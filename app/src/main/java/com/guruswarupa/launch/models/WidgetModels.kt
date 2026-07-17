package com.guruswarupa.launch.models

data class SystemWidgetInfo(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val minWidth: Int,
    val minHeight: Int,
    val customHeightDp: Int? = null
)

data class PendingSystemWidgetBindRequest(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String
)
