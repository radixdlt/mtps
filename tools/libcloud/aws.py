import logging
import os
import sys

import config
import csv
import base64

from os.path import expanduser
from libcloud.compute.drivers.ec2 import BaseEC2NodeDriver
from libcloud.compute.drivers.ec2 import EC2Connection
from libcloud.compute.drivers.ec2 import EC2NodeDriver
from libcloud.compute.drivers.ec2 import EC2Response
from libcloud.compute.providers import get_driver
from libcloud.compute.types import Provider
from libcloud.common.base import BaseDriver
from libcloud.common.base import ConnectionKey
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

            destroy_core_groups(driver, as_driver, region)
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
        response = as_driver.connection.request("/", params=params)

        logging.info("- Core group for %s has been created.", region)
    except BaseHTTPError as e:
        logging.info("An error occured communicating with autoscaling service.")


# destroy core auto scaling group
def destroy_core_groups(driver, as_driver, region):
    params = {}
    params["Action"] = "DeleteAutoScalingGroup"

    params["AutoScalingGroupName"] = "mtps-cores-{0}".format(driver.region_name)
    params["ForceDelete"] = "true"

    try:
        # use auto scaling driver
        response = as_driver.connection.request("/", params=params)
        logging.info("- Core group for %s has been destroyed.", region)
    except BaseHTTPError as e:
        logging.error("An error occured communicating with autoscaling service.")


# create core template
def create_core_template(driver):
    params = config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]
    params["Action"] = "CreateLaunchTemplate"

    # cloud init
    config.STORAGE["CORE_CLOUD_INIT"] = os.path.join(
        config.STORAGE["BASE_CLOUD_INIT_PATH"],
        config.STORAGE["CORE_CLOUD_INIT"])
    rendered_file = config.render_template(config.STORAGE["CORE_CLOUD_INIT"])
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
            config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"])


# destroy core template
def destroy_core_template(driver):
    params = {
        'Action': 'DeleteLaunchTemplate',
        'LaunchTemplateName': config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"]
    }
    try:
        response = driver.connection.request("/", params=params)
        logging.info("- Core template destroyed.")
    except BaseHTTPError: 
        logging.info(
            "- Seems like %s doesn't exist.",
            config.STORAGE["AWS_CORE_TEMPLATE_CONFIG"]["LaunchTemplateName"])


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
            
            logging.info("- Firewall rules created.")
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
