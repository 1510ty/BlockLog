import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

val gitCommit: String? by project
val buildTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))

version = if (!gitCommit.isNullOrBlank()) {
    "$buildTime-$gitCommit"
} else {
    "0.0.4"
}

group = "com.mc1510ty"

subprojects {
    version = rootProject.version

    apply(plugin = "java")
    apply(plugin = "java-library")

    // --- ここに一括設定を追加 ---
    tasks.withType<ProcessResources> {
        val props = mapOf("version" to project.version)
        inputs.properties(props)

        filteringCharset = "UTF-8"
        filesMatching("**/paper-plugin.yml") {
            expand(props)
        }
        filesMatching("**/plugin.yml") {
            expand(props)
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://maven.fabricmc.net/")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}