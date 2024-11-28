Docker
* create image:
    -   docker build -t t1santos/tukanoimage .
* push image:
       docker push t1santos/tukanoimage
* run docker locally:
  
    - docker run --rm -p 8080:8080 t1santos/tukanoimage
# Azure Docker
* create cluster:
    -   az group create --name scc2425-container-tukano --location germanywestcentral
* start container:
     - az container create --resource-group scc2425-container-tukano --name tukano --image t1santos/tukanoimage --ports 8080 --dns-name-label scc2425-t1santos


* delete container:
    - az container delete --resource-group scc2425-container-tukano --name tukano 

* logs:
    - az container logs --resource-group scc2425-container-tukano --name tukano
      az container exec --resource-group scc2425-container-tukano --name tukano --exec-command "/bin/bash"

# Kubernetes
* create a service:
    -  az ad sp create-for-rbac --name http://scc2324-kuber --role Contributor --scope /subscriptions/<ID_OF_YOUR_SUBSCRIPTION>
       az ad sp create-for-rbac --name http://scc2425-t1santos --role Contributor --scope /subscriptions/4470b7dc-d45f-4a3f-aa4d-032931825859
* create a cluster:
    - az aks create --resource-group scc2425-container-tukano --name scc2425-cluster-tukano --node-vm-size Standard_B2s --generate-ssh-keys --node-count 2 --service-principal 09dc3e44-3dcc-49c1-9c97-2cb29fa480db --client-secret X9o8Q~Nqn9hEkmtdzjZKjaOAcO3DEfKxt6eL6cAL

* get credentials:
    - az aks get-credentials --resource-group scc2425-container-tukano --name scc2425-cluster-tukano

* deploy file:
    - kubectl apply -f azure-store.yaml

* check service:
    - kubectl get services

* check pods:
    - kubectl get pods

* Stream Logs:
    - kubectl logs -f tukano-deployment-8648b77454-zr5x6

* Delete All Objects:
    -  kubectl delete deployments,services,pods --all

* Delete All Persistent Volumes:
    - kubectl delete pv --all

* Delete Cluster
    - az group delete --resource-group scc2425-container-tukano
    - 