import os
import logging
import re
import config
import time

from libcloud.common.google import ResourceNotFoundError
from libcloud.common.google import ResourceExistsError
from libcloud.common.google import InvalidRequestError
from tempfile import NamedTemporaryFile


# login GCP
def login_gcp(ComputeEngine):
    gce = ComputeEngine(
        os.getenv('RADIX_MTPS_CLOUD_EMAIL', config.DEFAULT_EMAIL),
        os.getenv('RADIX_MTPS_CLOUD_CREDENTIALS', config.DEFAULT_CREDS), 
        project=config.PROJECT)
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
            zones = [{"zone": "zones/" + z.name} for z in gce.ex_list_zones() if z.status == "UP" and z.name.startswith(region.name)]
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
            if manager["name"].startswith("core-boot-") or manager["name"].startswith("cores-"):
                request = '/regions/{0}/instanceGroupManagers/{1}'.format(manager["region"].split("/")[-1], manager["name"])
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
            name = name,
            image = config.CORE_MACHINE_IMAGE,
            size = config.CORE_MACHINE_SIZE,
            external_ip = 'ephemeral',
            tags = name,
            metadata = {"user-data":cloud_init.read()},
            description = None,
            disks_gce_struct = [
                {
                    'autoDelete': True,
                    'boot': True,
                    "size": config.CORE_MACHINE_STORAGE,
                    "deviceName": '{0}-persistent'.format(name),
                    "initializeParams": {
                        "diskType": 'pd-ssd',
                        "diskName": '{0}-persistent'.format(name),
                        "diskSizeGb": config.CORE_MACHINE_STORAGE,
                        "sourceImage": config.CORE_MACHINE_IMAGE,
                    }
                },
                {
                    "autoDelete":True,
                    "deviceName": '{0}-atoms'.format(name),
                    "initializeParams": {
                        "diskType": 'pd-standard',
                        "diskName": '{0}-atoms'.format(name),
                        "diskSizeGb": config.CORE_EXTRA_DISK_SIZE,
                        "sourceImage": config.CORE_EXTRA_DISK_IMAGE_NAME,
                    }
                }
            ],
            nic_gce_struct=None)
        return True
    except ResourceExistsError:
        logging.debug("- %s template already exists, updating...", name)
        destroyed=destroy_core_template(gce, name)
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
            gce.wait_until_running([node], 5, 600) # 5 second loops | 10 minute timeout
            return node
    return False


def wait_boot_node(gce):
    for i in range(60):
        node = get_boot_node(gce)
        if node:
            return node
        time.sleep(10)
    raise RuntimeError("boot node did not start in 600 seconds")


def wait_explorer_node(gce):
    for i in range(60):
        node = get_explorer(gce)
        if node:
            return node
        time.sleep(10)
    raise RuntimeError("boot node did not start in 600 seconds")


def count_core_nodes(gce, region):
    nodes = gce.list_nodes()
    count = 0
    for node in nodes:
        if node.name.startswith("cores-{0}-".format(region.name)):
            count += 1
    return count


# explorer
def create_explorer(gce, cloud_init):
    node = gce.create_node(
        name = config.EXPLORER_MACHINE_INSTANCE_NAME,
        image = config.EXPLORER_MACHINE_IMAGE,
        size = config.EXPLORER_MACHINE_SIZE,
        location = config.EXPLORER_MACHINE_ZONE,
        ex_tags = config.EXPLORER_MACHINE_INSTANCE_NAME,
        ex_metadata = {"user-data":cloud_init.read()},
        ex_labels = "type={0}".format(config.EXPLORER_MACHINE_INSTANCE_NAME),
        ex_disks_gce_struct=[
            {
                'autoDelete': True,
                'boot': True,
                'type': 'PERSISTENT',
                'mode': 'READ_WRITE',
                'deviceName': config.EXPLORER_MACHINE_INSTANCE_NAME,
                'initializeParams': {
                    'diskName': config.EXPLORER_MACHINE_INSTANCE_NAME,
                    'sourceImage': config.EXPLORER_MACHINE_IMAGE
                }
            },
        ]
    )
    return node


def destroy_explorer(gce, name):
    try:
        node = gce.ex_get_node(name)
        gce.destroy_node(node, True)
    except ResourceNotFoundError:
        logging.info("- Explorer node not found in GCP.")


def explorer_exists(gce):
    try:
        gce.ex_get_node(config.EXPLORER_MACHINE_INSTANCE_NAME)
        return True
    except ResourceNotFoundError:
        return False


def get_explorer(gce):
    node = gce.ex_get_node(config.EXPLORER_MACHINE_INSTANCE_NAME)
    gce.wait_until_running([node], 5, 600) # 5 second loops | 10 minute timeout
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

