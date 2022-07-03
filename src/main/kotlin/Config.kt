import java.nio.file.Files
import java.nio.file.Paths

object Config {
    private val entryPattern = Regex("^(\\S+)\\s*[:=]\\s*[\"\']?(.*)[\"\']?$")
    private val entries = Files
        .readAllLines(Paths.get("config.ini"))
        .mapNotNull {
            it.split(";").firstOrNull()
        }
        .filterNot { it.isBlank() }
        .mapNotNull {
            val match = entryPattern.matchEntire(it)
            match?.let {
                it.groupValues[1] to it.groupValues[2]
            }
        }
        .toMap()

    val token = entries["token"]!!
    val permissions = entries["permissions"]!!.toLong()
}