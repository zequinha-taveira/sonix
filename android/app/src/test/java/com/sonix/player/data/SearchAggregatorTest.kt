package com.sonix.player.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAggregatorTest {

    private val provider1 = mockk<MusicSearchProvider> {
        coEvery { name } returns "iTunes"
    }

    private val provider2 = mockk<MusicSearchProvider> {
        coEvery { name } returns "Deezer"
    }

    private val aggregator = SearchAggregator(listOf(provider1, provider2))

    @Test
    fun `search with Todas as Origens should call all providers and merge results`() = runTest {
        val query = "Audioslave"
        val track1 = Track("1", "Like a Stone", "Audioslave", "Audioslave", "url1", "4:54")
        val track2 = Track("2", "Show Me How to Live", "Audioslave", "Audioslave", "url2", "4:37")

        coEvery { provider1.search(query) } returns listOf(track1)
        coEvery { provider2.search(query) } returns listOf(track2)

        val results = aggregator.search(query, "Todas as Origens")

        assertEquals(2, results.size)
        assertTrue(results.contains(track1))
        assertTrue(results.contains(track2))

        coVerify(exactly = 1) { provider1.search(query) }
        coVerify(exactly = 1) { provider2.search(query) }
    }

    @Test
    fun `search with specific source should only call that provider`() = runTest {
        val query = "Audioslave"
        val track1 = Track("1", "Like a Stone", "Audioslave", "Audioslave", "url1", "4:54")

        coEvery { provider1.search(query) } returns listOf(track1)

        val results = aggregator.search(query, "iTunes")

        assertEquals(1, results.size)
        assertEquals(track1, results.first())

        coVerify(exactly = 1) { provider1.search(query) }
        coVerify(exactly = 0) { provider2.search(any()) }
    }

    @Test
    fun `search should deduplicate results by url`() = runTest {
        val query = "Audioslave"
        val track1 = Track("1", "Like a Stone", "Audioslave", "Audioslave", "url1", "4:54")
        // Different ID, same url
        val track2 = Track("2", "Like a Stone (Duplicate)", "Audioslave", "Audioslave", "url1", "4:54")

        coEvery { provider1.search(query) } returns listOf(track1)
        coEvery { provider2.search(query) } returns listOf(track2)

        val results = aggregator.search(query, "Todas as Origens")

        assertEquals(1, results.size)
        assertEquals(track1, results.first())
    }

    @Test
    fun `search should ignore failed providers and keep results from successful ones`() = runTest {
        val query = "Audioslave"
        val track2 = Track("2", "Show Me How to Live", "Audioslave", "Audioslave", "url2", "4:37")

        coEvery { provider1.search(query) } throws Exception("Network error")
        coEvery { provider2.search(query) } returns listOf(track2)

        val results = aggregator.search(query, "Todas as Origens")

        assertEquals(1, results.size)
        assertEquals(track2, results.first())
    }
}
