import os
import logging
import argparse
import json
import secrets
import string
import time
import re
from tempfile import NamedTemporaryFile

PLATFORM_GCP = "gcp"
PLATFORM_AWS = "aws"

STORAGE = {
    # cloudinit
    "BASE_CLOUD_INIT_PATH": os.path.join(
        os.path.dirname(os.path.realpath(__file__)),
        "..",
        "cloudinit"),

    "CORE_DOCKER_IMAGE": "radixdlt/radixdlt-core:atom-pump-amd64",

    # default credentials
    "DEFAULT_CLOUD_PLATFORM": PLATFORM_GCP,
    "DEFAULT_CLOUD_EMAIL": "libcloud@m-tps-test-2.iam.gserviceaccount.com",
    "DEFAULT_CLOUD_CREDENTIALS": "~/.gcloud-m-tps-test2.json",
    "DEFAULT_CLOUD_PROJECT": "m-tps-test-2",

    # network config
    "DEFAULT_NETWORK_UNIVERSE": "",
    "DEFAULT_NETWORK_PASSWORD": "DEFAULT_PASSWORD",
    "DEFAULT_NETWORK_ATOMS_FILE": "/radix/atoms_v4_full.zst",
    "DEFAULT_NETWORK_START_URL": "https://1m.radixdlt.com/api/metrics",
    "DEFAULT_NETWORK_SHARD_COUNT": "1100",
    "DEFAULT_NETWORK_SHARD_OVERLAP": "0.1",

    # cores
    "CORE_MACHINE_BOOT_NODE_LOCATION": "us-west2",
    "CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME": "core-boot-template",
    "CORE_MACHINE_INSTANCE_TEMPLATE_NAME": "core-template",
    "CORE_MACHINE_SIZE": "n1-standard-8",
    "CORE_MACHINE_IMAGE": "projects/fast-gateway-233909/global/images/ubuntu-1604-lts-radix-1mtps",
    "CORE_MACHINE_STORAGE": "100",  # GB
    "CORE_EXTRA_DISK_IMAGE_NAME": "projects/fast-gateway-233909/global/images/atoms-v4-full-standard-persistent-image3",
    "CORE_EXTRA_DISK_SIZE": "200",  # GB
    "CORE_REGIONS": {
        # Europe: 345 nodes
        # 130 IP, 1000 CPU, 25TB, 4GB HDD, MAX 125 nodes
        "europe-north1": 125,
        # 110 IP, 1000 CPU, 24TB
        "europe-west4": 110,
        # 110 IP, 1000 CPU, 24TB - 3 vm, bottleneck: SSD_TOTAL_GB,MAX 104 nodes
        "europe-west1": 104,
        # 110 IP, 1000 CPU, 22TB, MAX 110 nodes, new DC
        "europe-west2": 2,
        # 8 IP, 24 CPU, 4TB, MAX 2 nodes
        "europe-west3": 2,
        # 8 IP, 24 CPU, 4TB, MAX 2 nodes
        "europe-west6": 2,

        # Americas: 538 nodes
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

        # Asia: 306 nodes
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

        # Australia: 2 nodes
        # 8 IP, 400 CPU, MAX 2 nodes
        "australia-southeast1": 2
    },

    # extra
    "EXTRA_INSTANCE_TEMPLATE_NAME": "extra-template",

    # explorer
    "EXPLORER_MACHINE_INSTANCE_NAME": "explorer",
    "EXPLORER_MACHINE_ZONE": "europe-west3-a",
    "EXPLORER_MACHINE_SIZE": "n1-standard-8",
    "EXPLORER_MACHINE_IMAGE": "https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1604-xenial-v20190530c",
    "EXPLORER_MACHINE_STORAGE": "10",  # GB

    # test prepper
    "TEST_PREPPER_MACHINE_INSTANCE_NAME": "dataset-preparator",
    "TEST_PREPPER_MACHINE_IMAGE": "https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/ubuntu-1604-xenial-v20190530c",
    "TEST_PREPPER_MACHINE_SIZE": "n1-standard-8",
    "TEST_PREPPER_MACHINE_ZONE": "europe-west3-a",
    "TEST_PREPPER_MACHINE_STORAGE": "1536"  # GB
}

# cores
STORAGE["CORE_CLOUD_INIT"] = os.path.join(STORAGE["BASE_CLOUD_INIT_PATH"], "core")

# extra
STORAGE["CORE_CLOUD_EXTRA"] = os.path.join(STORAGE["BASE_CLOUD_INIT_PATH"], "extra")

# explorer
STORAGE["EXPLORER_CLOUD_INIT"] = os.path.join(STORAGE["BASE_CLOUD_INIT_PATH"], "explorer")

# test prepper
STORAGE["TEST_PREPPER_CLOUD_INIT"] = os.path.join(STORAGE["BASE_CLOUD_INIT_PATH"], "testprepper")

# logging
logging.basicConfig(
    format="%(message)s",
    level=os.environ.get('LOGLEVEL', logging.INFO))
logging.getLogger("paramiko").setLevel(logging.WARNING)


def get(key):
    if key not in STORAGE:
        raise OSError("Missing variable '{0}'".format(key))
    value = os.getenv('RADIX_MTPS_' + key, STORAGE[key])
    logging.debug("config key:'{0}' value:'{1}'".format(key, value))
    return value


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
    parser.add_argument(
        "--config", '-c',
        type=str,
        help="User configuration file (e.g. -c core_2.json",
    )
    return parser.parse_args()


args = get_arguments(argparse.ArgumentParser())
if args.config is not None:
    for file_name in args.config.split(","):
        logging.info("importing '{0}'".format(file_name))
        file = os.path.join(os.path.dirname(os.path.realpath(__file__)), "config/", file_name)
        if not os.path.isfile(file):
            logging.error("Path '{0}' doesn't point to a file".format(file))
            raise OSError
        with open(file) as json_config:
            data = json.load(json_config)
            STORAGE = {**STORAGE, **data}
    logging.debug(json.dumps(STORAGE, indent=4, sort_keys=True))

# read env vars
STORAGE["CLOUD_PROJECT"] = os.getenv('RADIX_MTPS_CLOUD_PROJECT', STORAGE["DEFAULT_CLOUD_PROJECT"])
STORAGE["CLOUD_CREDENTIALS"] = os.getenv('RADIX_MTPS_CLOUD_CREDENTIALS', STORAGE["DEFAULT_CLOUD_CREDENTIALS"])
STORAGE["CLOUD_EMAIL"] = os.getenv('RADIX_MTPS_CLOUD_CLOUD_EMAIL', STORAGE["DEFAULT_CLOUD_EMAIL"])

def generate_password():
    alphabet = string.ascii_letters + string.digits
    return ''.join(secrets.choice(alphabet) for i in range(30))  # for a 20-character password


def pretty_time(date_epoch):
    """Parses the give time string (compatible with the date CLI)"""
    try:
        parsed_time = float(date_epoch[1:])
        return time.asctime(time.localtime(parsed_time))
    except ValueError:
        logging.error("couldn't parse: '{0}' value to time".format(date_epoch))
    return "none"


def print_config():

    # count nodes
    core_nodes_num = 0
    extra_nodes_num = 0
    if "CORE_REGIONS" in STORAGE:
        for region, size in STORAGE["CORE_REGIONS"].items():
            core_nodes_num += size
    if "EXTRA_REGIONS" in STORAGE:
        for region, size in STORAGE["EXTRA_REGIONS"].items():
            extra_nodes_num += size

    logging.info(
        "Configuration: \n\temail: '%s'\n\tcreadentials file: '%s'\n\tproject: '%s'\n\tatom file: "
        "'%s'\n\tshard count: '%s'\n\tshard overlap: '%s'\n\ttotal core nodes with boot_node: '%s'\n\ttotal extra nodes: '%s'\n\tenvironment test starts: '%s'",
        os.getenv('RADIX_MTPS_CLOUD_EMAIL', STORAGE["DEFAULT_CLOUD_EMAIL"]),
        os.getenv('RADIX_MTPS_CLOUD_CREDENTIALS', STORAGE["DEFAULT_CLOUD_CREDENTIALS"]),
        os.getenv('RADIX_MTPS_CLOUD_PROJECT', STORAGE["DEFAULT_CLOUD_PROJECT"]),
        os.environ.get("RADIX_MTPS_NETWORK_ATOMS_FILE", STORAGE["DEFAULT_NETWORK_ATOMS_FILE"]),
        os.environ.get("RADIX_MTPS_SHARD_COUNT", STORAGE["DEFAULT_NETWORK_SHARD_COUNT"]),
        os.environ.get("RADIX_MTPS_SHARD_OVERLAP", STORAGE["DEFAULT_NETWORK_SHARD_OVERLAP"]),
        core_nodes_num + 1,
        extra_nodes_num,
        pretty_time(os.environ.get("RADIX_MTPS_NETWORK_START_PUMP", ""))
    )


# envsubst variables
def render_template(path):
    temp_file = NamedTemporaryFile(delete=False)
    logging.debug("- Rendering %s to %s", path, temp_file.name)
    with open(path) as original_file:
        for line in original_file:
            temp_file.write(expandvars(line).encode())
    return temp_file.name


# replace unset env variables with white space
def expandvars(path, default=''):
    def replace_var(m):
        return os.environ.get(m.group(2) or m.group(1), '')

    reVar = (r'(?<!\\)' '') + r'\$(\w+|\{([^}]*)\})'
    return re.sub(reVar, replace_var, path)

