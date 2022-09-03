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

class JenkaProvider : MainAPI() {
    override var mainUrl = "https://www.jenkastudiovn.net"
    override var name = "JenkaStudioVN"
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
        "$mainUrl/search/label/phim-hot?&max-results=48" to "Phim Hot",
        "$mainUrl/search/label/phim-le?&max-results=48" to "Phim Lẻ",
        "$mainUrl/search/label/ghibli-collection?&max-results=48" to "Phim Ghibli",
    )
    
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document ?: return@mapNotNull null
    }
    
     private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")
}
