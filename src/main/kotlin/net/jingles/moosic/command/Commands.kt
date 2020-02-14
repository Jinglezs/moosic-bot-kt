package net.jingles.moosic.command

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    val rawContent = event.message.contentRaw.toLowerCase()

    // Ignore messages that do not start with ::
    if (!rawContent.startsWith("::")) return

    // Remove the prefix and split by spaces
    val trigger = rawContent.removePrefix("::").split(" ")[0]

    // Find a command with a matching trigger
    val command = commands.find { it.meta.triggers.any { trig -> trig == trigger } } ?: return

    // Create context based on the event
    val context = CommandContext(event)

    // Terminate early if the arguments are guaranteed to fail
    if (context.arguments.size < command.meta.minArgs) {
      event.channel.sendMessage("Invalid arguments. Use ::help <category> for more information").queue()
      return
    }

    // Asynchronously execute the command logic
    command.job = GlobalScope.launch {

      try {
        command.execute(context)
      } catch (exception: RuntimeException) {

        println("\n${exception.javaClass.simpleName}: ${exception.message}. Location: ${exception.stackTrace[0]
          .methodName}, line ${exception.stackTrace[0].lineNumber}\n")

        exception.printStackTrace(); println()

        val message = when (exception) {
          is CommandException -> exception.message
          else -> exception.localizedMessage ?: "Unknown cause."
        }

        context.event.channel.sendMessage("Error processing command: $message").queue()

      }

    }

  }

}

/**
 * A base class that all commands implement. Defines the command meta
 * and a function that is executed when a user calls the command.
 */
abstract class Command {

  val meta = javaClass.getAnnotation(CommandMeta::class.java)!!
  var job: Job? = null

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
class CommandContext(val event: MessageReceivedEvent) {

  val arguments: LinkedList<String> = LinkedList(event.message.contentStripped.split(" ").drop(1))
  fun getArgCount(): Int = arguments.size

}
