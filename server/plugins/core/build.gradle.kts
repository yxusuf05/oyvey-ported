dependencies {
    implementation(rootProject.libs.hikaricp)
    implementation(rootProject.libs.sqlite.jdbc)
}

tasks.shadowJar {
    // Relocated so a second plugin shading Hikari cannot clash with ours.
    relocate("com.zaxxer.hikari", "me.alpha432.network.core.lib.hikari")
}
