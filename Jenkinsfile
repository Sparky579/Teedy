pipeline {
    agent any
    
    environment {
        DOCKER_HUB_CREDENTIALS = credentials('docker-hub-credentials')
        DOCKER_IMAGE_NAME = 'sparkydock/teedy'
        DOCKER_IMAGE_TAG = "${env.BUILD_NUMBER}"
    }
    
    stages {
        stage('Build Docker Image') {
            steps {
                sh 'docker build -t ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} .'
            }
        }
        
        stage('Push to Docker Hub') {
            steps {
                sh 'echo ${DOCKER_HUB_CREDENTIALS_PSW} | docker login -u ${DOCKER_HUB_CREDENTIALS_USR} --password-stdin'
                sh 'docker push ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}'
                sh 'docker tag ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} ${DOCKER_IMAGE_NAME}:latest'
                sh 'docker push ${DOCKER_IMAGE_NAME}:latest'
            }
        }
        
        stage('Deploy Containers') {
            steps {
                // Stop and remove existing containers if they exist
                sh '''
                    docker stop teedy-container-8082 || true
                    docker rm teedy-container-8082 || true
                    docker stop teedy-container-8083 || true
                    docker rm teedy-container-8083 || true
                    docker stop teedy-container-8084 || true
                    docker rm teedy-container-8084 || true
                '''
                
                // Run three containers with different ports
                sh '''
                    docker run -d --name teedy-container-8082 -p 8082:8080 -v ${WORKSPACE}/docs/data:/data ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}
                    docker run -d --name teedy-container-8083 -p 8083:8080 -v ${WORKSPACE}/docs/data:/data ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}
                    docker run -d --name teedy-container-8084 -p 8084:8080 -v ${WORKSPACE}/docs/data:/data ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}
                '''
            }
        }
    }
    
    post {
        always {
            sh 'docker logout'
        }
    }
} 