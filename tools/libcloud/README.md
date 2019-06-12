# Installing Dependencies

This project needs `python3` in order to work. Please start by installing the dependencies:

```shell
pip3 install --user -r requirements.txt
```

# Pre-requisites

## Building Custom VM Image

You need to build your own custom image with [packer](../packer/README.md).

# Building Custom Data

TODO: we need to add tooling to create a node for with `bitcoind` and the
 `millionare-dataset-preparator`.

# Creating Test Network

```
python3 libcloud/mtps.py
```

# Destroying Test Network

```
python3 mtps.py --destroy-cores --destroy-explorer --destroying-firewall-rules
```

If you just want to re-run the tests its enough to destroy the Core Nodes:

```
python3 mtps.py --destroy-cores
```
