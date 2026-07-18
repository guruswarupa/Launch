package com.guruswarupa.launch.core

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors




data class ShareableApp(
    val name: String,
    val packageName: String
)





class ShareManager(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "ShareManager"
        const val FILE_PICKER_REQUEST_CODE = 1001

        private const val DIALOG_OPTION_SHARE_APK = 0
        private const val DIALOG_OPTION_SHARE_FILE = 1
    }




    fun showApkSharingDialog() {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.share_dialog_title))
            .setItems(arrayOf(
                context.getString(R.string.share_app_apk),
                context.getString(R.string.share_file)
            )) { _, which ->
                when (which) {
                    DIALOG_OPTION_SHARE_APK -> showAppSharingDialog()
                    DIALOG_OPTION_SHARE_FILE -> showFileSharingDialog()
                    else -> {  }
                }
            }
            .setNegativeButton(context.getString(android.R.string.cancel), null)
            .show()
    }




    private fun showAppSharingDialog() {
        val installedApps = getInstalledApps()
        if (installedApps.isEmpty()) {
            showToast(context.getString(R.string.no_apps_available_to_share))
            return
        }

        val appNames = installedApps.map { it.name }.toTypedArray()

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(context.getString(R.string.select_app_to_share))
            .setItems(appNames) { _, which ->
                if (which in appNames.indices) {
                    val selectedApp = installedApps[which]
                    shareApk(selectedApp.packageName, selectedApp.name)
                }
            }
            .setNegativeButton(context.getString(android.R.string.cancel), null)
            .show()
    }




    @Suppress("DEPRECATION")
    private fun showFileSharingDialog() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = Constants.MIME_TYPE_ALL
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        try {
            val activity = context as? Activity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                activity.startActivityForResult(intent, FILE_PICKER_REQUEST_CODE)
            } else {
                showToast(context.getString(R.string.activity_not_available))
            }
        } catch (_: ActivityNotFoundException) {
            showToast(context.getString(R.string.no_file_manager_available))
        } catch (_: Exception) {
            showToast(context.getString(R.string.no_file_manager_available))
        }
    }

    fun showFileSharingDialogWithLauncher(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = Constants.MIME_TYPE_ALL
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            showToast(context.getString(R.string.no_file_manager_available))
        } catch (_: Exception) {
            showToast(context.getString(R.string.no_file_manager_available))
        }
    }




    fun handleFilePickerResult(uri: Uri?) {
        if (uri != null) {
            shareFile(uri)
        } else {
            showToast(context.getString(R.string.no_file_selected))
        }
    }




    private fun shareFile(uri: Uri) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: Constants.MIME_TYPE_ALL
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.sharing_file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_file))
            context.startActivity(chooser)
        } catch (e: Exception) {
            showToast(context.getString(R.string.error_sharing_file, e.message ?: ""))
        }
    }




    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }





    private fun getInstalledApps(): List<ShareableApp> {
        val packageManager = context.packageManager
        return try {
            @Suppress("DEPRECATION")
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            installedApps
                .filter { app ->

                    (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                            (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                }
                .mapNotNull { app ->
                    try {
                        val appName = packageManager.getApplicationLabel(app).toString()
                        ShareableApp(appName, app.packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(TAG, "Package not found: ${app.packageName}", e)
                        null
                    } catch (e: Exception) {
                        Log.w(TAG, "Error getting app label for ${app.packageName}", e)
                        null
                    }
                }
                .sortedBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }






    fun shareApk(packageName: String, appName: String) {
        showToast(context.getString(R.string.preparing_apk_for_sharing))

        executor.execute {
            try {
                val apkUri = prepareApkForSharing(packageName, appName)
                apkUri?.let { uri ->
                    handler.post {
                        launchShareIntent(uri, appName)
                    }
                }
            } catch (_: PackageManager.NameNotFoundException) {
                showToast(context.getString(R.string.apk_file_not_found))
            } catch (_: IOException) {
                showToast(context.getString(R.string.error_copying_apk))
            } catch (e: Exception) {
                showToast(context.getString(R.string.error_sharing_apk, e.message ?: ""))
            }
        }
    }





    private fun prepareApkForSharing(packageName: String, appName: String): Uri? {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        val sourceApk = File(applicationInfo.sourceDir)

        if (!sourceApk.exists()) {
            showToast(context.getString(R.string.apk_file_not_found))
            return null
        }


        val cacheDir = File(context.cacheDir, Constants.SHARED_APKS_DIR)
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            showToast(context.getString(R.string.error_creating_cache_directory))
            return null
        }


        val sanitizedAppName = appName.replace(
            Constants.APP_NAME_SANITIZE_REGEX.toRegex(),
            Constants.APP_NAME_SANITIZE_REPLACEMENT
        )
        val copiedApk = File(cacheDir, "$sanitizedAppName${Constants.APK_EXTENSION}")


        FileInputStream(sourceApk).use { input ->
            FileOutputStream(copiedApk).use { output ->
                input.copyTo(output)
            }
        }


        return FileProvider.getUriForFile(
            context,
            "${context.packageName}${Constants.FILE_PROVIDER_AUTHORITY_SUFFIX}",
            copiedApk
        )
    }




    private fun launchShareIntent(apkUri: Uri, appName: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = Constants.MIME_TYPE_APK
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.sharing_apk, appName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserTitle = context.getString(R.string.share_apk_title, appName)
            val chooser = Intent.createChooser(shareIntent, chooserTitle)
            context.startActivity(chooser)
        } catch (e: Exception) {
            showToast(context.getString(R.string.error_sharing_apk, e.message ?: ""))
        }
    }




    fun cleanup() {
        executor.shutdown()
        handler.removeCallbacksAndMessages(null)
    }
}
