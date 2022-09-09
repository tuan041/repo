package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.PlayListItem
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class Phim247Provider : MainAPI() {
    override var mainUrl = "https://247phim.com"
    override var name = "247Phim"
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
        "$mainUrl/phim/khong-the-bo-lo/trang-" to "Không Thể Bỏ Lỡ",
        "$mainUrl/phim/phim-chieu-rap/trang-" to "Phim Chiếu Rạp",
        "$mainUrl/phim/phim-le/trang-" to "Phim Lẻ",
        "$mainUrl/phim/phim-bo/trang-" to "Phim Bộ",
        "$mainUrl/phim/hoat-hinh/trang-" to "Phim hoạt hình",
        "$mainUrl/phim/sieu-anh-hung/trang-" to "Phim siêu anh hùng",

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

    private fun Element.toSearchResult(): SearchResponse {
        val title = if (this.selectFirst("h3")?.text()?.trim().toString().isNotEmpty())
            this.selectFirst("h3")?.text()?.trim().toString() else this.selectFirst("p.subtitle")?.text()?.trim().toString()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("div.img-4-6 > div.inline > img")?.attr("src")
        val temp = this.select("span.ribbon").text()
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
        val link = "$mainUrl/tim-kiem-phim/?keyword=$query"
        val document = app.get(link).document

        return document.select("div.item.col-lg-2.col-md-3.col-sm-4.col-6").mapNotNull {
                val main = it.select("div.item.col-lg-2.col-md-3.col-sm-4.col-6") ?: return@mapNotNull null
                val recUrl = it.select("a").attr("href") ?: return@mapNotNull null
                val recTitle = if (main.select("a > p:nth-child(2)").text().isNotEmpty())
                    main.select("a > p:nth-child(2)").text() else main.select("a > p:nth-child(3)").text()
                val poster = main.select("img").attr("src") ?: return@mapNotNull null
                MovieSearchResponse(
                    recTitle,
                    recUrl,
                    this.name,
                    TvType.Movie,
                    poster,
                )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = if (document.selectFirst("h2.title-vod.mt-2")?.text()?.trim().toString().isNotEmpty())
            document.selectFirst("h2.title-vod.mt-2")?.text()?.trim().toString() else document.selectFirst("h3.title-vod.mt-2")?.text()?.trim().toString()
        val link = document.select("div.row.mt-2 > div.col-6.col-md-3 > button").attr("onclick")
        val poster = document.selectFirst("div.item > div.img-4-6 > div.inline > img")?.attr("src")
        val tags = if (document.select("div#myTabContent.tab-content").isNotEmpty())
            document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:nth-child(5)").map { it.text().trim().substringAfter(": ").substringBefore(", Phim") }
            else document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:nth-child(4)").map { it.text().trim().substringAfter(": ").substringBefore(", Phim") }
        val year = if (document.select("div#myTabContent.tab-content").isNotEmpty())
            document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:nth-child(6)").text().trim().takeLast(4).toIntOrNull()
            else document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:nth-child(5)").text().trim().takeLast(4).toIntOrNull()
        val tvType = if (document.select("div#myTabContent.tab-content").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val description = document.select("div.detail > div.mt-2").text().trim().substringAfter("Play ")
        val rating =
            document.select("div.col-md-6.col-12:nth-child(1) > ul.more-info > li:last-child").text().removePrefix("IMDB: ").toRatingInt()
        val actors = document.select("div.col-md-6.col-12:nth-child(2) > ul.more-info").mapNotNull { actor ->
                actor.text().trim().substringAfter(": ")
            }.toList()
        val recommendations = document.select("div.item.col-lg-2.col-md-3.col-sm-4.col-6").mapNotNull {
                val main = it.select("div.item.col-lg-2.col-md-3.col-sm-4.col-6") ?: return@mapNotNull null
                val recUrl = it.select("a").attr("href") ?: return@mapNotNull null
                val recTitle = if (it.select("h4").text().isNotEmpty())
                    it.select("h4").text() else it.select("p.subtitle").text()
                val posterUrl = main.select("img").attr("src")
                MovieSearchResponse(
                    recTitle,
                    recUrl,
                    this.name,
                    TvType.Movie,
                    posterUrl,
                )
        }
        
        return if (tvType == TvType.TvSeries) {
            val docEpisodes = app.get(url).document
            val episodes = docEpisodes.select("ul.list-episodes.row > li").map {
                val href = it.select("ul.list-episodes.row > li").attr("data-url_web")
                val episode = it.select("a").text().trim().removePrefix("Tập ").toIntOrNull()
                val name = "Tập $episode"
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
            Pair("https://xemtv24h.com/statics/fmp4/films10/lordofrings2022ep1/index.m3u8", "247PHIM"),
            Pair("https://so-trym.topphimmoi.org/hlspm/$key", "PMFAST"),
            Pair("https://dash.megacdn.xyz/hlspm/$key", "PMHLS"),
            Pair("https://dash.megacdn.xyz/dast/$key/index.m3u8", "PMBK")
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