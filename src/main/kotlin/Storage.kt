import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

object Storage {
    fun connect() {
        Database.connect("jdbc:sqlite:/data.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Topics, Subscribers)
        }
        val topicsCount = transaction {
            Topics.selectAll().count()
        }
        val subscribersCount = transaction {
            Subscribers.selectAll().count()
        }
        println("Loaded database containing $topicsCount topics and $subscribersCount subscribers!")
    }
}