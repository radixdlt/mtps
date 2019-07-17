import logging

import config

from libcloud.common.google import ResourceExistsError


# login AWS
def login_gcp(ComputeEngine):
    aws_e = ComputeEngine(
        config.get("AWS_KEY_ID"),
        config.get("AWS_SECRET")
    )
    return aws_e

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
        ec2e.ex_create_security_group(
            name='explorer-ingress-rules',
            description='explorer-ingress-rules')
    except ResourceExistsError:
        logging.debug("- Explorer rules already exists.")

    try:
        # cores
        gce.ex_create_security_group(
            name='core-ingress-rules',
            description='core-ingress-rules')
    except ResourceExistsError:
        logging.debug("- Core rules already exists.")
