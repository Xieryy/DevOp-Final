pipeline {
    agent any

    // ─── Poll Git every 5 minutes for new commits ────────────────────────────
    triggers {
        pollSCM('*/5 * * * *')
    }

    // ─── Environment & tool versions ─────────────────────────────────────────
    environment {
        // Recipient that must always receive failure mail
        FIXED_RECIPIENT  = 'srengty@gmail.com'
        // Docker network shared with MySQL so Ansible can reach the DB
        DOCKER_NETWORK   = 'idcard_itc-devops-net'
        // Artifact filename produced by Maven
        JAR_NAME         = 'idcard-0.0.1-SNAPSHOT.jar'
    }

    stages {

        // ── 1. Checkout ───────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo "Checking out branch: ${env.GIT_BRANCH}"
                checkout scm
            }
        }

        // ── 2. Build & Test ───────────────────────────────────────────────────
        stage('Build & Test') {
            steps {
                // Run Maven clean package (includes unit tests) and tee output
                // to build-output.txt so it can be archived as an artefact.
                bat '''
                    mvnw.cmd clean package 2>&1 | tee build-output.txt
                    exit /b %ERRORLEVEL%
                '''
            }
            post {
                always {
                    // Archive the compiled JAR and the full build log
                    archiveArtifacts artifacts: "target/${JAR_NAME}, build-output.txt",
                                     allowEmptyArchive: true,
                                     fingerprint: true

                    // Publish JUnit test results so Jenkins shows a test trend
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        // ── 3. Deploy via Ansible ─────────────────────────────────────────────
        stage('Deploy via Ansible') {
            steps {
                echo 'Deploying to web server using Ansible …'
                // Run Ansible inside a Docker container so no local Ansible
                // install is required on the Windows Jenkins agent.
                bat """
                    docker run --rm ^
                      -v "%WORKSPACE%:/ansible" ^
                      -w /ansible ^
                      --network %DOCKER_NETWORK% ^
                      alpine sh -c ^
                      "apk add --no-cache ansible sshpass openssh-client && ansible-playbook -i hosts.ini playbook.yml"
                """
            }
        }
    }

    // ─── Post-build notifications ─────────────────────────────────────────────
    post {

        success {
            echo "✅ Build #${env.BUILD_NUMBER} succeeded and deployed successfully."
        }

        failure {
            script {
                // Build a human-readable body that includes commit info
                def commitAuthor = env.GIT_AUTHOR_NAME  ?: 'Unknown'
                def commitMsg    = env.GIT_COMMIT        ?: 'N/A'
                def emailBody = """
=== Jenkins Build FAILED ===

Job          : ${env.JOB_NAME}
Build Number : ${env.BUILD_NUMBER}
Branch       : ${env.GIT_BRANCH ?: 'N/A'}
Commit SHA   : ${commitMsg}
Commit Author: ${commitAuthor}
Console Log  : ${env.BUILD_URL}console
Build Artefacts: ${env.BUILD_URL}artifact/

Please review the console log and fix the issue.

-- Jenkins CI/CD (ITC DevOps)
"""
                emailext (
                    subject: "❌ BUILD FAILED: ${env.JOB_NAME} [#${env.BUILD_NUMBER}]",
                    body: emailBody,
                    // Always send to the fixed recipient
                    to: "${env.FIXED_RECIPIENT}",
                    // Also CC every developer who committed during this build
                    // and the culprits (people whose changes broke the build)
                    recipientProviders: [
                        developers(),   // all committers in this build
                        culprits(),     // committers from previous successful build onward
                        requestor()     // person who triggered the build manually (if any)
                    ],
                    attachLog: true,
                    compressLog: true
                )
            }
        }

        unstable {
            // Treat test failures (unstable) the same as hard failures
            script {
                emailext (
                    subject: "⚠️ BUILD UNSTABLE (Test Failures): ${env.JOB_NAME} [#${env.BUILD_NUMBER}]",
                    body: """
Build is UNSTABLE – one or more tests failed.

Job          : ${env.JOB_NAME}
Build Number : ${env.BUILD_NUMBER}
Branch       : ${env.GIT_BRANCH ?: 'N/A'}
Commit SHA   : ${env.GIT_COMMIT ?: 'N/A'}
Test Report  : ${env.BUILD_URL}testReport/
Console Log  : ${env.BUILD_URL}console

-- Jenkins CI/CD (ITC DevOps)
""",
                    to: "${env.FIXED_RECIPIENT}",
                    recipientProviders: [developers(), culprits()],
                    attachLog: true,
                    compressLog: true
                )
            }
        }
    }
}
