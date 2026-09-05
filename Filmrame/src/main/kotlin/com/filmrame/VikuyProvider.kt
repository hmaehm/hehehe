package com.filmrame

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class VikuyProvider : MainAPI() {
    override var mainUrl = "https://vikuy.click"
    override var name = "Vikuy"
    override val supportedTypes = setOf(TvType.NSFW)
    override var lang = "en"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/explore.php?tab=latest&safe=0&page=" to "Latest",
        "$mainUrl/explore.php?tab=premium&safe=0&page=" to "Premium"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}$page").document
        val home = document.select("article.video-card").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/explore.php?tab=latest&safe=0&q=$query").document
        return document.select("article.video-card").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst(".video-title")?.text()
            ?: document.selectFirst("h1")?.text()
            ?: "Video"
        val poster = fixUrlNull(
            document.selectFirst("video#mainVid")?.attr("poster")
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        )
        val plot = document.selectFirst(".video-desc")?.text()
            ?: document.selectFirst("meta[name=description]")?.attr("content")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val videoSrc = fixUrlNull(document.selectFirst("video#mainVid source")?.attr("src")) ?: return false

        callback.invoke(
            ExtractorLink(
                source = name,
                name = name,
                url = videoSrc,
                referer = data,
                quality = Qualities.P1080.value,
                headers = mapOf(
                    "Referer" to data,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                ),
                extractorData = null,
                type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("a") ?: return null
        val href = fixUrlNull(aTag.attr("href")) ?: return null
        val title = selectFirst(".video-title")?.text() ?: return null
        val posterUrl = fixUrlNull(selectFirst(".video-thumb img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }
}
