package com.guruswarupa.launch.apps

enum class BloatRisk { SAFE, CAUTION }

/**
 * A preinstalled app that phone makers commonly ship.
 * [alt] is the id of a Launch suite app that does the same job, if there is one.
 * CAUTION means other system features may depend on the app, so it is better to disable than uninstall.
 */
data class Bloat(val pkg: String, val name: String, val vendor: String, val risk: BloatRisk, val alt: String? = null)

object BloatCatalog {
    private fun s(pkg: String, name: String, vendor: String, alt: String? = null) = Bloat(pkg, name, vendor, BloatRisk.SAFE, alt)
    private fun c(pkg: String, name: String, vendor: String, alt: String? = null) = Bloat(pkg, name, vendor, BloatRisk.CAUTION, alt)

    val all: List<Bloat> = listOf(
        // ---- Samsung ----
        s("com.sec.android.app.popupcalculator", "Samsung Calculator", "Samsung", "calculator"),
        s("com.samsung.android.calendar", "Samsung Calendar", "Samsung", "calendar"),
        s("com.sec.android.app.clockpackage", "Samsung Clock", "Samsung", "clock"),
        c("com.sec.android.app.camera", "Samsung Camera", "Samsung", "camera"),
        c("com.sec.android.gallery3d", "Samsung Gallery", "Samsung", "gallery"),
        c("com.sec.android.app.myfiles", "My Files", "Samsung", "files"),
        s("com.samsung.android.video", "Samsung Video Player", "Samsung", "player"),
        s("com.sec.android.app.music", "Samsung Music", "Samsung", "player"),
        s("com.sec.android.app.voicenote", "Voice Recorder", "Samsung"),
        s("com.samsung.android.bixby.agent", "Bixby Voice", "Samsung"),
        s("com.samsung.android.bixby.wakeup", "Bixby Wake-up", "Samsung"),
        s("com.samsung.android.app.spage", "Samsung Free / Bixby Home", "Samsung"),
        s("com.samsung.android.visionintelligence", "Bixby Vision", "Samsung"),
        s("com.samsung.android.game.gamehome", "Game Launcher", "Samsung"),
        s("com.samsung.android.game.gametools", "Game Tools", "Samsung"),
        s("com.samsung.android.arzone", "AR Zone", "Samsung"),
        s("com.samsung.android.aremoji", "AR Emoji", "Samsung"),
        s("com.samsung.android.ardrawing", "AR Doodle", "Samsung"),
        s("com.samsung.android.app.tips", "Tips", "Samsung"),
        s("com.samsung.android.app.reminder", "Samsung Reminder", "Samsung"),
        s("com.samsung.android.email.provider", "Samsung Email", "Samsung"),
        s("com.samsung.android.stickercenter", "Sticker Center", "Samsung"),
        s("com.samsung.android.themestore", "Galaxy Themes", "Samsung"),
        s("com.samsung.android.kidsinstaller", "Kids Mode installer", "Samsung"),
        s("com.samsung.android.samsungpass", "Samsung Pass", "Samsung"),
        s("com.sec.android.app.shealth", "Samsung Health", "Samsung"),
        s("com.sec.android.app.sbrowser", "Samsung Internet", "Samsung"),
        c("com.samsung.android.messaging", "Samsung Messages", "Samsung", "messages"),
        c("com.android.mms", "Messaging", "Xiaomi / AOSP", "messages"),
        c("com.google.android.apps.messaging", "Google Messages", "Google", "messages"),

        // ---- Xiaomi / Redmi / POCO ----
        s("com.miui.calculator", "Mi Calculator", "Xiaomi", "calculator"),
        s("com.xiaomi.calendar", "Mi Calendar", "Xiaomi", "calendar"),
        s("com.android.deskclock", "Clock", "Xiaomi / AOSP", "clock"),
        c("com.android.camera", "Mi Camera", "Xiaomi", "camera"),
        c("com.miui.gallery", "Mi Gallery", "Xiaomi", "gallery"),
        c("com.mi.android.globalFileexplorer", "Mi File Manager", "Xiaomi", "files"),
        s("com.android.fileexplorer", "Mi File Explorer", "Xiaomi", "files"),
        s("com.miui.player", "Mi Music", "Xiaomi", "player"),
        s("com.miui.videoplayer", "Mi Video", "Xiaomi", "player"),
        s("com.xiaomi.mipicks", "GetApps", "Xiaomi"),
        s("com.miui.msa.global", "MSA (system ads)", "Xiaomi"),
        s("com.miui.analytics", "Xiaomi Analytics", "Xiaomi"),
        s("com.miui.bugreport", "Bug Report", "Xiaomi"),
        s("com.xiaomi.glgm", "Mi Games", "Xiaomi"),
        s("com.mi.globalbrowser", "Mi Browser", "Xiaomi"),
        s("com.miui.yellowpage", "Yellow Pages", "Xiaomi"),
        s("com.miui.hybrid", "Quick Apps", "Xiaomi"),
        s("com.miui.compass", "Compass", "Xiaomi"),
        s("com.miui.notes", "Mi Notes", "Xiaomi"),

        // ---- Oppo / Realme / OnePlus (ColorOS, OxygenOS) ----
        s("com.coloros.calculator", "Calculator", "Oppo / Realme", "calculator"),
        s("com.oneplus.calculator", "Calculator", "OnePlus", "calculator"),
        s("com.oplus.calculator", "Calculator", "Oppo / OnePlus", "calculator"),
        s("com.coloros.calendar", "Calendar", "Oppo / Realme", "calendar"),
        s("com.oplus.calendar", "Calendar", "Oppo / OnePlus", "calendar"),
        s("com.coloros.alarmclock", "Clock", "Oppo / Realme", "clock"),
        s("com.oneplus.deskclock", "Clock", "OnePlus", "clock"),
        c("com.oplus.camera", "Camera", "Oppo / OnePlus", "camera"),
        c("com.oneplus.camera", "Camera", "OnePlus", "camera"),
        c("com.coloros.gallery3d", "Photos", "Oppo / Realme", "gallery"),
        c("com.oneplus.gallery", "Gallery", "OnePlus", "gallery"),
        c("com.coloros.filemanager", "Files", "Oppo / Realme", "files"),
        c("com.oneplus.filemanager", "File Manager", "OnePlus", "files"),
        s("com.heytap.music", "Music", "Oppo / Realme", "player"),
        s("com.coloros.video", "Video", "Oppo / Realme", "player"),
        s("com.heytap.browser", "Browser", "Oppo / Realme"),
        s("com.heytap.cloud", "HeyTap Cloud", "Oppo / Realme"),
        c("com.heytap.market", "App Market", "Oppo / Realme"),
        s("com.coloros.weather.service", "Weather service", "Oppo / Realme"),

        // ---- Vivo / iQOO ----
        s("com.android.bbkcalculator", "Calculator", "Vivo", "calculator"),
        s("com.bbk.calendar", "Calendar", "Vivo", "calendar"),
        s("com.android.BBKClock", "Clock", "Vivo", "clock"),
        c("com.vivo.gallery", "Album", "Vivo", "gallery"),
        c("com.android.filemanager", "Files", "Vivo", "files"),
        s("com.android.VideoPlayer", "Video Player", "Vivo", "player"),
        s("com.vivo.browser", "Browser", "Vivo"),
        s("com.vivo.appstore", "V-AppStore", "Vivo"),
        s("com.vivo.game", "Game Center", "Vivo"),
        s("com.vivo.easyshare", "EasyShare", "Vivo"),
        s("com.vivo.space", "Vivo Space", "Vivo"),

        // ---- Huawei / Honor ----
        s("com.huawei.calculator", "Calculator", "Huawei / Honor", "calculator"),
        s("com.huawei.calendar", "Calendar", "Huawei / Honor", "calendar"),
        c("com.huawei.camera", "Camera", "Huawei / Honor", "camera"),
        c("com.huawei.photos", "Gallery", "Huawei / Honor", "gallery"),
        c("com.huawei.hidisk", "Files", "Huawei / Honor", "files"),
        s("com.huawei.music", "Music", "Huawei / Honor", "player"),
        s("com.huawei.himovie", "Huawei Video", "Huawei / Honor", "player"),
        s("com.huawei.browser", "Browser", "Huawei / Honor"),
        c("com.huawei.appmarket", "AppGallery", "Huawei / Honor"),

        // ---- Motorola ----
        c("com.motorola.camera3", "Moto Camera", "Motorola", "camera"),
        s("com.motorola.moto", "Moto app", "Motorola"),
        s("com.motorola.genie", "Moto Tips / Genie", "Motorola"),

        // ---- Google / stock Android ----
        s("com.google.android.calculator", "Google Calculator", "Google", "calculator"),
        s("com.android.calculator2", "Calculator", "AOSP", "calculator"),
        s("com.google.android.calendar", "Google Calendar", "Google", "calendar"),
        s("com.android.calendar", "Calendar", "AOSP", "calendar"),
        s("com.google.android.deskclock", "Google Clock", "Google", "clock"),
        c("com.android.camera2", "Camera", "AOSP", "camera"),
        c("com.google.android.GoogleCamera", "Google Camera", "Google", "camera"),
        s("com.android.gallery3d", "Gallery", "AOSP", "gallery"),
        s("com.google.android.apps.nbu.files", "Files by Google", "Google", "files"),

        // ---- Common preinstalled third party ----
        s("com.facebook.katana", "Facebook", "Preinstalled"),
        s("com.facebook.appmanager", "Facebook App Manager", "Preinstalled"),
        s("com.facebook.services", "Facebook Services", "Preinstalled"),
        s("com.facebook.system", "Facebook Installer", "Preinstalled"),
        s("com.netflix.partner.activation", "Netflix activation", "Preinstalled"),
        s("com.netflix.mediaclient", "Netflix", "Preinstalled"),
        s("com.amazon.appmanager", "Amazon App Manager", "Preinstalled"),
        s("com.amazon.mShop.android.shopping", "Amazon Shopping", "Preinstalled"),
        s("com.linkedin.android", "LinkedIn", "Preinstalled"),
        s("com.microsoft.office.officehubrow", "Microsoft 365", "Preinstalled"),
        s("com.microsoft.skydrive", "OneDrive", "Preinstalled")
    )
}
