package net.jingles.moosic.menu

import com.adamratzman.spotify.models.Artist
import com.adamratzman.spotify.models.PagingObject
import com.adamratzman.spotify.models.Track
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.jingles.moosic.SPOTIFY_ICON
import net.jingles.moosic.toNumberedArtists
import net.jingles.moosic.toSimpleNumberedTrackInfo
import java.awt.Color
import java.time.Instant

private const val LEFT = "\u25C0"
private const val RIGHT = "\u25B6"
private const val STOP = "\u23F9"

abstract class PaginatedMessage<T : Any>(
  protected val pagingObject: PagingObject<T>,
  protected val title: String,
  private val timeout: Long
) {

  private lateinit var listener: ReactionListener
  private lateinit var jda: JDA
  private lateinit var job: Job
  var messageId: Long = 0

  /**
   * Draws the items of either the current, previous, or next page.
   * @param direction negative for previous, 0 for current, positive for next
   */
  abstract suspend fun render(direction: Int): MessageEmbed

  suspend fun create(channel: MessageChannel) {

    val message = channel.sendMessage(render(0)).complete()

    with (message) {
      addReaction(LEFT).queue()
      addReaction(STOP).queue()
      addReaction(RIGHT).queue()
    }

    jda = channel.jda
    messageId = message.idLong

    listener = ReactionListener(this)
    jda.addEventListener(listener)

    job = GlobalScope.launch {
      delay(timeout)
      stop()
    }

  }

  fun stop() {
    job.cancel()
    jda.removeEventListener(listener)
  }

}

class ReactionListener(private val message: PaginatedMessage<*>) {

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    println("Reaction event received! >:VV")

    if (event.messageIdLong != message.messageId) return

    println("They are the same message too! >:VVV")

    val reaction = event.reaction.reactionEmote.emoji

    if (reaction == STOP) {
      println("Stopping reaction listener")
      message.stop(); return
    }

    val direction = when (reaction) {
      LEFT -> -1
      RIGHT -> 1
      else -> 0
    }

    println("Received directional command: $direction")

    event.reaction.removeReaction().queue()

    GlobalScope.launch {
      event.channel.editMessageById(event.messageId, message.render(direction)).queue {
        println("Edited the message!")
      }
    }

  }

}

class OrderedTracksMessage(tracks: PagingObject<Track>, title: String, timeout: Long) :
  PaginatedMessage<Track>(tracks, title, timeout) {

  private val builder = EmbedBuilder()
    .setTitle(title)
    .setColor(Color.WHITE)
    .setFooter("Powered by Spotify", SPOTIFY_ICON)
    .setTimestamp(Instant.now())

  override suspend fun render(direction: Int): MessageEmbed {

    val items = when (direction) {
      -1 -> pagingObject.getPrevious()?.items
      1 -> pagingObject.getNext()?.items
      else -> pagingObject.items
    } ?: pagingObject.items

    return builder.setDescription(items.toSimpleNumberedTrackInfo()).build()

  }

}

class OrderedArtistsMessage(artists: PagingObject<Artist>, title: String, timeout: Long) :
  PaginatedMessage<Artist>(artists, title, timeout) {

  private val builder = EmbedBuilder()
    .setTitle(title)
    .setColor(Color.WHITE)
    .setFooter("Powered by Spotify", SPOTIFY_ICON)
    .setTimestamp(Instant.now())

  override suspend fun render(direction: Int): MessageEmbed {

    val items = when (direction) {
      -1 -> pagingObject.getPrevious()?.items
      1 -> pagingObject.getNext()?.items
      else -> pagingObject.items
    } ?: pagingObject.items

    return builder.setDescription(items.toNumberedArtists()).build()

  }

}