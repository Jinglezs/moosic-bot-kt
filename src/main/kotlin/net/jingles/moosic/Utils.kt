package net.jingles.moosic

import com.adamratzman.spotify.models.*
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Formatting for numbers and dates

fun Int.format() = "%,d".format(this)

fun Double.format(digits: Int) = "%,.${digits}f".format(this)

fun Float.toPercent(): String = NumberFormat.getPercentInstance().format(this.toDouble())

fun String.toZonedTime(): ZonedDateTime = ZonedDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)

fun ZonedDateTime.toReadable(): String = this.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL))

// Conversion from Spotify objects to readable Strings

fun Iterable<SimpleArtist>.toNames() = joinToString { it.name }

fun Iterable<Artist>.toSimpleNames() = joinToString { it.name }

fun Iterable<Artist>.toNumberedArtists() =
  mapIndexed { index, artist -> "${index + 1}. ${artist.name}" }.joinToString("\n")

fun SimpleTrack.toTrackInfo() = "$name by ${artists.toNames()}"

fun Track.toSimpleTrackInfo() = "$name by ${artists.toNames()}"

fun Iterable<SimpleTrack>.toNumberedTrackInfo() =
  mapIndexed { index, track -> "${index + 1}. ${track.toTrackInfo()}" }.joinToString("\n")

fun Iterable<Track>.toSimpleNumberedTrackInfo() =
  mapIndexed { index, track -> "${index + 1}. ${track.toSimpleTrackInfo()}" }.joinToString("\n")

fun SimpleAlbum.toAlbumInfo() = "$name by ${artists.toNames()}"

fun Iterable<SimpleAlbum>.toAlbumInfo() = joinToString("\n") { it.toAlbumInfo() }
