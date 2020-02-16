package net.jingles.moosic.command.impl

import net.jingles.moosic.command.*
import net.jingles.moosic.game.SongGuess
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

    if (type != "track" && type != "artist")
      throw CommandException("The type must either be \"track\" or \"artist\"")

    val rounds = min(context.arguments.pollFirst().toInt(), 20)

    SongGuess(context.event.channel, spotify, type, rounds)

  }

}