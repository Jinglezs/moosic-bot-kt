package net.jingles.moosic

import com.adamratzman.spotify.models.*
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Formatting for numbers and dates

fun Int.format() = "%,d".format(this)

fun Double.format(digits: Int) = "%,.${digits}f".format(this)

fun Float.toPercent(): String = NumberFormat.getPercentInstance().format(this.toDouble())

fun String.toLocalTime(): LocalDateTime = LocalDateTime.parse(this)

fun LocalTime.toSimpleReadable(): String = this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))

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
