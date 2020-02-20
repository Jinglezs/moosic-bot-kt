package net.jingles.moosic.service

import org.json.JSONObject

private val token: String = System.getenv("genius_token")

data class SearchResult(val title: String, val url: String)

suspend fun search(query: String): List<SearchResult> {

  val hitArray = khttp.get(
    url = "https://api.genius.com/search",
    headers = mapOf("Authorization" to token),
    params = mapOf("q" to query)
  ).jsonObject.getJSONObject("response").getJSONArray("hits")

  return hitArray.map { (it as JSONObject).getJSONObject("result") }
    .map { SearchResult(it.getString("full_title"), it.getString("url")) }

}