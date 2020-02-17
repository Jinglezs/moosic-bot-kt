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
import net.jingles.moosic.toUnicodeEmoji
import java.awt.Color
import java.time.Instant

private val SYMBOLS = mapOf(
  "left" to ":arrow_left:".toUnicodeEmoji(),
  "right" to ":arrow_right:".toUnicodeEmoji(),
  "stop" to ":stop:".toUnicodeEmoji()
)

private val NUMBERS = mapOf(
  1 to ":one:".toUnicodeEmoji(), 2 to ":two:".toUnicodeEmoji(),
  3 to ":three:".toUnicodeEmoji(), 4 to ":four:".toUnicodeEmoji(),
  5 to ":five:".toUnicodeEmoji(), 6 to ":six".toUnicodeEmoji(),
  7 to ":seven:".toUnicodeEmoji(), 8 to ":eight:".toUnicodeEmoji(),
  9 to ":nine:".toUnicodeEmoji(), 9 to ":nine:".toUnicodeEmoji(),
  10 to ":ten:".toUnicodeEmoji()
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
      addReaction(":arrow_left:".toUnicodeEmoji()).queue()
      addReaction(":stop_button:".toUnicodeEmoji()).queue()
      addReaction(":arrow_right".toUnicodeEmoji()).queue()
    }

    this.messageId = message.idLong
    this.listeners = arrayOf(listeners)

    jda = channel.jda
    jda.addEventListener(listeners)

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

class PaginatedSelectionReactionListener(private val message: PaginatedSelection<*>) {

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
