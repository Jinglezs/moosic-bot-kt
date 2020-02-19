package net.jingles.moosic

import com.adamratzman.spotify.SpotifyAppApi
import com.adamratzman.spotify.SpotifyAppApiBuilder
import com.adamratzman.spotify.SpotifyCredentials
import net.dv8tion.jda.api.AccountType
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.jingles.moosic.command.CommandManager
import net.jingles.moosic.service.UserInfo
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.util.*
import javax.security.auth.login.LoginException
import kotlin.concurrent.timerTask


const val SPOTIFY_ICON = "https://developer.spotify.com/assets/branding-guidelines/icon2@2x.png"

lateinit var credentials: SpotifyCredentials
lateinit var spotify: SpotifyAppApi

open class MoosicBot {

  companion object {

    @JvmStatic fun main(args: Array<String>) {
      connect(System.getenv("discord_token"), args[0].toInt())
    }

    private fun connect(token: String, port: Int) {
      try {

        connectDatabase()
        createSpotifyAPI()
        RedirectServer(port)

        JDABuilder(AccountType.BOT)
          .setEventManager(AnnotatedEventManager())
          .addEventListeners(CommandManager)
          .setBulkDeleteSplittingEnabled(false)
          .setToken(token)
          .build()

      } catch (ex: LoginException) {
        System.err.println(ex.message)
      }
    }

    private fun createSpotifyAPI() {

      val token = System.getenv("spotify_client_id")
      val secret = System.getenv("spotify_client_secret")

      credentials = SpotifyCredentials(token, secret, "http://moosic-bot-kt.herokuapp.com")

      // Refresh the token every 55 minutes
      Timer().schedule(timerTask {
        spotify = SpotifyAppApiBuilder(credentials).build()
      }, 0, 33e5.toLong())

    }

    private fun connectDatabase() {

      val jdbUri = URI(System.getenv("JAWSDB_URL"))
      val port = jdbUri.port.toString()
      val url = "jdbc:mysql://${jdbUri.host}:$port${jdbUri.path}"

      val username = jdbUri.userInfo.split(":").toTypedArray()[0]
      val password = jdbUri.userInfo.split(":").toTypedArray()[1]

      Database.connect(url, driver = "com.mysql.jdbc.Driver", user = username, password = password)

      transaction {
        SchemaUtils.create(UserInfo)
      }

    }

  }

}