package eu.kanade.tachiyomi.animeextension.en.rule34video

import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Rule34Video : ParsedAnimeHttpSource() {

    override val name = "Rule34Video"
    override val baseUrl = "https://rule34video.com"
    override val lang = "en"
    override val supportsLatest = true
    override val isNsfw = true

    override fun popularAnimeSelector() = "div.thumbs div.thumb"
    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            title = element.selectFirst("a img")!!.attr("alt")
            thumbnail_url = element.selectFirst("a img")!!.attr("data-original")
        }
    }

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1")!!.text()
            genre = document.select("div.info a").joinToString { it.text() }
        }
    }

    override fun episodeListSelector() = "div#tab1"
    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            setUrlWithoutDomain(baseUrl + document.location())
            name = "Video"
        }
    }

    override fun videoListSelector() = "video source"
    override fun videoFromElement(element: Element): Video {
        val url = element.attr("src")
        return Video(url, "Default", url)
    }

    override fun videoListParse(document: Document): List<Video> {
        val src = document.selectFirst("video source")?.attr("src") ?: return emptyList()
        return listOf(Video(src, "720p", src))
    }
}
