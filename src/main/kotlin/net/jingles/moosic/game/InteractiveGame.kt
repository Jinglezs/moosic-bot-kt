package net.jingles.moosic.game

import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.jingles.moosic.service.SpotifyClient
import net.jingles.moosic.service.getSpotifyClient
import kotlin.time.ClockMark
import kotlin.time.ExperimentalTime

@ExperimentalTime
abstract class InteractiveGame(
  protected val channel: MessageChannel,
  owner: SpotifyClient
) {

  // State variables
  protected var started = false
  protected lateinit var clockMark: ClockMark

  // Game information
  protected val players = mutableSetOf<SpotifyClient>()
  private val commands = mutableSetOf<GameCommand>()

  // Game commands

  init {
    players.add(owner)
  }

  open fun registerGameCommands() {

    registerGameCommand(">start") { _, _ -> start().let { true }}

    registerGameCommand(">join") { event, client ->
      players.add(client)
      channel.sendMessage("${event.author.name} has joined the game!").queue()
      true
    }

    registerGameCommand(">stop") { _, _ ->
      endGame(); true
    }

    channel.jda.addEventListener(InputListener(this))

  }

  abstract fun start();

  abstract fun endGame();

  abstract fun onPlayerInput(event: MessageReceivedEvent, client: SpotifyClient);

  fun registerGameCommand(trigger: String, executor: (MessageReceivedEvent, SpotifyClient) -> Boolean) =
    commands.add(GameCommand(trigger, executor))

  fun unregisterGameCommand(trigger: String) = commands.removeIf { it.trigger.equals(trigger, true) }

  private class InputListener(private val game: InteractiveGame) {

    @SubscribeEvent
    fun onMessageReceived(event: MessageReceivedEvent) {

      // Ignore messages from other channels, bots, or non-players
      if (event.channel != game.channel || event.message.author.isBot) return
      if (game.started && game.players.none { it.discordId == event.author.idLong }) return

      val client: SpotifyClient = game.players.firstOrNull { it.discordId == event.author.idLong }
        ?: runBlocking { getSpotifyClient(event.author.idLong) } ?: return

      val result = game.commands.firstOrNull { it.trigger.equals(event.message.contentStripped, true) }
        ?.executor?.invoke(event, client) ?: true

      if (game.started && result) game.onPlayerInput(event, client)

    }

  }

}

private data class GameCommand(val trigger: String, val executor: (MessageReceivedEvent, SpotifyClient) -> Boolean)

data class Score(val accuracy: Double, val time: Double, val guessed: Boolean)