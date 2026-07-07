package com.stylefit.tryon.network

import android.content.Context
import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Talks to a free, publicly hosted Hugging Face "Space" running an AI virtual
 * try-on model (Gradio app), using Gradio's HTTP API:
 *   1. POST /upload            -> uploads a file, returns a server-side path
 *   2. POST /call/{api_name}   -> starts a prediction job, returns an event_id
 *   3. GET  /call/{api_name}/{event_id} -> Server-Sent-Events stream; the
 *      "complete" event contains the final JSON result.
 *
 * NOTE: Because this points at a free, community-hosted demo (not a paid,
 * guaranteed-uptime API), the exact parameter names/order can occasionally
 * change if the Space owner updates their app. If try-on calls start failing,
 * open the Space's "Use via API" page in a browser to see the current
 * parameter list and update [buildRequestData] below to match.
 */
class GradioClient(private val baseUrl: String, private val apiName: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Uploads a local file to the Space and returns the server-side path Gradio assigned it. */
    fun uploadFile(context: Context, uri: Uri): String {
        val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not read selected image")

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "files", tempFile.name,
                tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$baseUrl/upload")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Upload failed: HTTP ${response.code}")
            }
            val text = response.body?.string() ?: "[]"
            // Response is a JSON array of server paths, e.g. ["/tmp/gradio/abc/photo.jpg"]
            val arr = JSONArray(text)
            if (arr.length() == 0) throw IllegalStateException("Upload returned no file path")
            return arr.getString(0)
        }
    }

    /** Builds the "data" payload sent to the try-on endpoint. Adjust here if the Space's API changes. */
    private fun buildRequestData(personPath: String, garmentPath: String): JSONArray {
        fun fileData(path: String): JSONObject = JSONObject().apply {
            put("path", path)
            put("meta", JSONObject().put("_type", "gradio.FileData"))
        }

        val personEditor = JSONObject().apply {
            put("background", fileData(personPath))
            put("layers", JSONArray())
            put("composite", JSONObject.NULL)
        }

        return JSONArray().apply {
            put(personEditor)          // person photo (image editor component)
            put(fileData(garmentPath)) // garment photo
            put("")                    // garment text description (optional)
            put(true)                  // auto-mask
            put(false)                 // auto-crop
            put(30)                    // denoising steps
            put(42)                    // seed
        }
    }

    /**
     * Runs the try-on job and returns a direct URL to the resulting image.
     * Blocking call — run from a background thread/coroutine.
     */
    fun runTryOn(personPath: String, garmentPath: String): String {
        val payload = JSONObject().put("data", buildRequestData(personPath, garmentPath))

        val postRequest = Request.Builder()
            .url("$baseUrl/call/$apiName")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), payload.toString()))
            .build()

        val eventId: String
        client.newCall(postRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Try-on request failed: HTTP ${response.code}. The free AI model may be busy or offline — try again in a minute, or switch models in Settings.")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            eventId = json.optString("event_id", "")
            if (eventId.isEmpty()) throw IllegalStateException("No event id returned from model")
        }

        // Poll the SSE stream for the result
        val getRequest = Request.Builder()
            .url("$baseUrl/call/$apiName/$eventId")
            .header("Accept", "text/event-stream")
            .build()

        client.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Could not read result stream: HTTP ${response.code}")
            }
            val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
            var currentEvent = ""
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                when {
                    l.startsWith("event:") -> currentEvent = l.removePrefix("event:").trim()
                    l.startsWith("data:") -> {
                        val data = l.removePrefix("data:").trim()
                        if (currentEvent == "complete") {
                            return extractImageUrl(data)
                        } else if (currentEvent == "error") {
                            throw IllegalStateException("Model error: $data")
                        }
                    }
                }
            }
        }
        throw IllegalStateException("Model did not return a result in time. It may be overloaded — please try again.")
    }

    private fun extractImageUrl(data: String): String {
        val arr = JSONArray(data)
        val first = arr.get(0)
        val obj = if (first is JSONObject) first else JSONObject(first.toString())
        val url = obj.optString("url", "")
        if (url.isNotEmpty()) return url
        val path = obj.optString("path", "")
        if (path.isNotEmpty()) return "$baseUrl/file=$path"
        throw IllegalStateException("Unexpected response format from model")
    }
}

/** Known free virtual try-on Spaces the app can switch between from Settings. */
object TryOnPresets {
    data class Preset(val label: String, val baseUrl: String, val apiName: String)

    val presets = listOf(
        Preset("IDM-VTON (yisol)", "https://yisol-idm-vton.hf.space", "tryon"),
        Preset("Kolors Virtual Try-On", "https://kwai-kolors-kolors-virtual-try-on.hf.space", "tryon")
    )
}
