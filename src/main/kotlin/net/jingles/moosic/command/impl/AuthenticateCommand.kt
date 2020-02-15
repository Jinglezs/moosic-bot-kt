package net.jingles.moosic.command.impl

import com.adamratzman.spotify.SpotifyClientApiBuilder
import net.jingles.moosic.command.*
import net.jingles.moosic.credentials
import net.jingles.moosic.service.SCOPES
import net.jingles.moosic.service.getSpotifyClient
import net.jingles.moosic.service.removeSpotifyClient

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