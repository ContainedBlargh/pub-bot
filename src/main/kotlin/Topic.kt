import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

data class Topic(
    val id: Int,
    val name: String,
    val serverId: Long
) {
    val subscribers: List<Subscriber>
        get() = transaction {
            Topics
                .join(Subscribers, JoinType.INNER, onColumn = Topics.id, Subscribers.topic)
                .slice(Topics.id, Topics.name, Subscribers.user)
                .select { Topics.id eq this@Topic.id }
                .map {
                    Subscriber(this@Topic, it[Subscribers.user])
                }
        }
}
