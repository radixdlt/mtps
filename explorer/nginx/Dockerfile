FROM radixdlt/base-nginx:latest-amd64
LABEL maintainer="devops@radixdlt.com"

COPY conf.d/* /etc/nginx/conf.d/

RUN mkdir /etc/radix/
RUN touch /etc/radix/universe.txt