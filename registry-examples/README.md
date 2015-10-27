# GBIF Registry Examples

This project contains examples demonstrating how to implement the registry web service client.

## Setup instructions

The integration tests (ITs) rely on the registry sandbox web services running at http://api.gbif-uat.org/.  They are not run by default when building the project.

To setup the registry sandbox for these ITs, simply run the script `/resources/registry-inserts.sql`.  This script will
add the test organization and the permissions so that "ws_client_demo" can modify the organization in the tests.

The GBIF account with username "ws_client_demo" and password "Demo123" is assumed to always exist, with role `REGISTRY_EDITOR`.
