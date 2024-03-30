# WatchWolf - ServersManager
## Debug integration

Here you'll find all the required files to build&run WW-ServersManager providing locally-compiled dependencies (WW-ServersManager and WW-Server's jar files).

#### Build

To build the docker, you can run the `./build.sh --preclean` command.

#### Run

Run the built docker using the `./run.sh` script.

#### Run system tests

Use `./tests.sh --unit` to run the unit tests, or `./tests.sh --integration` for the integration tests.

You'll find the reports summary on `target/site`, or you can check `target/surefire-reports` (unit tests) and `target/failsafe-reports` (integration tests) for the output logs.