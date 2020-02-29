package net.jingles.moosic.game

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.jingles.moosic.*
import net.jingles.moosic.service.SpotifyClient
import net.jingles.moosic.service.getLyrics
import net.jingles.moosic.service.search
import java.awt.Color
import java.time.Instant
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.MonoClock

private const val SUCCESS_LIMIT = 0.5
private val ALPHANUMERIC = Regex("\\w")

@ExperimentalTime
class LyricsGuess(
  channel: MessageChannel,
  owner: SpotifyClient,
  private val rounds: Int
) : InteractiveGame(channel, owner) {

  private val lyricPrompts = populateLyricPrompts(owner)
  private val currentPrompt get() = lyricPrompts.peek()
  private lateinit var strippedResponse: String

  private val scores = mutableMapOf<SpotifyClient, MutableList<Score>>()

  private val builder = EmbedBuilder()
    .setTitle("Lyric Guess Prompt")
    .setColor(Color.WHITE)
    .setFooter("Powered by Genius", GENIUS_ICON)

  init {
    channel.sendMessage("A game of Song Guess has been created. Send >join to play >:V").queue()
    registerGameCommands()
  }

  override fun start() {
    started = true
    channel.sendMessage("The game has started. Complete the missing line from the verse!").queue()
    unregisterGameCommand(">start"); unregisterGameCommand(">join")
    nextRound(false)
  }

  private fun nextRound(remove: Boolean = true) {

    // Add the worst possible score for those who did not earn one
    scores.filterValues { it.size < getRoundNumber() }
      .forEach {
        scores[it.key]!!.add(Score(0.0, 15.0, false))
      }

    channel.sendMessage("The correct response was \"${currentPrompt.second}\"").queue()

    // Proceed to the next track
    if (remove) lyricPrompts.removeFirst()

    // End the game when all of the tracks are gone
    if (lyricPrompts.isEmpty()) {
      endGame(); return
    }

    strippedResponse = currentPrompt.second.filter { it.isLetterOrDigit() }.trim()

    // Marks the time this round began
    clockMark = MonoClock.markNow()

    builder.setDescription(currentPrompt.first)
    channel.sendMessage(builder.build()).queue()

    // Start the next round after 10 seconds
    GlobalScope.launch {
      delay(15_000)
      nextRound()
    }

  }

  override fun endGame() {

    val scoreboard = scores.mapValues { entry ->
      entry.value.sumBy {
        var points = if (it.guessed) 100 else 0 // 100 points for guessing
        points += (15 - it.time).toInt() * 10   // 10 points per second before 15 seconds
        points *= (1 + it.accuracy).toInt()     // Increase by the accuracy of the guess
        points
      }
    }.map { Pair(channel.jda.getUserById(it.key.discordId)?.name, it.value) }
      .sortedByDescending { it.second }.toNumbered { "$first: $second" }

    // Send an embedded message containing the results of the game
    val embed = EmbedBuilder()
      .setTitle("Lyric Guess Results")
      .setDescription(scoreboard)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .build()

    channel.sendMessage(embed).queue()
    channel.jda.removeEventListener(this)

  }

  override fun onPlayerInput(event: MessageReceivedEvent, client: SpotifyClient) {

    val guess = event.message.contentStripped.filter { it.isLetterOrDigit() || it.isWhitespace() }

    // Do not verify guesses after the player has a score for the current round
    if (scores[client]?.size == getRoundNumber()) return

    // Gets a decimal that reflects the accuracy
    val score = verifyGuess(client, guess.toLowerCase().trim()) ?: return

    // Delete the message so other players can't copy it
    if (channel.type == ChannelType.TEXT) event.message.delete().reason("Lyric Guess").queue()

    channel.sendMessage(
      "${event.author.name} guessed the lyrics with ${score.accuracy.toPercent()} " +
        "accuracy in ${score.time.format(3)} seconds"
    ).queue()

  }

  private fun populateLyricPrompts(owner: SpotifyClient): LinkedList<Pair<String, String>> {

    val tracks = owner.getRandomPlaylistTracks(rounds)
    val pairs = LinkedList<Pair<String, String>>()

    tracks.mapNotNull {

      val info = it.toSimpleTrackInfo()
      search(info).maxBy { result -> info.percentMatch(result.title) }?.url

    }.mapTo(pairs) { url ->

      val lines = getLyrics(url).random().second.split("\n").toList()

      // The line we want the user to respond with
      val answer = lines.filterNot { it.isBlank() }.random()

      // Replace the letters/numbers with blanks
      val blanks = answer.replace(ALPHANUMERIC, "_")

      // Join the lines into a single prompt String
      val prompt = lines.joinToString("\n").replace(answer, blanks)

      Pair(prompt, answer.filter { it.isLetterOrDigit() })

    }

    return pairs

  }

  private fun getRoundNumber() = (rounds - lyricPrompts.size) + 1

  /**
   * Determines whether or a player's guess is correct
   * Returns a pair containing a boolean, where "true"
   * means correct, and a Long that contains the true
   * time it took for the player to guess.
   */
  private fun verifyGuess(player: SpotifyClient, guess: String): Score? {

    val strippedGuess = guess.filter { it.isLetterOrDigit() }
    val accuracy = strippedResponse.toLowerCase().percentMatch(strippedGuess)
    if (accuracy < SUCCESS_LIMIT) return null

    val score = Score(accuracy, clockMark.elapsedNow().inSeconds, true)

    if (!scores.containsKey(player)) scores[player] = mutableListOf()
    scores[player]!!.add(score)

    return score

  }


}