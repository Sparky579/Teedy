#!/bin/bash

# Docker Hub credentials - replace with your actual values
DOCKER_HUB_USERNAME="sparkydock"
DOCKER_HUB_PASSWORD="your-password"
DOCKER_IMAGE_NAME="$DOCKER_HUB_USERNAME/teedy"
DOCKER_IMAGE_TAG="latest"

echo "===== Building Docker Image ====="
sudo docker build -t $DOCKER_IMAGE_NAME:$DOCKER_IMAGE_TAG .

# Docker Hub推送
echo "===== Pushing to Docker Hub ====="
echo $DOCKER_HUB_PASSWORD | sudo docker login -u $DOCKER_HUB_USERNAME --password-stdin
sudo docker push $DOCKER_IMAGE_NAME:$DOCKER_IMAGE_TAG

echo "===== Stopping and removing existing containers ====="
sudo docker stop teedy-container-8082 || true
sudo docker rm teedy-container-8082 || true
sudo docker stop teedy-container-8083 || true
sudo docker rm teedy-container-8083 || true
sudo docker stop teedy-container-8084 || true
sudo docker rm teedy-container-8084 || true

echo "===== Running three containers with different ports ====="
sudo docker run -d --name teedy-container-8082 -p 8082:8080 -v $(pwd)/docs/data:/data $DOCKER_IMAGE_NAME:$DOCKER_IMAGE_TAG
sudo docker run -d --name teedy-container-8083 -p 8083:8080 -v $(pwd)/docs/data:/data $DOCKER_IMAGE_NAME:$DOCKER_IMAGE_TAG
sudo docker run -d --name teedy-container-8084 -p 8084:8080 -v $(pwd)/docs/data:/data $DOCKER_IMAGE_NAME:$DOCKER_IMAGE_TAG

echo "===== Containers started ====="
sudo docker ps 