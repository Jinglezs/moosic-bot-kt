package net.jingles.moosic.command.impl

import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.*
import net.jingles.moosic.command.*
import net.jingles.moosic.reaction.ListHandler
import net.jingles.moosic.reaction.PaginatedMessage
import net.jingles.moosic.reaction.PaginatedSelection
import net.jingles.moosic.reaction.PagingObjectHandler
import net.jingles.moosic.service.getLyrics
import net.jingles.moosic.service.getSpotifyClient
import net.jingles.moosic.service.search
import java.awt.Color
import java.time.Instant

@CommandMeta(category = Category.SPOTIFY, triggers = ["new-releases"], description = "Displays Spotify's new releases.")
class NewReleasesCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val albums = spotify.browse.getNewReleases(limit = 10).complete()

    PaginatedMessage(PagingObjectHandler(albums), 6e4.toLong(), "New Releases on Spotify") {
      builder.setDescription(currentElements.toNumbered(offset = handler!!.offset) { toAlbumInfo() })
    }.create(context.event.channel)

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

    val topTracks = spotify.artists.getArtistTopTracks(artist.id).complete()
      .take(5).toNumbered { name }

    val info = """
      Genres: ${artist.genres.joinToString()}
      Follower Count: ${artist.followers.total.format()}
      Popularity: ${artist.popularity}
    """.trimIndent()

    val embed = EmbedBuilder()
      .setTitle(artist.name)
      .addField("Top Tracks", topTracks, true)
      .addField("General Info", info, true)
      .setImage(artist.images[0].url)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .setFooter("Powered by Spotify", SPOTIFY_ICON)
      .build()

    context.event.channel.sendMessage(embed).queue()

  }

}

@CommandMeta(
  category = Category.GENERAL, triggers = ["lyrics"], args = "<query>",
  description = "Fetches and displays song lyrics from Genius"
)
class LyricsCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    val query = when {

      context.getArgCount() != 0 -> context.arguments.joinToString(" ")

      else -> {

        val client = getSpotifyClient(context.event.author.idLong)?.clientAPI

        client?.player?.getCurrentlyPlaying()?.complete()?.track?.toSearchQuery()
          ?: throw CommandException("No query was provided and a song is not playing on Spotify >:V")
      }

    }


    PaginatedSelection(ListHandler(search(query), 5), 6e4.toLong(), "Genius Search Results",
      composer = {

        val boldIndex = currentSelection - 1

        val description = if (currentElements.isEmpty()) "No results were found."
        else currentElements.toNumbered(handler!!.offset, boldIndex) { "$title by $artist" }

        builder.setDescription(description)
        builder.setFooter("Powered by Genius", GENIUS_ICON)

      }, afterSelection = { selection ->

        getLyrics(selection.url).forEach { builder.addField(it.first, it.second, false) }

        builder.setTitle("${selection.title} by ${selection.artist}").setDescription(null).build()

      }).create(context.event.channel)

  }

}