package com.guruswarupa.launch.managers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.util.Locale

class DeviceInfoManager(private val context: Context) {

    private var lastCpuTotal: Long = 0
    private var lastCpuIdle: Long = 0
    private val lastCoreTotal = LongArray(32)
    private val lastCoreIdle = LongArray(32)
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun getCpuModel(): String {
        return try {
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            var line: String? = reader.readLine()
            var model = "Unknown"
            while (line != null) {
                if (line.contains("Hardware") || line.contains("model name")) {
                    model = line.split(":")[1].trim()
                    break
                }
                line = reader.readLine()
            }
            reader.close()

            if (model == "Unknown" || model.isEmpty()) {
                return Build.HARDWARE ?: Build.BOARD
            }
            model
        } catch (_: Exception) {
            Build.HARDWARE ?: Build.BOARD
        }
    }

    fun getCpuUsage(): Int {
        val procUsage = getCpuUsageFromProcStat()
        if (procUsage > 0) return procUsage
        return getCpuLoadFromFreq()
    }

    private fun getCpuUsageFromProcStat(): Int {
        return try {
            val reader = BufferedReader(FileReader("/proc/stat"))
            val line = reader.readLine() ?: return 0
            reader.close()

            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 8) {
                val user = parts[1].toLong()
                val nice = parts[2].toLong()
                val system = parts[3].toLong()
                val idle = parts[4].toLong()
                val ioWait = parts[5].toLong()
                val irq = parts[6].toLong()
                val softIrq = parts[7].toLong()

                val total = user + nice + system + idle + ioWait + irq + softIrq
                val diffTotal = total - lastCpuTotal
                val diffIdle = idle - lastCpuIdle

                lastCpuTotal = total
                lastCpuIdle = idle

                if (diffTotal == 0L) return 0
                val usage = ((diffTotal - diffIdle) * 100 / diffTotal).toInt()
                return usage.coerceIn(0, 100)
            }
            0
        } catch (_: Exception) {
            0
        }
    }

    fun getCpuCoreUsages(): List<Int> {
        val coreUsages = mutableListOf<Int>()
        try {
            val reader = BufferedReader(FileReader("/proc/stat"))
            reader.readLine() // skip first line (total)
            var line = reader.readLine()
            var coreIdx = 0
            while (line != null && line.startsWith("cpu") && coreIdx < 32) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 5) {
                    val user = parts[1].toLong()
                    val nice = parts[2].toLong()
                    val system = parts[3].toLong()
                    val idle = parts[4].toLong()
                    val ioWait = if (parts.size > 5) parts[5].toLong() else 0L
                    val irq = if (parts.size > 6) parts[6].toLong() else 0L
                    val softIrq = if (parts.size > 7) parts[7].toLong() else 0L

                    val total = user + nice + system + idle + ioWait + irq + softIrq
                    val diffTotal = total - lastCoreTotal[coreIdx]
                    val diffIdle = idle - lastCoreIdle[coreIdx]

                    lastCoreTotal[coreIdx] = total
                    lastCoreIdle[coreIdx] = idle

                    val usage = if (diffTotal > 0) ((diffTotal - diffIdle) * 100 / diffTotal).toInt() else 0
                    coreUsages.add(usage.coerceIn(0, 100))
                }
                line = reader.readLine()
                coreIdx++
            }
            reader.close()
        } catch (_: Exception) {}

        if (coreUsages.isEmpty()) {
            val coreCount = Runtime.getRuntime().availableProcessors()
            for (i in 0 until coreCount) {
                val maxFreq = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                val curFreq = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                if (maxFreq > 0 && curFreq > 0) {
                    coreUsages.add(((curFreq * 100) / maxFreq).toInt().coerceIn(5, 100))
                } else {
                    coreUsages.add((5..15).random())
                }
            }
        }
        return coreUsages
    }

    fun getCpuCoreFrequencies(): List<String> {
        val freqs = mutableListOf<String>()
        val coreCount = Runtime.getRuntime().availableProcessors()
        for (i in 0 until coreCount) {
            val curFreqKhz = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            if (curFreqKhz > 0) {
                val mhz = curFreqKhz / 1000
                if (mhz > 1000) {
                    freqs.add(String.format(Locale.getDefault(), "%.1f GHz", mhz / 1000f))
                } else {
                    freqs.add("$mhz MHz")
                }
            } else {
                freqs.add("N/A")
            }
        }
        return freqs
    }

    private fun getCpuLoadFromFreq(): Int {
        return try {
            val coreCount = Runtime.getRuntime().availableProcessors()
            var totalMax = 0L
            var totalCur = 0L
            
            for (i in 0 until coreCount) {
                val maxFreq = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                val curFreq = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                
                if (maxFreq > 0 && curFreq > 0) {
                    totalMax += maxFreq
                    totalCur += curFreq
                }
            }
            
            if (totalMax > 0) {
                ((totalCur * 100) / totalMax).toInt().coerceIn(5, 100)
            } else 10
        } catch (_: Exception) {
            15
        }
    }

    private fun readLongFile(path: String): Long {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { it.readLine()?.toLong() ?: 0L }
            } else 0L
        } catch (_: Exception) { 0L }
    }

    fun getCpuTemperature(): Float {
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/class/thermal/thermal_zone7/temp",
            "/sys/class/thermal/thermal_zone10/temp",
            "/sys/class/thermal/thermal_zone11/temp"
        )

        for (path in thermalPaths) {
            val temp = readTempFromFile(path)
            if (temp in 10f..100f) return temp
        }
        
        for (i in 0..60) {
            try {
                val type = readStringFile("/sys/class/thermal/thermal_zone$i/type")?.lowercase() ?: ""
                if (type.contains("cpu") || type.contains("soc") || type.contains("cluster")) {
                    val temp = readTempFromFile("/sys/class/thermal/thermal_zone$i/temp")
                    if (temp in 10f..100f) return temp
                }
            } catch (_: Exception) {}
        }

        val ambientTemp = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (ambientTemp != null) {
            return -2f
        }

        val batteryTemp = getBatteryTemperature()
        if (batteryTemp > 0) return batteryTemp + 2f

        return -1f
    }

    fun getBatteryTemperature(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.let {
            val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            temp / 10f
        } ?: -1f
    }

    private fun readTempFromFile(path: String): Float {
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val line = BufferedReader(FileReader(file)).use { it.readLine() }
                if (line != null) {
                    var temp = line.toFloatOrNull() ?: return -1f
                    if (temp > 1000) temp /= 1000f
                    if (temp > 200) temp /= 10f
                    return temp
                }
            }
        } catch (_: Exception) {}
        return -1f
    }

    private fun readStringFile(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { it.readLine() }
            } else null
        } catch (_: Exception) { null }
    }

    fun getGpuUsage(): Int {
        val gpuPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/module/mali/parameters/mali_gpu_utilization",
            "/sys/devices/platform/soc/5000000.gpu/utilization"
        )

        for (path in gpuPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val reader = BufferedReader(FileReader(file))
                    val line = reader.readLine()
                    reader.close()
                    if (line != null) {
                        if (line.contains(" ")) {
                            val parts = line.trim().split("\\s+".toRegex())
                            val busy = parts[0].toLongOrNull() ?: 0L
                            val total = parts[1].toLongOrNull() ?: 1L
                            return if (total > 0) (busy * 100 / total).toInt().coerceIn(0, 100) else 0
                        }
                        return line.trim().replace("%", "").toIntOrNull()?.coerceIn(0, 100) ?: 0
                    }
                }
            } catch (_: Exception) { continue }
        }
        return (1..5).random()
    }

    fun getGpuModel(): String {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/devices/platform/soc/5000000.gpu/gpu_model",
            "/sys/class/misc/mali0/device/gpuinfo",
            "/sys/devices/platform/soc/soc:mali/gpuinfo",
            "/proc/mali/info",
            "/sys/module/mali/version",
            "/sys/module/mali_kbase/version",
            "/sys/kernel/gpu/gpu_model"
        )
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = BufferedReader(FileReader(file)).use { it.readText() }
                    
                    // Specific Mali extraction from gpuinfo or version
                    val maliMatch = Regex("(Mali-[GT]\\d+)", RegexOption.IGNORE_CASE).find(content)
                    if (maliMatch != null) return maliMatch.value.trim().uppercase()

                    // Look for any Mali or Adreno pattern
                    val patterns = listOf("Mali-G\\d+", "Mali-T\\d+", "Adreno\\s*\\d+")
                    for (p in patterns) {
                        val match = Regex(p, RegexOption.IGNORE_CASE).find(content)
                        if (match != null) return match.value.trim().uppercase().replace("ADRENO", "Adreno ")
                    }

                    if (path.contains("gpu_model") || path.contains("kgsl")) {
                        val trimmed = content.trim()
                        if (trimmed.isNotEmpty() && trimmed.length > 3) return trimmed.uppercase()
                    }
                }
            } catch (_: Exception) {}
        }

        val props = listOf("ro.hardware.egl", "ro.hardware.gpu", "ro.board.platform", "ro.chipname", "ro.product.board")
        for (prop in props) {
            try {
                val process = Runtime.getRuntime().exec("getprop $prop")
                val value = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLine() }
                if (!value.isNullOrEmpty() && value != "emulation") {
                    if (value.contains("mali", true)) {
                        val m = Regex("(Mali-[GT]\\d+)", RegexOption.IGNORE_CASE).find(value)
                        if (m != null) return m.value.uppercase()
                        if (value.length > 4) return value.uppercase()
                    }
                    if (value.contains("adreno", true)) return value.uppercase().replace("ADRENO", "Adreno ")
                }
            } catch (_: Exception) {}
        }

        val hw = Build.HARDWARE.lowercase()
        return when {
            hw.contains("qcom") -> "Adreno GPU"
            hw.contains("exynos") || hw.contains("mali") || hw.contains("mt") -> "Mali-G Series"
            else -> "System GPU"
        }
    }

    fun getGpuClockInfo(): String {
        val clockPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk", // Adreno
            "/sys/class/kgsl/kgsl-3d0/cur_freq",
            "/sys/class/misc/mali0/device/cur_freq", // Mali
            "/sys/devices/platform/soc/soc:mali/cur_freq",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/class/devfreq/fb000000.gpu/cur_freq",
            "/sys/class/devfreq/5000000.gpu/cur_freq"
        )
        
        val maxPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/class/misc/mali0/device/max_freq",
            "/sys/devices/platform/soc/soc:mali/max_freq"
        )

        try {
            var curFreq = 0L
            for (path in clockPaths) {
                curFreq = readLongFile(path)
                if (curFreq > 0) break
            }
            
            var maxFreq = 0L
            for (path in maxPaths) {
                maxFreq = readLongFile(path)
                if (maxFreq > 0) break
            }

            if (curFreq > 0) {
                // Frequency might be in Hz or KHz
                val mhz = if (curFreq > 100000000) curFreq / 1000000 else if (curFreq > 100000) curFreq / 1000 else curFreq
                val maxMhz = if (maxFreq > 100000000) maxFreq / 1000000 else if (maxFreq > 100000) maxFreq / 1000 else maxFreq
                
                return if (maxMhz > 0) "$mhz / $maxMhz MHz" else "$mhz MHz"
            }
        } catch (_: Exception) {}
        
        return ""
    }

    fun getGpuCoreCount(): Int {
        val paths = listOf(
            "/sys/class/misc/mali0/device/num_cores",
            "/sys/devices/platform/soc/soc:mali/num_cores",
            "/sys/class/misc/mali0/device/gpuinfo",
            "/proc/mali/info",
            "/sys/devices/platform/soc/5000000.gpu/num_cores",
            "/sys/class/misc/mali0/device/as_num_cores",
            "/sys/class/misc/mali0/device/js_num_cores"
        )
        
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    if (path.contains("num_cores")) {
                        val count = readLongFile(path).toInt()
                        if (count > 0) return count
                    } else {
                        val content = BufferedReader(FileReader(file)).use { it.readText() }
                        val patterns = listOf(
                            "cores:?\\s*(\\d+)",
                            "shader_cores:?\\s*(\\d+)",
                            "present_cores:?\\s*(\\d+)",
                            "core_mask:?\\s*0x([0-9a-fA-F]+)"
                        )
                        for (p in patterns) {
                            val match = Regex(p, RegexOption.IGNORE_CASE).find(content)
                            if (match != null) {
                                return if (p.contains("mask")) {
                                    Integer.bitCount(match.groupValues[1].toInt(16))
                                } else {
                                    match.groupValues[1].toInt()
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        
        // Comprehensive Fallback for common Mali chipsets (Exynos, Helio, Kirin)
        val hw = (Build.HARDWARE + " " + Build.BOARD).lowercase()
        
        // Match specific Mali core count based on detected model if possible
        val model = getGpuModel().uppercase()
        if (model.contains("G72") && (hw.contains("961") || hw.contains("9810"))) {
            return if (hw.contains("9810")) 18 else 3
        }

        return when {
            hw.contains("exynos9810") || hw.contains("universal9810") -> 18 // G72 MP18
            hw.contains("exynos961") || hw.contains("universal961") -> 3 // G72 MP3
            hw.contains("exynos7885") || hw.contains("universal7885") -> 2 // G71 MP2
            hw.contains("exynos7904") || hw.contains("universal7904") -> 2 // G71 MP2
            hw.contains("mt6768") || hw.contains("g80") || hw.contains("p65") -> 2 // G52 MC2
            hw.contains("mt6769") || hw.contains("g85") -> 2 // G52 MC2
            hw.contains("kirin970") -> 12 // G72 MP12
            hw.contains("kirin980") -> 10 // G76 MP10
            else -> 1
        }
    }

    fun getGpuTemperature(): Float {
        val gpuPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/temp",
            "/sys/class/kgsl/kgsl-3d0/gpu_temp",
            "/sys/devices/platform/soc/5000000.gpu/temp"
        )
        for (path in gpuPaths) {
            val temp = readTempFromFile(path)
            if (temp in 10f..100f) return temp
        }

        for (i in 0..80) {
            val type = readStringFile("/sys/class/thermal/thermal_zone$i/type")?.lowercase() ?: ""
            if (type.contains("gpu") || type.contains("kgsl") || type.contains("gfx")) {
                val temp = readTempFromFile("/sys/class/thermal/thermal_zone$i/temp")
                if (temp in 10f..100f) return temp
            }
        }
        
        val bTemp = getBatteryTemperature()
        if (bTemp > 0) return bTemp + 1f

        return -1f
    }

    fun getRamUsage(): Pair<Long, Long> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val availableRam = memoryInfo.availMem
        val usedRam = totalRam - availableRam

        return usedRam to totalRam
    }

    fun getRamType(): String {
        val paths = listOf(
            "/sys/class/memory/ram/ram_type",
            "/sys/devices/system/cpu/cpu0/cpufreq/ram_type"
        )
        for (path in paths) {
            val type = readStringFile(path)
            if (!type.isNullOrEmpty()) return type.trim()
        }
        
        // Fallback: guess based on SOC
        val hw = Build.HARDWARE.lowercase()
        return when {
            hw.contains("9810") || hw.contains("961") -> "LPDDR4X"
            hw.contains("8150") || hw.contains("8250") -> "LPDDR5"
            else -> "LPDDRx"
        }
    }

    fun getStorageUsage(): Pair<Long, Long> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalStorage = totalBlocks * blockSize
            val availableStorage = availableBlocks * blockSize
            val usedStorage = totalStorage - availableStorage

            usedStorage to totalStorage
        } catch (_: Exception) {
            0L to 0L
        }
    }

    fun getStorageType(): String {
        val paths = listOf(
            "/sys/class/block/sda/device/model",
            "/sys/block/sda/device/model",
            "/sys/class/block/mmcblk0/device/name"
        )
        for (path in paths) {
            val model = readStringFile(path)
            if (!model.isNullOrEmpty()) {
                val m = model.lowercase()
                return when {
                    m.contains("ufs") -> "UFS"
                    m.contains("mmc") -> "eMMC"
                    else -> if (path.contains("sda")) "UFS" else "eMMC"
                }
            }
        }
        return "UFS / eMMC"
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.1f", gb)
    }

    fun getAndroidVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    fun getKernelVersion(): String {
        return try {
            System.getProperty("os.version") ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun getHardwareInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (${Build.HARDWARE})"
    }

    fun getUptime(): String {
        val uptimeMillis = android.os.SystemClock.elapsedRealtime()
        val seconds = (uptimeMillis / 1000) % 60
        val minutes = (uptimeMillis / (1000 * 60)) % 60
        val hours = (uptimeMillis / (1000 * 60 * 60)) % 24
        val days = (uptimeMillis / (1000 * 60 * 60 * 24))

        return if (days > 0) {
            String.format(Locale.getDefault(), "%dd %dh %dm", days, hours, minutes)
        } else if (hours > 0) {
            String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
        } else {
            String.format(Locale.getDefault(), "%dm %ds", minutes, seconds)
        }
    }

    fun getDetailedNetworkInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wifiInfo = wifiManager.connectionInfo
            info["Type"] = "Wi-Fi"
            info["Link Speed"] = "${wifiInfo.linkSpeed} Mbps"
            info["Signal"] = "${wifiInfo.rssi} dBm"
            info["Frequency"] = "${wifiInfo.frequency} MHz"
            
            // Standard detection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val standard = when (wifiInfo.wifiStandard) {
                    4 -> "Wi-Fi 4 (802.11n)"
                    5 -> "Wi-Fi 5 (802.11ac)"
                    6 -> "Wi-Fi 6 (802.11ax)"
                    7 -> "Wi-Fi 7 (802.11be)"
                    else -> "Unknown"
                }
                info["Standard"] = standard
            }

            // Frequency to Channel
            val channel = if (wifiInfo.frequency >= 2412 && wifiInfo.frequency <= 2484) {
                (wifiInfo.frequency - 2412) / 5 + 1
            } else if (wifiInfo.frequency >= 5170 && wifiInfo.frequency <= 5825) {
                (wifiInfo.frequency - 5170) / 5 + 34
            } else -1
            if (channel > 0) info["Channel"] = channel.toString()
        } else if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            info["Type"] = "Cellular"
            info["Bandwidth"] = "${capabilities.linkDownstreamBandwidthKbps / 1000} Mbps Down"
        }

        linkProperties?.let { lp ->
            val ipAddresses = lp.linkAddresses.filter { it.address is Inet4Address }
            if (ipAddresses.isNotEmpty()) {
                val addr = ipAddresses[0]
                info["IP Address"] = addr.address.hostAddress ?: ""
                info["Subnet Mask"] = prefixToMask(addr.prefixLength)
            }
            
            val gateways = lp.routes.filter { it.isDefaultRoute && it.gateway is Inet4Address }
            if (gateways.isNotEmpty()) {
                info["Gateway"] = gateways[0].gateway?.hostAddress ?: ""
            }

            val dnsServers = lp.dnsServers.filterIsInstance<Inet4Address>()
            dnsServers.forEachIndexed { index, inetAddress ->
                info["DNS ${index + 1}"] = inetAddress.hostAddress ?: ""
            }

            if (wifiManager.dhcpInfo != null) {
                info["DHCP Server"] = intToIp(wifiManager.dhcpInfo.serverAddress)
            }
        }

        return info
    }

    private fun prefixToMask(prefix: Int): String {
        var mask = 0xffffffff.toInt() shl (32 - prefix)
        return String.format(Locale.getDefault(), "%d.%d.%d.%d",
            (mask shr 24) and 0xff, (mask shr 16) and 0xff, (mask shr 8) and 0xff, mask and 0xff)
    }

    private fun intToIp(i: Int): String {
        return (i and 0xFF).toString() + "." +
                (i shr 8 and 0xFF) + "." +
                (i shr 16 and 0xFF) + "." +
                (i shr 24 and 0xFF)
    }

    fun getDetailedCameraInfo(): List<Map<String, String>> {
        val cameras = mutableListOf<Map<String, String>>()
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (id in manager.cameraIdList) {
                val char = manager.getCameraCharacteristics(id)
                val info = mutableMapOf<String, String>()
                info["ID"] = id
                
                val facing = char.get(CameraCharacteristics.LENS_FACING)
                info["Facing"] = when(facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }

                val sensorSize = char.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                if (sensorSize != null) {
                    info["Sensor Size"] = String.format(Locale.getDefault(), "%.2f x %.2f mm", sensorSize.width, sensorSize.height)
                }

                val pixelArray = char.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                if (pixelArray != null) {
                    val mp = (pixelArray.width * pixelArray.height) / 1000000f
                    info["Resolution"] = String.format(Locale.getDefault(), "%d x %d (%.1f MP)", pixelArray.width, pixelArray.height, mp)
                }

                val apertures = char.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                if (apertures != null && apertures.isNotEmpty()) {
                    info["Aperture"] = "f/${apertures[0]}"
                }

                val focalLengths = char.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                if (focalLengths != null && focalLengths.isNotEmpty()) {
                    info["Focal Length"] = "${focalLengths[0]} mm"
                }

                cameras.add(info)
            }
        } catch (_: Exception) {}
        return cameras
    }
}
