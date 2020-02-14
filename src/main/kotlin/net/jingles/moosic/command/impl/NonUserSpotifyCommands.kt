package net.jingles.moosic.command.impl

import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.SPOTIFY_ICON
import net.jingles.moosic.command.*
import net.jingles.moosic.spotify
import java.awt.Color
import java.time.Instant

@CommandMeta(category = Category.SPOTIFY, triggers = ["new-releases"], description = "Displays Spotify's new releases.")
class NewReleasesCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val description = spotify.browse.getNewReleases().complete().joinToString("\n") {
      "*${it.name}*   -   ${it.artists.joinToString { artist -> artist.name }}"
    }

    val embed = EmbedBuilder()
      .setTitle("New Releases on Spotify")
      .setDescription(description)
      .setColor(Color.BLACK)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed).queue()

  }

}

@CommandMeta(
  category = Category.SPOTIFY, triggers = ["artist"], minArgs = 1,
  description = "Displays basic information about an artist from Spotify."
)
class ArtistInfoCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val query = context.arguments.joinToString(" ")

    val artist = spotify.search.searchArtist(query).complete().firstOrNull()
      ?: throw CommandException("An artist with that name could not be found.")

    /*
    val topTracks = spotify.artists.getArtistTopTracks(artist.id).complete()
      .take(5)
      .mapIndexed { index, track -> "$index. ${track.name}" }
      .joinToString(separator = "\n")
     */

    val info = """
      Genres: ${artist.genres.joinToString { ", " }}
      Follower Count: ${artist.followers.total}
      Popularity: ${artist.popularity}
    """.trimIndent()

    val embed = EmbedBuilder()
      .setTitle(artist.name)
      //.addField("Top Tracks", topTracks, false)
      .addField("General Information", info, false)
      .setImage(artist.images.firstOrNull()?.url)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed).queue()

  }

}