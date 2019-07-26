## Commands
This section is where you can find the commands used for running multiple scenarios of the test.

### Run a small atoms file test
```
python3 mtps.py -c gcp_project_test2.json,gcp_test_small_atoms.json
```

### Run a full atoms file test
```
python3 mtps.py -c gcp_project_fastgateway.json,gcp_test_1000.json
```

### Run test on AWS
```
python3 mtps.py -c aws_test_100.json
```