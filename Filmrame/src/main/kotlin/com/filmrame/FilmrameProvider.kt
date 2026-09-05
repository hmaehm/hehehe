package com.filmrame

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class FilmrameProvider : MainAPI() {
    override var mainUrl = "https://bokepindo69.net"
    override var name = "Filmrame"
    override val supportedTypes = setOf(TvType.NSFW)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/cate/jav-sub-indo/page/" to "JAV Sub Indo",
        "$mainUrl/cate/bokep-indo/page/" to "Bokep Indo",
        "$mainUrl/cate/bokep-asia/page/" to "Bokep Asia",
        "$mainUrl/cate/bokep-japan/page/" to "Bokep Japan",
        "$mainUrl/cate/bokep-barat/page/" to "Bokep Barat",
        "$mainUrl/page/" to "Terbaru"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data.removeSuffix("page/").removeSuffix("/") + "/"
        } else {
            "${request.data}$page/"
        }
        val document = app.get(url).document
        val home = document.select("article.thumb-block").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.thumb-block").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()
            ?: document.selectFirst("meta[itemprop=name]")?.attr("content")
            ?: "Video"
        val poster = fixUrlNull(
            document.selectFirst("meta[itemprop=thumbnailUrl]")?.attr("content")
                ?: document.selectFirst(".desc img")?.attr("src")
        )
        val plot = document.selectFirst(".desc p")?.text()
        val tags = document.select(".tags-list a").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframeSrc = fixUrlNull(
            document.selectFirst("div.responsive-player iframe")?.attr("src")
                ?: document.selectFirst("div.video-player iframe")?.attr("src")
        ) ?: return false

        try {
            val iframeResponse = app.get(iframeSrc, referer = data).text
            val m3u8Regex = Regex("""playlistUrl\s*=\s*['"]([^'"]+)['"]""")
            val playlistUrl = m3u8Regex.find(iframeResponse)?.groupValues?.get(1)

            if (!playlistUrl.isNullOrEmpty()) {
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = name,
                        url = playlistUrl,
                        referer = iframeSrc,
                        quality = Qualities.Unknown.value,
                        type = ExtractorLinkType.M3U8
                    )
                )
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return loadExtractor(iframeSrc, subtitleCallback, callback)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("a") ?: return null
        val href = fixUrlNull(aTag.attr("href")) ?: return null
        val title = aTag.attr("title").ifEmpty {
            selectFirst("header.entry-header span")?.text()
        } ?: return null
        val imgTag = selectFirst("img")
        val rawPoster = imgTag?.attr("data-src")?.ifEmpty { imgTag.attr("src") } ?: imgTag?.attr("src")
        val posterUrl = fixUrlNull(rawPoster)

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }
}
