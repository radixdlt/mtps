import os
import logging
import argparse


# cloudinit
BASE_CLOUD_INIT_PATH=os.path.join(
    os.path.dirname(os.path.realpath(__file__)),
    "..",
    "gcp",
    "cloudinit")

# default credentials
DEFAULT_EMAIL = 'libcloud@m-tps-test-2.iam.gserviceaccount.com'
DEFAULT_CREDS = '~/.gcloud.json'
PROJECT = os.getenv('RADIX_MTPS_CLOUD_PROJECT', 'm-tps-test-2')

# network config
DEFAULT_NETWORK_UNIVERSE=""
DEFAULT_NETWORK_PASSWORD="DEFAULT_PASSWORD"
DEFAULT_NETWORK_ATOMS_FILE="/radix/atoms_v4_full.zst"
DEFAULT_NETWORK_START_PUMPING=40 # X minutes
DEFAULT_NETWORK_SHARD_COUNT="1000"
DEFAULT_NETWORK_SHARD_OVERLAP="0.1"

# cores
CORE_CLOUD_INIT = os.path.join(BASE_CLOUD_INIT_PATH, "core")
CORE_DOCKER_IMAGE = "radixdlt/radixdlt-core:atom-pump-amd64"
CORE_MACHINE_BOOT_NODE_LOCATION = "us-west2"
CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME = "core-boot-template"
CORE_MACHINE_INSTANCE_TEMPLATE_NAME = "core-template"
CORE_MACHINE_SIZE = "n1-standard-8"
CORE_MACHINE_IMAGE = "projects/fast-gateway-233909/global/images/ubuntu-1604-lts-radix-1mtps"
CORE_MACHINE_STORAGE = "100" # GB
CORE_EXTRA_DISK_IMAGE_NAME = "projects/fast-gateway-233909/global/images/atoms-v4-full-standard-persistent-image"
CORE_EXTRA_DISK_SIZE = "200" # GB
CORE_REGIONS = {
    # 130 IP, 1000 CPU, 20.48TB
    #"asia-northeast1": 1,
    #"us-central1": 1,
    # 110 IP, 1000 CPU, 20.48TB
    #"us-west1": 1,
    #"us-east1": 1,
    "europe-west4": 1,
    # 110 IP, 1000 CPU, 19TB
    "europe-west1": 1,
    # 100 IP, 1000 CPU, 20.48TB
    #"us-east4": 1,
    #"asia-southeast1": 1,
    #"asia-east1": 1,
    # 130 IP, 1000 CPY, 0.5 TB
    #"europe-north1": 1,
    # 8 IP, 1000 CPU
    #"northamerica-northeast1": 1,
    # 8 IP, 400 CPU
    #"australia-southeast1": 1,
}

# explorer
EXPLORER_CLOUD_INIT = os.path.join(BASE_CLOUD_INIT_PATH, "explorer")
EXPLORER_MACHINE_INSTANCE_NAME = "explorer"
EXPLORER_MACHINE_ZONE = "europe-west3-a"
EXPLORER_MACHINE_SIZE = "n1-standard-8"
EXPLORER_MACHINE_IMAGE = "https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1604-xenial-v20190530c"
EXPLORER_MACHINE_STORAGE = "10" # GB

# test prepper
TEST_PREPPER_MACHINE_INSTANCE_NAME = "dataset-preparator"
TEST_PREPPER_MACHINE_IMAGE = "https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1604-xenial-v20190530c"
TEST_PREPPER_MACHINE_SIZE="n1-standard-8"
TEST_PREPPER_MACHINE_ZONE="europe-west3-a"
TEST_PREPPER_MACHINE_STORAGE="1536" # GB
TEST_PREPPER_CLOUD_INIT = os.path.join(BASE_CLOUD_INIT_PATH, "testprepper")

# logging
logging.basicConfig(
        format = "%(message)s",
        level = os.environ.get('LOGLEVEL', 'INFO').upper())
logging.getLogger("paramiko").setLevel(logging.WARNING)

# arguments
def get_arguments(parser):
    parser.add_argument(
        "--destroy-explorer",
        action = "store_true",
        help = "Delete the explorer node of the network.")
    parser.add_argument(
        "--destroy-cores",
        action = "store_true",
        help = "Delete the core nodes of the network.")
    parser.add_argument(
        "--destroy-firewall-rules",
        action = "store_true",
        help = "Delete the firewall rules of the network.")
    parser.add_argument(
        "--create-test-prepper",
        action = "store_true",
        help = "Create a specific machine to generate the bitcoin dataset.")
    parser.add_argument(
        "--destroy-test-prepper",
        action = "store_true",
        help = "Destroy the machine to generate the bitcoin dataset.")
    return parser.parse_args()


get_arguments(argparse.ArgumentParser())