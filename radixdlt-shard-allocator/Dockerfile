FROM node:alpine

USER root

# copy shard-allocator app
COPY ./app /usr/src/app/

# npm install
RUN cd /usr/src/app/ && npm install

# add user
RUN addgroup -S radix && adduser -S radix -G radix

# set permissions
RUN mkdir /data && \
    chown -R radix:radix /usr/src/app/ /data/

# set user
USER radix

# expose port
EXPOSE 8080

# set workdir
WORKDIR /data

# set cmd
CMD ["node", "/usr/src/app/app.js"]
