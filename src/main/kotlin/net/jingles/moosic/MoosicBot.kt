package net.jingles.moosic

import com.adamratzman.spotify.SpotifyAppAPI
import com.adamratzman.spotify.SpotifyAppApiBuilder
import com.adamratzman.spotify.SpotifyCredentials
import net.dv8tion.jda.api.AccountType
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.jingles.moosic.command.CommandManager
import javax.security.auth.login.LoginException
import kotlin.system.exitProcess


const val SPOTIFY_ICON = "https://developer.spotify.com/assets/branding-guidelines/icon2@2x.png"

lateinit var credentials: SpotifyCredentials
lateinit var spotify: SpotifyAppAPI

fun main(args: Array<String>) {
  connect(System.getenv("discord_token"), args[0].toInt())
}

fun connect(token: String, port: Int) {
  try {

    createSpotifyAPI()
    RedirectServer(port)

    JDABuilder(AccountType.BOT)
      .setEventManager(AnnotatedEventManager())
      .addEventListeners(CommandManager())
      .setBulkDeleteSplittingEnabled(false)
      .setToken(token)
      .build()

  } catch (ex: LoginException) {
    System.err.println(ex.message)
    exitProcess(ExitStatus.INVALID_TOKEN.code)
  }
}

private fun createSpotifyAPI() {

  val token = System.getenv("spotify_token")
  val secret = System.getenv("spotify_secret")

  credentials = SpotifyCredentials(token, secret, "http://moosic-bot-kt.herokuapp.com")
  spotify = SpotifyAppApiBuilder(credentials).buildPublic() as SpotifyAppAPI

}

enum class ExitStatus(val code: Int) {
  // Non error
  UPDATE(10),
  SHUTDOWN(11),
  RESTART(12),
  NEW_CONFIG(13),

  // Error
  INVALID_TOKEN(20),
  CONFIG_MISSING(21),
  INSUFFICIENT_ARGS(22),

  // SQL
  SQL_ACCESS_DENIED(30),
  SQL_INVALID_PASSWORD(31),
  SQL_UNKNOWN_HOST(32),
  SQL_UNKNOWN_DATABASE(33)
}