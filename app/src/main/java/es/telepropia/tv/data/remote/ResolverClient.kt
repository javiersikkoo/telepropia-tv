package es.telepropia.tv.data.remote

import es.telepropia.tv.data.model.Failure
import es.telepropia.tv.data.model.Match
import es.telepropia.tv.data.model.Outcome
import es.telepropia.tv.data.model.PlaybackSpec
import es.telepropia.tv.data.model.SourceHost
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class ResolverClient(private val http: OkHttpClient = defaultHttpClient()) {

    suspend fun live(host: SourceHost, sportId: Int): Outcome<List<Match>> {
        val sourceName = HostUrls.label(host.baseUrl)
        return get(host.baseUrl, "api/live", listOf("sportType" to sportId.toString())) { body ->
            ResolverJson.decodeFromString(LiveResponseDto.serializer(), body).matches
                .map { it.toModel(host.id, sourceName) }
        }
    }

    suspend fun resolve(baseUrl: String, matchId: String, sportId: Int): Outcome<PlaybackSpec> =
        get(baseUrl, "api/resolve", listOf("matchId" to matchId, "sportType" to sportId.toString())) { body ->
            val dto = ResolverJson.decodeFromString(ResolveResponseDto.serializer(), body)
            val url = requireNotNull(HostUrls.rewritePlayable(dto.playableUrl, baseUrl)) { "playableUrl" }
            PlaybackSpec(url = url, referer = dto.referer)
        }

    private suspend fun <T> get(
        baseUrl: String,
        path: String,
        query: List<Pair<String, String>>,
        parse: (String) -> T
    ): Outcome<T> = withContext(Dispatchers.IO) {
        val url = HostUrls.endpoint(baseUrl, path, query)
        if (url == null) Outcome.Err(Failure.NoHost) else call(url, parse)
    }

    private fun <T> call(url: HttpUrl, parse: (String) -> T): Outcome<T> = try {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.isSuccessful) {
                Outcome.Ok(parse(response.body?.string().orEmpty()))
            } else {
                Outcome.Err(FailureMapper.fromStatus(response.code))
            }
        }
    } catch (e: IllegalArgumentException) {
        Outcome.Err(Failure.Other)
    } catch (e: IOException) {
        Outcome.Err(FailureMapper.fromThrowable(e))
    }
}

private const val CONNECT_TIMEOUT_S = 8L
private const val READ_TIMEOUT_S = 8L
private const val CALL_TIMEOUT_S = 12L

private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
    .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
    .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
    .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
    .build()
