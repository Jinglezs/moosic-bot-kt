package net.jingles.moosic.menu

import com.adamratzman.spotify.models.AbstractPagingObject
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
  "left" to "\u25C0", "right" to "\u25B6", "stop" to "\u23F9"
)

private val NUMBERS = mapOf(
  1 to "1\u20E3", 2 to "2\u20E3", 3 to "3\u20E3",
  4 to "4\u20E3", 5 to "5\u20E3", 6 to "6\u20E3",
  7 to "7\u20E3", 8 to "8\u20E3", 9 to "9\u20E3",
  10 to "\uD83D\uDD1F"
)

abstract class Menu<T : Any>(
  protected var pagingObject: AbstractPagingObject<T>,
  private val timeout: Long,
  title: String,
  protected val composer: (AbstractPagingObject<T>, EmbedBuilder) -> Unit
) {

  protected val builder = EmbedBuilder()
    .setTitle(title)
    .setColor(Color.WHITE)
    .setFooter("Powered by Spotify", SPOTIFY_ICON)
    .setTimestamp(Instant.now())

  private lateinit var jda: JDA
  private lateinit var job: Job
  private lateinit var listeners: Array<Any>
  internal var messageId: Long = 0

  open suspend fun create(channel: MessageChannel, vararg listeners: Any): Message {

    val message = channel.sendMessage(render(0)).complete()

    with(message) {
      addReaction(SYMBOLS["left"]!!).queue()
      addReaction(SYMBOLS["stop"]!!).queue()
      addReaction(SYMBOLS["right"]!!).queue()
    }

    this.messageId = message.idLong
    this.listeners = arrayOf(*listeners)

    jda = channel.jda
    jda.addEventListener(*listeners)

    job = GlobalScope.launch {
      delay(timeout)
      stop()
    }

    return message

  }

  abstract suspend fun render(direction: Int? = 0): MessageEmbed

  /**
   * Unregisters the event listener and cancels the timeout job
   */
  open fun stop() {
    job.cancel()
    jda.removeEventListener(*listeners)
  }

}

open class PaginatedMessage<T : Any>(
  pagingObject: AbstractPagingObject<T>,
  timeout: Long,
  title: String,
  composer: (AbstractPagingObject<T>, EmbedBuilder) -> Unit
) : Menu<T>(pagingObject, timeout, title, composer) {

  /**
   * Draws the items of either the current, previous, or next page.
   * @param direction negative for previous, 0 for current, positive for next
   */
  override suspend fun render(direction: Int?): MessageEmbed {

    pagingObject = when (direction) {
      -1 -> pagingObject.getPrevious()
      1 -> pagingObject.getNext()
      else -> pagingObject
    } ?: pagingObject

    composer.invoke(pagingObject, builder)
    return builder.build()

  }

}

/**
 * A reaction listener for the default PaginatedMessage in which clicking the
 * left arrow displays the previous set of items from the PagingObject, the
 * right displays the next set of items, and stop unregisters the listener.
 */
class PaginatedReactionListener(private val message: PaginatedMessage<*>) {

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != message.messageId || event.user?.isBot == true) return

    val reaction = event.reaction.reactionEmote.emoji

    if (reaction == SYMBOLS["stop"]) {
      message.stop(); return
    }

    val direction = when (reaction) {
      SYMBOLS["left"] -> -1
      SYMBOLS["right"] -> 1
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

/**
 * An extended version of the default PaginatedMessage that includes number emotes that
 * represents selections the user may choose from.
 */
class PaginatedSelection<T : Any>(
  pagingObject: AbstractPagingObject<T>,
  timeout: Long,
  title: String,
  composer: (AbstractPagingObject<T>, EmbedBuilder) -> Unit,
  private val afterSelection: (T, EmbedBuilder) -> MessageEmbed
) : PaginatedMessage<T>(pagingObject, timeout, title, composer) {

  override suspend fun create(channel: MessageChannel, vararg listeners: Any): Message {

    if (pagingObject.limit > 10)
      throw IllegalArgumentException("The paging object's limit must be 10 items or less.")

    with (super.create(channel, *listeners)) {

      for  (option in 1..pagingObject.limit) {
        addReaction(NUMBERS[option]!!).queue()
      }

      return this

    }

  }

  fun onSelection(selection: Int): MessageEmbed {

    val selected: T = pagingObject[selection]
    stop()

    return afterSelection.invoke(selected, builder)

  }

}

class SelectionReactionListener(private val message: PaginatedSelection<*>) {

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != message.messageId || event.user?.isBot == true) return

    val reaction = event.reaction.reactionEmote.emoji

    val numericalValue = NUMBERS.entries
      .firstOrNull { it.component2() == reaction }?.key

    if (numericalValue != null) {
      GlobalScope.launch {
        event.channel.editMessageById(event.messageId, message.onSelection(numericalValue))
      }
    }

  }

}
