package net.jingles.moosic.service

import com.adamratzman.spotify.SpotifyClientAPI
import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.SpotifyUserAuthorizationBuilder
import com.adamratzman.spotify.getCredentialedToken
import net.jingles.moosic.credentials
import net.jingles.moosic.spotify
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

private val spotifyClients: MutableMap<Long, Spotify> = mutableMapOf()

suspend fun getSpotifyClient(id: Long): Spotify? {

  if (spotifyClients.containsKey(id)) return spotifyClients[id]!!

  val spotify: Spotify? = newSuspendedTransaction {

    UserInfo.select { UserInfo.id eq id }.withDistinct()
      .limit(1)
      .map {

        val token = getCredentialedToken(spotify.clientId, spotify.clientSecret, spotify)
        val authorization = SpotifyUserAuthorizationBuilder(token = token).build()
        val clientAPI = SpotifyClientApiBuilder(credentials, authorization).build()

        Spotify(clientAPI)

      }.firstOrNull()

  }

  if (spotify != null) spotifyClients[id] = spotify
  return spotify

}

fun addSpotifyClient(id: Long, spotify: Spotify) {

  spotifyClients[id] = spotify;

  val refreshToken = spotify.clientAPI.token.refreshToken!!

  transaction {
    UserInfo.insert {
      it[this.id] = id
      it[this.refreshToken] = refreshToken
    }
  }

}

data class Spotify(val clientAPI: SpotifyClientAPI)

object UserInfo : Table() {
  val id = long("discord_id")
  val refreshToken = varchar("refresh_token", 500)
}