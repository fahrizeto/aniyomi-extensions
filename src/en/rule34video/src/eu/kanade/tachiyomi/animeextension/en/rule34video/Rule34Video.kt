package eu.kanade.tachiyomi.animeextension.en.rule34video

import android.app.Application
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.doodextractor.DotDooExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Rule34Video : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Rule34Video"
    override val baseUrl = "https://rule34video.com"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/latest-updates/$page/")
    }

    override fun popularAnimeSelector(): String = "div.thumbs div.thumb"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val a = element.selectFirst("a")!!
            setUrlWithoutDomain(a.attr("href"))
            title = element.selectFirst("a div.thumb_title")?.text() ?: a.attr("title")
            thumbnail_url = element.selectFirst("img")?.let { 
                it.attr("data-original").ifEmpty { it.attr("src") }
            }
        }
    }

    override fun popularAnimeNextPageSelector(): String = "li.next a"

    override fun latestUpdatesRequest(page: Int) = popularAnimeRequest(page)
    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search/$query/$page/", headers)
        } else {
            GET("$baseUrl/latest-updates/$page/", headers)
        }
    }

    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            description = document.select("div.info a").eachText().joinToString(", ")
            genre = document.select("div.info a").eachText().joinToString(", ")
            status = SAnime.COMPLETED
        }
    }

    override fun episodeListSelector(): String = "div.table"

    override fun episodeListParse(response: Response): List<SEpisode> {
        return listOf(
            SEpisode.create().apply {
                name = "Video"
                episode_number = 1f
                url = response.request.url.toString()
            }
        )
    }

    override fun episodeFromElement(element: Element): SEpisode {
        throw UnsupportedOperationException()
    }

    override fun videoListParse(document: Document): List<Video> {
        val videoUrl = document.selectFirst("source")?.attr("src") ?: ""
        return listOf(Video(videoUrl, "Original", videoUrl, headers))
    }

    override fun videoListSelector(): String = ""
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", "1080p")!!
        return sortedWith(compareBy { it.quality.contains(quality) }).reversed()
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080p", "720p", "480p", "360p")
            setDefaultValue("1080p")
            summary = "%s"
        }.also(screen::addPreference)
    }
}
