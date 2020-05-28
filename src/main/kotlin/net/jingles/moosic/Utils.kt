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

fun Playable.toSimpleTrackInfo() = when (this) {
  is Track -> "$name by ${artists.toNames()}"
  is LocalTrack -> "$name by ${artists.joinToString(", ") { it.name }}"
  is Episode -> "$name on ${show.name}"
  else -> "Some mystical, inaccessible playable object."
}

fun Playable.toSearchQuery() = when (this) {
  is Track -> "${name.substringBeforeLast("-")} ${artists.first().name}"
  is LocalTrack -> "${name.substringBeforeLast("-")} ${artists.firstOrNull()?.name ?: ""}"
  is Episode -> "$name ${show.name}"
  else -> "I really hope this isn't the title of some obscure song or podcast."
}

fun Playable.toInfo() = when (this) {
  is Track -> "$name by ${artists.toNames()}"
  is LocalTrack -> "$name by ${artists.joinToString { it.name }}"
  is Episode -> "$name on ${show.name}"
  else -> "This should never be the result lul"
}

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

fun getRandomPlaylistTracks(
  client: SpotifyClient, limit: Int,
  playlist: Playlist? = null, allowLocal: Boolean = false
): LinkedList<Playable> {

  if (playlist != null) return LinkedList(playlist.tracks.toList()
    .filterNotNull()
    .filter { it.track?.type.equals("track") }
    .filter { it.isLocal == null || (allowLocal && it.isLocal!!) }
    .mapRandomly(limit) { track })

  val playlists = client.clientAPI.playlists.getClientPlaylists().complete().items
  val fullPlaylists = mutableMapOf<SimplePlaylist, Playlist>()

  val populatedList = playlists.mapRandomly(limit) {

    val full = fullPlaylists.computeIfAbsent(this) { this.toFullPlaylist().complete()!! }
    val track = full.tracks.random()

    if (track?.track?.type.equals("track") || track?.isLocal!!) null
    else track.track

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
  var attempt = 1

  while (populatedList.size < limit) {

    println("Randomly mapping! Attempt $attempt")
    attempt++

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

suspend fun <T : CoreObject> playContext(client: SpotifyClient, context: T) {

  val player = client.clientAPI.player

  when (context) {
    is Track -> player.startPlayback(tracksToPlay = listOf(context.uri)).queue()
    is SimpleAlbum -> player.startPlayback(collection = context.uri).queue()
    is SimplePlaylist -> player.startPlayback(collection = context.uri).queue()
    is Artist -> player.startPlayback(tracksToPlay = context.api.artists.getArtistAlbums(context.uri.uri).complete()
      .getAllItemsNotNull().complete()
      .mapNotNull { it.toFullAlbum().complete() }
      .mapNotNull { it.tracks }.flatten()
      .mapNotNull { it?.uri }.toList()
    ).queue()
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
