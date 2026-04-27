dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("org.xerial:sqlite-jdbc:3.53.0.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.jar {
    archiveFileName.set("${rootProject.name}-${project.version}-PaperFolia26.1.2.jar")
}