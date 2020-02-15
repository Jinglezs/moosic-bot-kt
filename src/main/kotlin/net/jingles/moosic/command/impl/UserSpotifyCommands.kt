package net.jingles.moosic.command.impl

import com.adamratzman.spotify.endpoints.client.ClientPersonalizationAPI
import com.adamratzman.spotify.models.Artist
import com.adamratzman.spotify.models.Track
import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.SPOTIFY_ICON
import net.jingles.moosic.command.*
import net.jingles.moosic.service.Spotify
import net.jingles.moosic.service.getSpotifyClient
import net.jingles.moosic.toPercent
import java.awt.Color
import java.time.Instant

private const val NOT_AUTHENTICATED = "This command requires Spotify authentication >:V"

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["favorite", "favourite"], minArgs = 3,
  description = "Displays the user's top artists and songs on Spotify.",
  args = "<tracks or artists> <short, medium, or long> <username>"
)
class FavoritesCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val type = context.arguments.pollFirst().toLowerCase()

    val timeRange = when (context.arguments.pollFirst().toLowerCase()) {
      "short" -> ClientPersonalizationAPI.TimeRange.SHORT_TERM
      "medium" -> ClientPersonalizationAPI.TimeRange.MEDIUM_TERM
      "long" -> ClientPersonalizationAPI.TimeRange.LONG_TERM
      else -> ClientPersonalizationAPI.TimeRange.MEDIUM_TERM
    }

    val name = context.arguments.joinToString(" ")

    val user = context.event.jda.getUsersByName(name, true).firstOrNull()
      ?: throw CommandException("A user by the name of \"$name\" could not be found.")

    val spotify = getSpotifyClient(user.idLong)
      ?: throw CommandException("${user.name} has not authenticated MoosicBot for Spotify interactions >:V")

    val mediaList = when (type) {
      "artists" -> getArtistList(spotify, timeRange)
      "tracks" -> getTrackList(spotify, timeRange)
      else -> throw CommandException("The first argument must either be \"tracks\" or \"artists\"")
    }

    val embed = EmbedBuilder()
      .setTitle("$name's Favorite ${type.capitalize()} - ${timeRange.name.toLowerCase().capitalize().replace("_", " ")}")
      .setDescription(mediaList)
      .setColor(Color.BLACK)
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed).queue()

  }

  private fun getArtistList(spotify: Spotify, range: ClientPersonalizationAPI.TimeRange): String {

    val artists: List<Artist> = spotify.clientAPI.personalization.getTopArtists(timeRange = range, limit = 15).complete()

    return if (artists.isEmpty()) "Unable to find favorite artists."
    else artists.mapIndexed { index, artist -> "${index + 1}. ${artist.name}" }.joinToString("\n")

  }

  private fun getTrackList(spotify: Spotify, range: ClientPersonalizationAPI.TimeRange): String {

    val tracks: List<Track> = spotify.clientAPI.personalization.getTopTracks(timeRange = range, limit = 15).complete()

    return if (tracks.isEmpty()) "Unable to find favorite tracks."
    else tracks.mapIndexed { index, track ->
      "${index + 1}. ${track.name}  -  ${track.artists.joinToString(", ") { it.name }}"
    }.joinToString("\n")

  }

}

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["recommend", "recommendations"],
  description = "Provides songs similar to what you are currently playing."
)
class RecommendationsCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val spotify = getSpotifyClient(context.event.author.idLong)?.clientAPI
      ?: throw CommandException(NOT_AUTHENTICATED)

    val current = spotify.player.getCurrentlyPlaying().complete()?.track
      ?: throw CommandException("You are not currently playing a track >:V")

    val seedTracks = listOf(current.id)
    val seedArtists = current.artists.map { it.id }

    val tracks = spotify.browse.getTrackRecommendations(
      seedTracks = seedTracks,
      seedArtists = seedArtists,
      limit = 10
    ).complete().tracks.mapIndexed { index, track ->
      "${index + 1}. ${track.name}  -  ${track.artists.joinToString { it.name }}"
    }.joinToString("\n")

    val embed = EmbedBuilder()
      .setTitle("Recommended Tracks from ${current.name}")
      .setDescription(tracks)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed).queue()

  }

}

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["features"],
  description = "Displays the features of your currently playing track."
)
class SongFeaturesCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val spotify = getSpotifyClient(context.event.author.idLong)?.clientAPI
      ?: throw CommandException(NOT_AUTHENTICATED)

    val currentTrack = spotify.player.getCurrentlyPlaying().complete()?.track
      ?: throw CommandException("You are not currently playing a track >:V")

    val features = spotify.tracks.getAudioFeatures(currentTrack.id).complete()

    val mainInfo = with (features) {
      """
        Key: $key
        Mode: ${if (mode == 0) "Major" else "Minor"}
        Time Signature: $timeSignature 
        Tempo: $tempo
      """.trimIndent()
    }

    val confidenceMeasurements = with (features) {
      """
        Acousticness: ${acousticness.toPercent()}
        Danceability: ${danceability.toPercent()}
        Energy: ${energy.toPercent()}
        Instrumentalness: ${instrumentalness.toPercent()}
        Liveness: ${liveness.toPercent()}
        Loudness: ${loudness.toPercent()}
        Speechiness: ${speechiness.toPercent()}
        Valence: ${valence.toPercent()}
      """.trimIndent()
    }

    val embed = EmbedBuilder()
      .setTitle("Audio Features of ${currentTrack.name}")
      .addField("General Info", mainInfo, false)
      .addField("Confidence Measurements", confidenceMeasurements, false)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed)

  }

}