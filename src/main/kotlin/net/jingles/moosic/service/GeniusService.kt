package net.jingles.moosic.service

import org.json.JSONObject
import org.jsoup.Jsoup

private val token: String = System.getenv("genius_token")

data class SearchResult(val title: String, val url: String)

fun search(query: String): List<SearchResult> {

  val hitArray = khttp.get(
    url = "https://api.genius.com/search",
    params = mapOf("q" to query, "access_token" to token)
  ).jsonObject.getJSONObject("response").getJSONArray("hits")

  return hitArray.map { (it as JSONObject).getJSONObject("result") }
    .map { SearchResult(it.getString("full_title"), it.getString("url")) }

}

/**
 * Returns the lyrics as a Pair in which the first String is the header/title
 * of the section and the second String is the actual content of the section
 */
fun getLyrics(url: String): List<Pair<String, String>> {

  val lyrics = Jsoup.connect(url)
    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:71.0) Gecko/20100101 Firefox/71.0")
    .get().getElementsByClass("lyrics").first().wholeText()

  val regex = Regex("(\\[.*?])")

  return if (lyrics.contains(regex))

    regex.findAll(lyrics).map {
      Pair(it.value, lyrics.substring(it.range.first, it.range.last))
    }.toList()

  else lyrics.split("\n\n").map { Pair('\u200b'.toString(), it) }

}