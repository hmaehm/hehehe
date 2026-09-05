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
                ?: document.selectFirst("meta[itemprop=embedURL]")?.attr("content")
        ) ?: return false

        var foundStream = false

        try {
            // Check if iframeSrc embeds an inner URL (e.g. v.fbplay.vip/embed/https://www.xvideos.com/embedframe/...)
            val innerUrlMatch = Regex("""/embed/(https?://.+)""").find(iframeSrc)?.groupValues?.get(1)
            if (!innerUrlMatch.isNullOrEmpty()) {
                try {
                    loadExtractor(innerUrlMatch, subtitleCallback, callback)
                } catch (_: Exception) {}
            }

            val iframeResponse = app.get(iframeSrc, referer = data).text
            val candidateUrls = mutableSetOf<String>()

            // Pattern 1: Variable declarations (playlistUrl = '...', videoSrc = '...')
            Regex("""(?:playlistUrl|videoSrc)\s*=\s*['"]([^'"]+)['"]""").findAll(iframeResponse).forEach {
                candidateUrls.add(it.groupValues[1])
            }

            // Pattern 2: JSON object fields ("video": "...", "file": "...")
            Regex("""['"](?:video|file)['"]\s*:\s*['"](https?://[^'"]+\.(?:m3u8|mp4)[^'"]*)['"]""").findAll(iframeResponse).forEach {
                candidateUrls.add(it.groupValues[1])
            }

            // Pattern 3: Direct HLS or MP4 URLs inside scripts
            Regex("""['"](https?://[^'"]+?\.(?:m3u8|mp4)[^'"]*?)['"]""").findAll(iframeResponse).forEach {
                val url = it.groupValues[1]
                if (!url.contains("plyr") && !url.contains("hls.js") && !url.contains("jwplayer")) {
                    candidateUrls.add(url)
                }
            }

            for (streamUrl in candidateUrls) {
                val isM3u8 = streamUrl.contains(".m3u8")
                val linkType = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                val streamHeaders = mapOf("Referer" to iframeSrc)

                // If the stream uses AV1 codec (unsupported by many Android TVs), provide both H264 and AV1 streams
                if (streamUrl.contains(".av1.mp4.m3u8")) {
                    val h264Url = streamUrl.replace(".av1.mp4.m3u8", ".mp4.m3u8")
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "$name (H264 / Android TV)",
                            url = h264Url,
                            referer = iframeSrc,
                            quality = Qualities.Unknown.value,
                            type = linkType,
                            headers = streamHeaders
                        )
                    )
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "$name (AV1)",
                            url = streamUrl,
                            referer = iframeSrc,
                            quality = Qualities.Unknown.value,
                            type = linkType,
                            headers = streamHeaders
                        )
                    )
                    foundStream = true
                } else {
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = streamUrl,
                            referer = iframeSrc,
                            quality = Qualities.Unknown.value,
                            type = linkType,
                            headers = streamHeaders
                        )
                    )
                    foundStream = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!foundStream) {
            return loadExtractor(iframeSrc, subtitleCallback, callback)
        }

        return true
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
