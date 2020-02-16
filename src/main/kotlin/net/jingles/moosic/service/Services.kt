package net.jingles.moosic.service

import com.adamratzman.spotify.SpotifyClientAPI
import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.SpotifyScope
import com.adamratzman.spotify.SpotifyUserAuthorizationBuilder
import com.adamratzman.spotify.models.Token
import net.jingles.moosic.credentials
import net.jingles.moosic.spotify
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

internal val SCOPES = arrayOf(
  SpotifyScope.PLAYLIST_READ_PRIVATE,
  SpotifyScope.PLAYLIST_READ_COLLABORATIVE,
  SpotifyScope.STREAMING,
  SpotifyScope.USER_FOLLOW_READ,
  SpotifyScope.USER_LIBRARY_READ,
  SpotifyScope.USER_MODIFY_PLAYBACK_STATE,
  SpotifyScope.USER_READ_PRIVATE,
  SpotifyScope.USER_TOP_READ,
  SpotifyScope.USER_READ_PLAYBACK_STATE,
  SpotifyScope.USER_READ_CURRENTLY_PLAYING,
  SpotifyScope.USER_READ_RECENTLY_PLAYED
)

private val spotifyClients: MutableMap<Long, SpotifyClient> = mutableMapOf()

suspend fun getSpotifyClient(id: Long): SpotifyClient? {

  if (spotifyClients.containsKey(id)) return spotifyClients[id]!!

  val spotify: SpotifyClient? = newSuspendedTransaction {

    val refreshToken = UserInfo.select { UserInfo.id eq id }
      .limit(1)
      .withDistinct()
      .map { it[UserInfo.refreshToken] }
      .firstOrNull() ?: return@newSuspendedTransaction null

    val authorization = SpotifyUserAuthorizationBuilder(token = getAccessToken(refreshToken)).build()
    val clientAPI = SpotifyClientApiBuilder(credentials, authorization).build()

    SpotifyClient(clientAPI, id)

  }

  if (spotify != null) spotifyClients[id] = spotify
  return spotify

}

private fun getAccessToken(refreshToken: String): Token {

  val encoded = String(Base64.getEncoder().encode("${spotify.clientId}:${spotify.clientSecret}".toByteArray()))

  val response = khttp.post(
    url = "https://accounts.spotify.com/api/token",
    headers = mapOf("Authorization" to "Basic $encoded"),
    data = mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken)
  )

  val obj = response.jsonObject

  return Token.from(obj.getString("access_token"), refreshToken,
    SCOPES.toList(), obj.getInt("expires_in"))

}

fun addSpotifyClient(id: Long, spotify: SpotifyClient) {

  spotifyClients[id] = spotify

  val refreshToken = spotify.clientAPI.token.refreshToken!!

  transaction {
    UserInfo.insert {
      it[this.id] = id
      it[this.refreshToken] = refreshToken
    }
  }

}

fun removeSpotifyClient(id: Long) {

  spotifyClients.remove(id)

  transaction {
    UserInfo.deleteWhere { UserInfo.id eq id }
  }

}

data class SpotifyClient(val clientAPI: SpotifyClientAPI, val discordId: Long)

object UserInfo : Table() {
  val id = long("discord_id")
  val refreshToken = varchar("refresh_token", 500)
}