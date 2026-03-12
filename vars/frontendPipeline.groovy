def call(Map config) {
    pipeline {
        agent any

        environment {
            DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
            IMAGE_NAME = "${config.dockerhubUser}/${config.serviceName}"
            IMAGE_TAG = "${env.BUILD_NUMBER}"
        }

        stages {
            stage('Build') {
                steps {
                    echo "Building ${config.serviceName}..."
                    sh 'npm install'
                    sh 'npm run build'
                }
            }

            stage('Test') {
                steps {
                    echo "Testing ${config.serviceName}..."
                    sh 'npm test -- --watchAll=false --passWithNoTests'
                }
            }

            stage('Security Scan') {
                steps {
                    echo "Scanning ${config.serviceName}..."
                    sh """
                        docker run --rm \
                        -v /var/run/docker.sock:/var/run/docker.sock \
                        aquasec/trivy image ${IMAGE_NAME}:${IMAGE_TAG} || true
                    """
                }
            }

            stage('Container Build') {
                steps {
                    echo "Building Docker image..."
                    sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest ."
                }
            }

            stage('Container Push') {
                when {
                    anyOf {
                        branch 'develop'
                        branch 'release/*'
                        branch 'main'
                    }
                }
                steps {
                    echo "Pushing to Docker Hub..."
                    sh """
                        echo $DOCKERHUB_CREDENTIALS_PSW | docker login \
                        -u $DOCKERHUB_CREDENTIALS_USR --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                    """
                }
            }

            stage('Deploy Dev') {
                when { branch 'develop' }
                steps {
                    echo "Deploying ${config.serviceName} to Dev..."
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                        script {
                            def kubeconfig = env.KUBECONFIG
                            sh "kubectl apply -f k8s/${config.serviceName}/dev/ --kubeconfig=${kubeconfig} --insecure-skip-tls-verify=true"
                        }
                    }
                }
            }

            stage('Deploy Staging') {
                when { branch 'release/*' }
                steps {
                    echo "Deploying ${config.serviceName} to Staging..."
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                        script {
                            def kubeconfig = env.KUBECONFIG
                            sh "kubectl apply -f k8s/${config.serviceName}/staging/ --kubeconfig=${kubeconfig} --insecure-skip-tls-verify=true"
                        }
                    }
                }
            }

            stage('Deploy Prod') {
                when { branch 'main' }
                input {
                    message "Deploy to Production?"
                    ok "Approve"
                }
                steps {
                    echo "Deploying ${config.serviceName} to Prod..."
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                        script {
                            def kubeconfig = env.KUBECONFIG
                            sh "kubectl apply -f k8s/${config.serviceName}/prod/ --kubeconfig=${kubeconfig} --insecure-skip-tls-verify=true"
                        }
                    }
                }
            }
        }

        post {
            always {
                sh 'docker logout'
            }
            success {
                echo "Pipeline completed successfully!"
            }
            failure {
                echo "Pipeline failed!"
            }
        }
    }
}