# Jenkins Setup Guide for Teedy Docker Pipeline

## Prerequisites
- Jenkins server installed and running
- Docker installed on the Jenkins server
- Docker Hub account

## Configuration Steps

### 1. Jenkins Plugins
Ensure the following plugins are installed in Jenkins:
- Docker Pipeline
- Pipeline
- Credentials Binding

To install plugins, go to **Manage Jenkins → Manage Plugins → Available** and search for these plugins.

### 2. Add Docker Hub Credentials

1. Go to **Manage Jenkins → Manage Credentials → (global) → Add Credentials**
2. Select **Username with password** as the kind
3. Enter your Docker Hub username and password
4. Set the ID to `docker-hub-credentials`
5. Click **OK** to save

### 3. Create a Jenkins Pipeline

1. From the Jenkins dashboard, click **New Item**
2. Enter a name for your pipeline (e.g., "Teedy-Docker-Pipeline")
3. Select **Pipeline** and click **OK**
4. In the configuration page:
   - Under **Pipeline**, select **Pipeline script from SCM**
   - Select **Git** as the SCM
   - Enter your repository URL
   - Specify the branch (e.g., `*/master` or `*/main`)
   - Set the **Script Path** to `Jenkinsfile`
   - Click **Save**

### 4. Update Docker Hub Username

Before running the pipeline, edit the `Jenkinsfile` and replace `your-dockerhub-username` with your actual Docker Hub username.

### 5. Running the Pipeline

- Click **Build Now** to run the pipeline manually
- Alternatively, set up webhook triggers or scheduled builds

## Troubleshooting

If you encounter permission issues when running Docker commands in Jenkins:
```bash
# Add the jenkins user to the docker group
sudo usermod -aG docker jenkins

# Restart Jenkins
sudo systemctl restart jenkins
```

Make sure the data directory exists on your Jenkins server:
```bash
mkdir -p ${WORKSPACE}/docs/data
``` 