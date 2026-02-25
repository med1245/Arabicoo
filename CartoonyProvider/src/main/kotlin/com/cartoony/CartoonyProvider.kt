package com.cartoony

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.homePageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.movieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class CartoonyProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://cartoony.net"
    override var name = "Cartoony"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = homePageOf(
        "$mainUrl/" to "Latest"
    )

    private fun Document.pickFirstSelector(vararg selectors: String): String? {
        for (s in selectors) {
            if (this.select(s).isNotEmpty()) return s
        }
        return null
    }

    private fun Element.firstBy(vararg selectors: String): Element? {
        for (s in selectors) {
            val e = this.selectFirst(s)
            if (e != null) return e
        }
        return null
    }

    private fun Element.absSrc(): String? {
        val s = this.attr("abs:src")
        if (s.isNotBlank()) return s
        val d = this.attr("abs:data-src")
        return d.ifBlank { null }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val cardSel = doc.pickFirstSelector("article", ".post", ".grid-item", ".GridItem", ".Block") ?: "article"
        val items = doc.select(cardSel).mapNotNull { el ->
            val titleEl = el.firstBy("h2.entry-title a", ".entry-title a", ".GridTitle a", "a[rel=bookmark]")
            val imgEl = el.firstBy("img.wp-post-image", "img[data-src]", "figure img", "img")
            val title = titleEl?.text()?.trim()
            val href = titleEl?.absUrl("href")
            val poster = imgEl?.absSrc()
            if (title.isNullOrBlank() || href.isNullOrBlank()) return@mapNotNull null
            val type = if (el.text().contains("حلقة") || el.text().contains("مسلسل")) TvType.TvSeries else TvType.Movie
            MovieSearchResponse(title, href, this.name, type, poster)
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        val cardSel = doc.pickFirstSelector("article", ".post", ".grid-item", ".GridItem", ".Block") ?: "article"
        return doc.select(cardSel).mapNotNull { el ->
            val titleEl = el.firstBy("h2.entry-title a", ".entry-title a", ".GridTitle a", "a[rel=bookmark]")
            val imgEl = el.firstBy("img.wp-post-image", "img[data-src]", "figure img", "img")
            val title = titleEl?.text()?.trim()
            val href = titleEl?.absUrl("href")
            val poster = imgEl?.absSrc()
            if (title.isNullOrBlank() || href.isNullOrBlank()) return@mapNotNull null
            val type = if (el.text().contains("حلقة") || el.text().contains("مسلسل")) TvType.TvSeries else TvType.Movie
            MovieSearchResponse(title, href, this.name, type, poster)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: "Cartoony Item"
        val poster = doc.selectFirst("img.wp-post-image")?.absUrl("src")
            ?: doc.selectFirst("figure img")?.absUrl("src")

        val epSelector = doc.pickFirstSelector(
            "ul.episodes li a",
            ".episodes-list a",
            ".EpisodeList a",
            ".ep-box a",
            ".episodes a"
        )

        if (epSelector != null) {
            val episodes = doc.select(epSelector).map { a ->
                val epTitle = a.text().trim().ifBlank { "Episode" }
                val epUrl = a.absUrl("href")
                val epNumber = Regex("""\d+""").find(epTitle)?.value?.toIntOrNull()
                com.lagradost.cloudstream3.Episode(epUrl, epTitle, null, epNumber)
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = doc.selectFirst(".entry-content p")?.text()
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = doc.selectFirst(".entry-content p")?.text()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc0 = app.get(data).document
        val frame1 = doc0.selectFirst("iframe[src]")?.absUrl("src")
        if (!frame1.isNullOrBlank()) {
            val doc1 = app.get(frame1, referer = data).document
            val frame2 = doc1.selectFirst("iframe[src]")?.absUrl("src") ?: frame1
            loadExtractor(frame2, data, subtitleCallback, callback)
            return true
        }
        doc0.select("video source[src]").forEach { s ->
            val src = s.absUrl("src")
            if (src.isNotBlank()) {
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        "Cartoony",
                        src,
                        data,
                        Qualities.P720.value
                    )
                )
            }
        }
        return true
    }
}
