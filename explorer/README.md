# Radix Explorer
This is the Radix Explorer project. The Explorer offers means to see basic network metrics and to search for stored data in the ledger.

The project is divided into two parts; in the `app` project you'll find the default user interface for the Explorer as VueJS based web app and in the `service` project you'll find the middle ware that's "talking" to the Radix network in one and and exposes a REST API (consumed by said web app) in the other end.