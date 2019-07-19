import os
import sys
import logging
import time
import random

import config
import gcp
import ssh
import aws

from libcloud.compute.types import Provider
from libcloud.compute.providers import get_driver

import secrets
import string


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


def wait_for_public_ip(node):
    gce.wait_until_running([node], 5, 600)
    while not node.public_ips:
        logging.info("Waiting for %s to get its public IP ...", node.name)
        time.sleep(5)
        node = gce.ex_get_node(node.name)


def random_string(string_length=4):
    """Generate a random string of fixed length """
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(string_length))


# count nodes
core_nodes_num = 0
extra_nodes_num = 0
if "CORE_REGIONS" in config.STORAGE:
    for region, size in config.STORAGE["CORE_REGIONS"].items():
        core_nodes_num += size
if "EXTRA_REGIONS" in config.STORAGE:
    for region, size in config.STORAGE["EXTRA_REGIONS"].items():
        extra_nodes_num += size


# show test variables
logging.info(
    "Configuration: \n\temail: '%s'\n\tcreadentials file: '%s'\n\tproject: '%s'\n\tatom file: "
    "'%s'\n\tshard count: '%s'\n\tshard overlap: '%s'\n\ttotal core nodes with boot_node: '%s'\n\ttotal extra nodes: '%s'\n\texplorer metrics URL: '%s'",
    os.getenv('RADIX_MTPS_CLOUD_EMAIL', config.STORAGE["DEFAULT_CLOUD_EMAIL"]),
    os.getenv('RADIX_MTPS_CLOUD_CREDENTIALS', config.STORAGE["DEFAULT_CLOUD_CREDENTIALS"]),
    os.getenv('RADIX_MTPS_CLOUD_PROJECT', config.STORAGE["DEFAULT_CLOUD_PROJECT"]),
    os.environ.get("RADIX_MTPS_NETWORK_ATOMS_FILE", config.STORAGE["DEFAULT_NETWORK_ATOMS_FILE"]),
    os.environ.get("RADIX_MTPS_SHARD_COUNT", config.STORAGE["DEFAULT_NETWORK_SHARD_COUNT"]),
    os.environ.get("RADIX_MTPS_SHARD_OVERLAP", config.STORAGE["DEFAULT_NETWORK_SHARD_OVERLAP"]),
    core_nodes_num + 1,
    extra_nodes_num,
    os.environ.get("RADIX_MTPS_NETWORK_START_PUMP_URL", config.STORAGE["DEFAULT_NETWORK_START_URL"])
)

# drivers
ComputeEngine = get_driver(Provider.GCE)
gce = gcp.login_gcp(ComputeEngine)
aws_e = aws.getEngines()

# firewall rules
if '--destroying-firewall-rules' in sys.argv:
    logging.info("Destroying firewall rules...")
    gcp.destroy_ingress_rules(gce)
else:
    logging.info("Checking if firewall rules are created...")
    gcp.create_ingress_rules(gce)
    aws.create_ingress_rules(aws_e)

# create test prepper
if '--create-test-prepper' in sys.argv:
    if not gcp.test_prepper_exists(gce):
        logging.info("Creating dataset preparator...")

        rendered_file = gcp.render_template(config.STORAGE["TEST_PREPPER_CLOUD_INIT"])
        test_prepper_rendered_file = open(rendered_file, 'r')
        gcp.create_test_prepper(gce, test_prepper_rendered_file)
# destroy test prepper
elif '--destroy-test-prepper' in sys.argv:
    if gcp.test_prepper_exists(gce):
        logging.info("Destroying dataset preparator...")
        gcp.destroy_node(gce, config.STORAGE["TEST_PREPPER_MACHINE_INSTANCE_NAME"])

# destroy explorer
if '--destroy-explorer' in sys.argv:
    if gcp.explorer_exists(gce):
        logging.info("Destroying explorer node...")
        gcp.destroy_node(gce, config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"])

# create explorer
else:
    if not gcp.explorer_exists(gce):
        logging.info("Creating explorer...")

        if not os.environ.get("RADIX_MTPS_NETWORK_PASSWORD"):
            # generate random password
            os.environ["RADIX_MTPS_NETWORK_PASSWORD"] = generate_password()
            logging.info("NOTE: generated the admin/metrics password for you: %s",
                         os.environ["RADIX_MTPS_NETWORK_PASSWORD"])

        if not os.environ.get("RADIX_MTPS_NGINX_ACCESS"):
            os.environ["RADIX_MTPS_NGINX_ACCESS"] = "SUCCESS"

        # render file
        rendered_file = gcp.render_template(config.STORAGE["EXPLORER_CLOUD_INIT"])
        explorer_rendered_file = open(rendered_file, 'r')
        explorer = gcp.create_explorer(gce, explorer_rendered_file)
        logging.info("- Explorer: https://%s", explorer.public_ips[0])
    else:
        logging.info("An explorer node seems to be up and running.")

# destroy cores
if '--destroy-cores' in sys.argv:
    # destroy core nodes
    logging.info("Destroying core nodes...")
    gcp.destroy_all_core_groups(gce)

    while gcp.count_core_groups(gce) > 0:
        logging.info("Waiting for core nodes to come down, sleeping 10 seconds")
        time.sleep(10)
    # delete templates
    logging.debug("Destroying templates...")
    gcp.destroy_core_template(gce, config.STORAGE["CORE_MACHINE_INSTANCE_TEMPLATE_NAME"])
    gcp.destroy_core_template(gce, config.STORAGE["CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME"])
    gcp.destroy_core_template(gce, config.STORAGE["EXTRA_INSTANCE_TEMPLATE_NAME"])
# create cores
else:
    os.environ["CORE_DOCKER_IMAGE"] = config.STORAGE["CORE_DOCKER_IMAGE"]

    explorer = gcp.get_explorer(gce)
    attempts = 0

    # wait for the explorer to come up if necessary
    while attempts < 3:
        try:
            os.environ["RADIX_MTPS_NETWORK_EXPLORER_IP"] = explorer.public_ips[0]
            os.environ["RADIX_MTPS_NETWORK_ATOMS_FILE"] = os.environ.get("RADIX_MTPS_NETWORK_ATOMS_FILE",
                                                                         config.STORAGE["DEFAULT_NETWORK_ATOMS_FILE"])
            os.environ["RADIX_MTPS_NETWORK_PASSWORD"] = ssh.get_admin_password(explorer.public_ips[0])
            attempts = 3
        except Exception:
            attempts += 1
            time.sleep(15)

    # start pumping URL
    if not os.environ.get("RADIX_MTPS_NETWORK_START_PUMP_URL"):
            os.environ["RADIX_MTPS_NETWORK_START_PUMP_URL"] = config.STORAGE["DEFAULT_NETWORK_START_URL"]

    boot_node = gcp.get_boot_node(gce)
    if boot_node:
        logging.info("A boot node seems to be up and running.")
        # extract the universe from host
        os.environ["RADIX_MTPS_NETWORK_UNIVERSE"] = ssh.get_test_universe(boot_node.public_ips[0])
    else:
        # reconfigure shard allocator
        shard_count = os.environ.get("RADIX_MTPS_SHARD_COUNT", config.STORAGE["DEFAULT_NETWORK_SHARD_COUNT"])
        ssh.update_shard_count(
            explorer.public_ips[0],
            os.environ.get("RADIX_MTPS_SHARD_COUNT", config.STORAGE["DEFAULT_NETWORK_SHARD_COUNT"]),
            os.environ.get("RADIX_MTPS_SHARD_OVERLAP", config.STORAGE["DEFAULT_NETWORK_SHARD_OVERLAP"]))

        # generate a new universe
        if "RADIX_MTPS_NETWORK_UNIVERSE" not in os.environ:
            logging.info("Generating new universe...")
            attempts = 0

            # wait for docker to be available
            while attempts < 3:
                try:
                    universe = ssh.generate_universe(explorer.public_ips[0])
                    attempts = 3
                except Exception as e:
                    if attempts == 2:
                        raise Exception(e)

                    attempts += 1
                    time.sleep(15)
            os.environ["RADIX_MTPS_NETWORK_UNIVERSE"] = universe

        # boot node
        logging.info("Creating boot node...")

        rendered_file = gcp.render_template(config.STORAGE["CORE_CLOUD_INIT"])
        region = gce.ex_get_region(config.STORAGE["CORE_MACHINE_BOOT_NODE_LOCATION"])
        gcp.create_core_template(gce, config.STORAGE["CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME"],
                                 open(rendered_file, "r"))
        gcp.create_core_group(gce, region, 1, config.STORAGE["CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME"],
                              prefix="core-boot")
        boot_node = gcp.wait_boot_node(gce)
        wait_for_public_ip(boot_node)

        # TODO: update node-finder with boot node info
        logging.info("Updating node finder...")
        ssh.update_node_finder(explorer.public_ips[0], boot_node.public_ips[0])
    logging.debug("Test universe: %s", os.environ["RADIX_MTPS_NETWORK_UNIVERSE"])
    logging.info("Test will run at: (go to %s)", os.environ["RADIX_MTPS_NETWORK_START_PUMP_URL"])

    os.environ["RADIX_MTPS_NETWORK_SEEDS"] = boot_node.public_ips[0]

    # create core node machine template
    rendered_file = gcp.render_template(config.STORAGE["CORE_CLOUD_INIT"])
    gcp.create_core_template(gce, config.STORAGE["CORE_MACHINE_INSTANCE_TEMPLATE_NAME"], open(rendered_file, "r"))

    for region, size in config.STORAGE["CORE_REGIONS"].items():
        region = gce.ex_get_region(region)
        count = gcp.count_core_nodes(gce, region)
        if count == 0:
            logging.info("Creating %d Core node in %s...", size, region.name)
            gcp.create_core_group(gce, region, size, config.STORAGE["CORE_MACHINE_INSTANCE_TEMPLATE_NAME"])
        elif count < size:
            logging.warning("Not enough Core nodes in %s than requested: %d < %d", region.name, count, size)
        else:
            logging.info("Core nodes in %s are already up and running", region.name)

    # create extra nodes
    if "EXTRA_REGIONS" in config.STORAGE:
        rendered_file = gcp.render_template(config.STORAGE["CORE_CLOUD_EXTRA"])
        gcp.create_core_template(gce, name=config.STORAGE["EXTRA_INSTANCE_TEMPLATE_NAME"], cloud_init=open(rendered_file, "r"))

        for region, size in config.STORAGE["EXTRA_REGIONS"].items():
            region = gce.ex_get_region(region)
            count = gcp.count_core_nodes(gce, region, "extra")
            if count == 0:
                logging.info("Creating %d extra node in %s...", size, region.name)
                gcp.create_core_group(gce, region, size, config.STORAGE["EXTRA_INSTANCE_TEMPLATE_NAME"],
                                      prefix="extra")
            elif count < size:
                logging.warning("Not enough Malicious nodes in %s than requested: %d < %d", region.name, count, size)
            else:
                logging.info("Malicious nodes in %s are already up and running", region.name)
