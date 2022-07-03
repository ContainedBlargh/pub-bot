import org.jetbrains.exposed.dao.id.IntIdTable

object Topics : IntIdTable("topics") {
    val name = varchar("name", 256)
    val serverId = long("server")
}