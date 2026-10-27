package eu.kanade.tachiyomi.animeextension.en.rule34video

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Rule34Video : ParsedAnimeHttpSource() {
    override val name = "Rule34Video"
    override val baseUrl = "https://rule34video.com"
    override val lang = "en"
    override val supportsLatest = true

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/latest-updates/$page/", headers)
    override fun popularAnimeSelector() = "div.thumbs div.item, div.thumb"
    override fun popularAnimeFromElement(element: Element): SAnime {
        val a = element.selectFirst("a")!!
        return SAnime.create().apply {
            setUrlWithoutDomain(a.attr("href"))
            title = element.selectFirst(".thumb_title, .title")?.text() ?: a.attr("title").ifEmpty { a.text() }
            // FIX thumbnail - handle lazy load
            val img = element.selectFirst("img")
            var thumb = img?.attr("data-original") ?: ""
            if (thumb.isEmpty()) thumb = img?.attr("data-src") ?: ""
            if (thumb.isEmpty()) thumb = img?.attr("src") ?: ""
            if (thumb.startsWith("//")) thumb = "https:$thumb"
            thumbnail_url = thumb
        }
    }
    override fun popularAnimeNextPageSelector() = "li.next a, a.next"

    override fun latestUpdatesRequest(page: Int) = popularAnimeRequest(page)
    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) = GET("$baseUrl/search/$query/$page/", headers)
    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            val img = document.selectFirst("meta[property=og:image]")?.attr("content")
            thumbnail_url = img
            description = document.select("div.info a, .tag").eachText().joinToString(", ")
        }
    }

    override fun episodeListSelector() = "div"
    override fun episodeListParse(response: Response): List<SEpisode> {
        return listOf(SEpisode.create().apply {
            name = "Video"
            episode_number = 1f
            url = response.request.url.toString()
        })
    }
    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // FIX VIDEO - parse dari JS, bukan dari <source> aja
    override fun videoListParse(document: Document): List<Video> {
        val html = document.html()
        // cari di flashvars atau video_url
        val regex = Regex("""video_url:\s*['"]([^'"]+)['"]|source:\s*['"]([^'"]+\.mp4[^'"]*)['"]""")
        val match = regex.find(html)
        var videoUrl = match?.groups?.get(1)?.value ?: match?.groups?.get(2)?.value ?: ""
        
        if (videoUrl.isEmpty()) {
            videoUrl = document.selectFirst("source")?.attr("src") ?: ""
        }
        if (videoUrl.isEmpty()) {
            videoUrl = document.selectFirst("video source")?.attr("src") ?: ""
        }
        if (videoUrl.startsWith("//")) videoUrl = "https:$videoUrl"
        
        return if (videoUrl.isNotEmpty()) {
            listOf(Video(videoUrl, "Original", videoUrl, headers))
        } else {
            emptyList()
        }
    }

    override fun videoListSelector() = ""
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = ""
}
