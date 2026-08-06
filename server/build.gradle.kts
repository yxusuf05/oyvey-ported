plugins {
    java
    alias(libs.plugins.shadow) apply false
}

val runtimePluginsDir = layout.projectDirectory.dir("runtime/plugins")

allprojects {
    group = "me.alpha432.network"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        "compileOnly"(rootProject.libs.paper.api)
        "testImplementation"(rootProject.libs.paper.api)
        "testImplementation"(platform(rootProject.libs.junit.bom))
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.named<ProcessResources>("processResources") {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    // The shaded jar is the only artifact we ship, so it takes the plain name.
    tasks.named<Jar>("jar") {
        archiveClassifier.set("dev")
    }
    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("")
    }
    tasks.named("build") {
        dependsOn("shadowJar")
    }
}

/** Copies every plugin jar into the local test server so it can be started right away. */
tasks.register<Copy>("installPlugins") {
    group = "network"
    description = "Copies all plugin jars into server/runtime/plugins."
    subprojects.forEach { from(it.tasks.named("shadowJar")) }
    into(runtimePluginsDir)
}
