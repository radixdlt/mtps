from contextlib import contextmanager

import config

from paramiko.client import SSHClient, WarningPolicy


@contextmanager
def ssh_client(host):
    client = SSHClient()
    # client.load_system_host_keys()
    client.set_missing_host_key_policy(WarningPolicy)
    try:
        client.connect(host, username="radix")
        yield client
    finally:
        client.close()


def ssh_exec(host, command):
    """
    Executes command on the ssh server

    :param host: host to connect to
    :param command: command to be executed on that host
    :return: list of std_output lines

    :raise: Exception when executed command return_status in non zero
    """
    with ssh_client(host) as client:
        stdin, stdout, stderr = client.exec_command(command)
        exit_status = stdout.channel.recv_exit_status()
        if exit_status != 0:
            raise Exception('exec_command exception: {0}'.format(stdout.readlines))
        lines = stdout.readlines()
        return lines


def generate_universe(host):
    entrypoint = "/opt/radixdlt/bin/generate_universes"
    command = "docker pull {0} && docker run --rm --entrypoint {1} {0} --dev.planck 3600 --universe.timestamp 1231006505".format(
        config.CORE_DOCKER_IMAGE, entrypoint)
    magic = "UNIVERSE - TEST: "

    output_lines = ssh_exec(host, command)

    for line in output_lines:
        if line.startswith(magic):
            return line[len(magic):].strip()
    raise RuntimeError("Could not generate universe on " + host)


def read_env_from_docker_compose(host, key):
    command = "grep '{0}' /etc/radixdlt/docker-compose.yml".format(key)

    output_lines = ssh_exec(host, command)
    quoted = output_lines[0].split("#")[0].split(":", 1)[1].strip()
    if not quoted:
        raise RuntimeError("Could not read {0} from {1}".format(host, key))
    return quoted.strip('"')


def get_test_time(host):
    return read_env_from_docker_compose(host, 'TIMETORUNTEST')


def get_test_universe(host):
    return read_env_from_docker_compose(host, 'CORE_UNIVERSE')


def get_admin_password(host):
    return read_env_from_docker_compose(host, 'ADMIN_PASSWORD')


def update_node_finder(host, boot_node_ip):
    command = "sudo sed -i -r 's/BOOT_NODES: \".*\"/BOOT_NODES: \"{0}\"/g' /etc/radixdlt/docker-compose.yml".format(
        boot_node_ip)
    ssh_exec(host, command)

    command = '''docker exec radixdlt_shard_allocator_1 rm -f seeds.db && 
                     docker-compose -f /etc/radixdlt/docker-compose.yml up -d'''
    ssh_exec(host, command)
    return True


def update_shard_count(host, shard_count, shard_overlap):
    command = '''
            sudo sed -i -r 's/SHARD_COUNT:\ \"*.*\"*/SHARD_COUNT:\ {0}/g; s/SHARD_OVERLAP:\ \"*.*\"*/SHARD_OVERLAP:\ {1}/g' /etc/radixdlt/docker-compose.yml &&
            docker exec radixdlt_shard_allocator_1 rm -f seeds.db && 
            docker-compose -f /etc/radixdlt/docker-compose.yml up -d
            '''.format(
                shard_count,
                shard_overlap
            )
    ssh_exec(host, command)
    return True
