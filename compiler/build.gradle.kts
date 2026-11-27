// standalone mochypck cli compiler: validates dex entry classes, abis, and signing certs, then emits verified .mochypck binaries
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    applicationName = "mochypck"
    mainClass.set("com.mochyy.mochypck.compiler.MainKt")
}

dependencies {
    api(project(":api"))
    api(project(":pack-format"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation("junit:junit:4.13.2")
}
