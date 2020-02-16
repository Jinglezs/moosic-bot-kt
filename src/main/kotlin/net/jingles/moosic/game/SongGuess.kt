package net.jingles.moosic.game

import com.adamratzman.spotify.models.Track
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.jingles.moosic.format
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
const val SUCCESS_LIMIT = 0.75

@ExperimentalTime
class SongGuess(private val channel: MessageChannel, owner: SpotifyClient,
                private val type: String, private val rounds: Int) {

  // State variables
  private var started = false
  private lateinit var clockMark: ClockMark

  // Game information
  private val players = mutableSetOf<SpotifyClient>()
  private val scores = mutableMapOf<SpotifyClient, MutableList<Score>>()
  private val tracks = populateTracks(owner, rounds)
  private val currentTrack get() = tracks.peek()
  private lateinit var possibleMatches: List<String>

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
          scores[it.key]!!.add(Score(0.0, 10.0))
        }

      // Proceed to the next track
      tracks.removeFirst()

    } else {

      started = true
      channel.sendMessage("The game has started! Guess the $type of each song >:V").queue()

    }

    // End the game when all of the tracks are gone
    if (tracks.isEmpty()) {
      endGame(); return
    }

    // Removes any extra information from the title, which is usually in parentheses
    // or following a hyphen. Ex: Never Be Alone - MTV Unplugged -> Never Be Alone

    possibleMatches = when(type) {
      "track" -> currentTrack.name.substringBefore("( | -")
        .split(" ").map { it.trim() }
      else -> currentTrack.artists.map { it.name }
    }

    // Spotify only accepts lists of track IDs/URIs to play
    val tracksToPlay = listOf(currentTrack.id)

    // Determines a random position in the track to begin playing at
    val maxDuration = (currentTrack.durationMs * (1 - 0.75)).toInt()
    val seekPosition = (0..maxDuration).random()

    players.forEach {

      with(it.clientAPI.player) {
        startPlayback(tracksToPlay = tracksToPlay).suspendQueue()// Play the track
        seek(seekPosition.toLong()).suspendQueue() // Skip to a random position
      }

    }

    // Marks the time this round began
    clockMark = MonoClock.markNow()

    // Start the next round after 10 seconds
    GlobalScope.launch {
      delay(10_000)
      nextRound()
    }

  }

  private fun endGame() {

    // Average the score times and select the entry with the smallest value
    val fastestTime = scores.mapValues { it.value.sumByDouble { score -> score.time } / it.value.size }
      .minBy { entry -> entry.value }

    // Average the accuracies and select the entry with the highest value
    val mostAccurate = scores.mapValues { it.value.sumByDouble { score -> score.accuracy } / it.value.size }
      .maxBy { entry -> entry.value }

    // Pair the usernames with the formatted results
    val time = Pair(
      fastestTime?.key?.discordId?.let { channel.jda.getUserById(it)?.name } ?: "N/A",
      fastestTime?.value.let { "${it?.format(3) ?: "0.0"} seconds" }
    )

    val accuracy = Pair(
      mostAccurate?.key?.discordId?.let { channel.jda.getUserById(it)?.name } ?: "N/A",
      mostAccurate?.value.let { "${it?.format(3) ?: "0"}%" }
    )

    // Send an embedded message containing the results of the game
    val embed = EmbedBuilder()
      .setTitle("Song Guess Results")
      .addField("Fastest Average Time", "${time.first}: ${time.second}", true)
      .addField("Highest Average Accuracy", "${accuracy.first}: ${accuracy.second}", true)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .build()

    channel.sendMessage(embed).queue()

  }

  /**
   * Determines whether or a player's guess is correct
   * Returns a pair containing a boolean, where "true"
   * means correct, and a Long that contains the true
   * time it took for the player to guess.
   */
  private fun verifyGuess(player: SpotifyClient, guess: String): Score? {

    val matches = (possibleMatches + guess.split(" "))
      .groupingBy { it }.eachCount().count { it.value > 1 }

    val accuracy = matches.toDouble() / possibleMatches.size
    if (accuracy < SUCCESS_LIMIT) return null

    val score = Score(accuracy, clockMark.elapsedNow().inSeconds)

    if (!scores.containsKey(player)) scores[player] = mutableListOf()
    scores[player]!!.add(score)

    return score

  }

  private fun populateTracks(spotify: SpotifyClient, rounds: Int): LinkedList<Track> {

    val populatedList = LinkedList<Track>()
    val playlists = spotify.clientAPI.playlists.getClientPlaylists().complete().items

    while (populatedList.size < rounds) {

      val full = playlists.random().toFullPlaylist().complete() ?: continue
      val track = full.tracks.random().track

      if (track != null) populatedList.add(track)

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

    // Gets a decimal that reflects the accuracy
    val score = verifyGuess(spotify, event.message.contentStripped) ?: return

    channel.sendMessage(
      "${event.author.name} guessed the title with ${score.accuracy.format(2)} " +
        "accuracy in ${score.time.format(3)} seconds"
    ).queue()

  }

}

private data class Score(val accuracy: Double, val time: Double)