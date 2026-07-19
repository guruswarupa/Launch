package com.guruswarupa.launch.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import android.webkit.MimeTypeMap
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptedFolderManager(private val context: Context) {

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val ITERATIONS = 310000
        private const val KEY_LENGTH = 256
        private const val VAULT_DIR = "encrypted_vault"
        private const val THUMBS_DIR = "vault_thumbs"
        private const val TEMP_DIR = "vault_temp"
        private const val CONFIG_FILE = ".vault_config"


        private var masterKey: SecretKey? = null
    }

    private val encryptedFolder = File(context.filesDir, VAULT_DIR)
    private val thumbnailFolder = File(context.filesDir, THUMBS_DIR)
    private val configFile = File(encryptedFolder, CONFIG_FILE)

    init {
        if (!encryptedFolder.exists()) encryptedFolder.mkdirs()
        if (!thumbnailFolder.exists()) thumbnailFolder.mkdirs()
    }

    fun isVaultSetup(): Boolean = configFile.exists()

    fun getEncryptedFolder(): File = encryptedFolder
    fun getThumbnailFolder(): File = thumbnailFolder

    fun setupVault(password: String): Boolean {
        try {
            val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
            val key = deriveKey(password, salt)


            val verificationData = "VAULT_OPEN".toByteArray()
            val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encryptedVerification = cipher.doFinal(verificationData)

            FileOutputStream(configFile).use { fos ->
                fos.write(salt)
                fos.write(iv)
                fos.write(encryptedVerification)
            }

            masterKey = key
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun unlock(password: String): Boolean {
        if (!configFile.exists()) return false

        try {
            FileInputStream(configFile).use { fis ->
                val salt = ByteArray(SALT_SIZE)
                val iv = ByteArray(IV_SIZE)
                if (!readFully(fis, salt) || !readFully(fis, iv)) {
                    return false
                }
                val encryptedVerification = fis.readBytes()

                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

                val decrypted = cipher.doFinal(encryptedVerification)
                if (String(decrypted) == "VAULT_OPEN") {
                    masterKey = key
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun lock() {
        masterKey = null
    }

    fun isUnlocked(): Boolean = masterKey != null

    private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val intermediate = factory.generateSecret(spec).encoded
        return SecretKeySpec(intermediate, "AES")
    }

    private fun resolveVaultFile(fileName: String): File {
        val sanitized = fileName.replace("/", "_").replace("\\", "_").replace("..", "_").trim()
        if (sanitized.isBlank() || sanitized.equals(".", ignoreCase = true)) {
            throw IllegalArgumentException("Invalid file name: $fileName")
        }
        val resolved = File(encryptedFolder, sanitized).canonicalFile
        val base = encryptedFolder.canonicalFile
        if (resolved != base && !resolved.path.startsWith("${base.path}${File.separator}")) {
            throw SecurityException("Path traversal detected: $fileName")
        }
        return resolved
    }

    fun encryptFile(sourceUri: Uri, fileName: String) {
        val key = masterKey ?: throw IllegalStateException("Vault is locked")
        val destinationFile = resolveVaultFile(fileName)


        generateThumbnail(sourceUri, fileName)

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))

                FileOutputStream(destinationFile).use { fos ->
                    fos.write(iv)
                    javax.crypto.CipherOutputStream(fos, cipher).use { cos ->
                        inputStream.copyTo(cos)
                    }
                }
            }
        } catch (e: Exception) {
            if (destinationFile.exists()) destinationFile.delete()
            throw e
        }
    }

    fun getDecryptedInputStream(fileName: String): InputStream {
        val key = masterKey ?: throw IllegalStateException("Vault is locked")
        val sourceFile = resolveVaultFile(fileName)

        val fis = FileInputStream(sourceFile)
        try {
            val iv = ByteArray(IV_SIZE)
            if (fis.read(iv) != IV_SIZE) {
                throw IOException("Invalid file format")
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

            return javax.crypto.CipherInputStream(fis, cipher)
        } catch (e: Exception) {
            fis.close()
            throw e
        }
    }

    fun decryptToOutputStream(fileName: String, outputStream: OutputStream) {
        val key = masterKey ?: throw IllegalStateException("Vault is locked")
        val sourceFile = resolveVaultFile(fileName)

        FileInputStream(sourceFile).use { inputStream ->
            val iv = ByteArray(IV_SIZE)
            if (inputStream.read(iv) != IV_SIZE) {
                throw IOException("Invalid file format")
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

            val bufferSize = DEFAULT_BUFFER_SIZE * 8
            var previousBuffer = ByteArray(bufferSize)
            var previousCount = inputStream.read(previousBuffer)

            if (previousCount == -1) {
                val finalBytes = cipher.doFinal()
                if (finalBytes.isNotEmpty()) {
                    outputStream.write(finalBytes)
                }
                outputStream.flush()
                return
            }

            while (true) {
                val currentBuffer = ByteArray(bufferSize)
                val currentCount = inputStream.read(currentBuffer)
                if (currentCount == -1) {
                    val finalBytes = cipher.doFinal(previousBuffer, 0, previousCount)
                    if (finalBytes.isNotEmpty()) {
                        outputStream.write(finalBytes)
                    }
                    break
                }

                val updatedBytes = cipher.update(previousBuffer, 0, previousCount)
                if (updatedBytes != null && updatedBytes.isNotEmpty()) {
                    outputStream.write(updatedBytes)
                }
                previousBuffer = currentBuffer
                previousCount = currentCount
            }
            outputStream.flush()
        }
    }

    fun deleteFile(fileName: String): Boolean {
        File(thumbnailFolder, "$fileName.thumb").delete()
        return File(encryptedFolder, fileName).delete()
    }

    fun renameFile(oldName: String, newName: String): Boolean {
        val oldEncrypted = File(encryptedFolder, oldName)
        if (!oldEncrypted.exists()) return false
        val newEncrypted = File(encryptedFolder, newName)
        if (oldName == newName) return true
        if (newEncrypted.exists()) {
            newEncrypted.delete()
            File(thumbnailFolder, "$newName.thumb").delete()
        }
        val renamed = oldEncrypted.renameTo(newEncrypted)
        if (renamed) {
            val oldThumb = File(thumbnailFolder, "$oldName.thumb")
            if (oldThumb.exists()) {
                oldThumb.renameTo(File(thumbnailFolder, "$newName.thumb"))
            }
        }
        return renamed
    }

    fun getEncryptedFiles(): List<File> {
        return encryptedFolder.listFiles()?.filter {
            !it.name.startsWith(".") && it.name != CONFIG_FILE
        } ?: emptyList()
    }

    fun decryptToCache(fileName: String): File {
        val sourceFile = File(encryptedFolder, fileName)
        val cacheDir = File(context.cacheDir, TEMP_DIR)
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val tempFile = File(cacheDir, fileName)
        if (tempFile.exists() && tempFile.length() > 0L && tempFile.lastModified() >= sourceFile.lastModified()) {
            return tempFile
        }
        if (tempFile.exists()) tempFile.delete()

        try {
            tempFile.outputStream().use { outputStream ->
                decryptToOutputStream(fileName, outputStream)
            }
            if (tempFile.length() <= 0L) {
                tempFile.delete()
                throw IOException("Decrypted file is empty")
            }
            tempFile.setLastModified(sourceFile.lastModified())
            return tempFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    fun clearCache() {
        val cacheDir = File(context.cacheDir, TEMP_DIR)
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }


    private fun generateThumbnail(uri: Uri, fileName: String) {
        val key = masterKey ?: return
        var mimeType = context.contentResolver.getType(uri)

        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }

        var bitmap: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (mimeType?.startsWith("image/") == true || mimeType?.startsWith("video/") == true) {
                    try {
                        bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                    } catch (e: Exception) {}
                }
            }

            if (bitmap == null && (mimeType?.startsWith("image/") == true || mimeType == null)) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        context.contentResolver.openInputStream(uri)?.use { input2 ->
                            options.inJustDecodeBounds = false
                            options.inSampleSize = calculateInSampleSize(options, 512, 512)
                            bitmap = BitmapFactory.decodeStream(input2, null, options)
                        }
                    }
                }
            }

            if (bitmap == null) bitmap = getDefaultThumbnailForFileType(mimeType)

            bitmap?.let {
                saveThumbnail(it, fileName, key)
                it.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun thumbFileFor(fileName: String): File {
        val sanitized = fileName.replace("/", "_").replace("\\", "_").replace("..", "_").trim()
        if (sanitized.isBlank() || sanitized.equals(".", ignoreCase = true)) {
            throw IllegalArgumentException("Invalid file name: $fileName")
        }
        return File(thumbnailFolder, "$sanitized.thumb")
    }

    private fun saveThumbnail(bitmap: Bitmap, fileName: String, key: SecretKey) {
        val thumbFile = thumbFileFor(fileName)
        val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))

        val stream = ByteArrayOutputStream()
        val width = bitmap.width
        val height = bitmap.height
        val size = if (width > height) height else width
        val x = (width - size) / 2
        val y = (height - size) / 2

        val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
        cropped.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteArray = stream.toByteArray()

        FileOutputStream(thumbFile).use { fos ->
            fos.write(iv)
            javax.crypto.CipherOutputStream(fos, cipher).use { cos ->
                cos.write(byteArray)
            }
        }
        if (cropped != bitmap) cropped.recycle()
    }

    fun getThumbnail(fileName: String): Bitmap? {
        val key = masterKey ?: return null
        val thumbFile = thumbFileFor(fileName)
        if (!thumbFile.exists()) return null

        return try {
            val fis = FileInputStream(thumbFile)
            val iv = ByteArray(IV_SIZE)
            fis.read(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

            val cis = javax.crypto.CipherInputStream(fis, cipher)
            BitmapFactory.decodeStream(cis)
        } catch (e: Exception) {
            null
        }
    }

    private fun getDefaultThumbnailForFileType(mimeType: String?): Bitmap? {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val backgroundColor = when {
            mimeType?.startsWith("audio/") == true -> android.graphics.Color.parseColor("#FF6200EE")
            mimeType?.contains("pdf") == true -> android.graphics.Color.parseColor("#FFF44336")
            mimeType?.startsWith("text/") == true -> android.graphics.Color.parseColor("#FF2196F3")
            else -> android.graphics.Color.parseColor("#FF9E9E9E")
        }
        canvas.drawColor(backgroundColor)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 100f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val iconText = when {
            mimeType?.startsWith("audio/") == true -> "🎵"
            mimeType?.contains("pdf") == true -> "📄"
            mimeType?.startsWith("text/") == true -> "📝"
            else -> "📁"
        }
        val y = canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(iconText, canvas.width / 2f, y, paint)
        return bitmap
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }


    fun exportVault(outputStream: OutputStream): Boolean {
        return try {
            java.util.zip.ZipOutputStream(outputStream).use { zipOut ->
                encryptedFolder.listFiles()?.forEach { file ->
                    val entry = java.util.zip.ZipEntry("data/${file.name}")
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }

                thumbnailFolder.listFiles()?.forEach { file ->
                    val entry = java.util.zip.ZipEntry("thumbs/${file.name}")
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importVault(inputStream: InputStream): Boolean {
        val canonicalDataDir = encryptedFolder.canonicalPath
        val canonicalThumbsDir = thumbnailFolder.canonicalPath
        return try {
            java.util.zip.ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName.contains("..") || File(entryName).isAbsolute) {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }

                    val destFile = if (entryName.startsWith("data/")) {
                        File(encryptedFolder, entryName.substring(5))
                    } else if (entryName.startsWith("thumbs/")) {
                        File(thumbnailFolder, entryName.substring(7))
                    } else {
                        null
                    }

                    if (destFile != null) {
                        val canonicalDest = destFile.canonicalPath
                        val isInsideData = canonicalDest.startsWith("$canonicalDataDir${File.separator}") ||
                                canonicalDest == canonicalDataDir
                        val isInsideThumbs = canonicalDest.startsWith("$canonicalThumbsDir${File.separator}") ||
                                canonicalDest == canonicalThumbsDir
                        if (isInsideData || isInsideThumbs) {
                            destFile.parentFile?.mkdirs()
                            FileOutputStream(destFile).use { fos ->
                                zipIn.copyTo(fos)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
