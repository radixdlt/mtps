REGISTRY_PREFIX ?= radixdlt/
TAG ?= latest

.PHONY: radixdlt-bitcoind
radixdlt-bitcoind:
	docker build -t $(REGISTRY_PREFIX)radixdlt-bitcoind:$(TAG) -f radixdlt-bitcoind/Dockerfile ./radixdlt-bitcoind

.PHONY: radixdlt-bitcoind-push
radixdlt-bitcoind-push:
	docker push $(REGISTRY_PREFIX)radixdlt-bitcoind:$(TAG)

.PHONY: millionaire-dataset-preparator
millionaire-dataset-preparator:
	cd millionaire-dataset-preparator/ && \
	./gradlew clean distTar --refresh-dependencies && \
	docker build -t $(REGISTRY_PREFIX)millionaire-dataset-preparator:$(TAG) -f ./Dockerfile.alpine .

.PHONY: millionaire-dataset-preparator-push
millionaire-dataset-preparator-push:
	docker push $(REGISTRY_PREFIX)millionaire-dataset-preparator:$(TAG)


.PHONY: explorer
explorer:
	cd explorer/ && \
	./gradlew clean distTar --refresh-dependencies && \
	docker build -t $(REGISTRY_PREFIX)explorer:mtps-$(TAG) -f ./Dockerfile.alpine .

.PHONY: explorer-push
explorer-push:
	docker push $(REGISTRY_PREFIX)explorer:mtps-$(TAG)

.PHONY: explorer-nginx
explorer-nginx:
	cd explorer/nginx && \
	docker build -t $(REGISTRY_PREFIX)explorer-nginx:mtps-$(TAG) -f ./Dockerfile .

.PHONY: explorer-nginx-push
explorer-nginx-push:
	docker push $(REGISTRY_PREFIX)explorer-nginx:mtps-$(TAG)

.PHONY: radixdlt-shard-allocator
radixdlt-shard-allocator:
	cd radixdlt-shard-allocator && \
	docker build -t $(REGISTRY_PREFIX)radixdlt-shard-allocator:$(TAG) -f ./Dockerfile .

.PHONY: radixdlt-shard-allocator-push
radixdlt-shard-allocator-push:
	docker push $(REGISTRY_PREFIX)radixdlt-shard-allocator:$(TAG)


