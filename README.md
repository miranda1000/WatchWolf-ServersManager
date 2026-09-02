# WatchWolf - ServersManager [![CodeFactor](https://www.codefactor.io/repository/github/miranda1000/watchwolf-serversmanager/badge/dev)](https://www.codefactor.io/repository/github/miranda1000/watchwolf-serversmanager/overview/dev)

Provides Minecraft servers on demand for the [WatchWolf](https://watchwolf.dev/) framework. It
listens on TCP **8000**, and for every *start server* petition it assembles a server folder (jar,
plugins, world, config files), launches it inside its own Docker container, streams the console
back to the requester, and frees everything once the server stops.

`dev.watchwolf:watchwolf-servers-manager` · **Java 17** · Docker

## How it works

```
Tester ──"start Spigot 1.19 with these plugins"──▶ ServersManager :8000
                                                          │
                                     ServerRequirements    │  builds tmp/<id>/ :
                                                          │    server.jar, eula.txt,
                                                          │    server.properties, bukkit.yml,
                                                          │    plugins/, world/
                                                          ▼
                                     DockerizedServerInstantiator
                                                          │  docker run eclipse-temurin:<jdk>
                                                          ▼
                            ┌──────────── Minecraft server container ────────────┐
                            │  :25565 → host :N      (players connect here)      │
                            │  :25566 → host :N+1    (WatchWolf-Server socket)   │
                            └────────────────────────────────────────────────────┘
                                                          │
Tester ◀───"started, at <ip>:N"───── console scraped for "Done (…)! For help, type help"
```

Servers take a **consecutive pair** of ports starting at **8001**. The JDK image is chosen from
the Minecraft version (Java 8 below 1.17, 16 for 1.17, 17 up to 1.20.4, 21 from 1.20.5).

## Dependencies

- [Docker](https://www.docker.com/get-started/)
- The JDK images the servers run on:
  ```bash
  docker pull eclipse-temurin:8-jdk
  docker pull eclipse-temurin:16-jdk
  docker pull eclipse-temurin:17-jdk
  docker pull eclipse-temurin:21-jdk
  ```
- A [WatchWolf-Core release](https://github.com/watch-wolf/WatchWolf-Core/releases) matching the
  version in `pom.xml`; place the `.jar` inside `lib/`

## Compile

```bash
./ci/debug/build.sh --preclean
```

`--preclean` matters: the WatchWolf-Core jar in `lib/` is installed into your local Maven
repository during the **clean** phase, so skipping it will keep using whatever was there before.

Add `--test` to also assemble `ServersManager.jar` and build the Docker image; that mode expects a
`watchwolf-server-<version>.jar` in `ci/debug/`.

For a build against the published releases instead of local jars, use `./ci/release/build.sh` —
it downloads the latest ServersManager release and builds the image.

## Run

```bash
./ci/release/run.sh     # or ./ci/debug/run.sh
```

This is what the [WatchWolf setup script](https://github.com/watch-wolf/WatchWolf) invokes. It
exports `MACHINE_IP`, `PUBLIC_IP`, `PARENT_PWD` and `SERVER_PATH_SHIFT` and runs
`docker compose up --detach`.

The container bind-mounts `/var/run/docker.sock`, so the servers it starts are **siblings** on the
host rather than nested containers — that is why the paths it passes around have to be host paths.

## Data folders

All of these live next to the compose file, in `ci/release/` (or `ci/debug/`), and are gitignored:

| Folder | Contents |
| --- | --- |
| `server-types/<Type>/<version>.jar` | The available server softwares, e.g. `Spigot/1.16.5.jar`, `Paper/1.20.jar`. Any folder name is a valid server type — `CustomSpigot/1.20.4.jar` works too. See [`server-types/README.md`](ci/release/server-types/README.md). |
| `usual-plugins/<Name>-<pluginVer>-<minMc>-<maxMc>.jar` | Plugins kept on the machine, e.g. `WorldGuard-7.0.8-1.19-LATEST.jar`. Spaces become `_`. **At least one WatchWolf-Server jar must be here.** See [`usual-plugins/README.md`](ci/release/usual-plugins/README.md). |
| `tmp/<id>/` | One scratch folder per running server; removed when it stops |
| `logs/<id>/` | `info.txt` (type, version, IP, timestamp) and `latest.log`, kept after the server dies |

The setup script populates `server-types/` and `usual-plugins/` for you; `src/tools/` holds the
legacy Spigot/Paper build scripts it uses.

## Test

```bash
./ci/debug/tests.sh --unit
./ci/debug/tests.sh --integration
./ci/debug/tests.sh --unit --tests 'ServerRequirementsShould'
./ci/debug/validator.sh          # checks the test naming conventions
```

Reports land in `target/site` (HTML summary), `target/surefire-reports` (unit) and
`target/failsafe-reports` (integration). Unit tests must be named `*Should`; integration tests
must be named `IT*` and declare `@Timeout`. Integration tests genuinely start containers, so they
need Docker and the data folders above.

## Related

- [WatchWolf](https://github.com/watch-wolf/WatchWolf) — the protocol specification and setup script
- [WatchWolf-Core](https://github.com/watch-wolf/WatchWolf-Core) — shared entities and the RPC runtime
- [WatchWolf-Server](https://github.com/miranda1000/WatchWolf-Server) — the plugin injected into every server it starts
- [WatchWolf-Tester](https://github.com/miranda1000/WatchWolf-Tester) — its client
