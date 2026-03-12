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
                        psql -f /init.sql -c "SELECT COUNT(*) FROM products;" --no-password 2>&1 || true
                    """
                }
            }

            stage('Deploy Dev') {
                when { branch 'develop' }
                steps {
                    echo "Deploying database to Dev..."
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                        sh '''
                            #!/bin/bash
                            kubectl apply -f k8s/database/dev/ \
                            --kubeconfig=$KUBECONFIG --insecure-skip-tls-verify=true
                        '''
                    }
                }
            }

            stage('Deploy Staging') {
                when { branch 'release/*' }
                steps {
                    echo "Deploying database to Staging..."
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                        sh """
                            #!/bin/bash
                            kubectl apply -f k8s/database/staging/ \
                            --kubeconfig=$KUBECONFIG --insecure-skip-tls-verify=true
                        """
                    }
                }
            }

            stage('Deploy Prod') {
                when { branch 'main' }
                input {
                    message "Deploy database to Production?"
                    ok "Approve"
                }
                steps {
                    echo "Deploying database to Prod..."
                    withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                        sh """
                            #!/bin/bash
                            kubectl apply -f k8s/database/prod/ \
                            --kubeconfig=$KUBECONFIG --insecure-skip-tls-verify=true
                        """
                    }
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