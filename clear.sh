#!/usr/bash
mvn clean
kubectl delete -f  swagger2word.yml
kubectl delete -n tool configmap swagger2word-filebeat-config
kubectl get pod -n tool -o wide