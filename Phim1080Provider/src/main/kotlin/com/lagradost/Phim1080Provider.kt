package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.Base64

class Phim1080Provider : MainAPI() {
    override var mainUrl = "https://xemphim1080.com"
    override var name = "Phim1080"
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
        "$mainUrl/phim-de-cu/" to "Phim đề cử",
        "$mainUrl/phim-chieu-rap?page=" to "Phim Chiếu Rạp",
        "$mainUrl/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/phim-bo?page=" to "Phim Bộ",

    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.tray-item > a").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("div.tray-item-title")?.text()?.trim().toString()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("img.tray-item-thumbnail")?.attr("data-src")
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
        val link = "$mainUrl/tim-kiem/$query"
        val document = app.get(link).document

        return document.select("div.tray-item > a").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("div.container")?.attr("data-name")?.trim().toString()
        val dataslug = document.select("div.container").attr("data-slug")
        val link = "$mainUrl/$dataslug"
        val poster = document.selectFirst("div.image img[itemprop=image]")?.attr("src")
        val tags = document.select("div.film-content div.film-info-genre:nth-child(8) a").map { it.text() }
        val year = document.select("div.film-content div.film-info-genre:nth-child(2)").text().substringAfter("Năm phát hành:").trim()
            .toIntOrNull()
        val tvType = if (document.select("div.episode-list-header").isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.film-info-description").text().trim()
        val rating =
            document.select("div.film-content div.film-info-genre:nth-child(6)").text().substringAfter("Điểm IMDB:").substringBefore("/10").toRatingInt()
        val recommendations = document.select("div.related-item").mapNotNull {
                val main = it.select("div.related-item")
                val recUrl = it.select("a").attr("href")
                val recTitle = it.select("div.related-item-meta > a").text()
                val posterUrl = main.select("img.related-item-thumbnail").attr("data-src")
                MovieSearchResponse(
                    recTitle,
                    recUrl,
                    this.name,
                    TvType.Movie,
                    posterUrl,
                    isHorizontalImages = true
                )
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
                this.rating = rating
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
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
        val key = document.select(video.player-video).attr("src")

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
