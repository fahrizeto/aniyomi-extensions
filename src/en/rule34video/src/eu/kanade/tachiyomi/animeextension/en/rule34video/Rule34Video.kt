package eu.kanade.tachiyomi.animeextension.en.rule34video

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class Rule34Video : AnimeHttpSource() {

    override val name = "Rule34Video"
    override val baseUrl = "https://rule34video.com"
    override val lang = "en"
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularAnimeRequest(page: Int): Request = latestUpdatesRequest(page)
    override fun latestUpdatesRequest(page: Int): Request = 
        GET("$baseUrl/latest-updates/${if (page > 1) "?page=$page" else ""}", headers)

    override fun popularAnimeParse(response: Response) = latestUpdatesParse(response)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val entries = parseAnimeCards(document)
        val hasNext = document.select("a.next, [rel=next]").isNotEmpty()
        return AnimesPage(entries, hasNext)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.selectedValue() ?: "latest"
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name()).replace("+", "%20")
        val url = "$baseUrl/search/$encodedQuery/".toHttpUrl().newBuilder()
            .addQueryParameter("sort_by", sort)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val entries = parseAnimeCards(document)
        val hasNext = document.select("a.next, [rel=next]").isNotEmpty()
        return AnimesPage(entries, hasNext)
    }

    private fun parseAnimeCards(document: Document): List<SAnime> {
        val cards = document.select(".thumb-block, .video-item").ifEmpty { document.select(".item") }
        return cards.mapNotNull { card ->
            val link = card.selectFirst("a[href*=/videos/], a[href*=/video/]") ?: card.selectFirst("a") ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { return@mapNotNull null }
            val title = link.attr("title").ifBlank { card.selectFirst(".title")?.text() ?: link.text() }.ifBlank { return@mapNotNull null }
            val img = card.selectFirst("img")
            val thumb = img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src")
            SAnime.create().apply {
                url = href
                this.title = title.trim()
                thumbnail_url = thumb?.let { if (it.startsWith("//")) "https:$it" else it }
            }
        }.distinctBy { it.url }
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        return SAnime.create().apply {
            url = response.request.url.encodedPath
            title = doc.selectFirst("h1")?.text()?.trim() ?: ""
            thumbnail_url = doc.selectFirst("meta[property=og:image]")?.attr("content")
            genre = doc.select("a[href*=/categories/]").joinToString(", ") { it.text() }
            description = doc.selectFirst("meta[name=description]")?.attr("content")
            initialized = true
        }
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        return listOf(SEpisode.create().apply {
            url = response.request.url.encodedPath
            name = "Video"
            episode_number = 1f
        })
    }

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        val found = linkedMapOf<String, String>()
        doc.select("video source[src], source[src]").forEach {
            val src = it.absUrl("src").ifBlank { it.attr("src") }
            if (src.startsWith("http")) found.putIfAbsent(src, it.attr("label").ifBlank { qualityFromText(src) })
        }
        doc.select("video[src]").forEach {
            val src = it.absUrl("src").ifBlank { it.attr("src") }
            if (src.startsWith("http")) found.putIfAbsent(src, qualityFromText(src))
        }
        val scripts = doc.select("script").joinToString("\n") { it.data() }
        Regex("""video_url\s*[:=]\s*["']([^"']+\.mp4[^"']*)["']""").findAll(scripts).forEach {
            val src = it.groupValues[1].replace("\\/", "/")
            if (src.startsWith("http")) found.putIfAbsent(src, qualityFromText(src))
        }

        return found.map { (url, q) ->
            Video(url, q.ifBlank { "720p" }, url)
        }.sortedBy { if (it.quality.contains("1080")) 0 else if (it.quality.contains("720")) 1 else 2 }
    }

    override fun getFilterList() = AnimeFilterList(SortFilter())

    private fun qualityFromText(v: String): String {
        return Regex("""(1080p|720p|480p|360p)""", RegexOption.IGNORE_CASE).find(v)?.value?.lowercase() ?: "720p"
    }

    private class SortFilter : AnimeFilter.Select<String>("Sort by", arrayOf("Latest", "Most viewed", "Top rated", "Longest")) {
        fun selectedValue(): String = when (state) {
            1 -> "most-viewed"
            2 -> "top-rated"
            3 -> "longest"
            else -> "latest"
        }
    }
}
