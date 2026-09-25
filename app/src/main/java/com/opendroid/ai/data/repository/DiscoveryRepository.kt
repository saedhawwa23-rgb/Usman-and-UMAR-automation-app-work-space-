package com.opendroid.ai.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opendroid.ai.data.models.GitHubProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class DiscoveryException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Singleton
class DiscoveryRepository @Inject constructor(
    private val client: OkHttpClient
) {
    private val gson = Gson()

    suspend fun searchGitHub(query: String): List<GitHubProject> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val url = "https://api.github.com/search/repositories".toHttpUrl().newBuilder()
            .addQueryParameter("q", trimmed)
            .addQueryParameter("sort", "stars")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", "20")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DiscoveryException("GitHub search failed (${response.code})")
            val root = gson.fromJson(response.body.string(), JsonObject::class.java)
            root.getAsJsonArray("items")?.mapNotNull { element ->
                runCatching {
                    val item = element.asJsonObject
                    GitHubProject(
                        id = item.get("id")?.asLong ?: return@runCatching null,
                        fullName = item.get("full_name")?.asString.orEmpty(),
                        description = item.get("description")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                        htmlUrl = item.get("html_url")?.asString.orEmpty(),
                        cloneUrl = item.get("clone_url")?.asString.orEmpty(),
                        language = item.get("language")?.takeUnless { it.isJsonNull }?.asString,
                        stars = item.get("stargazers_count")?.asInt ?: 0,
                        topics = item.getAsJsonArray("topics")?.map { it.asString }.orEmpty()
                    )
                }.getOrNull()
            }.orEmpty()
        }
    }

    suspend fun loadGitHubRepository(urlOrPath: String): GitHubProject = withContext(Dispatchers.IO) {
        val normalized = urlOrPath.trim().removeSuffix("/")
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
        val path = normalized.substringBefore("/") + "/" + normalized.substringAfter("/", "")
        require(path.count { it == '/' } == 1) { "Enter a GitHub repository URL or owner/name." }
        val url = "https://api.github.com/repos/$path".toHttpUrl()
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DiscoveryException("GitHub repository lookup failed (${response.code})")
            val item = gson.fromJson(response.body.string(), JsonObject::class.java)
            GitHubProject(
                id = item.get("id")?.asLong ?: 0L,
                fullName = item.get("full_name")?.asString.orEmpty(),
                description = item.get("description")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                htmlUrl = item.get("html_url")?.asString.orEmpty(),
                cloneUrl = item.get("clone_url")?.asString.orEmpty(),
                language = item.get("language")?.takeUnless { it.isJsonNull }?.asString,
                stars = item.get("stargazers_count")?.asInt ?: 0,
                topics = item.getAsJsonArray("topics")?.map { it.asString }.orEmpty()
            )
        }
    }
}
