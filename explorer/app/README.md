# Radix Explorer Web App

This is the Radix Explorer user facing interface project. This project provides a web app that consumes the REST API exposed by the Explorer service. The output of this project can be zipped and deployed to the service project which then can serve it along with its REST API.

## Running the app in dev mode

From the `app` project root folder:

```bash
yarn run serve
```

This will start a separate web server, serving the web app from `http://localhost:8080`. The web app will try to communicate with the Explorer service on `http://localhost:5050` so make sure you have an instance running there (see `service` project for details).

## Project setup

If this is the first time you try to run the web app, you need to setup all dev dependencies as well. You do this by issuing below command from the `app` project's root directory:

```
yarn install
```

## Deploy the project

For now this is a manual process. You'll need to build the output, zip it and move it to the `service` project's resources folder:

```bash
yarn run build
cp -r dist www
zip -r www.zip www
mv -f www.zip ../service/src/main/resources
```

