plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.hdl"
version = "1.0.0"

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
}

kotlin {
    jvmToolchain(21)
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("253.*")
        changeNotes.set("""
            <ul>
                <li>Added Tcl language support (.tcl, .xdc)</li>
                <li>Syntax highlighting for Tcl and Vivado commands</li>
                <li>Code folding and auto-completion for Tcl</li>
                <li>New Vivado actions: Run Tcl Script, Open Project, IP Composer</li>
                <li>Integrated Icarus Verilog linter</li>
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