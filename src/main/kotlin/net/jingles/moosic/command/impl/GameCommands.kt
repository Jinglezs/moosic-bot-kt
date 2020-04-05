package net.jingles.moosic.command.impl

import com.adamratzman.spotify.models.Playlist
import net.jingles.moosic.command.*
import net.jingles.moosic.game.LyricsGuess
import net.jingles.moosic.game.SongGuess
import net.jingles.moosic.service.SpotifyClient
import net.jingles.moosic.service.getSpotifyClient
import kotlin.math.min
import kotlin.time.ExperimentalTime

@CommandMeta(
  category = Category.GAMES, triggers = ["song-guess"], minArgs = 2, args = "<artist/track> <rounds>",
  description = "Creates a Song Guess game, where the players must guess the title/artist of the song being played."
)
class SongGuessCommand : Command() {

  @ExperimentalTime
  override suspend fun execute(context: CommandContext) {

    val spotify = getSpotifyClient(context.event.author.idLong)
      ?: throw CommandException("You must be authenticated to play Song Guess >:V")

    val type = context.arguments.pollFirst().toLowerCase()

    if (type != "title" && type != "artist")
      throw CommandException("The type must either be \"title\" or \"artist\"")

    val rounds = min(context.arguments.pollFirst().toInt(), 20)

    val playlistName = if (context.arguments.isEmpty()) null
    else context.arguments.joinToString(" ")

    SongGuess(context.event.channel, spotify, type, rounds, getPlaylist(spotify, playlistName))

  }

}

@CommandMeta(
  category = Category.GAMES, triggers = ["lyric-guess"], minArgs = 1, args = "<rounds>",
  description = "Creates a Lyric Guess game, where the players must fill in the missing line of a verse."
)
class LyricGuessCommand : Command() {

  @ExperimentalTime
  override suspend fun execute(context: CommandContext) {

    val spotify = getSpotifyClient(context.event.author.idLong)
      ?: throw CommandException("You must be authenticated to play Lyric Guess >:V")

    val rounds = min(context.arguments.pollFirst().toInt(), 20)

    val playlistName = if (context.arguments.isEmpty()) null
    else context.arguments.joinToString(" ")

    LyricsGuess(context.event.channel, spotify, rounds, getPlaylist(spotify, playlistName))

  }

}

private fun getPlaylist(client: SpotifyClient, name: String?): Playlist? {

  try {

    if (name == null) return null

    return client.clientAPI.playlists.getClientPlaylists().getAllItems().complete()
      .firstOrNull { it.name.equals(name, true) }?.toFullPlaylist()?.complete()

  } catch (e: Exception) {
    return null
  }

}
