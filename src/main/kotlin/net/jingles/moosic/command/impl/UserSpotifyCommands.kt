package net.jingles.moosic.command.impl

import com.adamratzman.spotify.endpoints.client.ClientPersonalizationAPI
import com.adamratzman.spotify.models.Artist
import com.adamratzman.spotify.models.Track
import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.SPOTIFY_ICON
import net.jingles.moosic.command.*
import net.jingles.moosic.service.Spotify
import net.jingles.moosic.service.getSpotifyClient
import java.awt.Color

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["favorite", "favourite"], minArgs = 3,
  description = "Displays the user's top artists and songs on Spotify."
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