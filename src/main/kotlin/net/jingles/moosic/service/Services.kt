package net.jingles.moosic.service

import com.adamratzman.spotify.SpotifyClientAPI
import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.SpotifyScope
import com.adamratzman.spotify.SpotifyUserAuthorizationBuilder
import com.adamratzman.spotify.models.Token
import net.jingles.moosic.command.CommandException
import net.jingles.moosic.credentials
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

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

private val spotifyClients: MutableMap<Long, Spotify> = mutableMapOf()

suspend fun getSpotifyClient(id: Long): Spotify? {

  if (spotifyClients.containsKey(id)) return spotifyClients[id]!!

  val spotify: Spotify? = newSuspendedTransaction {

    val refreshToken = UserInfo.select { UserInfo.id eq id }
      .limit(1)
      .withDistinct()
      .map { it[UserInfo.refreshToken] }
      .firstOrNull() ?: throw CommandException("Could not retrieve Spotify information")

    val token = Token(
      accessToken = "temp",
      refreshToken = refreshToken,
      tokenType = "Bearer",
      expiresIn = -1,
      scopes = SCOPES.toList()
    )

    val authorization = SpotifyUserAuthorizationBuilder(token = token).build()
    val clientAPI = SpotifyClientApiBuilder(credentials, authorization).build()

    Spotify(clientAPI)

  }

  if (spotify != null) spotifyClients[id] = spotify
  return spotify

}

fun addSpotifyClient(id: Long, spotify: Spotify) {

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

data class Spotify(val clientAPI: SpotifyClientAPI)

object UserInfo : Table() {
  val id = long("discord_id")
  val refreshToken = varchar("refresh_token", 500)
}