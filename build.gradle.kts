plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.hdl"
version = "1.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        zipSigner()
    }
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

kotlin {
    // Must match the JBR the targeted platform runs on: 2025.1 (build 251) ships JBR 21.
    // Lowering sinceBuild below 251 without also lowering this produces a plugin the
    // older IDE cannot load — verifyPluginProjectConfiguration reports the mismatch.
    jvmToolchain(21)
}

intellijPlatform {
    pluginVerification {
        // Without this block `verifyPlugin` fails with "No IDE resolved for verification".
        // recommended() resolves the IDEs covered by the sinceBuild/untilBuild range,
        // so widening sinceBuild automatically widens what gets verified.
        ides {
            recommended()
        }
    }
}

/**
 * Marketplace change notes, rendered from the top section of CHANGELOG.md so the
 * two cannot drift apart. Returns empty when the file is missing or has no entries.
 *
 * Understands two shapes inside the section:
 *   **Group heading**   -> <b>Group heading</b>, starting a fresh list
 *   - bullet            -> <li>bullet</li>
 * Inline `code` spans become <code> so the notes read the same as the file.
 */
fun latestChangeNotesHtml(): String {
    val changelog = file("CHANGELOG.md")
    if (!changelog.exists()) return ""

    val firstSection = Regex(
        """^## .*?$(.*?)(?=^## |\z)""",
        setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
    ).find(changelog.readText())?.groupValues?.get(1) ?: return ""

    fun inline(s: String) = Regex("`([^`]+)`").replace(s) { "<code>${it.groupValues[1]}</code>" }

    val out = StringBuilder()
    var listOpen = false
    for (line in firstSection.lines().map { it.trim() }) {
        val heading = Regex("""^\*\*(.+)\*\*$""").find(line)?.groupValues?.get(1)
        when {
            heading != null -> {
                if (listOpen) { out.appendLine("</ul>"); listOpen = false }
                out.appendLine("<b>${inline(heading)}</b>")
            }
            line.startsWith("- ") -> {
                if (!listOpen) { out.appendLine("<ul>"); listOpen = true }
                out.appendLine("  <li>${inline(line.removePrefix("- ").trim())}</li>")
            }
        }
    }
    if (listOpen) out.appendLine("</ul>")

    return out.toString().trim()
}

tasks {
    patchPluginXml {
        // Built and tested against 2025.1 only. Widen this after running
        // `./gradlew verifyPlugin` against the older builds you want to claim.
        sinceBuild.set("251")
        untilBuild.set(provider { null })
        changeNotes.set(provider { latestChangeNotesHtml() })
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}