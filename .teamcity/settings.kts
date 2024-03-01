import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.triggers.schedule

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

version = "2023.11"

project {

    buildType(ProduceTeamCityArtifact)

    subProject(TaggedBuildConsumer)
}

object ProduceTeamCityArtifact : BuildType({
    name = "ProduceTeamCityArtifact"

    artifactRules = "TeamCity-*.tar.gz"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "Download TeamCity"
            id = "Temp_download_teamcity"
            scriptContent = """
                #!/bin/bash
                
                # Define the version to download
                VERSION="2023.11.3"
                
                # Define the download URL
                URL="https://download.jetbrains.com/teamcity/TeamCity-${'$'}VERSION.tar.gz"
                
                # Download TeamCity
                wget ${'$'}URL -O TeamCity-${'$'}VERSION.tar.gz
            """.trimIndent()
        }
    }
})


object TaggedBuildConsumer : Project({
    name = "TaggedBuildConsumer"

    buildType(TaggedBuildConsumer_BuildTcImage)
})

object TaggedBuildConsumer_BuildTcImage : BuildType({
    name = "BuildTcImage"

    buildNumberPattern = "%build.counter%-${ProduceTeamCityArtifact.depParamRefs.buildNumber}"

    vcs {
        root(DslContext.settingsRoot, "+:tc-image=>tc-image")
    }

    steps {
        dockerCommand {
            name = "Build TeamCity image"
            id = "Build_Buildserver_TeamCity_project_image"
            commandType = build {
                source = file {
                    path = "tc-image/Dockerfile"
                }
                platform = DockerCommandStep.ImagePlatform.Linux
                namesAndTags = "teamcity-image:%build.number%"
                commandArgs = "--pull"
            }
            param("dockerfile.content", """
                ARG baseImage='amazoncorretto:17.0.10-al2023-headless'
                
                FROM ${'$'}{baseImage}
                
                RUN yum -y update && \
                    yum install -y unzip
                    
                ENV TEAMCITY_DIST=/opt/teamcity
                
                RUN mkdir -p ${'$'}TEAMCITY_DIST
                
                RUN groupadd --gid 1000 teamcity && \
                    useradd -r -u 1000 -g teamcity -d ${'$'}TEAMCITY_DIST teamcity && \
                    chown -R teamcity:teamcity ${'$'}TEAMCITY_DIST
                
                COPY --chown=teamcity:teamcity TeamCity ${'$'}TEAMCITY_DIST
                
                USER teamcity:teamcity
                
                CMD ["/bin/sh", "-c", "${'$'}TEAMCITY_DIST/bin/teamcity-server.sh run"]
            """.trimIndent())
        }
    }

    triggers {
        schedule {
            schedulingPolicy = daily {
                hour = 13
                minute = 10
            }
            branchFilter = """
                +:<default>
                +:build-custom-tc-image-with-triggers
            """.trimIndent()
            triggerBuild = onWatchedBuildChange {
                buildType = "${ProduceTeamCityArtifact.id}"
                watchedBuildRule = ScheduleTrigger.WatchedBuildRule.TAG
                watchedBuildTag = "deploy"
                watchedBuildBranchFilter = """
                    +:<default>
                    +:build-custom-tc-image-with-triggers
                """.trimIndent()
            }
        }
    }

    dependencies {
        dependency(ProduceTeamCityArtifact) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                artifactRules = "TeamCity-*.tar.gz!TeamCity/** => tc-image/TeamCity"
            }
        }
    }
})
