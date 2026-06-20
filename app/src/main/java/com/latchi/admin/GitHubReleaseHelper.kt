package com.latchi.admin

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object GitHubReleaseHelper {

    data class ReleaseResult(
        val releaseId: Long,
        val uploadUrl: String,
        val htmlUrl: String,
        val tag: String,
        val browserDownloadUrl: String = ""
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun createOrGetRelease(
        token: String,
        owner: String,
        repo: String,
        tag: String,
        releaseName: String,
        notes: String
    ): ReleaseResult {
        val existing = findReleaseByTag(token, owner, repo, tag)
        if (existing != null) return existing

        val payload = JSONObject().apply {
            put("tag_name", tag)
            put("name", releaseName)
            put("body", notes)
            put("draft", false)
            put("prerelease", false)
            put("generate_release_notes", false)
        }.toString()

        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val json = client.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("GitHub create release failed: HTTP ${res.code} $body")
            JSONObject(body)
        }
        return toReleaseResult(json)
    }

    fun uploadAsset(
        token: String,
        release: ReleaseResult,
        assetName: String,
        file: File
    ): String {
        val uploadBase = release.uploadUrl.substringBefore("{")
        val url = "$uploadBase?name=${URLEncoder.encode(assetName, "UTF-8")}&label=${URLEncoder.encode(assetName, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/vnd.android.package-archive")
            .post(file.asRequestBody("application/vnd.android.package-archive".toMediaType()))
            .build()

        val json = client.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("GitHub upload asset failed: HTTP ${res.code} $body")
            JSONObject(body)
        }
        return json.optString("browser_download_url")
    }

    private fun findReleaseByTag(token: String, owner: String, repo: String, tag: String): ReleaseResult? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/tags/${URLEncoder.encode(tag, "UTF-8")}")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        return client.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.code == 404) return@use null
            if (!res.isSuccessful) throw IllegalStateException("GitHub get release failed: HTTP ${res.code} $body")
            toReleaseResult(JSONObject(body))
        }
    }

    private fun toReleaseResult(json: JSONObject): ReleaseResult {
        return ReleaseResult(
            releaseId = json.optLong("id"),
            uploadUrl = json.optString("upload_url"),
            htmlUrl = json.optString("html_url"),
            tag = json.optString("tag_name")
        )
    }
}
