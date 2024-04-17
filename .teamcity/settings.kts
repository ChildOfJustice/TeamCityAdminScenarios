import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2024.03"

project {

    buildType(Release)
    buildType(BuildApp)
    buildType(BuildAndPushDockerImage)
    buildType(SomeOtherInternalUpdate)
}

object BuildAndPushDockerImage : BuildType({
    name = "BuildAndPushDockerImage"

    steps {
        script {
            id = "simpleRunner"
            scriptContent = """echo "Built and pushed!""""
        }
    }

    dependencies {
        dependency(BuildApp) {
            snapshot {
            }

            artifacts {
                artifactRules = "artifact.txt"
            }
        }
    }
})

object BuildApp : BuildType({
    name = "BuildApp"

    artifactRules = "artifact.txt => artifact.txt"

    steps {
        script {
            id = "simpleRunner"
            scriptContent = """
                echo "APP is built!"
                echo "test" > artifact.txt
            """.trimIndent()
        }
    }
})

object Release : BuildType({
    name = "Release"

    type = BuildTypeSettings.Type.COMPOSITE

    vcs {
        showDependenciesChanges = true
    }

    dependencies {
        snapshot(BuildAndPushDockerImage) {
        }
        snapshot(BuildApp) {
        }
        snapshot(SomeOtherInternalUpdate) {
        }
    }
})

object SomeOtherInternalUpdate : BuildType({
    name = "SomeOtherInternalUpdate"

    steps {
        script {
            id = "simpleRunner"
            scriptContent = """echo "updated packages and notified some slack channel""""
        }
    }
})
