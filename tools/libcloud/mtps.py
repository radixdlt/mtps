import os

import config
import gcp
import aws


# show test variables
config.print_config()

# GCP
if config.STORAGE["DEFAULT_CLOUD_PLATFORM"] == config.PLATFORM_GCP:
    gcp.initialize_test()

# AWS
elif config.STORAGE["DEFAULT_CLOUD_PLATFORM"] == config.PLATFORM_AWS:
    aws.initialize_test()
