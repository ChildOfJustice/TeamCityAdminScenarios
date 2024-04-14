package patches.buildTypes

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.ui.*

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, create a buildType with id = 'Build'
in the root project, and delete the patch script.
*/
create(DslContext.projectId, BuildType({
    id("Build")
    name = "Build"

    steps {
        script {
            name = "BuildStep"
            id = "BuildStep"
            scriptContent = """echo "BUILT!""""
        }
    }
}))

