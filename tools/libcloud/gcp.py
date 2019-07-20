import logging
import re
import sys
import config
import time
import os

import config
import ssh

from libcloud.compute.providers import get_driver
from libcloud.compute.types import Provider
from libcloud.common.google import ResourceNotFoundError
from libcloud.common.google import ResourceExistsError
from libcloud.common.google import InvalidRequestError
from tempfile import NamedTemporaryFile


# initialize test
def initialize_test(): 
    ComputeEngine = get_driver(Provider.GCE)
    gce = login_gcp(ComputeEngine)

    # firewall rules
    if '--destroying-firewall-rules' in sys.argv:
        logging.info("Destroying firewall rules...")
        destroy_ingress_rules(gce)
    else:
        logging.info("Checking if firewall rules are created...")
        create_ingress_rules(gce)

    # create test prepper
    if '--create-test-prepper' in sys.argv:
        if not test_prepper_exists(gce):
            logging.info("Creating dataset preparator...")

            rendered_file = render_template(config.STORAGE["TEST_PREPPER_CLOUD_INIT"])
            test_prepper_rendered_file = open(rendered_file, 'r')
            create_test_prepper(gce, test_prepper_rendered_file)
    # destroy test prepper
    elif '--destroy-test-prepper' in sys.argv:
        if test_prepper_exists(gce):
            logging.info("Destroying dataset preparator...")
            destroy_node(gce, config.STORAGE["TEST_PREPPER_MACHINE_INSTANCE_NAME"])

    # destroy explorer
    if '--destroy-explorer' in sys.argv:
        if explorer_exists(gce):
            logging.info("Destroying explorer node...")
            destroy_node(gce, config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"])

    # create explorer
    else:
        if not explorer_exists(gce):
            logging.info("Creating explorer...")

            if not os.environ.get("RADIX_MTPS_NETWORK_PASSWORD"):
                # generate random password
                os.environ["RADIX_MTPS_NETWORK_PASSWORD"] = config.generate_password()
                logging.info("NOTE: generated the admin/metrics password for you: %s",
                            os.environ["RADIX_MTPS_NETWORK_PASSWORD"])

            if not os.environ.get("RADIX_MTPS_NGINX_ACCESS"):
                os.environ["RADIX_MTPS_NGINX_ACCESS"] = "SUCCESS"

            # render file
            rendered_file = render_template(config.STORAGE["EXPLORER_CLOUD_INIT"])
            explorer_rendered_file = open(rendered_file, 'r')
            explorer = create_explorer(gce, explorer_rendered_file)
            logging.info("- Explorer: https://%s", explorer.public_ips[0])
        else:
            logging.info("An explorer node seems to be up and running.")

    # destroy cores
    if '--destroy-cores' in sys.argv:
        # destroy core nodes
        logging.info("Destroying core nodes...")
        destroy_all_core_groups(gce)

        while count_core_groups(gce) > 0:
            logging.info("Waiting for core nodes to come down, sleeping 10 seconds")
            time.sleep(10)
        # delete templates
        logging.debug("Destroying templates...")
        destroy_core_template(gce, config.STORAGE["CORE_MACHINE_INSTANCE_TEMPLATE_NAME"])
        destroy_core_template(gce, config.STORAGE["CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME"])
        destroy_core_template(gce, config.STORAGE["EXTRA_INSTANCE_TEMPLATE_NAME"])
    # create cores
    else:
        os.environ["CORE_DOCKER_IMAGE"] = config.STORAGE["CORE_DOCKER_IMAGE"]

        explorer = get_explorer(gce)
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

        boot_node = get_boot_node(gce)
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

            rendered_file = render_template(config.STORAGE["CORE_CLOUD_INIT"])
            region = gce.ex_get_region(config.STORAGE["CORE_MACHINE_BOOT_NODE_LOCATION"])
            create_core_template(gce, config.STORAGE["CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME"],
                                    open(rendered_file, "r"))
            create_core_group(gce, region, 1, config.STORAGE["CORE_MACHINE_BOOT_INSTANCE_TEMPLATE_NAME"],
                                prefix="core-boot")
            boot_node = wait_boot_node(gce)
            wait_for_public_ip(gce, boot_node)

            # TODO: update node-finder with boot node info
            logging.info("Updating node finder...")
            ssh.update_node_finder(explorer.public_ips[0], boot_node.public_ips[0])
        logging.debug("Test universe: %s", os.environ["RADIX_MTPS_NETWORK_UNIVERSE"])
        logging.info("Test will run at: (go to %s)", os.environ["RADIX_MTPS_NETWORK_START_PUMP_URL"])

        os.environ["RADIX_MTPS_NETWORK_SEEDS"] = boot_node.public_ips[0]

        # create core node machine template
        rendered_file = render_template(config.STORAGE["CORE_CLOUD_INIT"])
        create_core_template(gce, config.STORAGE["CORE_MACHINE_INSTANCE_TEMPLATE_NAME"], open(rendered_file, "r"))

        for region, size in config.STORAGE["CORE_REGIONS"].items():
            region = gce.ex_get_region(region)
            count = count_core_nodes(gce, region)
            if count == 0:
                logging.info("Creating %d Core node in %s...", size, region.name)
                create_core_group(gce, region, size, config.STORAGE["CORE_MACHINE_INSTANCE_TEMPLATE_NAME"])
            elif count < size:
                logging.warning("Not enough Core nodes in %s than requested: %d < %d", region.name, count, size)
            else:
                logging.info("Core nodes in %s are already up and running", region.name)

        # create extra nodes
        if "EXTRA_REGIONS" in config.STORAGE:
            rendered_file = render_template(config.STORAGE["CORE_CLOUD_EXTRA"])
            create_core_template(gce, name=config.STORAGE["EXTRA_INSTANCE_TEMPLATE_NAME"], cloud_init=open(rendered_file, "r"))

            for region, size in config.STORAGE["EXTRA_REGIONS"].items():
                region = gce.ex_get_region(region)
                count = count_core_nodes(gce, region, "extra")
                if count == 0:
                    logging.info("Creating %d extra node in %s...", size, region.name)
                    create_core_group(gce, region, size, config.STORAGE["EXTRA_INSTANCE_TEMPLATE_NAME"],
                                        prefix="extra")
                elif count < size:
                    logging.warning("Not enough Malicious nodes in %s than requested: %d < %d", region.name, count, size)
                else:
                    logging.info("Malicious nodes in %s are already up and running", region.name)


# login GCP
def login_gcp(ComputeEngine):
    gce = ComputeEngine(
        config.get("CLOUD_EMAIL"),
        config.get("CLOUD_CREDENTIALS"),
        project=config.get("CLOUD_PROJECT"))
    return gce


# firewall rules
def create_ingress_rules(gce):
    core_ports = [
        {"IPProtocol": "tcp", "ports": ["22", "443", "20000"]},
        {"IPProtocol": "udp", "ports": ["20000"]}
    ]
    explorer_ports = [
        {"IPProtocol": "tcp", "ports": ["22", "80", "443", "8080"]},
        {"IPProtocol": "udp", "ports": ["20000"]}
    ]

    try:
        # explorer
        gce.ex_create_firewall(
            name='explorer-ingress-rules',
            allowed=explorer_ports,
            description='explorer-ingress-rules')
    except ResourceExistsError:
        logging.debug("- Explorer rules already exists.")

    try:
        # cores
        gce.ex_create_firewall(
            name='core-ingress-rules',
            allowed=core_ports,
            description='core-ingress-rules')
    except ResourceExistsError:
        logging.debug("- Core rules already exists.")


def destroy_ingress_rules(gce):
    try:
        explorer_firewall = gce.ex_get_firewall("explorer-ingress-rules")
        gce.ex_destroy_firewall(explorer_firewall)
    except ResourceNotFoundError:
        logging.debug("- Explorer firewall rules not found.")

    try:
        core_firewall = gce.ex_get_firewall("core-ingress-rules")
        gce.ex_destroy_firewall(core_firewall)
    except ResourceNotFoundError:
        logging.debug("- Core firewall rules not found.")


# cores
def create_core_group(gce, region, size, template, prefix="cores"):
    try:
        region = region or gce.region
        if not hasattr(region, 'name'):
            region = gce.ex_get_region(region)
        request = '/regions/%s/instanceGroupManagers' % (region.name)
        manager_data = {}

        # If the user gave us a name, we fetch the GCEInstanceTemplate for it.
        if not hasattr(template, 'name'):
            template = gce.ex_get_instancetemplate(template)
        manager_data['instanceTemplate'] = template.extra['selfLink']
        # If base_instance_name is not set, we use name.
        manager_data['name'] = manager_data['baseInstanceName'] = "{0}-{1}".format(prefix, region.name)
        manager_data['distributionPolicy'] = dict(
            zones=[{"zone": "zones/" + z.name} for z in gce.ex_list_zones() if
                   z.status == "UP" and z.name.startswith(region.name)]
        )
        manager_data['targetSize'] = size
        manager_data['description'] = None

        gce.connection.request(request, method='POST', data=manager_data)
    except ResourceExistsError:
        pass


def count_core_groups(gce):
    response = gce.connection.request("/aggregated/instanceGroupManagers", method='GET').object

    count = 0
    for item in response["items"].values():
        for manager in item.get("instanceGroupManagers", []):

            if manager["name"].startswith("core-boot-") or manager["name"].startswith("cores-"):
                count += 1
    return count


def destroy_all_core_groups(gce):
    response = gce.connection.request("/aggregated/instanceGroupManagers", method='GET').object
    for item in response["items"].values():
        for manager in item.get("instanceGroupManagers", []):
            if manager["name"].startswith("core-boot-") \
                    or manager["name"].startswith("cores-") \
                    or manager["name"].startswith("extra-"):
                request = '/regions/{0}/instanceGroupManagers/{1}'.format(manager["region"].split("/")[-1],
                                                                          manager["name"])
                try:
                    logging.info("Deleting instance group: %s ...", manager["name"])
                    gce.connection.request(request, method='DELETE')
                except ResourceNotFoundError:
                    pass
                # being deleted
                except InvalidRequestError:
                    pass


def create_core_template(gce, name, cloud_init):
    try:
        gce.ex_create_instancetemplate(
            name=name,
            image=config.STORAGE["CORE_MACHINE_IMAGE"],
            size=config.STORAGE["CORE_MACHINE_SIZE"],
            external_ip='ephemeral',
            tags=name,
            metadata={"user-data": cloud_init.read()},
            description=None,
            disks_gce_struct=[
                {
                    'autoDelete': True,
                    'boot': True,
                    "size": config.STORAGE["CORE_MACHINE_STORAGE"],
                    "deviceName": '{0}-persistent'.format(name),
                    "initializeParams": {
                        "diskType": 'pd-ssd',
                        "diskSizeGb": config.STORAGE["CORE_MACHINE_STORAGE"],
                        "sourceImage": config.STORAGE["CORE_MACHINE_IMAGE"],
                    }
                },
                {
                    "autoDelete": True,
                    "deviceName": '{0}-atoms'.format(name),
                    "initializeParams": {
                        "diskType": 'pd-standard',
                        "diskSizeGb": config.STORAGE["CORE_EXTRA_DISK_SIZE"],
                        "sourceImage": config.STORAGE["CORE_EXTRA_DISK_IMAGE_NAME"],
                    }
                }
            ],
            nic_gce_struct=None)
        return True
    except ResourceExistsError:
        logging.debug("- %s template already exists, updating...", name)
        destroyed = destroy_core_template(gce, name)
        if destroyed:
            create_core_template(gce, name, cloud_init)
            return True
    return False


def destroy_core_template(gce, name):
    template = lambda: None
    setattr(template, 'name', name)
    try:
        gce.ex_destroy_instancetemplate(template)
        return True
    except ResourceNotFoundError:
        pass
    except InvalidRequestError:
        logging.debug("- Unable to destroy template %s, being used.", name)
    return False


def get_boot_node(gce):
    nodes = gce.list_nodes()
    for node in nodes:
        if node.name.startswith("core-boot-"):
            gce.wait_until_running([node], 5, 600)  # 5 second loops | 10 minute timeout
            return node
    return False


def wait_boot_node(gce):
    for i in range(60):
        node = get_boot_node(gce)
        if node:
            return node
        time.sleep(10)
    raise RuntimeError("boot node did not start in 600 seconds")


def count_core_nodes(gce, region, prefix="cores-"):
    nodes = gce.list_nodes()
    count = 0
    for node in nodes:
        if node.name.startswith((prefix + ("{0}-")).format(region.name)):
            count += 1
    return count


# explorer
def create_explorer(gce, cloud_init):
    node = gce.create_node(
        name=config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"],
        image=config.STORAGE["EXPLORER_MACHINE_IMAGE"],
        size=config.STORAGE["EXPLORER_MACHINE_SIZE"],
        location=config.STORAGE["EXPLORER_MACHINE_ZONE"],
        ex_tags=config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"],
        ex_metadata={"user-data": cloud_init.read()},
        ex_labels="type={0}".format(config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"]),
        ex_disks_gce_struct=[
            {
                'autoDelete': True,
                'boot': True,
                'type': 'PERSISTENT',
                'mode': 'READ_WRITE',
                'deviceName': config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"],
                'initializeParams': {
                    'diskName': config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"],
                    'sourceImage': config.STORAGE["EXPLORER_MACHINE_IMAGE"]
                }
            },
        ]
    )
    return node


def destroy_node(gce, name):
    try:
        node = gce.ex_get_node(name)
        gce.destroy_node(node, True)
    except ResourceNotFoundError:
        logging.info("- Node %s not found in ", name)


def explorer_exists(gce):
    try:
        gce.ex_get_node(config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"])
        return True
    except ResourceNotFoundError:
        return False


def get_explorer(gce):
    node = gce.ex_get_node(config.STORAGE["EXPLORER_MACHINE_INSTANCE_NAME"])
    gce.wait_until_running([node], 5, 600)  # 5 second loops | 10 minute timeout
    return node


def test_prepper_exists(gce):
    try:
        gce.ex_get_node(config.STORAGE["TEST_PREPPER_MACHINE_INSTANCE_NAME"])
        return True
    except ResourceNotFoundError:
        return False


def create_test_prepper(gce, cloud_init):
    node = gce.create_node(
        name=config.STORAGE["TEST_PREPPER_MACHINE_INSTANCE_NAME"],
        image=config.STORAGE["TEST_PREPPER_MACHINE_IMAGE"],
        size=config.STORAGE["TEST_PREPPER_MACHINE_SIZE"],
        location=config.STORAGE["TEST_PREPPER_MACHINE_ZONE"],
        ex_tags=config.STORAGE["TEST_PREPPER_MACHINE_INSTANCE_NAME"],
        ex_metadata={"user-data": cloud_init.read()},
        ex_labels="type={0}".format(config.STORAGE["TEST_PREPPER_MACHINE_INSTANCE_NAME"]),
        ex_disks_gce_struct=[
            {
                'autoDelete': True,
                'boot': True,
                'type': 'PERSISTENT',
                'mode': 'READ_WRITE',
                'deviceName': config.STORAGE["TEST_PREPPER_MACHINE_INSTANCE_NAME"],
                'initializeParams': {
                    'diskName': config.STORAGE["TEST_PREPPER_MACHINE_INSTANCE_NAME"],
                    'sourceImage': config.STORAGE["TEST_PREPPER_MACHINE_IMAGE"]
                }
            },
        ]
    )
    return node


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


def wait_for_public_ip(gce, node):
    gce.wait_until_running([node], 5, 600)
    while not node.public_ips:
        logging.info("Waiting for %s to get its public IP ...", node.name)
        time.sleep(5)
        node = gce.ex_get_node(node.name)
