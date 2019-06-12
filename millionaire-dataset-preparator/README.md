# Tool for converting Bitcoin *.blk files to Radix Atoms

# Building

```shell
./gradlew clean distTar --refresh-dependencies
```

If you want a docker image then use the [Makefile](../Makefile) in the root:

```shell
make millionaire-dataset-preparator
```

# Deployment

Easy to get the Docker image running on any platform that support Docker.
You can use the [mtps.py](../libcloud/mtps.py) to create suitable VM in Google Cloud.

