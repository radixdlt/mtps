import logging
import os
import sys

import config
import csv
import base64
import ssh
import time

from os.path import expanduser
from libcloud.compute.drivers.ec2 import BaseEC2NodeDriver
from libcloud.compute.drivers.ec2 import EC2Connection
from libcloud.compute.drivers.ec2 import EC2NodeDriver
from libcloud.compute.drivers.ec2 import EC2Response
from libcloud.compute.providers import get_driver
from libcloud.compute.types import Provider
from libcloud.common.base import BaseDriver
from libcloud.common.base import ConnectionKey
from libcloud.compute.base import NodeImage
from libcloud.compute.base import NodeSize
from libcloud.common.exceptions import BaseHTTPError
from libcloud.utils.xml import fixxpath, findtext, findattr, findall


AUTOSCALE_REGION_DETAILS = {
    'us-east-1': {
        'endpoint': 'autoscaling.us-east-1.amazonaws.com',
        'api_name': 'autoscaling_us_east',
        'country': 'USA'
    },
    'us-west-1': {
        'endpoint': 'autoscaling.us-west-1.amazonaws.com',
        'api_name': 'autoscaling_us_west',
        'country': 'USA'
    },
    'us-west-2': {
        'endpoint': 'autoscaling.us-west-2.amazonaws.com',
        'api_name': 'autoscaling_us_west_oregon',
        'country': 'USA'
    },
    'eu-west-1': {
        'endpoint': 'autoscaling.eu-west-1.amazonaws.com',
        'api_name': 'autoscaling_eu_west',
        'country': 'Ireland'
    },
    'eu-central-1': {
        'endpoint': 'autoscaling.eu-central-1.amazonaws.com',
        'api_name': 'autoscaling_eu_central',
        'country': 'Germany'
    },
    'ap-southeast-1': {
        'endpoint': 'autoscaling.ap-southeast-1.amazonaws.com',
        'api_name': 'autoscaling_ap_southeast',
        'country': 'Singapore'
    },
    'ap-southeast-2': {
        'endpoint': 'autoscaling.ap-southeast-2.amazonaws.com',
        'api_name': 'autoscaling_ap_southeast_2',
        'country': 'Australia'
    },
    'ap-northeast-1': {
        'endpoint': 'autoscaling.ap-northeast-1.amazonaws.com',
        'api_name': 'autoscaling_ap_northeast',
        'country': 'Japan'
    },
    'sa-east-1': {
        'endpoint': 'autoscaling.sa-east-1.amazonaws.com',
        'api_name': 'autoscaling_sa_east',
        'country': 'Brazil'
    }
}

# initialize test
def initialize_test():

    # authenticate
    aws = get_driver(Provider.EC2)

    # explorer
    # TODO: If the explorer is created in a isolated region, no firewall rules will be applied
    region = config.STORAGE["AWS_EXPLORER_REGION"]
    boot_node_region = config.STORAGE["AWS_BOOT_NODE_REGION"]
    test_prepper_region = config.STORAGE["AWS_TEST_PREPPER_REGION"]

    explorer_driver = login_aws(aws, region)[0]
    boot_node_driver = login_aws(aws, boot_node_region)
    test_prepper_driver = login_aws(aws, test_prepper_region)[0]

    boot_node = None
    explorer = None


    if '--destroy-test-prepper' in sys.argv:
        if get_node(test_prepper_driver, config.STORAGE["AWS_TEST_PREPPER_NAME"]):
            logging.info("Destroying test prepper node")
            destroy_node(test_prepper_driver, config.STORAGE["AWS_TEST_PREPPER_NAME"])
    else:
        test_prepper = get_node(test_prepper_driver, config.STORAGE["AWS_TEST_PREPPER_NAME"])
        if test_prepper is None:
            logging.info("Creating test prepper node")
            create_test_prepper(test_prepper_driver)

    if '--destroy-explorer' in sys.argv:
        if explorer_exists(explorer_driver):
            logging.info("Destroying explorer")
            destroy_node(explorer_driver, config.STORAGE["AWS_EXPLORER_NAME"])
        else:
            logging.info("No explorer is running.")
    else:
        
        if not explorer_exists(explorer_driver):
            logging.info("Creating explorer")

            # radix network authentication
            if not os.environ.get("RADIX_MTPS_NETWORK_PASSWORD"):
                os.environ["RADIX_MTPS_NETWORK_PASSWORD"] = config.generate_password()
                logging.info("NOTE: generated the admin/metrics password for you: %s",
                            os.environ["RADIX_MTPS_NETWORK_PASSWORD"])
            if not os.environ.get("RADIX_MTPS_NGINX_ACCESS"):
                os.environ["RADIX_MTPS_NGINX_ACCESS"] = "SUCCESS"
            
            explorer = create_explorer(explorer_driver)
            logging.info("Explorer https://%s", explorer.public_ips[0])
        else:
            explorer = get_node(explorer_driver, config.STORAGE['AWS_EXPLORER_NAME'])
            logging.info("An explorer is already running")

        os.environ["CORE_DOCKER_IMAGE"] = config.STORAGE["CORE_DOCKER_IMAGE"]
        os.environ["RADIX_MTPS_NETWORK_EXPLORER_IP"] = explorer.public_ips[0]
        os.environ["RADIX_MTPS_NETWORK_ATOMS_FILE"] = os.environ.get("RADIX_MTPS_NETWORK_ATOMS_FILE",
                                                                    config.STORAGE["DEFAULT_NETWORK_ATOMS_FILE"])
        os.environ["RADIX_MTPS_NETWORK_PASSWORD"] = ssh.get_admin_password(explorer.public_ips[0])
        if not os.environ.get("RADIX_MTPS_NETWORK_START_PUMP_URL"):
            os.environ["RADIX_MTPS_NETWORK_START_PUMP_URL"] = config.STORAGE["DEFAULT_NETWORK_START_URL"]
        if not os.environ.get("AWS_CORE_FETCH_UNIVERSE_URL"):
            os.environ["AWS_CORE_FETCH_UNIVERSE_URL"] = \
                config.STORAGE["AWS_CORE_FETCH_UNIVERSE_URL"]


    # boot node
    if '--destroy-cores' in sys.argv:
        logging.info("Destroying boot node in %s", region)

        destroy_core_template(boot_node_driver[0], name=config.STORAGE["AWS_BOOT_NODE_NAME"])
        destroy_core_groups(
            boot_node_driver[0],
            boot_node_driver[1],
            name=config.STORAGE["AWS_BOOT_NODE_NAME"],
            region=region)
    else:
        boot_node = get_node(boot_node_driver[0], config.STORAGE['AWS_BOOT_NODE_NAME'])
        if not boot_node:
            # reconfigure shard allocator
            shard_count = os.environ.get("RADIX_MTPS_SHARD_COUNT", config.STORAGE["DEFAULT_NETWORK_SHARD_COUNT"])
            ssh.update_shard_count(
                explorer.public_ips[0],
                os.environ.get("RADIX_MTPS_SHARD_COUNT", config.STORAGE["DEFAULT_NETWORK_SHARD_COUNT"]),
                os.environ.get("RADIX_MTPS_SHARD_OVERLAP", config.STORAGE["DEFAULT_NETWORK_SHARD_OVERLAP"]))

            # generate a new universe
            if "RADIX_MTPS_NETWORK_UNIVERSE" not in os.environ:
                logging.info("Generating new universe")
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

                logging.info("Universe generated")
                # remove the first _ for rendering cloud init files with the universe
                os.environ["_RADIX_MTPS_NETWORK_UNIVERSE"] = universe

            # boot node
            logging.info("Creating boot node in %s", boot_node_region)

            atoms_snapshot = config.STORAGE['AWS_CORE_REGIONS'][boot_node_region]['atoms_snapshot']
            ami = config.STORAGE['AWS_CORE_REGIONS'][boot_node_region]['ami']

            create_core_template(
                boot_node_driver[0],
                name=config.STORAGE["AWS_BOOT_NODE_NAME"],
                atoms_snapshot=atoms_snapshot,
                ami=ami)

            create_core_group(
                boot_node_driver[0],
                boot_node_driver[1],
                amount=1,
                region=boot_node_region,
                template=config.STORAGE["AWS_BOOT_NODE_NAME"])
            
            attempts = 0
            while attempts < 3:
                boot_node = get_node(boot_node_driver[0], config.STORAGE['AWS_BOOT_NODE_NAME'])
                if not boot_node:
                    attempts += 1
                    time.sleep(4)
                else:
                    attempts = 3 
            boot_node = get_node(boot_node_driver[0], config.STORAGE['AWS_BOOT_NODE_NAME'])
            boot_node_driver[0].wait_until_running(nodes=[boot_node], wait_period=1, timeout=30)

            logging.info("Updating node finder")
            ssh.update_node_finder(explorer.public_ips[0], boot_node.public_ips[0])
        else:
            logging.info("Boot node seems to be up and running")
            os.environ["_RADIX_MTPS_NETWORK_UNIVERSE"] = ssh.get_test_universe(boot_node.public_ips[0])

        os.environ["RADIX_MTPS_NETWORK_SEEDS"] = boot_node.public_ips[0]
        logging.info("Updating explorer's universe to be served")
        ssh.update_explorer_universe(
            explorer.public_ips[0],
            os.environ["_RADIX_MTPS_NETWORK_UNIVERSE"])

    # core nodes
    for region in config.STORAGE["AWS_CORE_REGIONS"]:
        # driver
        driver,as_driver = login_aws(aws, region)

        # firewall rules
        if '--destroy-firewall-rules' in sys.argv:
            logging.info("Destroying firewall rules in %s", region)
            destroy_security_groups(driver)
        else:
            logging.info("Checking firewall rules in %s", region)
            create_security_groups(driver)


        # core nodes
        if '--destroy-cores' in sys.argv:
            logging.info("Destroying core nodes")
            destroy_core_template(driver)

            destroy_core_groups(driver, as_driver, region)
        else:
            logging.info("Checking if core nodes in %s are up", region)

            core_nodes = config.STORAGE["AWS_CORE_REGIONS"][region]["amount"]
            atoms_snapshot = config.STORAGE['AWS_CORE_REGIONS'][region]['atoms_snapshot']
            ami = config.STORAGE['AWS_CORE_REGIONS'][region]['ami']
            
            # atoms snapshot
            create_core_template(
                driver,
                atoms_snapshot=atoms_snapshot,
                ami=ami)
            create_core_group(
                driver=driver,
                as_driver=as_driver,
                amount=core_nodes,
                region=region)


# get node from a driver
def get_node(driver, name=None):
    if name is None:
        raise Exception("Name cannot be None for node to be retrieved.")

    nodes = driver.list_nodes()
    for node in nodes:
        if node.name == name \
                and node.state != "terminated" and node.state != "shutting-down":
            return node
    return None


# explorer exists
def explorer_exists(driver):
    return False if get_node(driver, config.STORAGE['AWS_EXPLORER_NAME']) is None else True


# destroy explorer
def destroy_explorer(driver):
    if explorer_exists(driver):
        explorer = get_node(driver, config.STORAGE['AWS_EXPLORER_NAME'])
        driver.destroy_node(explorer)


# destroy explorer
def destroy_node(driver, name=None):
    node = get_node(driver, name)
    if node != None:
        driver.destroy_node(node)


# create explorer
def create_explorer(driver):

    try:
        # config
        name = config.STORAGE["AWS_EXPLORER_NAME"]
        ami = config.STORAGE["AWS_EXPLORER_IMAGE_ID"]
        instance_type = config.STORAGE["AWS_EXPLORER_INSTANCE_TYPE"]

        # cloud init
        rendered_file = config.render_template(config.STORAGE["EXPLORER_CLOUD_INIT"])
        explorer_content = ""
        with open(rendered_file, 'r') as content_file:
            explorer_content = content_file.read()
            
        # create
        node = create_node(driver=driver, name=name, image=ami, size=instance_type, user_data=explorer_content)
        explorer = driver.wait_until_running(nodes=[node], wait_period=1, timeout=30)
        return explorer[0][0]
    except BaseHTTPError as e:
        logging.info("- An error occured creating the explorer")


# create test prepper
def create_test_prepper(driver):
    try:
        # config
        name = config.STORAGE["AWS_TEST_PREPPER_NAME"]
        ami = config.STORAGE["AWS_TEST_PREPPER_IMAGE_ID"]
        instance_type = config.STORAGE["AWS_TEST_PREPPER_INSTANCE_TYPE"]
        disk_size = config.STORAGE["AWS_TEST_PREPPER_DISK_DIZE"]
        extra_disk_size = config.STORAGE["AWS_TEST_PREPPER_EXTRA_DISK_DIZE"]

        # cloud init
        cloud_init_file = os.path.join(
            config.STORAGE["BASE_CLOUD_INIT_PATH"],
            config.STORAGE["TEST_PREPPER_CLOUD_INIT"])
        rendered_file = config.render_template(cloud_init_file)
        user_data = ""
        with open(rendered_file, 'r') as content_file:
            user_data = content_file.read()
            
        kwargs = {}

        # add 2048 gb volume
        extra_disk_disks = [
            {
                "DeviceName": "/dev/sda1",
                "Ebs": {
                    "Encrypted": "false",
                    "DeleteOnTermination": "true",
                    "VolumeSize": disk_size,
                    "VolumeType": "gp2"
                }
            },
            {
                "DeviceName": "/dev/sdb",
                "Ebs": {
                    "Encrypted": "false",
                    "DeleteOnTermination": "false",
                    "VolumeSize": extra_disk_size,
                    "VolumeType": "gp2"
                },
            }
        ]

        # create
        node = create_node(
            driver=driver,
            name=name,
            image=ami,
            size=instance_type,
            user_data=user_data,
            ex_blockdevicemappings=extra_disk_disks)
        running_node = driver.wait_until_running(nodes=[node], wait_period=1, timeout=30)
        return running_node[0][0]

    except BaseHTTPError as e:
        logging.info("- An error occured creating the test prepper")


# create core auto scaling group
def create_core_group(driver, as_driver, amount, region, template=None):
    params = {}
    params["Action"] = "CreateAutoScalingGroup"

    if template is None:
        template = config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"]

    # add availability zones
    zones = driver.list_locations()
    index=1
    for zone in zones:
        availability_zone="AvailabilityZones.member.{0}".format(index)
        params[availability_zone] = zone.name
        index+=1

    params["AutoScalingGroupName"] = template # make it easy to identify in the console 
    params["LaunchTemplate.LaunchTemplateName"] = template
    params["MinSize"] = amount
    params["MaxSize"] = amount

    try:
        # use auto scaling driver
        response = as_driver.connection.request("/", params=params)

        logging.info("- Core group %s in %s has been created.", template, region)
    except BaseHTTPError as e:
        logging.info("- An error occured communicating with autoscaling service.")


# destroy core auto scaling group
def destroy_core_groups(driver, as_driver, region, name=None):
    params = {}
    params["Action"] = "DeleteAutoScalingGroup"

    if name is None:
        name = config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"]

    params["AutoScalingGroupName"] = name
    params["ForceDelete"] = "true"

    try:
        # use auto scaling driver
        response = as_driver.connection.request("/", params=params)
        logging.info("- Core group for %s has been destroyed.", region)
    except BaseHTTPError as e:
        logging.error("- An error occured communicating with autoscaling service.")


# create core template
def create_core_template(driver, name=None, atoms_snapshot=None, ami=None):
    params = config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"].copy()
    params["Action"] = "CreateLaunchTemplate"
    params["Action"] = "CreateLaunchTemplate"
    
    
    # override name
    if name != None:
        # make it easy to identify in the console 
        params["LaunchTemplateName"] = name
        params["LaunchTemplateData.TagSpecification.1.Tag.2.Value"] = name

    # override atoms snapshot_id
    if atoms_snapshot != None:
        params['LaunchTemplateData.BlockDeviceMapping.2.Ebs.SnapshotId'] = \
            atoms_snapshot
    else:
        logging.info("No snapshot provided for core template %s", params["LaunchTemplateName"])

    # override ami
    if ami != None:
        params['LaunchTemplateData.ImageId'] = ami
    else:
        logging.info("No image AMI provided for core template %s", params["LaunchTemplateName"])
        
    # cloud init
    cloud_init_file = os.path.join(
        config.STORAGE["BASE_CLOUD_INIT_PATH"],
        config.STORAGE["CORE_CLOUD_INIT"])
    rendered_file = config.render_template(cloud_init_file)
    with open(rendered_file, 'r') as content_file:
        content = content_file.read()
        params["LaunchTemplateData.UserData"] = base64.urlsafe_b64encode(content.encode()).decode('utf-8')

    try:
        response = driver.connection.request("/", params=params)
        logging.info("- Core template created.")
    except BaseHTTPError as e:
        logging.info(
            "- %s for %s.",
            e,
            params["LaunchTemplateName"])


# destroy core template
def destroy_core_template(driver, name=None):
    params = {
        'Action': 'DeleteLaunchTemplate',
        'LaunchTemplateName': config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"]
    }

    if name != None:
        params["LaunchTemplateName"] = name

    try:
        response = driver.connection.request("/", params=params)
        logging.info("- Core template %s destroyed.", params["LaunchTemplateName"])
    except BaseHTTPError: 
        logging.info(
            "- Seems like %s doesn't template exist.",
            params["LaunchTemplateName"])


# create security groups
def create_security_groups(driver): 
    security_groups = driver.ex_get_security_groups(group_names=["default"])

    if security_groups:
        name = security_groups[0].name

        try:
            # tcp
            driver.ex_authorize_security_group(name, "22", "22", "0.0.0.0/0", "tcp")
            driver.ex_authorize_security_group(name, "80", "80", "0.0.0.0/0", "tcp")
            driver.ex_authorize_security_group(name, "443", "443", "0.0.0.0/0", "tcp")
            driver.ex_authorize_security_group(name, "8080", "8080", "0.0.0.0/0", "tcp")
            driver.ex_authorize_security_group(name, "20000", "20000", "0.0.0.0/0", "tcp")

            # udp
            driver.ex_authorize_security_group(name, "20000", "20000", "0.0.0.0/0", "udp")
            
            logging.info("- Firewall rules authorized.")
        except BaseHTTPError:
            logging.info("- Error authorizing security group rules.")
    else:
        logging.info("- Unable to find 'default' security group.")

# destroy security groups
def destroy_security_groups(driver):
    security_groups = driver.ex_get_security_groups(group_names=["default"])

    if security_groups:
        id = security_groups[0].id

        try:
            driver.ex_revoke_security_group_ingress(id, "22", "22", cidr_ips=["0.0.0.0/0"], protocol="tcp")
            driver.ex_revoke_security_group_ingress(id, "80", "80", cidr_ips=["0.0.0.0/0"], protocol="tcp")
            driver.ex_revoke_security_group_ingress(id, "443", "443", cidr_ips=["0.0.0.0/0"], protocol="tcp")
            driver.ex_revoke_security_group_ingress(id, "8080", "8080", cidr_ips=["0.0.0.0/0"], protocol="tcp")
            driver.ex_revoke_security_group_ingress(id, "20000", "20000", cidr_ips=["0.0.0.0/0"], protocol="tcp")

            driver.ex_revoke_security_group_ingress(id, "20000", "20000", cidr_ips=["0.0.0.0/0"], protocol="udp")
            logging.info("- Firewall rules revoked")
        except BaseHTTPError as ex: 
            print(ex)
            logging.info("- Unable to revoke firewall rules")
    else:
        logging.info("- Unable to find 'default' security group.")


# create a simple node
def create_node(driver, name=None, image=None, size=None, disk=None, user_data=None, **kwargs):

    if image is None:
        raise Exception("Invalid image while creating a node.")

    if size is None:
        raise Exception("Invalid instance type while creating a node.")

    image = NodeImage(id=image, name="", driver="")
    size = NodeSize(id=size, name="", ram=None, disk=disk, bandwidth=None, price=None, driver="")

    node = driver.create_node(name=name, image=image, size=size, ex_userdata=user_data, **kwargs)
    return node


# login AWS
def login_aws(driver, region):
    with open(expanduser(config.STORAGE["AWS_CLOUD_CREDENTIALS"])) as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            config.STORAGE["AWS_KEY_ID"] = row['Access key ID']
        config.STORAGE["AWS_SECRET"] = row['Secret access key']

    auth_driver = driver(
        config.STORAGE["AWS_KEY_ID"],
        config.STORAGE["AWS_SECRET"],
        region=region)
    EC2NodeDriver
    
    auto_scaling_driver = AutoScalingDriver(
            key=config.STORAGE["AWS_KEY_ID"],
            secret=config.STORAGE["AWS_SECRET"],
            secure=True,
            region=region,
            ec2_driver=auth_driver)
    return auth_driver,auto_scaling_driver


class AutoScaleConnection(EC2Connection):
    """
    Connection class for Auto Scaling node driver
    """

    # this is needed, as the 'ec2' is a different version
    version = '2011-01-01'


class AutoScalingDriver(BaseDriver):

    connectionCls = AutoScaleConnection

    name = 'Auto Scaling Driver'
    path = '/'


    def __init__(self, key, secret=None, secure=True, host=None, port=None,
                 region='us-east-1', **kwargs):
        if hasattr(self, '_region'):
            region = self._region

        details = AUTOSCALE_REGION_DETAILS[region]
        self.region_name = region
        self.api_name = details['api_name']
        self.country = details['country']

        host = host or details['endpoint']

        self.signature_version = details.pop('signature_version', '2')

        if kwargs.get('ec2_driver'):
            self.ec2 = kwargs['ec2_driver']
        else:
            self.ec2 = EC2NodeDriver(key, secret=secret, region=region,
                                     **kwargs)

        super(AutoScalingDriver, self).__init__(key=key, secret=secret,
                                                 secure=secure, host=host,
                                                 port=port, **kwargs)
