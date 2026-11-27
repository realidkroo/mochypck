plugins {
    kotlin("jvm") version "2.3.10" apply false
    kotlin("plugin.serialization") version "2.3.10" apply false
}

allprojects {
    group = "com.github.realidkroo.mochypck"
    version = providers.gradleProperty("mochypckVersion").orElse("1.0.0").get()
}

subprojects {
    apply(plugin = "maven-publish")

    afterEvaluate {
        extensions.findByType<PublishingExtension>()?.apply {
            publications {
                if (findByName("maven") == null && components.findByName("java") != null) {
                    create<MavenPublication>("maven") {
                        from(components["java"])
                        artifactId = project.name
                    }
                }
            }
        }
    }
}
