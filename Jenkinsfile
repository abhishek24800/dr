#!/usr/bin/env groovy

def jenkinsAgent = jenkinsAgentLabel()

pipeline {
    triggers {
        cron('0 12 * * 1-5')
    }

    options {
        buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '')
        timestamps()
    }

    parameters {
        choice(choices: "Manual\nBuild\nDaily", description: 'The build trigger type.', name: 'TRIGGER_TYPE')
        choice(choices: "sys\ndev\ncte\nlte\nsys,prod", description: 'The environment to run against.  (corresponds to Spring Config Profile)', name: 'ENVIRONMENT')
        choice(choices: "N/A\nE1\nE2", description: '(Dev only) The active environment.', name: 'APPLICATION_ENV')
        choice(choices: "RegressionTestSuite\nSmokeTestSuite\nUIRegressionTestSuite\nRootUiTestSuite\nWebUiTestSuite\nConfigUiTestSuite\nProductionSmokeTestSuite\nSystestSmokeTestSuite\nDevelopmentSmokeTestSuite\nLTESmokeTestSuite\nCTESmokeTestSuite", description: 'The target test suite.', name: 'TESTSUITE')
        string(defaultValue: "", description: '(Optional) The version of CPG being tested.  If blank, job will get version from environment alive.jsp.', name: 'VERSION')
    }

    environment {
        GIT_USER = "3156af36-a7e7-4412-88ac-3de5a5ae4aa2"
        PIPELINE_CHANNEL_HOOK = "https://drcorp.webhook.office.com/webhookb2/696db974-3fb6-443f-8448-a04347412174@c183d079-8e92-436b-9045-b793f607fd04/JenkinsCI/09758803860c4ef690b7f93397f12143/e84b9f4b-3194-416d-aeba-b946698402af"
    }

    agent {
        docker {
            image 'dev-docker-reg.digitalriverws.net/corretto:11.0.5'
            label jenkinsAgent
            reuseNode true
        }
    }

    stages {
        stage('Run Integration Acceptance Tests') {
            steps {
                sendNotification(Step.TEST, Status.START)

                git branch: 'master',
                        url: 'git@github.digitalriverws.net:Centralized-Payment-Gateway/cpg-build-tools',
                        credentialsId: env.GIT_USER,
                        poll: false

                script {
                    def cpgEndpointUrl = getCpgEndpointUrl()
                    def codeVersion = getCodeVersion(cpgEndpointUrl)
                    def executionName = getExecutionName()

                    try {
                        sshagent(['jenkinsadmin-ssh']) {
                            sh "./bin/run-earth-tests.sh -r Centralized-Payment-Gateway/CPG-EARTH -p -e ${executionName} -v ${codeVersion} -c ${ENVIRONMENT} -t ${TESTSUITE} -m 10"
                        }

                    } catch (e) {
                        failMessage = "Integration acceptance tests failed\n" +
                                "Message: ${e.getMessage()}\n" +
                                "Results: <${env.BUILD_URL}Earth_20Report/report.html|Earth Report>"

                        echo "Exception caught, message: " + e.getMessage()
                        currentBuild.result = 'FAILED'
                    } finally {
                        publishHTML(target: [
                                allowMissing         : false,
                                alwaysLinkToLastBuild: true,
                                keepAll              : true,
                                reportDir            : 'test-results/',
                                reportFiles          : 'report.html',
                                reportName           : "Earth Report"
                        ])
                    }
                }
            }
        }
    }
    post {
        success {sendNotification(Step.TEST, Status.SUCCESS)}
        failure {sendNotification(Step.TEST, Status.FAIL)}
    }
}

String getBuildTrigger() {
    boolean isStartedByTimer = false
    for (buildCause in currentBuild.getBuildCauses()) {
        if ("${buildCause}".contains("hudson.triggers.TimerTrigger\$TimerTriggerCause")) {
            isStartedByTimer = true
            break
        }
    }
    println("isStartedByTimer? ${isStartedByTimer}")

    return isStartedByTimer ? "Daily" : params.TRIGGER_TYPE
}

String getCpgEndpointUrl() {
    def cpgEndpointUrl
    switch(params.ENVIRONMENT) {
        case "sys":
            cpgEndpointUrl = "https://cpgsys.digitalriver.com"
            break
        case "dev":
            //def port = "E1".equals(params.APPLICATION_ENV) ? 44011 : 44012
            //cpgEndpointUrl = "http://h010081028038.cpg-pmt-dev.aws-ue1-a.vdc3.npcloud.zone:${port}"
            cpgEndpointUrl = "https://cpgdev.digitalriver.com"
            break
        case "cte":
            cpgEndpointUrl = "https://cpgcte.digitalriver.com"
            break
        case "lte":
            cpgEndpointUrl = "https://cpglte.digitalriver.com"
            break
    }

    println("cpgEndpointUrl: ${cpgEndpointUrl}")

    return cpgEndpointUrl;
}

String getCodeVersion(String cpgEndpointUrl) {
    def codeVersion
    if(params.VERSION == null || "${params.VERSION}".trim().isEmpty()) {
        println("Using code version from ${cpgEndpointUrl}/alive.jsp")
        codeVersion = sh(script: "curl -k ${cpgEndpointUrl}/alive.jsp | grep -Po '(?<=Code version : <tt>).*'", returnStdout: true).trim()
    } else {
        println('Using code version from VERSION parameter.')
        codeVersion = "${params.VERSION}".trim()
    }

    println("Code Version: ${codeVersion}")

    return codeVersion
}

String getExecutionName() {
    def buildTrigger = getBuildTrigger()
    def executionName = params.ENVIRONMENT.toUpperCase() + "-" + buildTrigger + "-CPG-" + params.TESTSUITE

    println("Execution Name: ${executionName}")

    return executionName
}

String getColor(Status status) {
    switch(status) {
        case Status.SUCCESS:
            return "3CB043"
        case Status.START:
        case Status.PROCESSING:
            return "1034A6"
        case Status.FAIL:
            return "A9180D"
    }
}

enum Step {
    TEST
}
enum Status {
    START,
    PROCESSING,
    FAIL,
    SUCCESS
}

void sendNotification(Step step, Status status) {
    def notificationTitle = "##$step CPG"
    def notificationMessage = "Environment: ${params.ENVIRONMENT}"

    office365ConnectorSend(
            webhookUrl: env.PIPELINE_CHANNEL_HOOK,
            message: "$notificationTitle \n\r $notificationMessage",
            status: status.toString(),
            color: getColor(status)
    )
}

String jenkinsAgentLabel() {
    def label
    if ("Production" == params.ENVIRONMENT || "PTE" == params.ENVIRONMENT) {
        label = "docker || prod-medium"
    } else {
        label = "docker || nprod-medium"
    }
    echo "It's ${params.ENVIRONMENT} environment, set Jenkins docker worker to \"$label\""

    return label
}
