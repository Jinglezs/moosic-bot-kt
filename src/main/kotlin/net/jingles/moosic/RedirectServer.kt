package net.jingles.moosic

import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.SpotifyUserAuthorizationBuilder
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.BlockingHandler
import net.jingles.moosic.service.Spotify
import net.jingles.moosic.service.addSpotifyClient
import kotlin.coroutines.suspendCoroutine

class RedirectServer(port: Int) {

  val server: Undertow = Undertow.builder()
    .addHttpListener(port, "0.0.0.0")
    .setHandler(BlockingHandler(SpotifyResponseHandler()))
    .build()

}

class SpotifyResponseHandler : HttpHandler {

  override fun handleRequest(exchange: HttpServerExchange?) {

    if (exchange == null) return
    exchange.startBlocking()

    if (!exchange.queryParameters.containsKey("code") || !exchange.queryParameters.containsKey("state")) return

    val code = exchange.queryParameters["code"]!!.first
    val discordId = exchange.queryParameters["state"]!!.first.toLong()

    val response = try {

      val authorization = SpotifyUserAuthorizationBuilder(code).build()
      val clientAPI = SpotifyClientApiBuilder(credentials = credentials, authorization = authorization).build()

      addSpotifyClient(discordId, Spotify(clientAPI))
      "Successfully authenticated MoosicBot for Spotify interactions! YUSSSSS!"

    } catch (e: Exception) {
      e.printStackTrace()
      "An exception occurred while attempting authentication: ${e.javaClass.simpleName} - ${e.message}"
    }

    exchange.responseSender.send(response)
    exchange.endExchange()

  }

}