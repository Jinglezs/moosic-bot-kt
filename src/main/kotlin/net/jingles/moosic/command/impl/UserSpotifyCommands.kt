package net.jingles.moosic.command.impl

import com.adamratzman.spotify.endpoints.client.ClientPersonalizationAPI
import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.SPOTIFY_ICON
import net.jingles.moosic.command.*
import net.jingles.moosic.service.Spotify
import net.jingles.moosic.service.getSpotifyClient
import java.awt.Color

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["favorite", "favourite"], minArgs = 4,
  description = "Displays the user's top artists and songs on Spotify."
)
class FavoritesCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val type = context.arguments.pollFirst().toLowerCase()

    val timeRange = ClientPersonalizationAPI.TimeRange.valueOf(
      when (val range = context.arguments.pollFirst().toLowerCase()) {
        "short", "long" -> "${range}_term"
        else -> "medium_range"
      }
    )

    val genre = context.arguments.pollFirst().toLowerCase()
    val name = context.arguments.joinToString { " " }

    val user = context.event.jda.getUsersByName(name, true).first()
      ?: throw CommandException("A user by the name of \"$name\" could not be found.")

    val spotify = getSpotifyClient(user.idLong)
      ?: throw CommandException("${user.name} has not authenticated MoosicBot for Spotify interactions >:V")

    val mediaList = when (type) {
      "artists" -> getArtistList(spotify, genre, timeRange)
      "tracks" -> getTrackList(spotify, genre, timeRange)
      else -> throw CommandException("The first argument must either be \"tracks\" or \"artists\"")
    }

    val embed = EmbedBuilder()
      .setTitle("$name's Favorite ${type.capitalize()}")
      .setDescription(mediaList)
      .setColor(Color.BLACK)
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed).queue()

  }

  private fun getArtistList(
    spotify: Spotify, genre: String,
    range: ClientPersonalizationAPI.TimeRange
  ): String {

    return spotify.clientAPI.personalization.getTopArtists(timeRange = range, limit = 45)
      .getAllItems().complete().filter { it.genres.any { g -> g == genre } }
      .map { it.name }
      .joinToString { "\n" }

  }

  private fun getTrackList(
    spotify: Spotify, genre: String,
    range: ClientPersonalizationAPI.TimeRange
  ): String {

    return spotify.clientAPI.personalization.getTopTracks(timeRange = range, limit = 45)
      .getAllItems().complete()
      .filter { track ->
        track.artists.any { it.toFullArtist().complete()?.genres?.any { g -> g == genre } ?: false }
      }.map { "${it.name}  -  ${it.artists.joinToString(", ") { a -> a.name }}" }
      .joinToString { "\n" }

  }

}