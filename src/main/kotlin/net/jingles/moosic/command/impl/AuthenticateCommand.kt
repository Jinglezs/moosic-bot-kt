package net.jingles.moosic.command.impl

import com.adamratzman.spotify.SpotifyClientApiBuilder
import com.adamratzman.spotify.SpotifyScope
import net.jingles.moosic.command.*
import net.jingles.moosic.credentials

@CommandMeta(category = Category.SPOTIFY, triggers = ["authenticate"], minArgs = 0,
  description = "Messages the sender an authentication link that will allow MoosicBot to interact with their Spotify account.")
class AuthenticateCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val authorizationUrl = SpotifyClientApiBuilder(credentials).getAuthorizationUrl(
      SpotifyScope.PLAYLIST_READ_PRIVATE,
      SpotifyScope.PLAYLIST_READ_COLLABORATIVE,
      SpotifyScope.STREAMING,
      SpotifyScope.USER_FOLLOW_READ,
      SpotifyScope.USER_LIBRARY_READ,
      SpotifyScope.USER_MODIFY_PLAYBACK_STATE,
      SpotifyScope.USER_READ_PRIVATE,
      SpotifyScope.USER_TOP_READ,
      SpotifyScope.USER_READ_PLAYBACK_STATE,
      SpotifyScope.USER_READ_CURRENTLY_PLAYING,
      SpotifyScope.USER_READ_RECENTLY_PLAYED
    ) + "&state=${context.userId}"

    context.message.addReaction("\uD83D\uDC4D").queue()

    context.userId.toUser(context.jda)?.openPrivateChannel()?.queue { channel ->
      channel.sendMessage("Click the following link to authenticate MoosicBot for Spotify interactions: $authorizationUrl").queue()
    }

  }

}