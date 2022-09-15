package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.Base64

class PhimnhuaProvider : MainAPI() {
    override var mainUrl = "https://phimnhua.com"
    override var name = "Phim Nhựa"
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
        "$mainUrl/topweek/page/" to "Top Phim",
        "$mainUrl/page/" to "Phim Mới",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.col-6.col-sm-4.col-md-3.col-xl-2 > div.card > div.card__cover").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("a > img")?.attr("alt")?.substringAfter("Xem phim")?.substringBefore(" – ")?.trim().toString()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("a > img")?.attr("src")
        val temp = this.select("span.tray-item-quality").text()
        return if (temp.contains(Regex("\\d"))) {
            val episode = Regex("\\d+").find(temp)?.groupValues?.distinct()?.firstOrNull()?.toIntOrNull()
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
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select("div.col-6.col-sm-4.col-md-3.col-xl-2 > div.card > div.card__cover").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("div.container > div.row > div:nth-child(1) > h1")?.text()?.substringAfter("Xem phim")?.substringBefore(" – ")?.trim().toString()
        val link = document.select("div.container").attr("data-slug")
        val poster = document.selectFirst("div.col-12.col-sm-6.col-md-4.col-lg-3.col-xl-5 > div.card__cover > img")?.attr("src")
        val tags = document.select("div.col-sm-6.col-md-8.col-lg-9.col-xl-7 > div.card__content > ul.card__meta > li:nth-child(3) > a").map { it.text() }
        val year = document.select("div.col-sm-6.col-md-8.col-lg-9.col-xl-7 > div.card__content > ul.card__meta > li:nth-child(4)").text().substringAfter("Năm phát hành:").trim()
            .toIntOrNull()
        val tvType = if (document.select("div.d-flex.justify-content-center.mt-3 > ul.list.list-inline.justify-content-center").isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.col-sm-6.col-md-8.col-lg-9.col-xl-7 > div.card__content > ul.card__meta > li:nth-child(7) > div > p").text().trim()
        val actors = document.select("div.col-sm-6.col-md-8.col-lg-9.col-xl-7 > div.card__content > ul.card__meta > li:nth-child(2) > a").map { it.text() }
        val recommendations = document.select("div.col-6.col-lg-2 > div.card.card--normal > div.card__cover").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val main = app.get(url).document
            val episodes = arrayListOf<Episode>()
            main.select("div.episode-list > a").forEach {
                entry ->
                    val href = entry.attr("data-url_cdn") ?: return@forEach
                    val text = entry.text() ?: ""
                    val name = text.replace(Regex("(^(\\d+)\\.)"), "")
                    episodes.add(
                        Episode(
                            name = name,
                            data = href
                        )
                    )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                addActors(actors)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                addActors(actors)
                this.recommendations = recommendations
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
        val key = document.select("div#content script")
            .find { it.data().contains("filmInfo.episodeID =") }?.data()?.let { script ->
                val id = script.substringAfter("filmInfo.episodeID = parseInt('")
                app.post(
                    // Not mainUrl
                    url = "https://phimmoichills.net/pmplayer.php",
                    data = mapOf("qcao" to id, "sv" to "0"),
                    referer = data,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                ).text.substringAfterLast("iniPlayers(\"")
                    .substringBefore("\",")
            }
            
        listOf(
            Pair("$key", "Phim1080")
        ).apmap { (link, source) ->
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        source,
                        source,
                        link,
                        referer = "$mainUrl/",
                        quality = Qualities.P1080.value,
                        isM3u8 = true,
                    )
                )
            }
        }
        return true
    }

}
