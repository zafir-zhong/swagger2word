#!/usr/bash
mvn clean package
docker docker build -t tool/swagger2word:1.0.03-release .
kubectl create -n tool configmap swagger2word-filebeat-config --from-file=config
kubectl apply -f swagger2word.yml
kubectl get pod -n tool -o wide