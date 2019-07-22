import logging
import sys

import config
import csv

from os.path import expanduser
from libcloud.compute.drivers.ec2 import BaseEC2NodeDriver
from libcloud.compute.drivers.ec2 import EC2Connection
from libcloud.compute.drivers.ec2 import EC2NodeDriver
from libcloud.compute.drivers.ec2 import EC2Response
from libcloud.compute.providers import get_driver
from libcloud.compute.types import Provider
from libcloud.common.exceptions import BaseHTTPError
from libcloud.utils.xml import fixxpath, findtext, findattr, findall

# initialize test
def initialize_test():

    # authenticate
    aws = get_driver(Provider.EC2)

    # regions
    for region in config.STORAGE["AWS_CORE_REGIONS"]:

        # driver
        driver,as_driver = login_aws(aws, region)

        # firewall rules
        if '--destroy-firewall-rules' in sys.argv:
            logging.info("Destroying firewall rules...")
            destroy_security_groups(driver)
        else:
            logging.info("Checking if firewall rules are created...")
            create_security_groups(driver)
        
        # core nodes
        if '--destroy-cores' in sys.argv:
            logging.info("Destroying core nodes...")
            destroy_core_template(driver)
        else:
            logging.info("Checking if core nodes in %s are up...", region)
            create_core_template(driver)

            core_nodes = config.STORAGE["AWS_CORE_REGIONS"][region]
            create_core_groups(driver, as_driver, core_nodes, region)


# create core auto scaling group
def create_core_groups(driver, as_driver, amount, region):
    params = {}
    params["Action"] = "CreateAutoScalingGroup"

    # add availability zones
    zones = driver.list_locations()
    index=1
    for zone in zones:
        availability_zone="AvailabilityZones.member.{0}".format(index)
        params[availability_zone] = zone.name
        index+=1

    params["AutoScalingGroupName"] = "mtps-cores-{0}".format(driver.region_name)
    params["LaunchTemplate.LaunchTemplateName"] = config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"]
    params["MinSize"] = amount
    params["MaxSize"] = amount
    try:
        # use auto scaling driver
        print(as_driver)
        response = as_driver.connection.request("/", params=params)

    except BaseHTTPError as e:
        logging.error("An error occured communicating with autoscaling.amazonaws.com:443")



# create core template
def create_core_template(driver):
    params = config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]
    params["Action"] = "CreateLaunchTemplate"

    # cloud init
    rendered_file = config.render_template(config.STORAGE["CORE_CLOUD_INIT"])
    try:
        response = driver.connection.request("/", params=params)
        
    except BaseHTTPError as e:
        logging.debug(
            "- Seems like %s already exists.",
            config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"])


# destroy core template
def destroy_core_template(driver):
    params = {
        'Action': 'DeleteLaunchTemplate',
        'LaunchTemplateName': config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"]
    }
    try:
        response = driver.connection.request("/", params=params)
    except BaseHTTPError: 
        logging.debug(
            "- Seems like %s doesn't exist.",
            config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"])


# create security groups
def create_security_groups(driver): 
    name = config.STORAGE["AWS_SECURITY_GROUP_NAME"]

    try:
        # create         
        driver.ex_create_security_group(
            name,
            "mtps ingress/egress rules")
            
        # tcp
        driver.ex_authorize_security_group(name, "22", "22", "0.0.0.0/0", "tcp")
        driver.ex_authorize_security_group(name, "80", "80", "0.0.0.0/0", "tcp")
        driver.ex_authorize_security_group(name, "443", "443", "0.0.0.0/0", "tcp")
        driver.ex_authorize_security_group(name, "8080", "8080", "0.0.0.0/0", "tcp")
        driver.ex_authorize_security_group(name, "20000", "20000", "0.0.0.0/0", "tcp")

        # udp
        driver.ex_authorize_security_group(name, "20000", "20000", "0.0.0.0/0", "udp")
        
    except BaseHTTPError:
        logging.debug("- Seems like %s already exists.", name)


# destroy security groups
def destroy_security_groups(driver):
    name = config.STORAGE["AWS_SECURITY_GROUP_NAME"]

    try:
        driver.ex_delete_security_group(name)
    except BaseHTTPError as ex: 
        logging.debug("- Seems like %s doesn't exist.", name)


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
    
    auto_scaling_driver = AutoScalingNodeDriver(
            key=config.STORAGE["AWS_KEY_ID"],
            secret=config.STORAGE["AWS_SECRET"],
            secure=True,
            host="autoscaling.amazonaws.com",
            port=443)
    return auth_driver,auto_scaling_driver


class AutoScaleConnection(EC2Connection):
    """
    Connection class for Auto Scaling node driver
    """

    # this is needed, as the 'ec2' is a different version
    version = '2011-01-01'


class AutoScalingNodeDriver(EC2NodeDriver):
    """
    Driver class for Auto Scaling
    """

    name = 'AutoScaling'
    connectionCls = AutoScaleConnection
    signature_version = '2'


    def __init__(self, key, secret=None, secure=True, host=None,
                 path=None, port=None, region="us-west-1", **kwargs):
        self.region_name = region
        super(BaseEC2NodeDriver, self).__init__(key=key, secret=secret,
                                                secure=secure, host=host,
                                                port=port, **kwargs)
