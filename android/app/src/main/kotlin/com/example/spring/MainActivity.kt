package com.example.spring

import android.content.ContentValues
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class MainActivity : FlutterActivity() {
    private val DOWNLOAD_CHANNEL = "com.example.spring/download"
    private val SHARE_CHANNEL = "com.example.spring/share"

    private var sharedText: String? = null
    private val client = OkHttpClient()

    // Gist configuration - Replace with your own values!
    private val GIST_ID = "7aeea27e2f06bbcfa5b0937f4929383a"
    private val FILE_NAME = "counter.txt"
    private val GITHUB_TOKEN = "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" // Keep secret!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Handle shared text from other apps
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SHARE_CHANNEL)
            .setMethodCallHandler { call, result ->
                if (call.method == "getSharedText") {
                    result.success(sharedText)
                    sharedText = null
                } else {
                    result.notImplemented()
                }
            }

        // Handle YouTube MP3 download requests
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, DOWNLOAD_CHANNEL)
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

                        val (path, title) = downloadMp3(url)
                        if (path != null && title != null) {
                            val ok = setAsRingtoneAndNotifyCount(path, title)
                            runOnUiThread {
                                if (ok) result.success(path)
                                else result.error("RINGTONE_FAILED", "Downloaded but failed to set ringtone", null)
                            }
                        } else {
                            runOnUiThread {
                                result.error("DOWNLOAD_FAILED", "MP3 download failed", null)
                            }
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

    private fun downloadMp3(youtubeUrl: String): Pair<String?, String?> {
        val id = extractYoutubeId(youtubeUrl) ?: return Pair(null, null)
        val apiUrl = "https://youtube-mp36.p.rapidapi.com/dl?id=$id"
        val headers = mapOf(
            "x-rapidapi-key" to "fd335aa6c6mshe28b8b17bddd070p1bb2a8jsn2b4aa0d1f693",
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

        if (link == null || title == null) return Pair(null, null)
        val bytes = client.newCall(Request.Builder().url(link!!).build()).execute().body!!.bytes()
        val name = title!!.replace("[\\\\/:*?\"<>|]".toRegex(), "_") + ".mp3"

        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return Pair(null, null)
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            uri.toString()
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            File(dir, name).apply { writeBytes(bytes) }.absolutePath
        }

        return Pair(path, title)
    }

    private fun setAsRingtoneAndNotifyCount(pathOrUri: String, title: String): Boolean {
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

            // Set ringtone on device
            RingtoneManager.setActualDefaultRingtoneUri(applicationContext, RingtoneManager.TYPE_RINGTONE, ringtoneUri)

            // Update count using GitHub Gist
            val updatedCount = incrementRingtoneCountFromGist()

            // Get device info
            val deviceModel = Build.MODEL ?: "Unknown Model"
            val androidVersion = Build.VERSION.RELEASE ?: "Unknown Version"

            // Compose Telegram message
            val message = "🔔 Ringtone has been set $updatedCount times.\n" +
                    "🎵 Song: $title\n" +
                    "📱 Device: $deviceModel (Android $androidVersion)"

            // Send Telegram message asynchronously
            sendTelegramMessage(message)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun incrementRingtoneCountFromGist(): Int {
        return try {
            val gistUrl = "https://api.github.com/gists/$GIST_ID"

            // GET gist
            val getRequest = Request.Builder()
                .url(gistUrl)
                .header("Authorization", "token $GITHUB_TOKEN")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val getResponse = client.newCall(getRequest).execute()
            val body = getResponse.body?.string()
            getResponse.close()

            if (body.isNullOrEmpty()) return -1

            val json = JSONObject(body)
            val files = json.getJSONObject("files")
            val fileObj = files.getJSONObject(FILE_NAME)
            val currentContent = fileObj.getString("content").trim()

            val currentCount = currentContent.toIntOrNull() ?: 0
            val newCount = currentCount + 1

            // PATCH update gist with new count
            val jsonBody = JSONObject().apply {
                put("files", JSONObject().apply {
                    put(FILE_NAME, JSONObject().apply {
                        put("content", newCount.toString())
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val patchRequest = Request.Builder()
                .url(gistUrl)
                .header("Authorization", "token $GITHUB_TOKEN")
                .header("Accept", "application/vnd.github.v3+json")
                .patch(requestBody)
                .build()

            val patchResponse = client.newCall(patchRequest).execute()
            val success = patchResponse.isSuccessful
            patchResponse.close()

            if (success) newCount else -1
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun sendTelegramMessage(text: String) {
        val botToken = "8134005386:AAESG7GSYcibFo7E8lxubjwxjmpoyhBLlBw"
        val chatId = "6264741586"
        val url = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=${Uri.encode(text)}"
        Thread {
            try {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
