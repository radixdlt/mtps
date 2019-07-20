import logging

import config

from libcloud.compute.providers import get_driver
from libcloud.compute.types import Provider


# login AWS
def login_gcp(ec2_driver, region):

    # read aws credentials csv
    with open(expanduser(config.STORAGE["AWS_CLOUD_CREDENTIALS"])) as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            STORAGE["AWS_KEY_ID"] = row['Access key ID']
            STORAGE["AWS_SECRET"] = row['Secret access key']
            logging.info("AWS_KEY_ID {0}", STORAGE["AWS_KEY_ID"])
            logging.info("AWS_SECRET {0}", STORAGE["AWS_SECRET"])
            exit(0)

    aws_e = ec2_driver(
        config.get("AWS_KEY_ID"),
        config.get("AWS_SECRET"),
        region=region
    )
    return aws_e


def getEngines():
    #  aws regions: 'ap-northeast-1','ap-northeast-2','ap-northeast-3','ap-south-1', 'ap-southeast-1', 'ap-southeast-2',
    #  'ca-central-1', 'cn-north-1', 'cn-northwest-1', 'eu-central-1', 'eu-west-1', 'eu-west-2', 'eu-west-3'
    #  'sa-east-1', 'us-east-1', 'us-east-2'.  'us-gov-west-1', 'us-west-1', 'us-west-2'
    aws_engines = []
    ec2_driver = get_driver(Provider.EC2)
    for region in config.STORAGE['AWS_REGIONS']:
        aws_engines.append(login_gcp(ec2_driver, region))
    return aws_engines


def create_ingress_rules(ec2e):
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
        ec2e[0].ex_create_security_group(
            name='explorer-ingress-rules',
            description='explorer-ingress-rules')
    except Exception as e:
        logging.debug("rule may already exist: " + str(e))

    try:
        # cores
        for regional_driver in ec2e:
            regional_driver.ex_create_security_group(
                name='core-ingress-rules',
                description='core-ingress-rules')
    except Exception as e:
        logging.debug("- Core rules may already exists." + str(e))
