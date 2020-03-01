package net.jingles.moosic.service

import org.json.JSONObject
import org.jsoup.Jsoup

private val token: String = System.getenv("genius_token")

data class SearchResult(val title: String, val artist: String, val url: String)

fun search(query: String): List<SearchResult> {

  val hitArray = khttp.get(
    url = "https://api.genius.com/search",
    params = mapOf("q" to query.replace(" ", "%20"), "access_token" to token)
  ).jsonObject.getJSONObject("response").getJSONArray("hits")

  return hitArray.asSequence().map { (it as JSONObject) }
    .filter { it.getString("type") == "song" }
    .map { it.getJSONObject("result") }
    .map {

      val artist = it.getJSONObject("primary_artist").getString("name")
      SearchResult(it.getString("title"), artist, it.getString("url"))

    }.toList()

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

  return if (lyrics.contains(regex)) {

    val headings = regex.findAll(lyrics).map { it.value }
    val sections = regex.split(lyrics, headings.count() + 1).drop(1)
    // The first element is dropped because it is always empty

    headings.mapIndexed { index, heading -> Pair(heading, sections[index]) }.toList()

  } else lyrics.split("\n\n").map { Pair('\u200b'.toString(), it) }

}