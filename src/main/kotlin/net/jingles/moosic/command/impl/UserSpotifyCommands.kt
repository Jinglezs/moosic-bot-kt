package net.jingles.moosic.command.impl

import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.endpoints.client.ClientPersonalizationApi
import com.adamratzman.spotify.endpoints.client.ClientPlayerApi
import com.adamratzman.spotify.models.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.jingles.moosic.*
import net.jingles.moosic.command.*
import net.jingles.moosic.menu.PaginatedMessage
import net.jingles.moosic.menu.PaginatedReactionListener
import net.jingles.moosic.menu.PaginatedSelection
import net.jingles.moosic.menu.SelectionReactionListener
import net.jingles.moosic.service.SCOPES
import net.jingles.moosic.service.SpotifyClient
import net.jingles.moosic.service.getSpotifyClient
import net.jingles.moosic.service.removeSpotifyClient
import java.awt.Color
import java.time.Instant

private const val NOT_AUTHENTICATED = "This command requires Spotify authentication >:V"
private val SEARCH_TYPES = listOf("track", "album", "playlist", "artist")

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

    val message: PaginatedMessage<*> = when (type) {

      "artists" -> {

        val artists = spotify.clientAPI.personalization.getTopArtists(timeRange = timeRange, limit = 10).complete()

        PaginatedMessage(artists, 9e5.toLong(), title) { paging, builder ->
          builder.setDescription(paging.items.toNumberedArtists(paging.offset))
        }

      }

      "tracks" -> {

        val tracks = spotify.clientAPI.personalization.getTopTracks(timeRange = timeRange, limit = 10).complete()

        PaginatedMessage(tracks, 9e5.toLong(), title) { paging, builder ->
          builder.setDescription(paging.items.toSimpleNumberedTrackInfo(paging.offset))
        }

      }

      else -> throw CommandException("The first argument must either be \"tracks\" or \"artists\"")

    }

    message.create(context.event.channel, PaginatedReactionListener(message))

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
  minArgs = 1, args = "<user>"
)
class StalkCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val name = context.arguments.pollFirst()

    val spotify = context.event.jda.getUsersByName(name, true)
      .mapNotNull { getSpotifyClient(it.idLong)?.clientAPI }.firstOrNull()
      ?: throw CommandException("An authenticated user by that name could not be found >:V")

    val pagingObject = spotify.player.getRecentlyPlayed(limit = 10).complete()

    val message = PaginatedMessage(pagingObject, 9e5.toLong(), "$name's Play History") { paging, builder ->

      paging.items.map { Pair(it.track, it.playedAt.toZonedTime()) }  // Pairs the track with the time it was played
        .sortedByDescending { it.second }                   // Puts the pairs in descending order (most recent first)
        .groupBy { it.second.hour }                         // Groups the pairs based on the hour the track was played
        .forEach { (_, pairs) ->
          // Places each hour block into its own field

          val title = pairs.first().second.toReadable()
          val tracks = pairs.map { it.first }.asIterable().toNumberedTrackInfo()

          builder.addField(title, tracks, false)

        }

    }

    message.create(context.event.channel, PaginatedReactionListener(message))

  }

}

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["player", "control", "remote"], minArgs = 1,
  description = "Allows the user to control their Spotify playback via commands.",
  args = "<info/pause/resume/play/repeat/volume/shuffle>", deleteCaller = true
)
class PlayerCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val client = getSpotifyClient(context.event.author.idLong)
      ?: throw CommandException(NOT_AUTHENTICATED)

    when (val subcommand = context.arguments.pollFirst().toLowerCase()) {

      "play" -> handlePlay(context, client)
      "pause" -> client.clientAPI.player.pause().complete()
      "resume" -> client.clientAPI.player.resume().complete()

      "repeat" -> {

        val state = try {
          ClientPlayerApi.PlayerRepeatState.valueOf(context.arguments.pollFirst())
        } catch (e: IllegalArgumentException) {
          throw CommandException("You must provide a repeat state: \"track\", \"context\", or \"off\"")
        }

        client.clientAPI.player.setRepeatMode(state).complete()
        context.event.channel.sendMessage("Set repeat mode to ${state.name}").queue()

      }

      "shuffle", "info" -> {

        with(client.clientAPI.player) {

          val playerContext = getCurrentContext().complete()
            ?: throw CommandException("Could not retrieve playback context >:V")

          if (subcommand == "shuffle") toggleShuffle(!playerContext.shuffleState).complete()
          else handleInfo(playerContext, context.event.channel)

        }

      }

    }

  }

  private fun handleInfo(context: CurrentlyPlayingContext, channel: MessageChannel) {

    val state = """
      Device: ${context.device.type.identifier}
      Shuffle: ${context.shuffleState}
      Repeat State: ${context.repeatState.name}
    """.trimIndent()

    val currentlyPlaying = if (context.track == null) "Nothing" else with(context.track!!) {

      """
        Now Playing: $name on ${album.toAlbumInfo()}
        Artists: ${artists.toNames()}
        Popularity: $popularity
        Progress: ${context.progressMs?.div(durationMs.toFloat())?.toPercent()}
      """.trimIndent()

    }

    val embed = EmbedBuilder()

    with(embed) {

      setTitle("Playback Context")
      addField("Playback State", state, false)
      addField("Currently Playing", currentlyPlaying, false)

      if (context.track != null) setImage(context.track!!.album.images[0].url)

      setColor(Color.WHITE)
      setTimestamp(Instant.now())
      setFooter("Powered by Spotify", SPOTIFY_ICON)

    }

    channel.sendMessage(embed.build()).queue()

  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun handlePlay(context: CommandContext, client: SpotifyClient) {

    if (context.getArgCount() < 2) throw CommandException("You must provide a search type and query >:V")

    val searchAPI = client.clientAPI.search
    val searchType = context.arguments.pollFirst()
    val query = context.arguments.joinToString(" ")

    val searchResult: PagingObject<*> = when (searchType) {
      "track" -> searchAPI.searchTrack(query = query, limit = 5).complete()
      "artist" -> searchAPI.searchArtist(query = query, limit = 5).complete()
      "album" -> searchAPI.searchAlbum(query = query, limit = 5).complete()
      "playlist" -> searchAPI.searchPlaylist(query = query, limit = 5).complete()
      else -> throw CommandException("Search type must be one of the following: ${SEARCH_TYPES.joinToString()}")
    }

    if (searchResult.items.isEmpty()) throw CommandException("There were no search results for that query.")

    val message = PaginatedSelection(searchResult, 9e5.toLong(), "${context.event.author.name}'s Search Results",
      composer = { pagingObject, builder ->

        val description = when (pagingObject.items[0]) {
          is Track -> (pagingObject.items as List<Track>).toSimpleNumberedTrackInfo()
          is Artist -> (pagingObject.items as List<Artist>).toNumberedArtists()
          is SimpleAlbum -> (pagingObject.items as List<SimpleAlbum>).toNumberedAlbumInfo()
          else -> (pagingObject.items as List<SimplePlaylist>).toNumberedPlaylistInfo()
        }

        builder.setDescription(description)

      }, afterSelection = { selection, builder ->

        when (selection) {

          is Track -> {
            builder.setTitle(selection.toSimpleTrackInfo()); builder.setImage(selection.album.images[0].url)
            client.clientAPI.player.startPlayback(tracksToPlay = listOf(selection.id)).complete()
          }

          is Artist -> {
            builder.setTitle(selection.name); builder.setImage(selection.images[0].url)
            client.clientAPI.player.startPlayback(artist = selection.id).complete()
          }

          is SimpleAlbum -> {
            builder.setTitle(selection.name); builder.setImage(selection.images[0].url)
            client.clientAPI.player.startPlayback(album = selection.id).complete()
          }

          else -> with (selection as SimplePlaylist) {
            builder.setTitle(toPlaylistInfo()); builder.setImage(images[0].url)
            client.clientAPI.player.startPlayback(playlist = uri).complete()
          }

        }

        builder.build()

      })

    message.create(context.event.channel, PaginatedReactionListener(message), SelectionReactionListener(message))

  }

}