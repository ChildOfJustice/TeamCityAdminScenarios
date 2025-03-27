import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

version = "2024.03"

project {
    description = "Jenkins Core Build Configuration converted from Jenkinsfile"

    params {
        param("env.BUILD_RETENTION_COUNT", "50")
        param("env.ARTIFACT_RETENTION_COUNT", "3")
        param("env.FAIL_FAST", "false")
        password("env.LAUNCHABLE_TOKEN", "credentialsJSON:launchable-jenkins-jenkins")
        param("env.BUILD_TAG", "%teamcity.build.id%")
    }

    // VCS Root definition
    val vcsRoot = GitVcsRoot {
        id("JenkinsCore_VCSRoot")
        name = "Jenkins Core Repository"
        url = "https://github.com/jenkinsci/jenkins.git"
        branch = "refs/heads/master"
        branchSpec = """
            +:refs/heads/*
        """.trimIndent()
//        useAlternates = true
        checkoutPolicy = GitVcsRoot.AgentCheckoutPolicy.USE_MIRRORS
//        useMirrors = true
    }
    vcsRoot(vcsRoot)

    // Template for build configurations
    template(BuildTemplate)

    // Build configurations
    buildType(LinuxJDK17)
    buildType(LinuxJDK21)
    buildType(WindowsJDK17)
}

object BuildTemplate : Template({
    name = "Jenkins Core Build Template"
    description = "Template for Jenkins Core builds"

    artifactRules = """
        +:**/*.jar
        +:**/*.war
        +:**/*.zip
        +:**/target/surefire-reports/** => test-reports.zip
        +:**/target/site/jacoco/** => coverage-report.zip
    """.trimIndent()

    params {
        param("teamcity.build.workingDir", "%system.teamcity.build.workingDir%")
        param("env.MAVEN_OPTS", "-Xmx1024m")
    }

    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
    }

    steps {
        script {
            name = "Prepare Build Environment"
            scriptContent = """
                echo "Preparing build environment for %env.PLATFORM% with JDK %env.JDK_VERSION%"
            """.trimIndent()
        }
//        maven {  }
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
            """.trimIndent()
        }

        script {
            name = "Record Launchable Tests"
            scriptContent = """
                launchable verify && launchable record tests --session %env.BUILD_TAG% --flavor platform=%env.PLATFORM% --flavor jdk=%env.JDK_VERSION% maven './**/target/surefire-reports'
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
        }

        // Code analysis reports
        feature {
            type = "spotbugs-report"
            param("spotbugs.report.paths", "**/target/spotbugsXml.xml")
        }
        feature {
            type = "checkstyle-report"
            param("checkstyle.report.paths", "**/target/checkstyle-result.xml")
        }
    }

    requirements {
        exists("env.JDK_%env.JDK_VERSION%_HOME")
    }

    failureConditions {
        executionTimeoutMin = 360
    }
})

object LinuxJDK17 : BuildType({
    templates(BuildTemplate)
    name = "Jenkins Core - Linux JDK 17"
    description = "Linux build with JDK 17"

    params {
        param("env.PLATFORM", "linux")
        param("env.JDK_VERSION", "17")
    }

    triggers {
        vcs {
            branchFilter = "+:*"
        }
    }
})

object LinuxJDK21 : BuildType({
    templates(BuildTemplate)
    name = "Jenkins Core - Linux JDK 21"
    description = "Linux build with JDK 21"

    params {
        param("env.PLATFORM", "linux")
        param("env.JDK_VERSION", "21")
    }

    dependencies {
        snapshot(LinuxJDK17) {
            onDependencyFailure = FailureAction.FAIL_TO_START
            onDependencyCancel = FailureAction.CANCEL
        }
        artifacts(LinuxJDK17) {
            artifactRules = """
                +:**/*.jar
                +:**/*.war
                +:**/*.zip => artifacts
            """.trimIndent()
        }
    }
})

object WindowsJDK17 : BuildType({
    templates(BuildTemplate)
    name = "Jenkins Core - Windows JDK 17"
    description = "Windows build with JDK 17"

    params {
        param("env.PLATFORM", "windows")
        param("env.JDK_VERSION", "17")
    }

    dependencies {
        snapshot(LinuxJDK17) {
            onDependencyFailure = FailureAction.FAIL_TO_START
            onDependencyCancel = FailureAction.CANCEL
        }
        artifacts(LinuxJDK17) {
            artifactRules = """
                +:**/*.jar
                +:**/*.war
                +:**/*.zip => artifacts
            """.trimIndent()
        }
    }
})