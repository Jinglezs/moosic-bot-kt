package net.jingles.moosic

import com.adamratzman.spotify.models.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.jingles.moosic.service.SpotifyClient
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

private const val BOLD = "**%s**"

// Conversions to Discord objects

fun Long.toUser(jda: JDA) = jda.getUserById(this)

fun Long.toMessage(channel: MessageChannel): Message = channel.retrieveMessageById(this).complete()

// Formatting for dates, numbers, and Strings

fun Int.format() = "%,d".format(this)

fun Double.format(digits: Int) = "%,.${digits}f".format(this)

fun Float.toPercent(): String = NumberFormat.getPercentInstance().format(this.toDouble())

fun Double.toPercent(): String = NumberFormat.getPercentInstance().format(this)

fun String.toZonedTime(): ZonedDateTime = ZonedDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)

fun ZonedDateTime.toReadable(): String = this.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL))

fun String.boldOnIndex(index: Int, bold: Int) = if (index == bold) String.format(BOLD, this) else this

// Conversion from Spotify objects to readable Strings

inline fun <T> Iterable<T>.toNumbered(offset: Int = 0, bold: Int = -1, composer: T.() -> String) =
  mapIndexed { index, element ->
    "${index + offset + 1}. ${composer.invoke(element).boldOnIndex(index, bold)}"
  }.joinToString("\n")

inline fun <T> Iterable<T>.toUnnumbered(crossinline composer: T.() -> String) =
  joinToString("\n") { composer.invoke(it) }

fun Iterable<SimpleArtist>.toNames() = joinToString { it.name }

fun SimpleTrack.toTrackInfo() = "$name by ${artists.toNames()}"

fun Track.toSimpleTrackInfo() = "$name by ${artists.toNames()}"

fun SimpleAlbum.toAlbumInfo() = "$name by ${artists.toNames()}"

fun SimplePlaylist.toPlaylistInfo() = "$name by ${owner.displayName} (${tracks.total} Tracks)"

// Other String stuff

fun String.percentMatch(other: String): Double {
  val levenshteinDistance = levenshtein(this, other)
  val largestLength = length.coerceAtLeast(other.length)
  return (largestLength - levenshteinDistance) / largestLength.toDouble()
}

private fun levenshtein(lhs : CharSequence, rhs : CharSequence) : Int {
  val lhsLength = lhs.length
  val rhsLength = rhs.length

  var cost = Array(lhsLength) { it }
  var newCost = Array(lhsLength) { 0 }

  for (i in 1 until rhsLength) {
    newCost[0] = i

    for (j in 1 until lhsLength) {
      val match = if(lhs[j - 1] == rhs[i - 1]) 0 else 1

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

fun SpotifyClient.getRandomPlaylistTracks(limit: Int): LinkedList<Track> {

  val populatedList = LinkedList<Track>()

  val playlists = clientAPI.playlists.getClientPlaylists().complete().items
  val erroneousPlaylists = mutableListOf<SimplePlaylist>()

  while (populatedList.size < limit) {

    val simple = playlists.random()
    if (erroneousPlaylists.contains(simple)) continue

    try {

      val full = simple.toFullPlaylist().complete() ?: continue

      val track = full.tracks.random().track
      if (track != null) populatedList.add(track)

    } catch (exception: Exception) {
      erroneousPlaylists.add(simple)
      println("Error parsing playlist: Local track files are currently unsupported.")
      continue
    }

  }

  return populatedList

}