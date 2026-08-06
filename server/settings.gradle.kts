pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "network"

include(":core")
include(":chat")
include(":lobby")
include(":smp")
include(":practice")

project(":core").projectDir = file("plugins/core")
project(":chat").projectDir = file("plugins/chat")
project(":lobby").projectDir = file("plugins/lobby")
project(":smp").projectDir = file("plugins/smp")
project(":practice").projectDir = file("plugins/practice")
