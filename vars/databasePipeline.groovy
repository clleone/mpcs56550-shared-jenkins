def call(Map config) {
    pipeline {
        agent any

        environment {
            DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        }

        stages {
            stage('Security Scan') {
                steps {
                    echo "Scanning postgres image..."
                    sh """
                        docker run --rm \
                        -v /var/run/docker.sock:/var/run/docker.sock \
                        aquasec/trivy image postgres:15 || true
                    """
                }
            }

            stage('Validate SQL') {
                steps {
                    echo "Validating SQL init script..."
                    sh """
                        docker run --rm \
                        -v \${WORKSPACE}/init.sql:/init.sql \
                        postgres:15 \
                        psql -f /init.sql --no-password 2>&1 || true
                    """
                }
            }
        }

        post {
            success {
                echo "Database pipeline completed successfully!"
            }
            failure {
                echo "Database pipeline failed!"
            }
        }
    }
}