package net.jingles.moosic.command.impl

import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.endpoints.client.ClientPersonalizationApi
import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.*
import net.jingles.moosic.command.*
import net.jingles.moosic.menu.OrderedArtistsMessage
import net.jingles.moosic.menu.OrderedTracksMessage
import net.jingles.moosic.service.SCOPES
import net.jingles.moosic.service.getSpotifyClient
import net.jingles.moosic.service.removeSpotifyClient
import java.awt.Color
import java.time.Instant
import kotlin.math.min

private const val NOT_AUTHENTICATED = "This command requires Spotify authentication >:V"

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["authenticate"], minArgs = 0,
  description = "Messages the sender an authentication link that will allow MoosicBot to interact with their Spotify account."
)
class AuthenticateCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    if (getSpotifyClient(context.event.author.idLong) != null)
      throw CommandException("You have already authenticated MoosicBot >:V")

    val authorizationUrl = SpotifyClientApiBuilder(credentials)
      .getAuthorizationUrl(*SCOPES) + "&state=${context.event.author.id}"

    context.event.message.addReaction("\uD83D\uDC4D").queue()

    context.event.author.openPrivateChannel().queue { channel ->
      channel.sendMessage("Click the following link to authenticate MoosicBot for Spotify interactions: $authorizationUrl")
        .queue()
    }

  }

}

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["unauthenticate"],
  description = "Revokes permission for MoosicBot to interact with your Spotify account."
)
class UnauthenticateCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    if (getSpotifyClient(context.event.author.idLong) == null)
      throw CommandException("You have not authenticated MoosicBot >:V")

    removeSpotifyClient(context.event.author.idLong);
    context.event.channel.sendMessage("Successfully unauthenticated MoosicBot-Spotify interactions.")

  }

}

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["favorite", "favourite"], minArgs = 3,
  description = "Displays the user's top artists and songs on Spotify.",
  args = "<tracks or artists> <short, medium, or long> <username>"
)
class FavoritesCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val type = context.arguments.pollFirst().toLowerCase()

    val timeRange = when (context.arguments.pollFirst().toLowerCase()) {
      "short" -> ClientPersonalizationApi.TimeRange.SHORT_TERM
      "medium" -> ClientPersonalizationApi.TimeRange.MEDIUM_TERM
      "long" -> ClientPersonalizationApi.TimeRange.LONG_TERM
      else -> ClientPersonalizationApi.TimeRange.MEDIUM_TERM
    }

    val name = context.arguments.joinToString(" ")

    val user = context.event.jda.getUsersByName(name, true).firstOrNull()
      ?: throw CommandException("A user by the name of \"$name\" could not be found.")

    val spotify = getSpotifyClient(user.idLong)
      ?: throw CommandException("${user.name} has not authenticated MoosicBot for Spotify interactions >:V")

    val title = "$name's Favorite ${type.capitalize()} - ${timeRange.name
      .toLowerCase().capitalize().replace("_", " ")}"

    when (type) {

      "artists" -> {
        val artists = spotify.clientAPI.personalization.getTopArtists(timeRange = timeRange, limit = 10).complete()
        OrderedArtistsMessage(artists, title, 9e5.toLong()).create(context.event.channel)
      }

      "tracks" -> {
        val tracks = spotify.clientAPI.personalization.getTopTracks(timeRange = timeRange, limit = 10).complete()
        OrderedTracksMessage(tracks, title, 9e5.toLong()).create(context.event.channel)
      }

      else -> throw CommandException("The first argument must either be \"tracks\" or \"artists\"")

    }

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
    ).complete().tracks.toSimpleNumberedTrackInfo()

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

    val mainInfo = with(features) {
      """
        Key: $key
        Mode: ${if (mode == 0) "Major" else "Minor"}
        Time Signature: $timeSignature 
        Tempo: $tempo
      """.trimIndent()
    }

    val confidenceMeasurements = with(features) {
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

    context.event.channel.sendMessage(embed).queue()

  }

}

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["play-history", "history", "stalk"],
  description = "Displays the tracks the provided user has recently played.",
  minArgs = 1, args = "<user> <limit>"
)
class StalkCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val name = context.arguments.pollFirst()

    val spotify = context.event.jda.getUsersByName(name, true)
      .mapNotNull { getSpotifyClient(it.idLong)?.clientAPI }.firstOrNull()
      ?: throw CommandException("An authenticated user by that name could not be found >:V")

    val limit = if (context.getArgCount() > 1) min(context.arguments.pollFirst().toInt(), 15) else 15

    val embed = EmbedBuilder()
      .setTitle("$name's Play History")
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)

    spotify.player.getRecentlyPlayed(limit = limit).complete()
      .take(limit)
      .map { Pair(it.track, it.playedAt.toZonedTime()) }  // Pairs the track with the time it was played
      .sortedByDescending { it.second }                   // Puts the pairs in descending order (most recent first)
      .groupBy { it.second.hour }                         // Groups the pairs based on the hour the track was played
      .forEach { (_, pairs) ->
        // Places each hour block into its own field

        val title = pairs.first().second.toReadable()
        val tracks = pairs.map { it.first }.asIterable().toNumberedTrackInfo()

        embed.addField(title, tracks, false)

      }

    context.event.channel.sendMessage(embed.build()).queue()

  }

}