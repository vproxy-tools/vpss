#!/bin/bash
set -e

ulimit -l 1048576
echo madvise > /sys/kernel/mm/transparent_hugepage/enabled
echo advise > /sys/kernel/mm/transparent_hugepage/shmem_enabled
exec /init.sh -XX:+UseZGC -XX:+UseLargePages -XX:+UseTransparentHugePages -jar /vproxy/vpss/vpss.jar $@
