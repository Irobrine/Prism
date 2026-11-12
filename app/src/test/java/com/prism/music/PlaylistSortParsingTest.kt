package com.prism.music

import com.prism.music.data.innertube.Parser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The playlist page's "Newest first" continuation and its song count, read for Library → Songs' "From" (#473). */
class PlaylistSortParsingTest {
    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test fun theNewestContinuationIsFoundByItsOption() {
        val page = json(
            """{"frameworkUpdates":{"entityBatchUpdate":{"mutations":[
                {"entityKey":"a","payload":{"commandEntity":{
                    "key":"EkFtdXNpY19zb3J0X29wdGlvbl9jb21tYW5kX2VudGl0eV9rZXlfVkxQTGJYZVFSQU03YXU4X05FV0VTVF9BRERFRCDxAigB",
                    "command":{"browseSectionListReloadEndpoint":{"continuation":{"reloadContinuationData":{"continuation":"newest-token"}}}}}}},
                {"entityKey":"b","payload":{"commandEntity":{
                    "key":"EjxtdXNpY19zb3J0X29wdGlvbl9jb21tYW5kX2VudGl0eV9rZXlfVkxQTGJYZVFSQU03YXU4X05PX1NPUlQg8QIoAQ%3D%3D",
                    "command":{"browseSectionListReloadEndpoint":{"continuation":{"reloadContinuationData":{"continuation":"default-token"}}}}}}}
            ]}}}""",
        )
        assertEquals("newest-token", Parser.sortContinuation(page, "NEWEST_ADDED"))
        assertNull("the default order's key doesn't match", Parser.sortContinuation(page, "OLDEST_ADDED"))
    }

    @Test fun theHeaderSongCountIsRead() {
        val header = json(
            """{"secondSubtitle":{"runs":[{"text":"334 views"},{"text":" • "},{"text":"1,000 tracks"},{"text":" • "},{"text":"3 days"}]}}""",
        )
        assertEquals(1000, Parser.trackCount(header))
    }

    @Test fun aPlaylistWithoutViewsStillReadsItsCount() {
        val header = json(
            """{"secondSubtitle":{"runs":[{"text":"812 Titel"},{"text":" • "},{"text":"2 days"}]}}""",
        )
        assertEquals(812, Parser.trackCount(header))
    }
}
