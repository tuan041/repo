package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.util.ArrayList

class XemphimProvider : MainAPI() {
    override var mainUrl = "https://phimbo.me"
    override var name = "Xemphim"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/phim-chieu-rap/" to "Phim Chiếu Rạp",
        "$mainUrl/phim-le/trang-" to "Phim Lẻ",
        "$mainUrl/phim-bo/trang-" to "Phim Bộ",
        "$mainUrl/category/hoat-hinh/trang-" to "Phim hoạt hình",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.item.col-lg-2.col-md-3.col-sm-4.col-6").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("p")?.text()?.trim().toString()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > div.img-4-6 > div.inline > img.ls-is-cached.lazyloaded")?.attr("data-src"))
        val temp = this.select("span.label").text()
        return if (temp.contains(Regex("\\d"))) {
            val episode = Regex("(\\((\\d+))|(\\s(\\d+))").find(temp)?.groupValues?.map { num ->
                num.replace(Regex("\\(|\\s"), "")
            }?.distinct()?.firstOrNull()?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality =
                temp.replace(Regex("(-.*)|(\\|.*)|(?i)(VietSub.*)|(?i)(Thuyết.*)"), "").trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/tim-kiem-phim/?keyword=$query"
        val document = app.get(link).document

        return document.select("div.list-vod.row.category-tabs-item").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h2.title-vod.mt-2")?.text()?.trim().toString()
        val link = document.select("div.row.mt-2 > div.col-6.col-md-3 > a").attr("href")
        val poster = document.selectFirst("div.item > div.img-4-6 > div.inline > img")?.attr("src")
        val tags = document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:nth-child(4)").map { it.text() }
        val year = document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:nth-child(5)").text().trim().takeLast(4)
            .toIntOrNull()
        val tvType = if (document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:nth-child(2) > #text").isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.detail > div.mt-2 > p").text().trim()
        val trailer =
            document.select("div#trailer script").last()?.data()?.substringAfter("file: \"")
                ?.substringBefore("\",")
        val rating =
            document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:last-child").text().substringAfter(": ").toRatingInt()
        val actors = document.select("div.col-md-6.col-12:nth-child(2) > ul.more-info#text").map { it.text() }
        val recommendations = document.select("div.item.col-lg-2.col-md-3.col-sm-4.col-6").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val docEpisodes = app.get(link).document
            val episodes = docEpisodes.select("ul#list_episodes > li").map {
                val href = it.select("a").attr("href")
                val episode =
                    it.select("a").text().replace(Regex("[^0-9]"), "").trim().toIntOrNull()
                val name = "Episode $episode"
                Episode(
                    data = href,
                    name = name,
                    episode = episode,
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val key = document.select("div#content script").mapNotNull { script ->
            if (script.data().contains("filmInfo.episodeID =")) {
                val id = script.data().substringAfter("filmInfo.episodeID = parseInt('")
                    .substringBefore("');")
                app.post(
                    // Not mainUrl
                    url = "https://phimmoichills.net/pmplayer.php",
                    data = mapOf("qcao" to id),
                    referer = data,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                ).text.also { println("HERERERR $it") }.substringAfterLast("iniPlayers(\"").substringBefore("\",")
            } else {
                null
            }
        }.first()

        listOf(
            Pair("https://so-trym.topphimmoi.org/hlspm/$key", "PMFAST"),
            Pair("https://dash.megacdn.xyz/hlspm/$key", "PMHLS"),
            Pair("https://dash.megacdn.xyz/dast/$key/index.m3u8", "PMBK")
        ).apmap { (link, source) ->
            safeApiCall {
                if (source == "PMBK") {
                    callback.invoke(
                        ExtractorLink(
                            source,
                            source,
                            link,
                            referer = "$mainUrl/",
                            quality = Qualities.P1080.value,
                            isM3u8 = true
                        )
                    )
                } else {
                    val playList = app.get(link, referer = "$mainUrl/")
                        .parsedSafe<ResponseM3u>()?.main?.segments?.map { segment ->
                            PlayListItem(
                                segment.link,
                                (segment.du.toFloat() * 1_000_000).toLong()
                            )
                        }

                    callback.invoke(
                        ExtractorLinkPlayList(
                            source,
                            source,
                            playList ?: return@safeApiCall,
                            referer = "$mainUrl/",
                            quality = Qualities.P1080.value,
                            headers = mapOf(
//                                "If-None-Match" to "*",
                                "Origin" to mainUrl,
                            )
                        )
                    )
                }
            }
        }
        return true
    }

    data class Segment(
        @JsonProperty("du") val du: String,
        @JsonProperty("link") val link: String,
    )

    data class DataM3u(
        @JsonProperty("segments") val segments: List<Segment>?,
    )

    data class ResponseM3u(
        @JsonProperty("2048p") val main: DataM3u?,
    )

}
