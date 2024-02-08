#!/usr/bin/env bash
set -e

instanceDir=${1}

cp /opt/amq/bin/broker.xml ${instanceDir}/etc/broker.xml