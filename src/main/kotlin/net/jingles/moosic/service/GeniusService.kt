package net.jingles.moosic.service

private val token: String = System.getenv("genius_token")

suspend fun getSongUrl(query: String) {

  val hitArray = khttp.get(
    url = "https://api.genius.com/search",
    headers = mapOf("Authorization" to token),
    params = mapOf("q" to query)
  ).jsonObject.getJSONObject("response").getJSONArray("hits")

  TODO("Find the hit that most closely matches the query and get its id/api path")

}