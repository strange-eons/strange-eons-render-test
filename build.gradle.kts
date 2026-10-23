plugins {
    `java-gradle-plugin`
}

group = "org.strangeeons.gradle"
version = "0.1.0-SNAPSHOT"
description = "Renders Strange Eons components from a local SE checkout and compares them against reference masters."

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("renderTest") {
            id = "org.strangeeons.render-test"
            implementationClass = "org.strangeeons.gradle.rendertest.RenderTestPlugin"
            displayName = "Strange Eons render test"
            description = project.description
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}
