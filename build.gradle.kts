buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath(platform("org.bouncycastle:bc-jdk18on-bom:1.87"))
        constraints {
            add("classpath", "com.google.guava:guava:33.8.0-jre") {
                because("Patch the Guava version selected by Android build tools")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.7") {
                because("Patch bundletool's transitive JOSE implementation")
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("Patch Jetifier's transitive XML parser")
            }
            add("classpath", "org.apache.commons:commons-lang3:3.22.0") {
                because("Patch the recursive type parser used by Android build tools")
            }
        }
    }
}
plugins {
    id("com.android.application") version "9.5.2" apply false
}

subprojects {
    configurations.configureEach {
        if (name == "androidLintTool") {
            listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on").forEach { module ->
                dependencyConstraints.add(project.dependencies.constraints.create("org.bouncycastle:$module:1.87"))
            }
            dependencyConstraints.add(project.dependencies.constraints.create("com.google.guava:guava:33.8.0-jre"))
            dependencyConstraints.add(project.dependencies.constraints.create("org.apache.commons:commons-lang3:3.22.0"))
            dependencyConstraints.add(project.dependencies.constraints.create("org.apache.httpcomponents:httpclient:4.5.14"))
        }
    }
}
