package net.jingles.moosic.service

import com.adamratzman.spotify.SpotifyClientAPI
import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.SpotifyUserAuthorizationBuilder
import com.adamratzman.spotify.getCredentialedToken
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.jingles.moosic.credentials
import net.jingles.moosic.spotify
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

private val spotifyClients: MutableMap<Long, Spotify> = mutableMapOf()

object UserDatabase {

  private val database = {

    val jdbUri = URI(System.getenv("JAWSDB_URL"))
    val port = jdbUri.port.toString()
    val url = "jdbc:mysql://${jdbUri.host}:$port${jdbUri.path}"

    val username = jdbUri.userInfo.split(":").toTypedArray()[0]
    val password = jdbUri.userInfo.split(":").toTypedArray()[1]

    Database.Companion.connect(url, driver = "com.mysql.jdbc.Driver", user = username, password = password)

  }

  init {

    transaction {
      SchemaUtils.create(UserInfo)
    }

  }

}

fun getSpotifyClient(id: Long): Spotify? {

  if (spotifyClients.containsKey(id)) return spotifyClients[id]!!

  val spotify: Spotify? = transaction {

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

  GlobalScope.async {

    suspendedTransactionAsync {
      UserInfo.insert { it[this.refreshToken] = refreshToken }
    }

  }

}

data class Spotify(val clientAPI: SpotifyClientAPI)

object UserInfo : Table() {
  val id = long("discord_id")
  val refreshToken = varchar("refresh_token", 500)
}