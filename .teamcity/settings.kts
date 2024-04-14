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

    buildType(Deploy)
    buildType(Build)
    buildType(Test)
}

object Build : BuildType({
    name = "Build"

    artifactRules = "artifact.txt => artifact.txt"

    steps {
        script {
            name = "BuildStep"
            id = "BuildStep"
            scriptContent = """
                echo "BUILT!"
                echo "TEST" > artifact.txt
            """.trimIndent()
        }
    }
})

object Deploy : BuildType({
    name = "Deploy"

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

    steps {
        script {
            name = "DeployStep"
            id = "DeployStep"
            scriptContent = """echo "DEPLOYED!""""
        }
    }

    dependencies {
        snapshot(Test) {
        }
        artifacts(Build) {
            artifactRules = "artifact.txt => artifact.txt"
        }
    }
})

object Test : BuildType({
    name = "Test"

    steps {
        script {
            name = "TestStep"
            id = "TestStep"
            scriptContent = """echo "TESTED! PASS""""
        }
    }

    dependencies {
        snapshot(Build) {
        }
    }
})
