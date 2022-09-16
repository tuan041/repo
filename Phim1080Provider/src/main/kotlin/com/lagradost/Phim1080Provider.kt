package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

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
        val poster = document.selectFirst("div.col-12.col-sm-6.col-md-4.col-lg-3.col-xl-5 > div.card__cover > img")?.attr("src")
        var year: Int? = null
        var tags: List<String>? = null
        var actors: List<String>? = null
        document.select("div.col-sm-6.col-md-8.col-lg-9.col-xl-7 > div.card__content > ul.card__meta > li").forEach { element ->
            val type = element?.select("span")?.text() ?: return@forEach
            when {
                type.contains("Năm phát hành") -> {
                    year = element.ownText().substringAfter("Năm phát hành:").trim().toIntOrNull()
                }
                type.contains("Tags") -> {
                    actors = element.select("a").mapNotNull { it.text() }
                }
                type.contains("Thể loại") -> {
                    tags = element.select("a").mapNotNull { it.text() }
                }
            }
        }
        val tvType = if (document.select("ul.list.list-inline.justify-content-center > li.list-inline-item").isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.col-sm-6.col-md-8.col-lg-9.col-xl-7 > div.card__content > ul.card__meta > li:last-child > div > p").text().trim()
        val episodes = document.select("ul.list.list-inline.justify-content-center > li.list-inline-item").map {
            val name = it.selectFirst("button")?.text().toString()
            val link = it.selectFirst("button")?.attr("data-url")
            Episode(link, name)
        }.reversed()
        
        val recommendations = document.select("div.col-6.col-lg-2 > div.card.card--normal > div.card__cover").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                addActors(actors)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, fixUrl(url)) {
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
        val doc = app.get(data).document
        val key = doc.select("div.player-warp > div > div > video > source").attr("src")
        listOf(
            Pair("$key", "Phimnhua")
        ).apmap { (link, source) ->
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        source,
                        source,
                        link,
                        referer = "",
                        quality = Qualities.P1080.value
                    )
                )
            }
        }
        return true
    }
}
