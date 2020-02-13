package net.jingles.moosic.command

import com.adamratzman.spotify.endpoints.client.ClientPersonalizationAPI
import net.jingles.moosic.service.Spotify
import net.jingles.moosic.service.getSpotifyClient

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["favorite", "favourite"], minArgs = 4,
  description = "Displays the user's top artists and songs on Spotify."
)
class FavoritesCommand : Command() {

  override fun execute(context: CommandContext) {

    val type = context.arguments.pollFirst().toLowerCase()

    val timeRange = ClientPersonalizationAPI.TimeRange.valueOf(
      when (val range = context.arguments.pollFirst().toLowerCase()) {
        "short", "long" -> "${range}_term"
        else -> "medium_range"
      }
    )

    val genre = context.arguments.pollFirst().toLowerCase()
    val name = context.arguments.joinToString { " " }
    val user = context.jda.getUsersByName(name, true).first()

    if (user == null) {
      this.error(context, "A user by the name of \"$name\" could not be found.")
      return
    }

    val spotify = getSpotifyClient(user.idLong)

    if (spotify == null) {
      this.error(context, "${user.name} has not authenticated MoosicBot for Spotify interactions >:V")
      return
    }

    val mediaList = when {

      type == "artists" -> getArtistList(spotify, genre, timeRange)
      else -> getTrackList(spotify, genre, timeRange)

      //TODO: Invalid argument error

    }

  }

  private fun getArtistList(spotify: Spotify, genre: String,
                            range: ClientPersonalizationAPI.TimeRange): String {

    return spotify.clientAPI.personalization.getTopArtists(timeRange = range, limit = 45)
      .getAllItems().complete().filter { it.genres.any { g -> g == genre } }
      .map { it.name }
      .joinToString { "\n" }

  }

  private fun getTrackList(spotify: Spotify, genre: String,
                           range: ClientPersonalizationAPI.TimeRange): String {

    return spotify.clientAPI.personalization.getTopTracks(timeRange = range, limit = 45)
      .getAllItems().complete().filter { track ->
        track.artists.any { it.toFullArtist().complete()?.genres?.any { g -> g == genre } ?: false }
      }.map { "${it.name}  -  ${it.artists.joinToString(", ") { a -> a.name}}" }
      .joinToString { "\n" }

  }

}