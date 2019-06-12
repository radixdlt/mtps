# Radix Explorer Service
This is the Radix Explorer middle ware project. This project exposes some metrics and simple crawling features of a targeted Radix network through a REST API.

## Executing the tests
When in the `service` project root folder:

```bash
./gradlew clean check
```

You can see the `SUCCESS` or `FAILURE` state directly in the terminal, but can also find a more detailed test report in the `{EXPLORER_ROOT_PROJ}/service/build/reports/tests/test` directory.

For further computer processing purposes you can find even more test results in the `./service/build/test-results/test` directory.

## Running the server
From the project root folder:

```bash
./gradlew clean run
```

You can then point your favourite HTTP client (Postman, Curl, etc) to `http://localhost:5050/api/metrics` or `http://localhost:5050/api/transactions/1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i`. The server will currently serve mocked data only.

## Configuration

Unless you provide your own `{EXPLORER_ROOT_PROJ}/config.properties` file, the service will copy a complete default configuration there (it won't overwrite an existing file). You can find complete details on supported properties in the `{EXPLORER_ROOT_PROJ}/service/src/main/resources/config.properties` file.

## User Interface

Similarly to the default configuration, the service will serve it's own default UI unless you provide your own in the `{EXPLORER_ROOT_PROJ}/www` directory. Once the service is successfully started the default UI will be accessible by browsing to `http://localhost:5050`.

## REST API

The service exposes it's feature set through a REST API. Currently the below end points are available:

**Metrics**

_<sub>Request:</sub>_

```
GET /api/metrics
```

_<sub>Request header</sub>_

```
Authorization: Basic abcdef0123456789 	# The node credentials in the target network
```

_<sub>Pseudo response body:</sub>_

```
{
  "type": "metrics",				# Type of data being returned
  "data": {
  	"tps": 123, 						# The calculated number of transactions per second
  	"age": 10321,						# The number of milliseconds since the previous calculation
  	"progress": 154,				# The number of transactions processed so far
  	"genesis": 1234567890		# The timestamp when the first atom was processed
  }
}
```

**Transaction** (WIP)

_<sub>Request:</sub>_

```
GET /api/transaction/:bitcoin_address?page=2
```

_<sub>Request header</sub>_

```
Authorization: Basic abcdef0123456789 		# The node credentials in the target network
```

_<sub>Pseudo response body:</sub>_

```
{
	"type": "transactions",													# The type of data being returned
	"meta": {
		"count": 20,																	# The page size
		"self": "/api/transactions/abc123?page=2",		# The link to this page
		"next": "/api/transactions/abc123?page=3",		# The link to the next page
		"prev": "/api/transactions/abc123?page=0"			# The link to the previous page
	},
	"data": [
		{
			"amount": "12",
			"bitcoin_transaction_hash": "SOME_HASH",		# The Bitcoin transaction hash id
			"bitcoin_block_timestamp": "9876543210",		# The Bitcoin block timestamp
			"radix_transaction_timestamp": "0123456789"	# The Radix transaction time stamp
		},
		...
	]
}
```

