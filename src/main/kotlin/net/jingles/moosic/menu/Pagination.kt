package net.jingles.moosic.menu

import com.adamratzman.spotify.SpotifyException
import com.adamratzman.spotify.models.AbstractPagingObject
import com.adamratzman.spotify.models.CursorBasedPagingObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.jingles.moosic.SPOTIFY_ICON
import java.awt.Color
import java.time.Instant

private val SYMBOLS = mapOf(
  "left" to "\u25C0", "right" to "\u25B6", "stop" to "\u23F9",
  "up" to "\u2B06", "down" to "\u2B07", "check" to "\u2611"
)

private val NUMBERS = mapOf(
  1 to "1\u20E3", 2 to "2\u20E3", 3 to "3\u20E3",
  4 to "4\u20E3", 5 to "5\u20E3", 6 to "6\u20E3",
  7 to "7\u20E3", 8 to "8\u20E3", 9 to "9\u20E3",
  10 to "\uD83D\uDD1F"
)

abstract class Menu<T : Any>(
  internal var pagingObject: List<T>,
  internal val limit: Int = 5,
  private val timeout: Long,
  title: String,
  protected val composer: Menu<T>.() -> Unit
) {

  internal val builder = EmbedBuilder()
    .setTitle(title)
    .setColor(Color.WHITE)
    .setFooter("Powered by Spotify", SPOTIFY_ICON)
    .setTimestamp(Instant.now())

  private lateinit var jda: JDA
  private lateinit var job: Job
  private lateinit var channel: Pair<Long, ChannelType>

  internal var messageId: Long = 0
  internal var offset: Int = 0

  open suspend fun create(channel: MessageChannel): Message {

    val message = channel.sendMessage(render()).complete()

    this.channel = Pair(channel.idLong, channel.type)
    this.messageId = message.idLong

    jda = channel.jda
    jda.addEventListener(this)

    job = GlobalScope.launch {
      delay(timeout)
      stop()
    }

    return message

  }

  open fun render(): MessageEmbed {
    composer.invoke(this)
    return builder.build()
  }

  abstract suspend fun get(direction: Int? = 0): List<T>

  /**
   * Unregisters the event listener and cancels the timeout job
   */
  open fun stop() {

    job.cancel()
    jda.removeEventListener(this)

    val message = when (channel.second) {
      ChannelType.TEXT -> jda.getTextChannelById(channel.first)?.retrieveMessageById(messageId)?.complete()
      ChannelType.PRIVATE -> jda.getPrivateChannelById(channel.first)?.retrieveMessageById(messageId)?.complete()
      else -> null
    } ?: return

    message.reactions.forEach { it.removeReaction().queue() }

  }

}

open class PaginatedMessage<T : Any>(
  pagingObject: List<T>,
  limit: Int = 5,
  timeout: Long,
  title: String,
  composer: PaginatedMessage<T>.() -> Unit
) : Menu<T>(pagingObject, limit, timeout, title, composer as Menu<T>.() -> Unit) {

  override suspend fun create(channel: MessageChannel): Message {

    with(super.create(channel)) {

      if (pagingObject !is CursorBasedPagingObject) addReaction(SYMBOLS["left"]!!).queue()
      addReaction(SYMBOLS["stop"]!!).queue()
      addReaction(SYMBOLS["right"]!!).queue()

      return this

    }

  }

  override suspend fun get(direction: Int?): List<T> {

    if (pagingObject is AbstractPagingObject<T>) {

      with (pagingObject as AbstractPagingObject<T>) {
        try {

          pagingObject = when (direction) {
            -1 -> if (previous != null) getPrevious()!! else pagingObject
            1 -> if (next != null) getNext()!! else pagingObject
            else -> pagingObject
          }

        } catch (e: SpotifyException) {
          println("${pagingObject.javaClass.simpleName}: Unable to parse the next paging object.")
        }

        return items
      }

    } else return pagingObject.subList(offset, offset + limit)

  }

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != messageId || event.user?.isBot == true) return

    val direction = handleReactionEvent(event)

    if (direction == 0) {
      stop(); return
    }

    GlobalScope.launch {
      event.channel.editMessageById(event.messageId, render()).queue()
    }

  }

}

/**
 * An extended version of the default PaginatedMessage that includes number emotes that
 * represents selections the user may choose from.
 */
class PaginatedSelection<T : Any>(
  pagingObject: AbstractPagingObject<T>,
  limit: Int = 5,
  timeout: Long,
  title: String,
  composer: PaginatedSelection<T>.() -> Unit,
  private val afterSelection: Menu<T>.(T) -> MessageEmbed
) : Menu<T>(pagingObject, limit, timeout, title, composer as Menu<T>.() -> Unit) {

  internal var currentSelection: Int = 1

  override suspend fun create(channel: MessageChannel): Message {

    with(super.create(channel)) {

      addReaction(SYMBOLS["up"]!!).queue()
      addReaction(SYMBOLS["down"]!!).queue()
      addReaction(SYMBOLS["check"]!!).queue()

      return this

    }

  }

  override suspend fun get(direction: Int?): List<T> {

    if (pagingObject is AbstractPagingObject<T>) {

      pagingObject = with (pagingObject as AbstractPagingObject<T>) {

        try {

          val newSelection = currentSelection + direction!!

          when {

            newSelection < 1 -> {
              currentSelection = limit
              getPrevious() ?: pagingObject
            }

            newSelection > limit -> {
              currentSelection = 1
              getNext() ?: pagingObject
            }

            else -> {
              currentSelection = newSelection
              pagingObject
            }

          }

        } catch (exception: SpotifyException) {
          println("${pagingObject.javaClass.simpleName}: Unable to parse the previous/next paging object.")
          pagingObject
        }

        return items

      }

    } else return pagingObject.subList(offset, (offset + limit).coerceAtMost(pagingObject.size - 1))

  }

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != messageId || event.user?.isBot == true) return

    val direction = handleReactionEvent(event)

    GlobalScope.launch {

      val embed = when(direction) {
        -1, 1 ->
        else -> {
          stop()
          afterSelection.invoke(this@PaginatedSelection, pagingObject[currentSelection - 1])
        }
      }

      event.channel.editMessageById(event.messageId, embed).queue()

    }

  }

}

class ImageSlideshow(private val builder: EmbedBuilder, private val images: List<String>) {

  private var messageId: Long = 0
  private var index = 0

  fun create(channel: MessageChannel, timeout: Long) {

    if (images.isEmpty()) throw IllegalStateException("The provided image url list is empty.")

    builder.setImage(images[0])

    with (channel.sendMessage(builder.build()).complete()) {
      messageId = idLong
      addReaction(SYMBOLS["left"]!!).queue()
      addReaction(SYMBOLS["right"]!!).queue()
    }

    channel.jda.addEventListener(this)

    GlobalScope.launch {
      delay(timeout)
      channel.jda.removeEventListener(this)
    }

  }

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != messageId || event.user?.isBot == true) return

    index += handleReactionEvent(event)

    when {
      index >= images.size -> index = 0
      index < 0 -> index = 0
    }

    builder.setImage(images[index])
    event.channel.editMessageById(messageId, builder.build()).queue()

  }

}

private fun handleReactionEvent(event: MessageReactionAddEvent): Int {

  val reaction = event.reaction.reactionEmote.emoji

  if (event.channelType != ChannelType.PRIVATE) {
    event.user?.let { event.reaction.removeReaction(it).queue() }
  }

  return when (reaction) {
    SYMBOLS["left"], SYMBOLS["up"] -> -1
    SYMBOLS["right"], SYMBOLS["down"] -> 1
    else -> 0
  }

}
