package com.guruswarupa.launch.utils

import java.net.HttpURLConnection
import java.net.URL

object NetworkUtils {
    private const val DEFAULT_CONNECT_TIMEOUT = 10000
    private const val DEFAULT_READ_TIMEOUT = 10000

    fun readTextFromUrl(
        urlString: String,
        connectTimeout: Int = DEFAULT_CONNECT_TIMEOUT,
        readTimeout: Int = DEFAULT_READ_TIMEOUT,
        userAgent: String = "Launch-App"
    ): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
