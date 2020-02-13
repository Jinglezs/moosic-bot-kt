package net.jingles.moosic.command

import com.neovisionaries.i18n.CountryCode
import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.spotify
import java.awt.Color
import java.time.Instant

@CommandMeta(category = Category.SPOTIFY, triggers = ["new-releases"], description = "Displays Spotify's new releases.")
class NewReleasesCommand : Command() {

  override fun execute(context: CommandContext) {

    val channel = context.message.textChannel

    spotify.browse.getNewReleases(market = CountryCode.US).queue {

      val description = it.getAllItems().complete().joinToString { album ->
        "*$album.name  - ${album.artists.joinToString(separator = ", ") { artist -> artist.name }}"
      }

      val embed = EmbedBuilder()
        .setTitle("New Releases on Spotify")
        .setDescription(description)
        .setColor(Color.BLACK)
        .setTimestamp(Instant.now())
        .setFooter("Powered by Spotify", "")
        .build()

      channel.sendMessage(embed).queue()

    }

  }

}