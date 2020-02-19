package net.jingles.moosic.command.impl

import com.adamratzman.spotify.endpoints.public.ArtistApi
import com.adamratzman.spotify.utils.Market
import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.*
import net.jingles.moosic.command.*
import net.jingles.moosic.menu.ImageSlideshow
import java.awt.Color
import java.time.Instant

@CommandMeta(category = Category.SPOTIFY, triggers = ["new-releases"], description = "Displays Spotify's new releases.")
class NewReleasesCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val description = spotify.browse.getNewReleases().complete().toUnnumbered { toAlbumInfo() }

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
  args = "<artist name>", description = "Displays basic information about an artist from Spotify."
)
class ArtistInfoCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val query = context.arguments.joinToString(" ")

    val artist = spotify.search.searchArtist(query).complete().firstOrNull()
      ?: throw CommandException("An artist with that name could not be found.")

    val albums = spotify.artists.getArtistAlbums(
      artist.id, market = Market.US,
      include = *arrayOf(ArtistApi.AlbumInclusionStrategy.ALBUM)
    ).complete()
      .distinctBy { it.name }
      .take(5)
      .joinToString("\n") { it.name }

    val info = """
      Genres: ${artist.genres.joinToString()}
      Follower Count: ${artist.followers.total.format()}
      Popularity: ${artist.popularity}
    """.trimIndent()

    val builder = EmbedBuilder()
      .setTitle(artist.name)
      .addField("Albums", albums, true)
      .addField("General Info", info, true)
      .setImage(artist.images.firstOrNull()?.url)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)

    ImageSlideshow(builder, artist.images.map { it.url }).create(context.event.channel, 3e5.toLong())

  }

}