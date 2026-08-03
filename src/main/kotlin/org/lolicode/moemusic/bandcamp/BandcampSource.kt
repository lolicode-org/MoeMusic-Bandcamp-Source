package org.lolicode.moemusic.bandcamp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.lolicode.moemusic.api.IdentifierResolutionResult
import org.lolicode.moemusic.api.LocalizedText
import org.lolicode.moemusic.api.MoeMusicUser
import org.lolicode.moemusic.api.SourceException
import org.lolicode.moemusic.api.SourceFormatException
import org.lolicode.moemusic.api.TrackUnavailableException
import org.lolicode.moemusic.api.UserResult
import org.lolicode.moemusic.api.model.ArtistInfo
import org.lolicode.moemusic.api.model.PlaybackResolution
import org.lolicode.moemusic.api.model.PlaybackResource
import org.lolicode.moemusic.api.model.TrackInfo
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration

private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
private const val MAX_TRALBUM_CHARS = 1024 * 1024
private const val MAX_URL_CHARS = 4096
private const val MAX_METADATA_CHARS = 512
private const val MAX_DURATION_MS = 7L * 24 * 60 * 60 * 1000
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 11.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.166 Safari/537.36"
private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
private val SLUG_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")

internal fun hasSafeJsonNesting(value: String, maxDepth: Int = 64): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (character in value) {
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
        } else {
            when (character) {
                '"' -> inString = true
                '{', '[' -> if (++depth > maxDepth) return false
                '}', ']' -> if (--depth < 0) return false
            }
        }
    }
    return depth == 0 && !inString
}

internal data class BandcampParsedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val album: String?,
    val artworkUrl: String?,
    val mp3Url: String?,
)

private class NotFoundFailure : IOException()

private class FetchFailure(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

private fun JsonElement?.textValue(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonElement?.metadataValue(): String? = textValue()
    ?.takeIf { it.length <= MAX_METADATA_CHARS && it.none(Char::isISOControl) }

private fun JsonElement?.doubleValue(): Double? =
    (this as? JsonPrimitive)?.doubleOrNull

private fun JsonElement?.objectValue(): JsonObject? = this as? JsonObject

private fun JsonObject.textValue(key: String): String? = this[key].textValue()

private fun JsonObject.metadataValue(key: String): String? = this[key].metadataValue()

private fun JsonObject.objectValue(key: String): JsonObject? = this[key].objectValue()

private fun JsonObject.arrayValue(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonElement?.isTrue(): Boolean = (this as? JsonPrimitive)?.let {
    it.booleanOrNull == true || it.intOrNull == 1
} == true

private fun isBandcampHost(host: String): Boolean =
    host == "bandcamp.com" || host.endsWith(".bandcamp.com")

private fun isBandcampMediaUrl(value: String): Boolean {
    if (value.length > MAX_URL_CHARS) return false
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.userInfo == null &&
        uri.port == -1 &&
        (host == "bcbits.com" || host.endsWith(".bcbits.com"))
}

private fun isBandcampArtworkUrl(value: String): Boolean {
    if (value.length > MAX_URL_CHARS) return false
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.userInfo == null &&
        uri.port == -1 &&
        (host == "bcbits.com" || host.endsWith(".bcbits.com"))
}

internal fun parseBandcampTrackUrl(value: String): String? {
    if (value.length > 2048) return null
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.port != -1) {
        return null
    }
    val host = uri.host?.lowercase() ?: return null
    if (!isBandcampHost(host)) return null

    val segments = uri.path.orEmpty().trim('/').split('/')
    if (segments.size != 2 || segments[0] != "track" || !SLUG_PATTERN.matches(segments[1])) return null
    return "https://" + host + "/track/" + segments[1]
}

private fun isOwnedBandcampUrl(value: String): Boolean {
    if (value.length > 2048) return false
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    return uri.host?.lowercase()?.let(::isBandcampHost) == true
}

internal fun parseBandcampTrackPage(html: String, canonicalUrl: String): BandcampParsedTrack? {
    if (html.length > MAX_RESPONSE_BYTES) return null
    val id = parseBandcampTrackUrl(canonicalUrl) ?: return null
    val attribute = Jsoup.parse(html, id)
        .selectFirst("[data-tralbum]")
        ?.attr("data-tralbum")
        ?.takeIf { it.isNotBlank() && it.length <= MAX_TRALBUM_CHARS }
        ?: return null
    if (!hasSafeJsonNesting(attribute)) return null
    val root = runCatching { JSON.parseToJsonElement(attribute).jsonObject }.getOrNull() ?: return null
    val track = root.arrayValue("trackinfo")?.firstOrNull()?.objectValue() ?: return null
    val title = track.metadataValue("title") ?: return null
    val artist = root.metadataValue("artist") ?: track.metadataValue("artist") ?: "Bandcamp"
    val current = root.objectValue("current")
    val album = current?.metadataValue("title") ?: root.metadataValue("album_title")
    val seconds = track["duration"].doubleValue()
    val durationMs = seconds?.times(1000.0)?.toLong()?.takeIf { it in 1..MAX_DURATION_MS } ?: -1L
    val artworkUrl = root.textValue("art_url")?.takeIf(::isBandcampArtworkUrl)
        ?: root.textValue("artwork_url")?.takeIf(::isBandcampArtworkUrl)
        ?: root.textValue("art_id")?.takeIf { it.length <= 20 && it.all(Char::isDigit) }
            ?.let { "https://f4.bcbits.com/img/a" + it + "_1.png" }
    val restricted = root["is_private_stream"].isTrue() ||
        root["tralbum_subscriber_only"].isTrue() ||
        current?.get("private").isTrue() ||
        track["private"].isTrue() ||
        track["streaming"]?.let { (it as? JsonPrimitive)?.intOrNull == 0 } == true
    val mp3Url = if (restricted) null else {
        track.objectValue("file")?.textValue("mp3-128")?.takeIf(::isBandcampMediaUrl)
    }
    return BandcampParsedTrack(
        id = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        album = album,
        artworkUrl = artworkUrl,
        mp3Url = mp3Url,
    )
}

private fun BandcampParsedTrack.toTrackInfo(): TrackInfo =
    TrackInfo(id = id, title = title, artists = listOf(ArtistInfo.fromName(artist)), durationMs = durationMs) {
        sourceId = BandcampPlugin.SOURCE_ID
        album = this@toTrackInfo.album
        coverUrl = this@toTrackInfo.artworkUrl
        unavailableReason = if (mp3Url == null) {
            LocalizedText.key("error.bandcamp.no_mp3")
        } else {
            null
        }
    }

class BandcampSource(initialConfig: BandcampConfig = BandcampConfig()) :
    org.lolicode.moemusic.api.IdentifierResolvableMusicSource {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    @Volatile
    private var config: BandcampConfig = initialConfig

    override val id: String = BandcampPlugin.SOURCE_ID
    override val displayName: LocalizedText = LocalizedText.key("source.bandcamp")

    fun updateConfig(config: BandcampConfig) {
        this.config = config
    }

    override suspend fun resolveIdentifier(
        identifier: String,
        submitter: MoeMusicUser?,
    ): IdentifierResolutionResult {
        if (!config.enabled) return IdentifierResolutionResult.Blocked(disabledMessage())
        val url = parseBandcampTrackUrl(identifier.trim())
            ?: return if (isOwnedBandcampUrl(identifier)) {
                IdentifierResolutionResult.Blocked(unsupportedLinkMessage())
            } else {
                IdentifierResolutionResult.Pass
            }

        return when (val result = getTrackInfo(url, submitter)) {
            is UserResult.Success -> result.value?.let(IdentifierResolutionResult::Resolved)
                ?: IdentifierResolutionResult.Blocked(trackNotFoundMessage())
            is UserResult.Error -> IdentifierResolutionResult.Blocked(result.message)
        }
    }

    override suspend fun getTrackInfo(trackId: String, submitter: MoeMusicUser?): UserResult<TrackInfo?> {
        if (!config.enabled) return UserResult.Error(disabledMessage())
        val id = parseBandcampTrackUrl(trackId) ?: return UserResult.Error(invalidTrackIdMessage())
        return try {
            val html = fetchText(id) ?: return UserResult.Success(null)
            val parsed = parseBandcampTrackPage(html, id)
                ?: return UserResult.Error(invalidResponseMessage())
            UserResult.Success(parsed.toTrackInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UserResult.Error(requestFailedMessage())
        }
    }

    override suspend fun resolve(track: TrackInfo, submitter: MoeMusicUser?): PlaybackResolution {
        if (!config.enabled) throw TrackUnavailableException(disabledMessage())
        val id = parseBandcampTrackUrl(track.id) ?: throw SourceFormatException()
        val parsed = try {
            val html = fetchText(id) ?: throw TrackUnavailableException(trackNotFoundMessage())
            parseBandcampTrackPage(html, id) ?: throw SourceException(invalidResponseMessage())
        } catch (e: TrackUnavailableException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SourceException(requestFailedMessage(), e)
        }

        val streamUrl = parsed.mp3Url ?: throw TrackUnavailableException(LocalizedText.key("error.bandcamp.no_mp3"))
        return PlaybackResolution(PlaybackResource(streamUrl))
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(url) }.getOrElse { throw FetchFailure("Invalid Bandcamp URL", it) }
        val host = uri.host?.lowercase()
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.userInfo != null ||
            uri.port != -1 ||
            host == null ||
            !isBandcampHost(host)
        ) {
            throw FetchFailure("Untrusted Bandcamp URL")
        }

        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: HttpTimeoutException) {
            throw FetchFailure("Bandcamp request timed out", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw FetchFailure("Bandcamp request interrupted", e)
        } catch (e: IOException) {
            throw FetchFailure("Bandcamp request failed", e)
        }

        if (response.statusCode() == 404) {
            response.body().close()
            return@withContext null
        }
        if (response.statusCode() !in 200..299) {
            response.body().close()
            throw FetchFailure("Bandcamp returned HTTP " + response.statusCode())
        }
        val bytes = response.body().use { it.readNBytes(MAX_RESPONSE_BYTES + 1) }
        if (bytes.size > MAX_RESPONSE_BYTES) throw FetchFailure("Bandcamp response is too large")
        String(bytes, StandardCharsets.UTF_8)
    }

    private fun disabledMessage(): LocalizedText = LocalizedText.key("error.bandcamp.disabled")

    private fun invalidTrackIdMessage(): LocalizedText = LocalizedText.key("error.bandcamp.invalid_track_id")

    private fun trackNotFoundMessage(): LocalizedText = LocalizedText.key("error.bandcamp.track_not_found")

    private fun unsupportedLinkMessage(): LocalizedText = LocalizedText.key("error.bandcamp.unsupported_link")

    private fun invalidResponseMessage(): LocalizedText = LocalizedText.key("error.bandcamp.invalid_response")

    private fun requestFailedMessage(): LocalizedText = LocalizedText.key("error.bandcamp.request_failed")
}
