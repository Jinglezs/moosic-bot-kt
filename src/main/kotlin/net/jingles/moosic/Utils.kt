package net.jingles.moosic

import com.adamratzman.spotify.models.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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