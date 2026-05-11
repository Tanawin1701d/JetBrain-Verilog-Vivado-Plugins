plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.hdl"
version = "0.3.0"

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
    jvmToolchain(21)
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set(provider { null })
        changeNotes.set("""
            <ul>
                <li>Version 0.3.0 — Viva-CoTerm:</li>
                <li>New: Vivado Console tool window (bottom panel) with live bidirectional TCL terminal</li>
                <li>New: Socket-based TCL bridge — works correctly even after start_gui is called</li>
                <li>New: 13 predefined TCL commands (buildProject, runSynthesis, programDevice, …)</li>
                <li>New: Run Command palette in toolbar for parameterized predefined commands</li>
                <li>New: Embedded MCP server (default port 19999) for Claude Code / Junie AI integration</li>
                <li>New: Command history (up/down arrow) in TCL input field</li>
                <li>New: Vivado > Launch Vivado Console right-click action</li>
                <li>New: MCP settings (port, enable/disable, default jobs, command timeout)</li>
                <li>Version 0.2.1:</li>
                <li>Added Vitis support: Open Vitis workspace from folder</li>
                <li>Added Vitis HLS support: Create HLS Kernel from folder</li>
            </ul>
        """.trimIndent())
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