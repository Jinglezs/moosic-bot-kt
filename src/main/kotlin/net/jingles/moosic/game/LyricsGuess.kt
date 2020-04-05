package net.jingles.moosic.game

import com.adamratzman.spotify.models.Playlist
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
private val WHITESPACE = Regex("[\\p{Zs}]+")

@ExperimentalTime
class LyricsGuess(
  channel: MessageChannel,
  owner: SpotifyClient,
  private val rounds: Int,
  private val playlist: Playlist?
) : InteractiveGame(channel, owner) {

  private val lyricPrompts = populateLyricPrompts(owner)
  private val currentPrompt get() = lyricPrompts.peek()

  private val scores = mutableMapOf<SpotifyClient, MutableList<Score>>()

  private val builder = EmbedBuilder()
    .setColor(Color.WHITE)
    .setFooter("Powered by Genius", GENIUS_ICON)

  init {
    channel.sendMessage("A game of Lyric Guess has been created. Send >join to play >:V").queue()
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
        scores[it.key]!!.add(Score(0.0, 20.0, false))
      }

    // Proceed to the next track
    if (remove) {
      channel.sendMessage("The correct response was \"${currentPrompt.uneditedAnswer}\"").queue()
      lyricPrompts.removeFirst()
    }

    // End the game when all of the tracks are gone
    if (lyricPrompts.isEmpty()) {
      if (!started) endGame(); return
    }

    // Marks the time this round began
    clockMark = MonoClock.markNow()

    with(currentPrompt) {
      builder.setTitle("Round ${getRoundNumber()} - $info")
      builder.setDescription(prompt)
    }

    channel.sendMessage(builder.build()).queue()

    // Start the next round after 10 seconds
    GlobalScope.launch {
      delay(20_000)
      nextRound()
    }

  }

  override fun endGame() {

    started = false
    lyricPrompts.clear()

    val scoreboard = scores.mapValues { entry ->
      entry.value.sumBy {
        var points = if (it.guessed) 100 else 0 // 100 points for guessing
        points += (20 - it.time).toInt() * 10   // 10 points per second before 15 seconds
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

  private fun getRoundNumber() = (rounds - lyricPrompts.size) + 1

  /**
   * Determines whether or a player's guess is correct
   * Returns a pair containing a boolean, where "true"
   * means correct, and a Long that contains the true
   * time it took for the player to guess.
   */
  private fun verifyGuess(player: SpotifyClient, guess: String): Score? {

    val strippedGuess = guess.toLowerCase().filter { it.isLetterOrDigit() }
    val strippedAnswer = currentPrompt?.strippedAnswer ?: return null

    val accuracy = strippedAnswer.percentMatch(strippedGuess)
    if (accuracy < SUCCESS_LIMIT) return null

    val score = Score(accuracy, clockMark.elapsedNow().inSeconds, true)

    if (!scores.containsKey(player)) scores[player] = mutableListOf()
    scores[player]!!.add(score)

    return score

  }

  private fun populateLyricPrompts(client: SpotifyClient): LinkedList<LyricPrompt> {

    val prompts = LinkedList<LyricPrompt>()

    while (prompts.size < rounds) {

      val track = if (playlist == null) getRandomPlaylistTracks(client, 1).first()
      else playlist.tracks.random().track!!

      val url = search(track.toSearchQuery()).firstOrNull() {
        track.artists.any { artist -> artist.name.equals(it.artist, true) }
          && track.name.equals(it.title, true)
      }?.url ?: continue

      val verse = getLyrics(url).random().second.split("\n")

      // The line we want the user to respond with
      val answer = verse.filter { it.isNotBlank() }.random()
      // Replace the letters/numbers with blanks
      val blanks = answer.replace(ALPHANUMERIC, "_")
      // Join the lines into a single prompt String
      val msg = "```${verse.joinToString("\n")
        .replace(WHITESPACE, " ")
        .replace(answer, blanks).trim()}```"

      prompts.add(LyricPrompt(track.toSimpleTrackInfo(), msg, answer,
        answer.toLowerCase().filter { c -> c.isLetterOrDigit() }))

    }

    return prompts

  }

}

private data class LyricPrompt(
  val info: String,
  val prompt: String,
  val uneditedAnswer: String,
  val strippedAnswer: String
)