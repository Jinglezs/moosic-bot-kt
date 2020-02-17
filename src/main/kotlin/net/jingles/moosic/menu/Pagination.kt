package net.jingles.moosic.menu

import com.adamratzman.spotify.models.AbstractPagingObject
import com.adamratzman.spotify.models.Artist
import com.adamratzman.spotify.models.PagingObject
import com.adamratzman.spotify.models.Track
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ChannelType
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
  protected var pagingObject: AbstractPagingObject<T>,
  private val timeout: Long,
  title: String
) {

  private lateinit var listener: ReactionListener
  private lateinit var jda: JDA
  private lateinit var job: Job
  var messageId: Long = 0

  private val builder = EmbedBuilder()
    .setTitle(title)
    .setColor(Color.WHITE)
    .setFooter("Powered by Spotify", SPOTIFY_ICON)
    .setTimestamp(Instant.now())

  /**
   * Determines the content that is displayed on the current page
   */
  abstract suspend fun getContent(): String

  /**
   * Draws the items of either the current, previous, or next page.
   * @param direction negative for previous, 0 for current, positive for next
   */
  suspend fun render(direction: Int): MessageEmbed {

    pagingObject = when (direction) {
      -1 -> pagingObject.getPrevious()
      1 -> pagingObject.getNext()
      else -> pagingObject
    } ?: pagingObject

    return builder.setDescription(getContent()).build()

  }

  /**
   * Sends the original message in the provided channel, adds the emoji
   * controls, and stores the message ID for future use.
   */
  suspend fun create(channel: MessageChannel) {

    val message = channel.sendMessage(render(0)).complete()

    with(message) {
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

  /**
   * Unregisters the event listener and cancels the timeout job
   */
  fun stop() {
    job.cancel()
    jda.removeEventListener(listener)
  }

}

class ReactionListener(private val message: PaginatedMessage<*>) {

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != message.messageId || event.user?.isBot == true) return

    val reaction = event.reaction.reactionEmote.emoji

    if (reaction == STOP) {
      message.stop(); return
    }

    val direction = when (reaction) {
      LEFT -> -1
      RIGHT -> 1
      else -> 0
    }

    if (event.channelType != ChannelType.PRIVATE) {
      event.user?.let { event.reaction.removeReaction(it).queue() }
    }

    GlobalScope.launch {
      event.channel.editMessageById(event.messageId, message.render(direction)).queue()
    }

  }

}

class OrderedTracksMessage(tracks: PagingObject<Track>, timeout: Long, title: String) :
  PaginatedMessage<Track>(tracks, timeout, title) {

  override suspend fun getContent(): String =
    pagingObject.items.toSimpleNumberedTrackInfo(pagingObject.offset)

}

class OrderedArtistsMessage(artists: PagingObject<Artist>, timeout: Long, title: String) :
  PaginatedMessage<Artist>(artists, timeout, title) {

  override suspend fun getContent(): String = with (pagingObject) { this.toNumberedArtists(offset) }

}