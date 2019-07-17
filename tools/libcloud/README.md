# Installing Dependencies

This project needs `python3` in order to work. Please start by installing the dependencies:

```shell
pip3 install --user -r requirements.txt
```

# Pre-requisites

## Building Custom VM Image

You need to build your own custom image with [packer](../packer/README.md).

# Building Custom Data

The host doing the conversion needs a lot of disk (1.5+ TB), which is why
we recommend that a dedicated VM is used for this. You can instantiate 
such a VM in GCE, with:

```
python3 mtps.py --create-test-prepper
```

## Syncing up with the Bitcoin Network

```shell
$ ssh radix@<host>
> cd /etc/radixdlt
> docker-compose up -d radixdlt-bitcoind
```

## Converting the Bitcoin Blockchain to BTC Atoms

**NOTE**: Be prepared - this takes a week or so. Fortunately, the tool
supports resuming the conversion process.

```shell
$ ssh radix@<host>
> cd /etc/radixdlt
> docker-compose up -d millionaire-dataset-preparator
```

## Safely stopping the millionaire-dataset-preparator tool

You can create a `STOP` file in `/radix_data` in order to abort the `millionaire-dataset-preparator` without risking data corruption:

```shell
$ ssh radix@<host>
> cd /radix_data
> touch STOP
```

## Compressing the atoms file

**NOTE**: Please stop the `millionaire-dataset-preparator` as described above

The compressed atoms file will be persisted on a dedicated disk, from which we create a disk image. This disk image can then later be mounted as extra (data) disk to each Core Node in the test network:

1. Connect a and separate 200GB disk to your VM (cheap HDD will do)
1. Format the disk with `mkfs.ext4`
1. Mount the disk to `/mnt`.

We use the `zstandard` compressor to create the `atoms_v4_full.zst` data file:

```shell
$ ssh radix@<host>
> sudo zstd -o /mnt/atoms_v4_full.zst /radix_data/atoms
```

## Creating the data disk image

**NOTE**: It is important that you unmount the data disk before creating the image so that you do not loose any data.

It is possible to create an image from a disk in GCE Console.

# Configuring Test Network size and geo-location.

Not the most user friendly solution but everything is configured in the [./config.py](config.py) file.

The number of Core Nodes and the geographical location is changed the `CORE_REGIONS` dictionary.
Each key is a valid Region and value is the number of Core Node instances. 

For example if you try to spin up 100 nodes/region like we do you will most likely hit quota limits. You need to make sure that you have enough CPU, SSD Disk and Public IP quotas.

It is recomended to start by spinning up 1-2 nodes in each regions, 
which should give to a network size of 15-30 nodes. This should be possible without hitting any quota issues.

With a network size this small it will not be possible to cover the full 1181 node shard space, which can be decreased through the `DEFAULT_NETWORK_SHARD_OVERLAP` config. However, decreasing this config  effectively means that more BTC Atoms will be loaded in memory of each 
Core Node. This you will most likely crash the nodes with an `OutOfMemoryException.

To solve the `OutOfMemoryException` you can switch to a smaller dataset through the `DEFAULT_NETWORK_ATOMS_FILE` config:

* `atoms_v4_6m.zst`: Contains only 6M BTC Atoms (shard overlap: 10-50)
* `atoms_v4_51m.zst`: Contains only 51M BTC Atoms (shard overlap: 51-500)
* `atoms_v4_full.zst`: Contains 410M+ BTC Atoms (shard overlap: 501-1000+)

# Creating Test Network

```
python3 mtps.py
```

# Destroying Test Network

```
python3 mtps.py --destroy-cores --destroy-explorer --destroying-firewall-rules
```

If you just want to re-run the tests its enough to destroy the Core Nodes:

```
python3 mtps.py --destroy-cores
```
