package com.guruswarupa.launch.managers

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class NetworkStatsManager {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val speedTestTimeout = 30L // seconds

    data class SpeedTestResult(
        val downloadSpeedMbps: Float,
        val uploadSpeedMbps: Float,
        val pingMs: Long,
        val jitterMs: Long
    )

    fun runSpeedTest(callback: (SpeedTestResult) -> Unit, onProgress: (String) -> Unit, onError: (String) -> Unit) {
        onProgress("Starting speedtest...")

        executor.execute {
            try {
                val testStartTime = System.currentTimeMillis()
                
                onProgress("Measuring latency...")
                val (ping, jitter) = measurePing()
                
                if (System.currentTimeMillis() - testStartTime > speedTestTimeout * 1000) {
                    handler.post { onError("Speed test timed out") }
                    return@execute
                }

                onProgress("Measuring download speed...")
                val downloadSpeed = measureDownloadSpeed()
                
                if (System.currentTimeMillis() - testStartTime > speedTestTimeout * 1000) {
                    handler.post { onError("Speed test timed out") }
                    return@execute
                }

                onProgress("Measuring upload speed...")
                val uploadSpeed = measureUploadSpeed()

                handler.post {
                    callback(SpeedTestResult(downloadSpeed, uploadSpeed, ping, jitter))
                }
            } catch (e: Exception) {
                Log.e("NetworkStatsManager", "Speedtest failed", e)
                handler.post {
                    onError("Failed: ${e.message}")
                }
            }
        }
    }

    private fun measurePing(): Pair<Long, Long> {
        val host = "8.8.8.8"
        val pings = mutableListOf<Long>()
        val count = 5

        repeat(count) {
            val start = System.currentTimeMillis()
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, 53), 1000)
                    val end = System.currentTimeMillis()
                    pings.add(end - start)
                }
            } catch (e: Exception) {
                Log.w("NetworkStatsManager", "Ping attempt failed: ${e.message}")
            }
        }

        if (pings.isEmpty()) return 0L to 0L

        val avgPing = pings.average().toLong()


        var jitterSum = 0.0
        for (p in pings) {
            jitterSum += abs(p - avgPing)
        }
        val jitter = (jitterSum / pings.size).toLong()

        return avgPing to jitter
    }

    private fun measureDownloadSpeed(): Float {
        val urls = listOf(
            "https://speed.cloudflare.com/__down?bytes=10485760", 
            "https://dl.google.com/android/repository/platform-tools-latest-linux.zip",
            "https://speedtest.tele2.net/10MB.zip"
        )
        
        val overallTimeout = 8000L // 8 seconds max
        val startTime = System.currentTimeMillis()

        for (fileUrl in urls) {
            if (System.currentTimeMillis() - startTime > overallTimeout) break
            
            var totalBytesRead = 0L
            val maxDuration = 5000L

            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 3000
                connection.readTimeout = 5000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    continue
                }

                val timeSeconds: Double
                val testStartTime = System.currentTimeMillis()
                connection.inputStream.use { inputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var n: Int

                    while (System.currentTimeMillis() - testStartTime < maxDuration) {
                        n = inputStream.read(buffer)
                        if (n == -1) break
                        totalBytesRead += n
                    }
                    val endTime = System.currentTimeMillis()
                    timeSeconds = (endTime - testStartTime) / 1000.0
                }

                connection.disconnect()

                if (timeSeconds > 0.1 && totalBytesRead > 0) {
                    val bitsLoaded = totalBytesRead * 8
                    val mbps = (bitsLoaded / timeSeconds) / 1_000_000.0
                    return (mbps * 10).roundToInt() / 10.0f
                }
            } catch (e: Exception) {
                Log.e("NetworkStatsManager", "Download test failed for $fileUrl", e)
            }
        }
        return 0f
    }

    private fun measureUploadSpeed(): Float {
        val uploadUrls = listOf(
            "https://speedtest.tele2.net/upload.php",
            "http://speedtest.belwue.net/upload-dummy.php"
        )
        
        val overallTimeout = 8000L // 8 seconds max
        val startTime = System.currentTimeMillis()

        for (uploadUrl in uploadUrls) {
            if (System.currentTimeMillis() - startTime > overallTimeout) break
            
            var totalBytesWritten = 0L
            val maxDuration = 5000L

            try {
                val url = URL(uploadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doOutput = true
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 7000


                connection.setChunkedStreamingMode(0)
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                val bufferSize = 64 * 1024
                val buffer = ByteArray(bufferSize)
                java.util.Random().nextBytes(buffer)

                val testStartTime = System.currentTimeMillis()
                connection.outputStream.use { outputStream ->
                    while (System.currentTimeMillis() - testStartTime < maxDuration) {
                        outputStream.write(buffer)
                        totalBytesWritten += bufferSize

                        if (totalBytesWritten > 50 * 1024 * 1024) break
                    }
                    outputStream.flush()
                }

                val responseCode = connection.responseCode
                val endTime = System.currentTimeMillis()
                connection.disconnect()

                if (totalBytesWritten > 0) {
                    val timeSeconds = (endTime - testStartTime) / 1000.0
                    if (timeSeconds > 0.1) {
                        val bitsUploaded = totalBytesWritten * 8
                        val mbps = (bitsUploaded / timeSeconds) / 1_000_000.0
                        return (mbps * 10).roundToInt() / 10.0f
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkStatsManager", "Upload test failed for $uploadUrl", e)
            }
        }
        return 0f
    }

    fun getNetworkUsage(): Pair<Long, Long> {
        val mobileRx = android.net.TrafficStats.getMobileRxBytes()
        val mobileTx = android.net.TrafficStats.getMobileTxBytes()
        val totalRx = android.net.TrafficStats.getTotalRxBytes()
        val totalTx = android.net.TrafficStats.getTotalTxBytes()

        val mobileTotal = if (mobileRx != -1L && mobileTx != -1L) mobileRx + mobileTx else 0L
        val grandTotal = if (totalRx != -1L && totalTx != -1L) totalRx + totalTx else 0L

        val wifiTotal = if (grandTotal > mobileTotal) grandTotal - mobileTotal else 0L

        return mobileTotal to wifiTotal
    }

    fun getWifiIpAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.contains("wlan") || networkInterface.name.contains("eth")) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return address.hostAddress ?: "Unknown"
                        }
                    }
                }
            }
            "Not Connected"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun formatDataUsage(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.getDefault(), "%.1f GB", gb)
    }




    fun cleanup() {
        executor.shutdown()
    }
}
