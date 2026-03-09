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
                    sh 'pip install -r requirements.txt'
                }
            }

            stage('Test') {
                steps {
                    echo "Testing ${config.serviceName}..."
                    sh 'pip install pytest pytest-mock && pytest tests/'
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
                steps {
                    echo "Pushing to Docker Hub..."
                    sh """
                        echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                    """
                }
            }

            stage('Deploy') {
                steps {
                    echo "Deploying ${config.serviceName} to ${env.BRANCH_NAME}..."
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
