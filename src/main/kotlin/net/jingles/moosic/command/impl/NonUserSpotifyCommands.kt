package net.jingles.moosic.command.impl

import com.neovisionaries.i18n.CountryCode
import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.SPOTIFY_ICON
import net.jingles.moosic.command.Category
import net.jingles.moosic.command.Command
import net.jingles.moosic.command.CommandContext
import net.jingles.moosic.command.CommandMeta
import net.jingles.moosic.spotify
import java.awt.Color
import java.time.Instant

@CommandMeta(category = Category.SPOTIFY, triggers = ["new-releases"], description = "Displays Spotify's new releases.")
class NewReleasesCommand : Command() {

  override suspend fun execute(context: CommandContext) {

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

      context.event.channel.sendMessage(embed).queue()

    }

  }

}

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["artist"], minArgs = 1,
  description = "Displays basic information about an artist from Spotify."
)
class ArtistInfoCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val query = context.arguments.joinToString { " " }
    val artist = spotify.search.searchArtist(query, limit = 1).getAllItems().complete().first()

    val topTracks = spotify.artists.getArtistTopTracks(artist.id).complete().joinToString(", ") { it.name }

    val info = """
      Genres: ${artist.genres.joinToString { ", " }}
      Follower Count: ${artist.followers.total}
      Popularity: ${artist.popularity}
    """.trimIndent()

    val embed = EmbedBuilder()
      .setTitle(artist.name)
      .addField("Top Tracks", topTracks, false)
      .addField("General Information", info, false)
      .setImage(artist.images.firstOrNull()?.url)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed).queue()

  }

}