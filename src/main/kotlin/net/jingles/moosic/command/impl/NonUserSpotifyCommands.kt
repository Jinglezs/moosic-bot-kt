package net.jingles.moosic.command.impl

import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.SPOTIFY_ICON
import net.jingles.moosic.command.*
import net.jingles.moosic.format
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

    val albums = spotify.artists.getArtistAlbums(artist.id, limit = 5).complete()
      .mapIndexed { index, album -> "${index + 1}. ${album.name}" }
      .joinToString("\n")

    val info = """
      Genres: ${artist.genres.joinToString()}
      Follower Count: ${artist.followers.total.format()}
      Popularity: ${artist.popularity}
    """.trimIndent()

    val embed = EmbedBuilder()
      .setTitle(artist.name)
      .addField("General Information", info, true)
      .addField("Albums", albums, true)
      .setImage(artist.images.firstOrNull()?.url)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed).queue()

  }

}