plugins {
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "org.strangeeons.gradle"
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = rootProject.name
        description = project.description
        inceptionYear = "2026"
        url = "https://github.com/strange-eons/strange-eons-render-test"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "christophercurrie"
                name = "Christopher Currie"
                url = "https://github.com/christophercurrie"
            }
        }
        scm {
            url = "https://github.com/strange-eons/strange-eons-render-test"
            connection = "scm:git:https://github.com/strange-eons/strange-eons-render-test.git"
            developerConnection = "scm:git:ssh://git@github.com/strange-eons/strange-eons-render-test.git"
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

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}
