package net.jingles.moosic

import com.adamratzman.spotify.models.*
import net.jingles.moosic.service.SpotifyClient
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

private const val BOLD = "**%s**"

// Formatting for dates, numbers, and Strings

fun Int.format() = "%,d".format(this)

fun Double.format(digits: Int) = "%,.${digits}f".format(this)

fun Float.toPercent(): String = NumberFormat.getPercentInstance().format(this.toDouble())

fun Double.toPercent(): String = NumberFormat.getPercentInstance().format(this)

fun String.toZonedTime(): ZonedDateTime = ZonedDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)

fun ZonedDateTime.toReadable(): String = this.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL))

fun String.boldOnIndex(index: Int, bold: Int) = if (index == bold) String.format(BOLD, this) else this

// Conversion from Spotify objects to readable Strings

fun Iterable<SimpleArtist>.toNames() = joinToString { it.name }

fun SimpleTrack.toTrackInfo() = "$name by ${artists.toNames()}"

fun Track.toSimpleTrackInfo() = "$name by ${artists.toNames()}"

fun Track.toSearchQuery() = "${name.substringBeforeLast("-")} ${artists.first().name}"

fun SimpleAlbum.toAlbumInfo() = "$name by ${artists.toNames()}"

fun SimplePlaylist.toPlaylistInfo() = "$name by ${owner.displayName} (${tracks.total} Tracks)"

// Other String stuff

fun String.percentMatch(other: String): Double {
  val levenshteinDistance = levenshtein(this, other)
  val largestLength = length.coerceAtLeast(other.length)
  return (largestLength - levenshteinDistance) / largestLength.toDouble()
}

private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
  val lhsLength = lhs.length
  val rhsLength = rhs.length

  var cost = Array(lhsLength) { it }
  var newCost = Array(lhsLength) { 0 }

  for (i in 1 until rhsLength) {
    newCost[0] = i

    for (j in 1 until lhsLength) {
      val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

      val costReplace = cost[j - 1] + match
      val costInsert = cost[j] + 1
      val costDelete = newCost[j - 1] + 1

      newCost[j] = costInsert.coerceAtMost(costDelete).coerceAtMost(costReplace)
    }

    val swap = cost
    cost = newCost
    newCost = swap
  }

  return cost[lhsLength - 1]
}

// Spotify stuffs

fun getRandomPlaylistTracks(client: SpotifyClient, limit: Int): LinkedList<Track> {

  val playlists = client.clientAPI.playlists.getClientPlaylists().complete().items

  val populatedList = playlists.mapRandomly(limit) {
    val full = this.toFullPlaylist().complete()
    full?.tracks?.random()?.track
  }

  return LinkedList(populatedList)

}

// Collection stuffs

inline fun <T> Iterable<T>.toNumbered(offset: Int = 0, bold: Int = -1, composer: T.() -> String) =
  mapIndexed { index, element ->
    "${index + offset + 1}. ${composer.invoke(element).boldOnIndex(index, bold)}"
  }.joinToString("\n")

inline fun <T> Iterable<T>.toUnnumbered(crossinline composer: T.() -> String) =
  joinToString("\n") { composer.invoke(it) }

inline fun <T, Z> Collection<T>.mapRandomly(limit: Int, mapper: T.() -> Z?): List<Z> {

  val populatedList = mutableListOf<Z>()

  while (populatedList.size < limit) {

    try {

      val mappedValue = mapper.invoke(this.random())
      if (mappedValue != null) populatedList.add(mappedValue)

    } catch (e: Exception) {
      continue
    }

  }

  return populatedList

}

// Spotify interactions

fun <T : CoreObject> playContext(client: SpotifyClient, context: T) {

  val player = client.clientAPI.player

  when (context) {
    is Track -> player.startPlayback(tracksToPlay = listOf(context.id)).queue()
    is Artist -> player.startPlayback(artist = context.id).queue()
    is SimpleAlbum -> player.startPlayback(album = context.id).queue()
    is SimplePlaylist -> player.startPlayback(playlist = context.uri).queue()
  }

}

fun CoreObject.toContextInfo(): ContextInfo = when (this) {
  is Track -> ContextInfo(name, album.images.firstOrNull()?.url)
  is Artist -> ContextInfo(name, images.firstOrNull()?.url)
  is SimpleAlbum -> ContextInfo(name, images.firstOrNull()?.url)
  is SimplePlaylist -> ContextInfo(name, images.firstOrNull()?.url)
  else -> ContextInfo("You did a dumb.", null)
}

data class ContextInfo(val title: String, val imageUrl: String?)
