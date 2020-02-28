package net.jingles.moosic.reaction

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
  "up" to "\u2B06", "down" to "\u2B07", "check" to "\u2611",
  "upvote" to "\u1F44D", "downvote" to "\u1F44E"
)

private val NUMBERS = mapOf(
  1 to "1\u20E3", 2 to "2\u20E3", 3 to "3\u20E3",
  4 to "4\u20E3", 5 to "5\u20E3", 6 to "6\u20E3",
  7 to "7\u20E3", 8 to "8\u20E3", 9 to "9\u20E3",
  10 to "\uD83D\uDD1F"
)

/**
 * Represents an embedded message that is meant to be controlled
 * via reactions in order to display information.
 */
abstract class Menu<T : Any>(
  internal val handler: ListHandler<T>? = null,
  private val timeout: Long,
  title: String,
  protected val composer: Menu<T>.() -> Unit
) {

  // A template for all menus
  internal val builder = EmbedBuilder()
    .setTitle(title)
    .setColor(Color.WHITE)
    .setFooter("Powered by Spotify", SPOTIFY_ICON)
    .setTimestamp(Instant.now())

  // Used to identify and control the this message
  private lateinit var jda: JDA
  private lateinit var job: Job
  private lateinit var channel: Pair<Long, ChannelType>
  internal var messageId: Long = 0

  // Used for displaying information
  internal lateinit var currentElements: List<T>

  open suspend fun create(channel: MessageChannel): Message {

    if (handler != null) currentElements = handler.getCurrent()
    val message = channel.sendMessage(render(0)).complete()

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

  abstract suspend fun render(direction: Int? = 0): MessageEmbed

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

    message.reactions.map { it.reactionEmote }.forEach {
      when {
        it.isEmote -> message.removeReaction(it.emote).queue()
        it.isEmoji -> message.removeReaction(it.emoji).queue()
      }
    }

  }

}

open class PaginatedMessage<T : Any>(
  handler: ListHandler<T>,
  timeout: Long,
  title: String,
  composer: PaginatedMessage<T>.() -> Unit
) : Menu<T>(handler, timeout, title, composer as Menu<T>.() -> Unit) {

  override suspend fun create(channel: MessageChannel): Message {

    with(super.create(channel)) {

      if (handler?.list !is CursorBasedPagingObject) addReaction(SYMBOLS["left"]!!).queue()
      addReaction(SYMBOLS["stop"]!!).queue()
      addReaction(SYMBOLS["right"]!!).queue()

      return this

    }

  }

  /**
   * Draws the items of either the current, previous, or next page.
   * @param direction negative for previous, 0 for current, positive for next
   */
  override suspend fun render(direction: Int?): MessageEmbed {

    currentElements = when (direction) {
      -1 -> handler?.getPrevious()
      1 -> handler?.getNext()
      else -> handler?.getCurrent()
    } ?: emptyList()

    composer.invoke(this)
    return builder.build()

  }

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != messageId || event.user?.isBot == true) return

    val direction = handleReactionEvent(event)

    if (direction == 0) {
      stop(); return
    }

    GlobalScope.launch {
      event.channel.editMessageById(event.messageId, render(direction)).queue()
    }

  }

}

/**
 * An extended version of the default PaginatedMessage that includes number emotes that
 * represents selections the user may choose from.
 */
class PaginatedSelection<T : Any>(
  handler: ListHandler<T>,
  timeout: Long,
  title: String,
  composer: PaginatedSelection<T>.() -> Unit,
  private val afterSelection: Menu<T>.(T) -> MessageEmbed
) : Menu<T>(handler, timeout, title, composer as Menu<T>.() -> Unit) {

  internal var currentSelection: Int = 1

  override suspend fun create(channel: MessageChannel): Message {

    with(super.create(channel)) {

      addReaction(SYMBOLS["up"]!!).queue()
      addReaction(SYMBOLS["down"]!!).queue()
      addReaction(SYMBOLS["check"]!!).queue()

      return this

    }

  }

  override suspend fun render(direction: Int?): MessageEmbed {

    val newSelection = currentSelection + direction!!

    currentElements = when {

      newSelection < 1 -> {
        currentSelection = currentElements.size
        handler?.getPrevious()
      }

      newSelection > currentElements.size -> {
        currentSelection = 1
        handler?.getNext()
      }

      else -> {
        currentSelection = newSelection
        currentElements
      }

    } ?: emptyList()

    composer.invoke(this)
    return builder.build()

  }

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != messageId || event.user?.isBot == true) return

    val direction = handleReactionEvent(event)

    GlobalScope.launch {

      val embed = when(direction) {
        -1, 1 -> render(direction)
        else -> {
          stop()
          afterSelection.invoke(this@PaginatedSelection, currentElements[currentSelection - 1])
        }
      }

      event.channel.editMessageById(event.messageId, embed).queue()

    }

  }

}

// An embedded message whose image can be changed via arrow reactions like a slideshow
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

class ReactionVote<T : Any>(
  val topic: T,
  val maxVotes: Int,
  private val requirement: Double,
  timeout: Long,
  title: String,
  composer: ReactionVote<T>.() -> Unit,
  private val onSuccess: ReactionVote<T>.() -> MessageEmbed,
  private val onFailure: ReactionVote<T>.() -> MessageEmbed
) : Menu<T>(null, timeout, title, composer as Menu<T>.() -> Unit) {

  private val voters = mutableListOf<Long>()
  var upvotes = 0
  var downvotes = 0

  override suspend fun render(direction: Int?): MessageEmbed {
    composer.invoke(this)
    return builder.build()
  }

  @SubscribeEvent
  fun onReactionAdd(event: MessageReactionAddEvent) {

    if (event.messageIdLong != messageId || voters.contains(event.userIdLong)
      || event.user?.isBot == true) return

    voters.add(event.userIdLong)

    val direction = handleReactionEvent(event)

    when (direction) {
      -1 -> downvotes++; 1 -> upvotes++
    }

    GlobalScope.launch {

      val embed = when {

        upvotes / maxVotes.toDouble() > requirement -> {
          stop()
          onSuccess.invoke(this@ReactionVote)
        }

        downvotes / maxVotes.toDouble() > 1 - requirement -> {
          stop()
          onFailure.invoke(this@ReactionVote)
        }

        else -> render(direction)

      }

      event.channel.editMessageById(messageId, embed).queue()

    }

  }

}

private fun handleReactionEvent(event: MessageReactionAddEvent): Int {

  val reaction = event.reaction.reactionEmote.emoji

  if (event.channelType != ChannelType.PRIVATE) {
    event.user?.let { event.reaction.removeReaction(it).queue() }
  }

  return when (reaction) {
    SYMBOLS["left"], SYMBOLS["up"], SYMBOLS["downvote"] -> -1
    SYMBOLS["right"], SYMBOLS["down"], SYMBOLS["upvote"] -> 1
    else -> 0
  }

}

open class ListHandler<T: Any>(
  internal var list: List<T>,
  protected val limit: Int = 5
) {

  internal var offset: Int = 0

  open suspend fun getPrevious(): List<T> {
    // Subtract the limit from the offset, with a min value of 0
    offset = (offset - limit).coerceAtLeast(0)
    // End index is the offset plus the max elements per "page"
    val endIndex = (offset + limit).coerceAtMost(list.size)
    // Return the items in between the two indexes
    return list.subList(offset, endIndex)

  }

  open suspend fun getNext(): List<T> {
    // Add the limit to the offset, with a max value of the highest index
    offset = (offset + limit).coerceAtMost(list.size)
    // End index is the offset plus elements per "page," max value of the list size
    val endIndex = (offset + limit).coerceAtMost(list.size)
    // Return the items in between the two indexes
    return list.subList(offset, endIndex)
  }

  open fun getCurrent(): List<T> = list.subList(offset, (offset + limit).coerceAtMost(list.size))

}

class PagingObjectHandler<T: Any>(list: AbstractPagingObject<T>) : ListHandler<T>(list, list.limit) {

  override suspend fun getPrevious(): List<T> {

    with (list as AbstractPagingObject<T>) {

      list = try {
        getPrevious() ?: list
      } catch (e: SpotifyException) {
        println("${javaClass.simpleName}: Unable to parse the previous paging object.")
        list
      }

      super.offset = offset
      return items

    }

  }

  override suspend fun getNext(): List<T> {

    with (list as AbstractPagingObject<T>) {

      list = try {
        getNext() ?: list
      } catch (e: SpotifyException) {
        println("${javaClass.simpleName}: Unable to parse the next paging object.")
        list
      }

      super.offset = offset
      return items

    }

  }

}


