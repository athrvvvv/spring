package com.example.spring

import android.content.ContentValues
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.spring/download"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                if (call.method == "downloadMP3") {
                    val url = call.argument<String>("url")
                    if (url.isNullOrEmpty()) {
                        result.error("INVALID_URL", "URL is null or empty", null)
                        return@setMethodCallHandler
                    }

                    Thread {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !Settings.System.canWrite(applicationContext)
                        ) {
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            runOnUiThread {
                                result.error("NO_PERMISSION", "Grant WRITE_SETTINGS permission then retry.", null)
                            }
                            return@Thread
                        }

                        val path = downloadMp3(url)
                        if (path != null) {
                            val ok = setAsRingtone(path)
                            runOnUiThread {
                                if (ok) result.success("Ringtone set successfully")
                                else result.error("RINGTONE_FAILED", "Downloaded but failed to set ringtone", null)
                            }
                        } else {
                            runOnUiThread { result.error("DOWNLOAD_FAILED", "MP3 download failed", null) }
                        }
                    }.start()
                } else {
                    result.notImplemented()
                }
            }
    }

    private fun extractYoutubeId(url: String): String? {
        val patterns = listOf(
            "v=([0-9A-Za-z_-]{11})".toRegex(),
            "youtu\\.be/([0-9A-Za-z_-]{11})".toRegex(),
            "youtube\\.com/shorts/([0-9A-Za-z_-]{11})".toRegex()
        )
        for (p in patterns) {
            val m = p.find(url)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun downloadMp3(youtubeUrl: String): String? {
        val id = extractYoutubeId(youtubeUrl) ?: return null
        val apiUrl = "https://youtube-mp36.p.rapidapi.com/dl?id=$id"
        val client = OkHttpClient()
        val headers = mapOf(
            "x-rapidapi-key" to "d920e99b2emsh0af7fdf3d091a27p110dd5jsnf01d3bd9c2e9",
            "x-rapidapi-host" to "youtube-mp36.p.rapidapi.com"
        )

        var link: String? = null
        var title: String? = null
        repeat(10) {
            val rb = Request.Builder().url(apiUrl)
            headers.forEach { (k, v) -> rb.addHeader(k, v) }
            client.newCall(rb.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val j = JSONObject(resp.body!!.string())
                    if (j.optString("status") == "ok") {
                        link = j.optString("link")
                        title = j.optString("title")
                        return@repeat
                    }
                }
            }
            Thread.sleep(3000)
        }

        if (link == null || title == null) return null
        val bytes = client.newCall(Request.Builder().url(link!!).build()).execute().body!!.bytes()
        val name = title!!.replace("[\\\\/:*?\"<>|]".toRegex(), "_") + ".mp3"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            uri.toString()
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            File(dir, name).apply { writeBytes(bytes) }.absolutePath
        }
    }

    private fun setAsRingtone(pathOrUri: String): Boolean {
        return try {
            val resolver = applicationContext.contentResolver
            val ringtoneUri: Uri = if (pathOrUri.startsWith("content://")) {
                Uri.parse(pathOrUri)
            } else {
                val file = File(pathOrUri)
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, file.absolutePath)
                    put(MediaStore.MediaColumns.TITLE, file.nameWithoutExtension)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.IS_RINGTONE, true)
                }
                val uri = MediaStore.Audio.Media.getContentUriForPath(file.absolutePath)
                resolver.insert(uri!!, cv)!!
            }

            RingtoneManager.setActualDefaultRingtoneUri(applicationContext, RingtoneManager.TYPE_RINGTONE, ringtoneUri)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
