import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnMetric
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnMetricChange
import jetbrains.buildServer.configs.kotlin.ui.add

version = "2024.03"

// Define available platforms and JDK versions for matrix builds
val platforms = listOf("linux", "windows")
val jdkVersions = listOf("17", "21")

project {
    description = "Jenkins Core Build Configuration converted from Jenkinsfile"

    // Common parameters for all builds
    params {
        param("env.BUILD_RETENTION_COUNT", "50")
        param("env.ARTIFACT_RETENTION_COUNT", "3")
        param("env.FAIL_FAST", "false")
        password("env.LAUNCHABLE_TOKEN", "credentialsJSON:launchable-jenkins-jenkins")
        param("env.BUILD_TAG", "%teamcity.build.id%")
        // Parameters for matrix builds
        param("env.MATRIX_PLATFORMS", platforms.joinToString(","))
        param("env.MATRIX_JDK_VERSIONS", jdkVersions.joinToString(","))
    }

    // VCS Root definition with enhanced settings
    val vcsRoot = GitVcsRoot {
        id("JenkinsCore_VCSRoot")
        name = "Jenkins Core Repository"
        url = "https://github.com/jenkinsci/jenkins.git"
        branch = "refs/heads/master"
        branchSpec = """
            +:refs/heads/*
            +:refs/pull/*/head
        """.trimIndent()
        checkoutPolicy = GitVcsRoot.AgentCheckoutPolicy.USE_MIRRORS
        authMethod = password {
            userName = "%github.username%"
            password = "%github.password%"
        }
    }
    vcsRoot(vcsRoot)

    // Template for build configurations
    template(BuildTemplate)

    // Dynamically create build configurations based on matrix
    platforms.forEach { platform ->
        jdkVersions.forEach { jdk ->
            // Skip Windows builds for JDK != 17 as per Jenkins logic
            if (platform == "windows" && jdk != "17") {
                return@forEach
            }
            buildType(createBuildConfiguration(platform, jdk))
        }
    }
}

// Function to create build configurations dynamically
fun createBuildConfiguration(platform: String, jdk: String) = BuildType({
    templates(BuildTemplate)
    id("JenkinsCore_${platform}_JDK${jdk}")
    name = "Jenkins Core - ${platform.capitalize()} JDK $jdk"
    description = "${platform.capitalize()} build with JDK $jdk"

    params {
        param("env.PLATFORM", platform)
        param("env.JDK_VERSION", jdk)
    }

    // Add VCS trigger for master branch
    triggers {
        vcs {
            branchFilter = "+:refs/heads/master"
        }
    }

    // Set up dependencies for build chain
    if (platform != "linux" || jdk != "17") {
        val linuxJdk17BuildId = "JenkinsCore_linux_JDK17"
        dependencies {
            snapshot(AbsoluteId(linuxJdk17BuildId)) {}
        }
    }

    // Add retry conditions
    failureConditions {
        executionTimeoutMin = 360
        failOnMetricChange {
            metric = BuildFailureOnMetric.MetricType.TEST_COUNT
            threshold = 20
            units = BuildFailureOnMetric.MetricUnit.PERCENTS
            comparison = BuildFailureOnMetric.MetricComparison.LESS
            compareTo = value()
        }
    }
})

object BuildTemplate : Template({
    name = "Jenkins Core Build Template"
    description = "Template for Jenkins Core builds with enhanced features"

    artifactRules = """
        +:**/*.jar
        +:**/*.war
        +:**/*.zip
        +:**/target/surefire-reports/** => test-reports.zip
        +:**/target/site/jacoco/** => coverage-report.zip
        +:**/target/spotbugsXml.xml => static-analysis/spotbugs.xml
        +:**/target/checkstyle-result.xml => static-analysis/checkstyle.xml
    """.trimIndent()

    params {
        param("teamcity.build.workingDir", "%system.teamcity.build.workingDir%")
        param("env.MAVEN_OPTS", "-Xmx1024m")
        param("env.JAVA_HOME", "%env.JDK_%env.JDK_VERSION%_HOME%")
        param("env.PATH", "%env.JAVA_HOME%/bin:%env.PATH%")
    }

    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
        showDependenciesChanges = true
    }

    steps {
        script {
            name = "Prepare Build Environment"
            scriptContent = """
                echo "Preparing build environment for %env.PLATFORM% with JDK %env.JDK_VERSION%"
                echo "JAVA_HOME=%env.JAVA_HOME%"
                java -version
            """.trimIndent()
        }

        maven {
            name = "Maven Build and Test"
            goals = "clean install"
            runnerArgs = """
                -Pdebug
                -Penable-jacoco
                --update-snapshots
                -Dmaven.repo.local=%system.teamcity.build.tempDir%/m2repo
                -Dmaven.test.failure.ignore=true
                -DforkCount=2
                -Dspotbugs.failOnError=false
                -Dcheckstyle.failOnViolation=false
                -Dset.changelist
                -B
            """.trimIndent()
            jdkHome = "%env.JAVA_HOME%"
            userSettingsSelection = "local-proxy"
        }

        script {
            name = "Record Launchable Tests"
            scriptContent = """
                if [ -z "${'$'}SKIP_LAUNCHABLE" ]; then
                    launchable verify && launchable record tests --build-number %teamcity.build.id% --flavor platform=%env.PLATFORM% --flavor jdk=%env.JDK_VERSION% maven './**/target/surefire-reports'
                else
                    echo "Skipping Launchable test recording as SKIP_LAUNCHABLE is set"
                fi
            """.trimIndent()
        }
    }

    features {
        perfmon {}

        // XML report processing
        feature {
            type = "xml-report-plugin"
            param("xmlReportParsing.reportType", "junit")
            param("xmlReportParsing.reportDirs", "**/target/surefire-reports/*.xml")
            param("xmlReportParsing.verbose", "true")
        }

        // Code analysis reports
        feature {
            type = "spotbugs-report"
            param("spotbugs.report.paths", "**/target/spotbugsXml.xml")
            param("spotbugs.include.source.files", "true")
        }
        feature {
            type = "checkstyle-report"
            param("checkstyle.report.paths", "**/target/checkstyle-result.xml")
            param("checkstyle.treat.warnings.as.errors", "false")
        }

        // Docker support for containerized builds
        dockerSupport {
            cleanupPushedImages = true
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_6"
            }
        }
    }

    requirements {
        exists("env.JDK_%env.JDK_VERSION%_HOME")
        contains("teamcity.agent.jvm.os.name", "%env.PLATFORM%")
    }

    // Set build timeout to 6 hours
    params {
        param("teamcity.runner.commandline.stdErr.timeout", "21600")
        param("teamcity.step.mode", "default")
        param("teamcity.build.workingDir", "%system.teamcity.build.workingDir%")
    }
})
