import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Subscribers : Table("subscription") {
    val topic = reference("topic", Topics, onUpdate = ReferenceOption.CASCADE, onDelete = ReferenceOption.CASCADE)
    val user = long("user")
    val key = PrimaryKey(topic, user)
}