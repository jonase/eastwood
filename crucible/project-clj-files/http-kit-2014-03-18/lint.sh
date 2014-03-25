#! /bin/bash

lein ${LEIN_PROFILE} eastwood '{:exclude-namespaces [ org.httpkit.client-test org.httpkit.server-test org.httpkit.ws-test ]}'
