package net.jingles.moosic.command.impl

import net.dv8tion.jda.api.EmbedBuilder
import net.jingles.moosic.command.*
import java.awt.Color
import java.time.Instant

@CommandMeta(category = Category.GENERAL, triggers = ["help"], minArgs = 0,
  description = "You wouldn't be seeing this if you didn't know what it does.")
class HelpCommand : Command() {

  override suspend fun execute(context: CommandContext) {

    if (context.getArgCount() == 0) {
      val message = "Resend the command with a category: ${Category.values().joinToString(", ") { it.name.toLowerCase() } }"
      throw CommandException(message)
    }

    val category = Category.valueOf(context.arguments.pollFirst())

    val description = CommandManager.commands.filter { it.meta.category == category }
      .joinToString("\n") {
        "::${it.meta.triggers[0]}   -   ${it.meta.description}"
      }

    val embed = EmbedBuilder()
      .setTitle("${category.getDisplayTitle()} Commands")
      .setDescription(description)
      .setColor(Color.WHITE)
      .setTimestamp(Instant.now())
      .build()

    context.message.channel.sendMessage(embed).queue()

  }

}