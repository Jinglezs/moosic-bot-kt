package net.jingles.moosic

import com.adamratzman.spotify.models.*
import com.vdurmont.emoji.EmojiParser
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Conversions to Discord objects

fun Long.toUser(jda: JDA) = jda.getUserById(this)

fun Long.toMessage(channel: MessageChannel): Message = channel.retrieveMessageById(this).complete()

fun String.toUnicodeEmoji(): String = EmojiParser.parseToUnicode(this)

// Formatting for numbers and dates

fun Int.format() = "%,d".format(this)

fun Double.format(digits: Int) = "%,.${digits}f".format(this)

fun Float.toPercent(): String = NumberFormat.getPercentInstance().format(this.toDouble())

fun Double.toPercent(): String = NumberFormat.getPercentInstance().format(this)

fun String.toZonedTime(): ZonedDateTime = ZonedDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)

fun ZonedDateTime.toReadable(): String = this.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL))

// Conversion from Spotify objects to readable Strings

fun Iterable<SimpleArtist>.toNames() = joinToString { it.name }

fun Iterable<Artist>.toSimpleNames() = joinToString { it.name }

fun Iterable<Artist>.toNumberedArtists(offset: Int = 0) =
  mapIndexed { index, artist -> "${index + offset + 1}. ${artist.name}" }.joinToString("\n")

fun SimpleTrack.toTrackInfo() = "$name by ${artists.toNames()}"

fun Track.toSimpleTrackInfo() = "$name by ${artists.toNames()}"

fun Iterable<SimpleTrack>.toNumberedTrackInfo(offset: Int = 0) =
  mapIndexed { index, track -> "${index + offset + 1}. ${track.toTrackInfo()}" }.joinToString("\n")

fun Iterable<Track>.toSimpleNumberedTrackInfo(offset: Int = 0) =
  mapIndexed { index, track -> "${index + offset + 1}. ${track.toSimpleTrackInfo()}" }.joinToString("\n")

fun SimpleAlbum.toAlbumInfo() = "$name by ${artists.toNames()}"

fun Iterable<SimpleAlbum>.toAlbumInfo() = joinToString("\n") { it.toAlbumInfo() }

fun Iterable<SimpleAlbum>.toNumberedAlbumInfo(offset: Int = 0) =
  mapIndexed { index, album -> "${index + offset + 1}. ${album.toAlbumInfo()}" }.joinToString("\n")

fun SimplePlaylist.toPlaylistInfo() = "$name by ${owner.displayName} (${tracks.total} Tracks)"

fun Iterable<SimplePlaylist>.toNumberedPlaylistInfo(offset: Int = 0) =
  mapIndexed { index, playlist -> "${index + offset + 1}. ${playlist.toPlaylistInfo()}" }.joinToString("\n")
