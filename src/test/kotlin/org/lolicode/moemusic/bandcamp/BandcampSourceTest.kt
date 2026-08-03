package org.lolicode.moemusic.bandcamp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BandcampSourceTest {
    @Test
    fun trackUrlParserRequiresAHostedTrackPage() {
        assertEquals(
            "https://artist.bandcamp.com/track/song",
            parseBandcampTrackUrl("https://artist.bandcamp.com/track/song?from=discover"),
        )
        assertNull(parseBandcampTrackUrl("https://artist.bandcamp.com/album/record"))
        assertNull(parseBandcampTrackUrl("https://artist.example.com/track/song"))
        assertNull(parseBandcampTrackUrl("https://artist.bandcamp.com.evil.test/track/song"))
        assertNull(parseBandcampTrackUrl("https://user@artist.bandcamp.com/track/song"))
        assertNull(parseBandcampTrackUrl("https://artist.bandcamp.com/track/" + "a".repeat(2048)))
    }

    @Test
    fun trackPageParserReadsMetadataAndMp3Stream() {
        val html = """<div data-tralbum='{"artist":"Artist","art_id":"42","current":{"title":"Album"},"trackinfo":[{"title":"Song","duration":91.5,"file":{"mp3-128":"https://t4.bcbits.com/stream/42/mp3-128"}}]}'></div>"""
        val parsed = parseBandcampTrackPage(html, "https://artist.bandcamp.com/track/song")

        assertEquals("Song", parsed?.title)
        assertEquals("Artist", parsed?.artist)
        assertEquals(91500L, parsed?.durationMs)
        assertEquals("https://t4.bcbits.com/stream/42/mp3-128", parsed?.mp3Url)
    }

    @Test
    fun trackPageParserRejectsRestrictedAndOversizedData() {
        val restricted = """<div data-tralbum='{"tralbum_subscriber_only":true,"trackinfo":[{"title":"Song","file":{"mp3-128":"https://t4.bcbits.com/stream/42/mp3-128"}}]}'></div>"""
        val oversizedTitle = """<div data-tralbum='{"trackinfo":[{"title":"${"x".repeat(513)}"}]}'></div>"""

        assertNull(parseBandcampTrackPage(restricted, "https://artist.bandcamp.com/track/song")?.mp3Url)
        assertNull(parseBandcampTrackPage(oversizedTitle, "https://artist.bandcamp.com/track/song"))
        assertNull(parseBandcampTrackPage("<div data-tralbum='{}'></div>", "https://evil.test/track/song"))
        assertEquals(false, hasSafeJsonNesting("[".repeat(65) + "]".repeat(65)))
        assertEquals(true, hasSafeJsonNesting("{\"value\":\"[[[\"}"))
    }
}
