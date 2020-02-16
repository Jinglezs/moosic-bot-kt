package net.jingles.moosic

import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.SpotifyUserAuthorizationBuilder
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.BlockingHandler
import net.jingles.moosic.service.SpotifyClient
import net.jingles.moosic.service.addSpotifyClient

class RedirectServer(port: Int) {

  private val server: Undertow = Undertow.builder()
    .addHttpListener(port, "0.0.0.0")
    .setHandler(BlockingHandler(SpotifyResponseHandler()))
    .build()

  init {
    server.start()
  }

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

      addSpotifyClient(discordId, SpotifyClient(clientAPI, discordId))
      "Successfully authenticated MoosicBot for Spotify interactions! YUSSSSS!"

    } catch (e: Exception) {
      e.printStackTrace()
      "An exception occurred while attempting authentication: ${e.javaClass.simpleName} - ${e.message}"
    }

    exchange.responseSender.send(response)
    exchange.endExchange()

  }

}