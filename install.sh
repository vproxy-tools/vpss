#!/bin/bash
set -e

mkdir -p /etc/vpss

echo " \$image $@" > /etc/vpss/launch-vpss

echo "ubuntu:20.04" > /etc/vpss/require-images
echo "/vpss-base" >> /etc/vpss/require-images
echo "/vpss" >> /etc/vpss/require-images
echo "/tools-dhclient" >> /etc/vpss/require-images
echo "/tools-socat-with-dhclient" >> /etc/vpss/require-images
echo "/vpws-agent-with-dhclient" >> /etc/vpss/require-images

docker run -d --restart=always \
    --net=host \
    -v /var/run/docker.sock:/var/run/docker.sock:ro \
    -v /etc/vpss:/etc/vpss \
    --name=vpss-launcher \
    vproxyio/vpss-launcher:latest
