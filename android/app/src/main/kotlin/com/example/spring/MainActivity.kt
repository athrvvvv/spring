package com.example.spring

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.spring/download"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "downloadMP3") {
                val url = call.argument<String>("url")
                if (url.isNullOrEmpty()) {
                    result.error("INVALID_URL", "URL is null or empty", null)
                    return@setMethodCallHandler
                }

                Thread {
                    val savedPath = downloadMp3(url)
                    runOnUiThread {
                        if (savedPath != null) result.success(savedPath)
                        else result.error("DOWNLOAD_FAILED", "Failed to download MP3", null)
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
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun downloadMp3(youtubeUrl: String): String? {
        val videoId = extractYoutubeId(youtubeUrl) ?: return null
        val apiUrl = "https://youtube-mp36.p.rapidapi.com/dl?id=$videoId"

        val client = OkHttpClient()
        val headers = mapOf(
            "x-rapidapi-key" to "d920e99b2emsh0af7fdf3d091a27p110dd5jsnf01d3bd9c2e9",
            "x-rapidapi-host" to "youtube-mp36.p.rapidapi.com"
        )

        var downloadUrl: String? = null
        var title: String? = null

        // Polling manually without break in lambda
        var attempt = 0
        while (attempt < 10) {
            val requestBuilder = Request.Builder().url(apiUrl)
            for ((k, v) in headers) {
                requestBuilder.addHeader(k, v)
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val status = json.optString("status")
                if (status == "ok") {
                    downloadUrl = json.optString("link")
                    title = json.optString("title")
                    break
                }
            }
            attempt++
            Thread.sleep(3000)
        }

        if (downloadUrl == null || title == null) return null

        // Download MP3
        val mp3Request = Request.Builder().url(downloadUrl!!).build()
        val mp3Response = client.newCall(mp3Request).execute()
        if (!mp3Response.isSuccessful) return null

        val mp3Bytes = mp3Response.body?.bytes() ?: return null
        val filename = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_") + ".mp3"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(mp3Bytes)
                    outputStream.flush()
                    return uri.toString()
                }
            }
            null
        } else {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (!musicDir.exists()) musicDir.mkdirs()
            val file = java.io.File(musicDir, filename)
            file.outputStream().use {
                it.write(mp3Bytes)
                it.flush()
            }
            file.absolutePath
        }
    }
}
