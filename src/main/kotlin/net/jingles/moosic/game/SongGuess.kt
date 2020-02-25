package net.jingles.moosic.game

import com.adamratzman.spotify.SpotifyException
import com.adamratzman.spotify.models.SimplePlaylist
import com.adamratzman.spotify.models.Track
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.jingles.moosic.*
import net.jingles.moosic.service.SpotifyClient
import net.jingles.moosic.service.getSpotifyClient
import java.awt.Color
import java.time.Instant
import java.util.*
import kotlin.time.ClockMark
import kotlin.time.ExperimentalTime
import kotlin.time.MonoClock

/**
 * The proportion of a player's guess that must match the song
 * title in order for it to be considered correct.
 */
const val SUCCESS_LIMIT = 0.70

@ExperimentalTime
class SongGuess(
  private val channel: MessageChannel, owner: SpotifyClient,
  private val type: String, private val rounds: Int
) {

  // State variables
  private var started = false
  private lateinit var clockMark: ClockMark

  // Game information
  private val players = mutableSetOf<SpotifyClient>()
  private val scores = mutableMapOf<SpotifyClient, MutableList<Score>>()
  private val tracks = populateTracks(owner, rounds)
  private val currentTrack get() = tracks.peek()
  private lateinit var editedName: String

  init {
    players.add(owner)
    channel.jda.addEventListener(this)
    channel.sendMessage("A game of Song Guess has been created. Send >join to play >:V").queue()
  }

  private fun getRoundNumber() = (rounds - tracks.size) + 1

  private suspend fun nextRound() {

    if (started) {

      // Add the worst possible score for those who did not earn one
      scores.filterValues { it.size < getRoundNumber() }
        .forEach {
          scores[it.key]!!.add(Score(0.0, 10.0, false))
        }

      channel.sendMessage("The correct $type was $editedName").queue()

      // Proceed to the next track
      tracks.removeFirst()

    } else {

      started = true
      channel.sendMessage("The game has started! You have 15 seconds to guess the __${type}__ of each song >:V").queue()

    }

    // End the game when all of the tracks are gone
    if (tracks.isEmpty()) {
      endGame(); return
    }

    // Removes any extra information from the title, which is usually in parentheses
    // or following a hyphen. Ex: Never Be Alone - MTV Unplugged -> Never Be Alone

    editedName = when (type) {

      "title" -> currentTrack.name.substringBeforeLast("-")
        .substringBeforeLast("(").trim()

      else -> currentTrack.artists.toNames()

    }.trim()

    // Spotify only accepts lists of track IDs/URIs to play
    val tracksToPlay = listOf(currentTrack.id)

    // Determines a random position in the track to begin playing at
    val maxDuration = (currentTrack.durationMs * 0.80).toInt()
    val seekPosition = (0..maxDuration).random()

    try {

      players.forEach {

        with(it.clientAPI.player) {
          startPlayback(tracksToPlay = tracksToPlay).suspendQueue()// Play the track
          seek(seekPosition.toLong()).suspendQueue() // Skip to a random position
        }

      }

    } catch (e: SpotifyException) {
      channel.sendMessage("Error continuing to the next round: ${e.message}").queue()
      nextRound(); return
    }

    // Marks the time this round began
    clockMark = MonoClock.markNow()

    // Start the next round after 10 seconds
    GlobalScope.launch {
      delay(15_000)
      nextRound()
    }

  }

  private fun endGame() {

    val scoreboard = scores.mapValues { entry ->
      entry.value.sumBy {
        var points = if (it.guessed) 100 else 0 // 100 points for guessing
        points += (15 - it.time).toInt() * 10   // 10 points per second before 15 seconds
        points *= (1 + it.accuracy).toInt()     // Increase by the accuracy of the guess
        points
      }
    }.map { Pair(channel.jda.getUserById(it.key.discordId)?.name, it.value) }
      .sortedByDescending { it.second }.toNumbered { "$first: $second" }

    // Average the score times and select the entry with the smallest value
    val fastestTime = scores.mapValues { it.value.sumByDouble { score -> score.time } / it.value.size }
      .minBy { entry -> entry.value }

    // Average the accuracies and select the entry with the highest value
    val mostAccurate = scores.mapValues { it.value.sumByDouble { score -> score.accuracy } / it.value.size }
      .maxBy { entry -> entry.value }

    val mostGuessed = scores.mapValues { it.value.sumBy { score -> if (score.guessed) 1 else 0 } }
      .maxBy { entry -> entry.value }

    // Pair the usernames with the formatted results
    val time = Pair(
      fastestTime?.key?.discordId?.let { channel.jda.getUserById(it)?.name } ?: "N/A",
      fastestTime?.value.let { "${it?.format(3) ?: "0.0"} seconds" }
    )

    val accuracy = Pair(
      mostAccurate?.key?.discordId?.let { channel.jda.getUserById(it)?.name } ?: "N/A",
      mostAccurate?.value.let { it?.toPercent() ?: "0%" }
    )

    val guessed = Pair(
      mostGuessed?.key?.discordId?.let { channel.jda.getUserById(it)?.name } ?: "N/A",
      mostGuessed?.value.let { "${it ?: "0"} correct guesses" }
    )

    // Send an embedded message containing the results of the game
    val embed = EmbedBuilder()
      .setTitle("Song Guess Results")
      .addField("Fastest Average Time", "${time.first}: ${time.second}", true)
      .addField("Highest Average Accuracy", "${accuracy.first}: ${accuracy.second}", true)
      .addField("Most Correct Guesses", "${guessed.first}: ${guessed.second}", true)
      .setDescription(scoreboard)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .build()

    channel.sendMessage(embed).queue()
    channel.jda.removeEventListener(this)

  }

  /**
   * Determines whether or a player's guess is correct
   * Returns a pair containing a boolean, where "true"
   * means correct, and a Long that contains the true
   * time it took for the player to guess.
   */
  private fun verifyGuess(player: SpotifyClient, guess: String): Score? {

    val accuracy = editedName.toLowerCase().percentMatch(guess)
    if (accuracy < SUCCESS_LIMIT) return null

    val score = Score(accuracy, clockMark.elapsedNow().inSeconds, true)

    if (!scores.containsKey(player)) scores[player] = mutableListOf()
    scores[player]!!.add(score)

    return score

  }

  private fun populateTracks(spotify: SpotifyClient, rounds: Int): LinkedList<Track> {

    val populatedList = LinkedList<Track>()

    val playlists = spotify.clientAPI.playlists.getClientPlaylists().complete().items
    val erroneousPlaylists = mutableListOf<SimplePlaylist>()

    while (populatedList.size < rounds) {

      val simple = playlists.random()
      if (erroneousPlaylists.contains(simple)) continue

      try {

        val full = simple.toFullPlaylist().complete() ?: continue

        val track = full.tracks.random().track
        if (track != null) populatedList.add(track)

      } catch (exception: Exception) {
        erroneousPlaylists.add(simple)
        println("Error parsing playlist: Local track files are currently unsupported.")
        continue
      }

    }

    return populatedList

  }

  @SubscribeEvent
  fun onGuess(event: MessageReceivedEvent) {

    // Ignore messages from other channels or bots
    if (event.channel != channel || event.message.author.isBot) return

    // Strip the message of formatted to facilitate comparisons
    val guess = event.message.contentStripped

    // Allow players to join/start the game
    if (!started) {

      GlobalScope.launch {

        when (guess.toLowerCase()) {
          ">start" -> nextRound()
          ">join" -> {

            val client = getSpotifyClient(event.author.idLong) ?: return@launch
            players.add(client)
            channel.sendMessage("${event.author.name} has joined the game!").queue()

          }
        }

      }

      return
    }

    val spotify = players.find { it.discordId == event.author.idLong }!!

    // Do not verify guesses after the player has a score for the current round
    if (scores[spotify]?.size == getRoundNumber()) return

    // Gets a decimal that reflects the accuracy
    val score = verifyGuess(spotify, event.message.contentStripped.toLowerCase().trim()) ?: return

    // Delete the message so other players can't copy it
    if (channel.type == ChannelType.TEXT) event.message.delete().reason("Song Guess").queue()

    channel.sendMessage(
      "${event.author.name} guessed the $type with ${score.accuracy.toPercent()} " +
        "accuracy in ${score.time.format(3)} seconds"
    ).queue()

  }

}

private data class Score(val accuracy: Double, val time: Double, val guessed: Boolean)