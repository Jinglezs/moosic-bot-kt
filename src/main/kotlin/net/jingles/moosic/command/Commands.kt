package net.jingles.moosic.command

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.reflections.Reflections
import java.util.*

/**
 * Locates and stores all of the commands.
 */
object CommandManager {

  internal val commands: List<Command> = Reflections("net.jingles.moosic.command.impl")
    .getTypesAnnotatedWith(CommandMeta::class.java)
    .map { it.getConstructor().newInstance() }
    .filterIsInstance(Command::class.java)

  init {
    println("Loaded ${commands.size} commands.")
  }

  @SubscribeEvent
  fun onCommandSend(event: MessageReceivedEvent) {

    println("Message received!")

    val rawContent = event.message.contentRaw.toLowerCase()

    if (!rawContent.startsWith("::")) return

    val trigger = rawContent.removePrefix("::").split(" ")[0]
    val command = commands.find { it.meta.triggers.any { trig -> trig == trigger } } ?: return

    val context = CommandContext(event)

    if (context.arguments.size < command.meta.minArgs) {
      event.channel.sendMessage("Invalid arguments. Use ::help <category> for more information")
      return
    }

    try {
      command.job = GlobalScope.async {

        println("Executing command asynchronously!")
        command.execute(context)

      }
    } catch (exception: CommandException) {
      context.message.channel.sendMessage(exception.message)
    }

  }

}

/**
 * A base class that all commands implement. Defines the command meta
 * and a function that is executed when a user calls the command.
 */
abstract class Command {

  val meta = javaClass.getAnnotation(CommandMeta::class.java)
  var job: Deferred<Unit>? = null

  /**
   * Determines what happens when the command is called.
   * @param context information about the message containing the command
   */
  abstract suspend fun execute(context: CommandContext)

}

class CommandException(override val message: String) : RuntimeException(message)

/**
 * Represents command information that remains constant throughout its lifetime.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CommandMeta(
  val category: Category,
  val triggers: Array<String>,
  val description: String,
  val minArgs: Int = 0
)

/**
 * Represents the type of a command / its general purpose
 */
enum class Category {

  PARTY,
  GENERAL,
  SPOTIFY,
  GAME;

  fun getDisplayTitle(): String {
    return name.toLowerCase().capitalize()
  }

}

/**
 * Holds information about a message that contains a command.
 */
class CommandContext(event: MessageReceivedEvent) {

  val arguments: LinkedList<String> = LinkedList(event.message.contentStripped.split(" ").drop(1))
  val userId: String = event.author.id
  val channelId: String = event.channel.id
  val message: Message = event.message
  val jda: JDA = event.jda

  fun getArgCount(): Int = arguments.size

}

fun String.toUser(jda: JDA): User? = jda.getUserById(this)

fun String.toChannel(jda: JDA): TextChannel? = jda.getTextChannelById(this)
