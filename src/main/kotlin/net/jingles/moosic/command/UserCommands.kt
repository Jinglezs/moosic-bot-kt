package net.jingles.moosic.command

import net.jingles.moosic.service.Spotify
import net.jingles.moosic.service.getSpotifyClient

@CommandMeta(category = Category.SPOTIFY, triggers = ["favorite", "favourite"], minArgs = 4,
  description = "Displays the user's top artists and songs on Spotify.")
class FavoritesCommand : Command() {

  override fun execute(context: CommandContext) {

    val type = context.arguments.pollFirst().toLowerCase()
    var timeRange = context.arguments.pollFirst().toLowerCase()
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

    timeRange = when {
      timeRange == "short" || timeRange == "long" -> "${timeRange}_term"
      else -> "medium_range"
    }

    val mediaList = when {

      type == "artists" -> spotify.clientAPI.personalization.getTopArtists()
      type == "tracks" -> TODO("Resolve the track list")
      else -> TODO("Resolve the track list")

    }

  }

  private fun getArtistList(spotify: Spotify, genre: String): String {
    TODO("Complete this")
  }

  private fun getTrackList(spotify: Spotify, genre: String): String {
    TODO("Complete this")
  }

}