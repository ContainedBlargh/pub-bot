import DiscordUtils.addSlashCommand
import org.javacord.api.DiscordApiBuilder
import org.javacord.api.entity.message.MessageBuilder
import org.javacord.api.entity.message.MessageDecoration
import org.javacord.api.entity.server.Server
import org.javacord.api.event.interaction.SlashCommandCreateEvent
import org.javacord.api.interaction.SlashCommandBuilder
import org.javacord.api.interaction.SlashCommandOption
import org.javacord.api.interaction.SlashCommandOptionType
import org.javacord.api.interaction.callback.InteractionCallbackDataFlag
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.jvm.optionals.getOrNull

@OptIn(ExperimentalStdlibApi::class)
object Main {
    private val discordApi = DiscordApiBuilder()
        .setToken(Config.token)
        .login()
        .get()

    private val applicationId = discordApi.applicationInfo.join().clientId

    private fun subscribe(event: SlashCommandCreateEvent) {
        val interaction = event.slashCommandInteraction
        val user = interaction.user
        val server = interaction.server.getOrNull()
        val channel = interaction.channel.getOrNull()
        if (server == null || channel == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Can't notify outside of a particular TextChannel")
                .respond()
                .join()
            return
        }
        val topicName = interaction.arguments.firstOrNull() { it.name == "topic" }?.stringValue?.getOrNull()
        if (topicName == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Can't subscribe without a topic.")
                .respond()
                .join()
            return
        }
        val topicIdOpt = transaction {
            Topics
                .select { Topics.name.like(topicName) }
                .map { it[Topics.id] }
                .firstOrNull()
        }
        val newTopic = topicIdOpt == null
        val topicId = if (newTopic) {
            //Subscribe to new topic
            transaction {
                Topics.insertAndGetId {
                    it[Topics.name] = topicName
                    it[Topics.serverId] = server.id
                }
            }
        } else topicIdOpt!!
        transaction {
            Subscribers.insert {
                it[Subscribers.topic] = topicId
                it[Subscribers.user] = user.id
            }
        }
        if (newTopic) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Created and subscribed you to the topic '$topicName'")
                .respond()
                .join()
        } else {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Subscribed you to '$topicName'")
                .respond()
                .join()
        }
    }

    private fun unsubscribe(event: SlashCommandCreateEvent) {
        val interaction = event.slashCommandInteraction
        val user = interaction.user
        val server = interaction.server.getOrNull()
        val channel = interaction.channel.getOrNull()
        if (server == null || channel == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Can't unsub outside of a particular TextChannel")
                .respond()
                .join()
            return
        }
        val topicName = interaction.arguments.firstOrNull() { it.name == "topic" }?.stringValue?.getOrNull()
        if (topicName == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Can't unsubscribe without a topic.")
                .respond()
                .join()
            return
        }
        val topicId = transaction {
            Topics.select { Topics.name like topicName }.map { it[Topics.id] }.firstOrNull()
        }
        if (topicId == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("No such topic: '$topicName'")
                .respond()
                .join()
            return
        }
        transaction {
            Subscribers.deleteWhere { Subscribers.topic eq topicId and (Subscribers.user eq user.id) }
        }
        //Cleanup if empty
        val empty = Topic(topicId.value, topicName, server.id).subscribers.isEmpty()
        if (empty) {
            transaction {
                Topics.deleteWhere { Topics.id eq topicId }
            }
            println("Deleted topic '$topicName', as there were no more subscribers.")
        }
        interaction.createImmediateResponder()
            .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
            .setContent("Unsubscribed you from '$topicName'")
            .respond()
            .join()
    }

    private fun notify(event: SlashCommandCreateEvent) {
        val interaction = event.slashCommandInteraction
        val user = interaction.user
        val topicName = interaction.arguments.firstOrNull { it.name == "topic" }?.stringValue?.getOrNull()
        if (topicName == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Can't notify without a topic.")
                .respond()
                .join()
            return
        }
        val message = interaction.arguments.firstOrNull { it.name == "message" }?.stringValue?.getOrNull()
        val server = interaction.server.getOrNull()
        val channel = interaction.channel.getOrNull()
        if (server == null || channel == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Can't notify outside of a particular TextChannel")
                .respond()
                .join()
            return
        }
        val topicId = transaction {
            Topics.select { Topics.name like topicName }.map { it[Topics.id] }.firstOrNull()
        }
        if (topicId == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("No such topic: '$topicName'.")
                .respond()
                .join()
            return
        }
        val topic = Topic(topicId.value, topicName, server.id)
        val toNotify = topic.subscribers
            .mapNotNull { sub ->
                server.members.firstOrNull { it.id == sub.userId }
            }
            .filterNot { it.id != user.id }
        val sender = MessageBuilder()
            .apply {
                toNotify.forEach { append(it); append(" ") }
            }
            .appendNewLine()
            .append(user)
            .apply {
                if (message == null) {
                    append(" wants you to pay attention to the channel!")
                } else {
                    append(": ")
                    append(message)
                }
            }
            .send(channel)
        interaction.createImmediateResponder()
            .setContent("Notified all '$topicName' subscribers.")
            .respond()
            .join()
        sender.join()
    }

    private fun topics(event: SlashCommandCreateEvent) {
        val interaction = event.slashCommandInteraction
        val user = interaction.user
        val server = interaction.server.getOrNull()
        val channel = interaction.channel.getOrNull()
        if (server == null || channel == null) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("Can't list topics outside of a particular TextChannel")
                .respond()
                .join()
            return
        }
        val topicInfos = transaction {
            Topics
                .join(Subscribers, JoinType.INNER, Topics.id, Subscribers.topic)
                .slice(Topics.name, Topics.serverId, Subscribers.user)
                .select { Topics.serverId eq server.id }
                .map { Triple(it[Topics.name], it[Subscribers.user] == user.id, 1) }
                .groupBy({ it.first }, { it.second to it.third })
                .map {
                    val (you, counts) = it.value.unzip()
                    Triple(it.key, you.any { it }, counts.sum())
                }
        }
        if (topicInfos.isEmpty()) {
            interaction.createImmediateResponder()
                .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
                .setContent("There are no topics on this server, use the /sub command to begin a topic!")
                .respond()
                .join()
        }
        interaction.createImmediateResponder()
            .setFlags(InteractionCallbackDataFlag.EPHEMERAL)
            .apply {
                append("There are ${topicInfos.size} topics on this server:")
                appendNewLine()
                topicInfos.forEach {
                    append("- ")
                    append(it.first, MessageDecoration.CODE_SIMPLE)
                    append(" (${it.third})")
                    appendNewLine()
                }
            }
            .respond()
            .join()
    }

    private fun setupForServer(server: Server) {
        println("Setting up for server: ${server.id}:${server.name}")
        val existingCommands = discordApi
            .getServerSlashCommands(server)
            .join()
            .filter { it.applicationId == applicationId }
        println("Existing slash commands: ${existingCommands.joinToString(", ") { "${it.id}:${it.name}" }}")
        existingCommands.forEach {
            println("Refreshing slash command ${it.name} for ${server.id}:${server.name}")
            it.deleteForServer(server).join()
        }
        discordApi.addSlashCommand(
            server,
            SlashCommandBuilder()
                .setName("sub")
                .setDescription("Subscribe to a topic.")
                .addOption(
                    SlashCommandOption.create(
                        SlashCommandOptionType.STRING,
                        "topic",
                        "The topic you want to subscribe to."
                    )
                ),
            ::subscribe
        )
        discordApi.addSlashCommand(
            server,
            SlashCommandBuilder()
                .setName("unsub")
                .setDescription("Unsubscribe from a topic.")
                .addOption(
                    SlashCommandOption.create(
                        SlashCommandOptionType.STRING,
                        "topic",
                        "The topic you want to unsubscribe from."
                    )
                ),
            ::unsubscribe
        )
        discordApi.addSlashCommand(
            server,
            SlashCommandBuilder()
                .setName("notify")
                .setDescription("Notify all subscribers of a topic.")
                .addOption(
                    SlashCommandOption.create(
                        SlashCommandOptionType.STRING,
                        "topic",
                        "The topic you want to notify.",
                        true
                    )
                )
                .addOption(
                    SlashCommandOption.create(
                        SlashCommandOptionType.STRING,
                        "message",
                        "The message you want to pass along with you notification.",
                        false
                    )
                ),
            ::notify
        )
        discordApi.addSlashCommand(
            server,
            SlashCommandBuilder()
                .setName("topics")
                .setDescription("List all topics and the number of subscribers for each."),
            ::topics
        )
    }

    @JvmStatic
    fun main(args: Array<String>) {
        Storage.connect()
        discordApi.servers.forEach(::setupForServer)
        discordApi.addServerJoinListener {
            val server = it.server
            setupForServer(server)
        }
        println(
            "https://discord.com/api/oauth2/authorize" +
                    "?client_id=992887370988933191&permissions=${Config.permissions}" +
                    "&scope=applications.commands%20bot"
        )
    }
}
