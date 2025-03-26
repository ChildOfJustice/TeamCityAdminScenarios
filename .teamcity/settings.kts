import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.notifications
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnMetric
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnMetricChange

version = "2024.03"

project {
    description = "Converted from Jenkinsfile"

    // Common parameters for all build configurations
    params {
        param("env.GRADLE_OPTS", "-Dorg.gradle.daemon=false")
        param("env.DOCKER_IMAGE", "my-app-image:latest")
        param("env.REGISTRY", "your-docker-registry.com")
    }

    // VCS Root configuration
    val vcsRoot = VcsRoot {
        id("MyProject_VCS")
        name = "MyProject Repository"
//        url = "git@github.com:myorg/myproject.git"
//        branch = "refs/heads/main"
//        branchSpec = "+:refs/heads/*"
    }
    vcsRoot(vcsRoot)

    // Build Configuration: Gradle Build
    val gradleBuild = BuildType {
        id("GradleBuild")
        name = "Gradle Build"

        vcs {
            root(vcsRoot)
        }

        requirements {
            equals("teamcity.agent.name", "build-node")
        }

        steps {
            gradle {
                name = "Build"
                tasks = "clean build"
                gradleParams = "--info"
                jdkHome = "%env.JDK_HOME%"
            }
        }

        triggers {
            vcs {
                branchFilter = "+:*"
            }
        }

        features {
            notifications {
                notifierSettings = emailNotifier {
                    email = "team@example.com"
                }
                buildFailed = true
//                buildSuccessful = true
            }
        }

        artifactRules = "+:build/libs/** => build/libs"

        failureConditions {
            executionTimeoutMin = 30
            failOnMetricChange {
                metric = BuildFailureOnMetric.MetricType.TEST_COUNT
                threshold = 10
                units = BuildFailureOnMetric.MetricUnit.PERCENTS
                comparison = BuildFailureOnMetric.MetricComparison.LESS
                compareTo = value()
            }
        }
    }

    // Build Configuration: Tests
    val runTests = BuildType {
        id("RunTests")
        name = "Run Tests"

        vcs {
            root(vcsRoot)
        }

        requirements {
            equals("teamcity.agent.name", "build-node")
        }

        steps {
            gradle {
                name = "Test"
                tasks = "test"
                gradleParams = "--info"
                jdkHome = "%env.JDK_HOME%"
            }
        }

        dependencies {
            snapshot(gradleBuild) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }

        artifactRules = "+:build/reports/** => reports"
    }

    // Build Configuration: Docker Build
    val dockerBuild = BuildType {
        id("DockerBuild")
        name = "Build Docker Image"

        requirements {
            exists("docker.version")
            equals("teamcity.agent.name", "build-node")
        }

        steps {
            script {
                name = "Build Docker Image"
                scriptContent = """
                    echo "Building Docker image..."
                    docker build -t %env.DOCKER_IMAGE% .
                """.trimIndent()
            }
        }

        dependencies {
            snapshot(runTests) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

    // Build Configuration: Docker Push
    val dockerPush = BuildType {
        id("DockerPush")
        name = "Push Docker Image"

        requirements {
            exists("docker.version")
            equals("teamcity.agent.name", "build-node")
        }

        steps {
            script {
                name = "Push Docker Image"
                scriptContent = """
                    echo "Pushing Docker image to registry..."
                    docker tag %env.DOCKER_IMAGE% %env.REGISTRY%/%env.DOCKER_IMAGE%
                    docker push %env.REGISTRY%/%env.DOCKER_IMAGE%
                """.trimIndent()
            }
        }

        dependencies {
            snapshot(dockerBuild) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }

        features {
            notifications {
                notifierSettings = emailNotifier {
                    email = "team@example.com"
                }
                buildFailed = true
//                buildSuccessful = true
            }
        }
    }

    // Register all build configurations
    buildType(gradleBuild)
    buildType(runTests)
    buildType(dockerBuild)
    buildType(dockerPush)
}