package com.lagradost

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
//import com.lagradost.cloudstream3.animeproviders.ZoroProvider
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.delay
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.util.*
import kotlin.system.measureTimeMillis

open class JenkaProvider : MainAPI() {
    override var mainUrl = "https://www.jenkastudiovn.net"
    override var name = "JenkaStudioVN"
    
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )
    
    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val html = app.get("$mainUrl").text
        val document = Jsoup.parse(html)

        val all = ArrayList<HomePageList>()

        val map = mapOf(
            "Phim lẻ" to "div#LinkList1_content_0.tab-content",
            "Phim bộ" to "div#LinkList1_content_1.tab-content",            
        )
        map.forEach {
            all.add(HomePageList(
                it.key,
                document.select(it.value).select("div.phimitem").map { element ->
                    element.toSearchResult()
                }
            ))
        }

        document.select("div.owl-stage").forEach {
            val title = it.select("h3.lable-home").text().trim()
            val elements = it.select("div.phimitem").map { element ->
                element.toSearchResult()
            }
            all.add(HomePageList(title, elements))
        }

        return HomePageResponse(all)
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=${query.replace(" ", "-")}"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("div.phimitem").map {
            val title = it.select("h3.lable-home").text()
            val href = fixUrl(it.select("a.lable-about").attr("href"))
            val image = it.select("img").attr("data-src")

            val metaInfo = it.select("div.main-movie-content > span.status")
            // val rating = metaInfo[0].text()
            val quality = getQualityFromString(metaInfo.getOrNull(1)?.text())

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    year,
                    quality = quality
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    year,
                    quality = quality
                )
            }
        }
    }
}
