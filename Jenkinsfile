#!groovy

def workerNode = "devel8"

pipeline {
    agent { label workerNode }

    tools {
        maven "Maven 3"
    }

    triggers {
        pollSCM("H/03 * * * *")
    }

    options {
        timestamps()
    }

    stages {
        stage("clear workspace") {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage("verify") {
            steps {
                sh "mvn verify pmd:pmd"
                junit "**/target/surefire-reports/TEST-*.xml,**/target/failsafe-reports/TEST-*.xml"
            }
        }

        stage("publish pmd results") {
            steps {
                step([$class: 'hudson.plugins.pmd.PmdPublisher',
                    pattern: '**/target/pmd.xml',
                    unstableTotalAll: "0",
                    failedTotalAll: "0"])
            }
        }

        stage("docker build") {
            steps {
                script {
                    version = env.BRANCH_NAME + '-' + env.BUILD_NUMBER

                    def image = docker.build("docker-i.dbc.dk/rawrepo-records-service:${version}")
                    image.push()

                    if (env.BRANCH_NAME == 'master') {
                        echo 'Pushing build to latest'
                        image.push('latest')
                    }
                }
            }
        }
    }
}