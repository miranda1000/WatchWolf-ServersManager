# WatchWolf - ServersManager
## Debug integration

Here you'll find all the required files to build&run WW-ServersManager providing locally-compiled dependencies (WW-ServersManager and WW-Server's jar files).

#### Build

To build the docker, you can run the `./build.sh --preclean` command.

#### Run

Run the built docker using `./run.sh`, or `./ci/debug/run.sh` from the repository
root. `--force-recreate` rebuilds from this directory's Dockerfile.

Only Docker and standard POSIX shell utilities are needed on the host. The launcher
uses a disposable `ubuntu:24.04` helper with host networking to install and run
`hostname`, `awk` and `curl` for address discovery. The helper is removed afterward;
the detached manager keeps running with the existing host mounts and Docker socket.
Download/tool failures stop startup instead of launching with empty addresses.

For offline or explicitly configured addresses, provide both variables:

```sh
MACHINE_IP=192.168.1.10 PUBLIC_IP=203.0.113.10 ./ci/debug/run.sh
```

With both values supplied, the helper skips package installation and IP lookup.
An uncached helper image still requires an initial Docker pull.

#### Run system tests

Use `./tests.sh --unit` to run the unit tests, or `./tests.sh --integration` for the integration tests.

You'll find the reports summary on `target/site`, or you can check `target/surefire-reports` (unit tests) and `target/failsafe-reports` (integration tests) for the output logs.