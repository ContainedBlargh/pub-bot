import org.javacord.api.DiscordApi
import org.javacord.api.entity.server.Server
import org.javacord.api.event.interaction.SlashCommandCreateEvent
import org.javacord.api.interaction.SlashCommandBuilder

object DiscordUtils {
    private val registeredCommands: MutableMap<String, (SlashCommandCreateEvent) -> Unit> = mutableMapOf()

    fun DiscordApi.addSlashCommand(
        server: Server,
        commandBuilder: SlashCommandBuilder,
        commandHandler: (SlashCommandCreateEvent) -> Unit
    ) {
        val built = commandBuilder
            .setDefaultPermission(true)
            .createForServer(server)
            .join()
        if (registeredCommands.isEmpty()) {
            this.addSlashCommandCreateListener {
                val name = it.slashCommandInteraction.commandName
                registeredCommands[name]?.invoke(it)
            }
        }
        registeredCommands[built.name] = commandHandler
    }
}