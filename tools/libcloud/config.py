import os
import logging
import argparse

# cloudinit
BASE_CLOUD_INIT_PATH = os.path.join(
    os.path.dirname(os.path.realpath(__file__)),
    "..",
    "cloudinit")

# default credentials
DEFAULT_EMAIL = 'libcloud@fast-gateway-233909.iam.gserviceaccount.com'
DEFAULT_CREDS = '~/.gcloud.json'
PROJECT = os.getenv('RADIX_MTPS_CLOUD_PROJECT', 'fast-gateway-233909')

# network config
DEFAULT_NETWORK_UNIVERSE = ""
DEFAULT_NETWORK_PASSWORD = "DEFAULT_PASSWORD"
DEFAULT_NETWORK_ATOMS_FILE = "/radix/atoms_v4_full.zst"
DEFAULT_NETWORK_START_PUMPING = 40  # X minutes
DEFAULT_NETWORK_SHARD_COUNT = "1298"
DEFAULT_NETWORK_SHARD_OVERLAP = "0.1"

# cores
CORE_CLOUD_INIT = os.path.join(BASE_CLOUD_INIT_PATH, "core")
CORE_DOCKER_IMAGE = "radixdlt/radixdlt-core:atom-pump-amd64"
CORE_MACHINE_BOOT_NODE_LOCATION = "us-west2"
CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME = "core-boot-template"
CORE_MACHINE_INSTANCE_TEMPLATE_NAME = "core-template"
CORE_MACHINE_SIZE = "n1-standard-8"
CORE_MACHINE_IMAGE = "projects/fast-gateway-233909/global/images/ubuntu-1604-lts-radix-1mtps"
CORE_MACHINE_STORAGE = "100"  # GB
CORE_EXTRA_DISK_IMAGE_NAME = "projects/fast-gateway-233909/global/images/atoms-v4-full-standard-persistent-image3"
CORE_EXTRA_DISK_SIZE = "200"  # GB
CORE_REGIONS = {
    # Europe
    # 130 IP, 1000 CPU, 25TB, 4GB HDD, MAX 125 nodes
    "europe-north1": 125,
    # 110 IP, 1000 CPU, 24TB
    "europe-west4": 110,
    # 110 IP, 1000 CPU, 24TB - 3 vm, bottleneck: SSD_TOTAL_GB,MAX 104 nodes
    "europe-west1": 104,
    # 110 IP, 1000 CPU, 22TB, MAX 110 nodes, new DC
    "europe-west2": 108,
    # 8 IP, 24 CPU, 4TB, MAX 2 nodes
    "europe-west3": 2,
    # 8 IP, 24 CPU, 4TB, MAX 2 nodes
    "europe-west6": 2,

    # Americas
    # 130 IP, 1000 CPU, 23TB, MAX 115 nodes
    "us-central1": 115,
    # 110 IP, 1000 CPU, 22TB
    "us-west1": 110,
    # 110 IP, 1000 CPU, 22TB
    "us-east1": 110,
    # 100 IP, 1000 CPU, 20.48TB
    "us-east4": 100,
    # 8 IP, 24 CPU, 4TB, MAX 2 nodes
    "us-west2": 2,

    # 100 IP, 1000 CPU, 25TB, DISK_TOTAL_GB MAX 100 nodes - 1 node
    "northamerica-northeast1": 99,
    # 8 IP, 24 CPU, 4TB, MAX 2 nodes:
    "southamerica-east1": 2,

    # Asia
    # 130 IP, 1000 CPU, 20.48TB, bottleneck: SSD's, MAX 102 nodes
    "asia-northeast1": 102,
    # 100 IP, 1000 CPU, 20.48TB - bottleneck: IP's, MAX 100 nodes
    "asia-southeast1": 100,
    # 100 IP, 1000 CPU, 20.48TB
    "asia-east1": 100,
    # 8 IP, 24 CPU, 4TB, MAX 2 nodes
    "asia-east2": 2,
    # 8 IP, 24 CPU, 4TB, MAX 2 nodes
    "asia-south1": 2,

    # Australia
    # 8 IP, 400 CPU, MAX 2 nodes
    "australia-southeast1": 2
}

# explorer
EXPLORER_CLOUD_INIT = os.path.join(BASE_CLOUD_INIT_PATH, "explorer")
EXPLORER_MACHINE_INSTANCE_NAME = "explorer"
EXPLORER_MACHINE_ZONE = "europe-west3-a"
EXPLORER_MACHINE_SIZE = "n1-standard-8"
EXPLORER_MACHINE_IMAGE = "https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1604-xenial-v20190530c"
EXPLORER_MACHINE_STORAGE = "10"  # GB

# test prepper
TEST_PREPPER_MACHINE_INSTANCE_NAME = "dataset-preparator"
TEST_PREPPER_MACHINE_IMAGE = "https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1604-xenial-v20190530c"
TEST_PREPPER_MACHINE_SIZE = "n1-standard-8"
TEST_PREPPER_MACHINE_ZONE = "europe-west3-a"
TEST_PREPPER_MACHINE_STORAGE = "1536"  # GB
TEST_PREPPER_CLOUD_INIT = os.path.join(BASE_CLOUD_INIT_PATH, "testprepper")

# logging
logging.basicConfig(
    format="%(message)s",
    level=os.environ.get('LOGLEVEL', 'INFO').upper())
logging.getLogger("paramiko").setLevel(logging.WARNING)


# arguments
def get_arguments(parser):
    parser.add_argument(
        "--destroy-explorer",
        action="store_true",
        help="Delete the explorer node of the network.")
    parser.add_argument(
        "--destroy-cores",
        action="store_true",
        help="Delete the core nodes of the network.")
    parser.add_argument(
        "--destroy-firewall-rules",
        action="store_true",
        help="Delete the firewall rules of the network.")
    parser.add_argument(
        "--create-test-prepper",
        action="store_true",
        help="Create a specific machine to generate the bitcoin dataset.")
    parser.add_argument(
        "--destroy-test-prepper",
        action="store_true",
        help="Destroy the machine to generate the bitcoin dataset.")
    return parser.parse_args()


get_arguments(argparse.ArgumentParser())
