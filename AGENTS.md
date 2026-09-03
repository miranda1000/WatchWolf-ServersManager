# AGENTS.md — WatchWolf-ServersManager

Provides Minecraft servers on demand. It listens on TCP **8000**, and for every `startServer`
petition it materialises a server folder (jar + plugins + world + config), launches it in its own
Docker container, streams the console back, and cleans up when the container dies.

`dev.watchwolf:watchwolf-servers-manager` · **Java 17** · Maven · DST `0b000` in the
[WatchWolf protocol](https://github.com/watch-wolf/WatchWolf).

## Layout

```
src/main/java/dev/watchwolf/serversmanager/
├── ServersManager.java                 main(); accept loop, one RPC per client
├── rpc/
│   ├── ServersManagerLocalImplementation   implements ServersManagerPetitions (from WW-Core)
│   ├── ServersManagerLocalFactory          RPCImplementerFactory wiring
│   └── RequesteeIpGetter*                  who asked, so we can hand back a reachable IP
└── server/
    ├── ServersManager.java             the service: setup folder -> start -> log -> cleanup
    ├── ServerRequirements.java          builds the server folder (eula, server.properties,
    │                                    bukkit.yml, plugins, maps, config files)
    ├── ServerJarUnavailableException
    ├── instantiator/                   ServerInstantiator + DockerizedServerInstantiator,
    │                                   Server / ThrowableServer (console -> events)
    ├── ip/                             IpManager, ExternalizeIpManager (LAN vs public IP)
    └── plugins/                        PluginDeserializer: Plugin -> a jar on disk

src/tools/                              legacy Bash Spigot/Paper builders used by WatchWolfSetup.sh
ci/debug/                               build & run against a locally compiled WW-Core / WW-Server
ci/release/                             build & run against the published release jars
```

## Build, run, test

```bash
./ci/debug/build.sh --preclean          # compile (dockerized maven:3.8.3-openjdk-17)
./ci/debug/build.sh --preclean --test   # + assemble ServersManager.jar and build the container
./ci/debug/run.sh                       # docker compose up
./ci/debug/tests.sh --unit
./ci/debug/tests.sh --integration
./ci/debug/tests.sh --unit --tests 'ServerRequirementsShould'
./ci/debug/validator.sh                 # test-naming lint

./ci/release/build.sh                   # downloads the latest GitHub release jar, builds the image
./ci/release/run.sh                     # docker compose up (this is what WatchWolfSetup.sh runs)
```

**`--preclean` is not optional when the WW-Core jar changes.** The `local-ww-core-profile`
(active by default) installs `lib/watchwolf-core-<version>.jar` into `~/.m2` during the **`clean`**
phase. Without a clean, Maven resolves whatever is already in the local repo.

### Dependencies you must provide

- `lib/watchwolf-core-0.3.1.jar` — build it with WatchWolf-Core's `./ci/build.sh` and copy it in.
  `lib/` is gitignored, and the version must match `<dependency>` in `pom.xml`.
- Docker, plus the JDK images the servers run on:
  `eclipse-temurin:{8,16,17,21}-jdk` (chosen per Minecraft version by
  `dev.watchwolf.core.utils.DockerUtilities.getJavaVersion`).
- For `ci/debug` with `--test`: a `watchwolf-server-<version>.jar` in `ci/debug/`.

## Runtime contract

The container bind-mounts `/var/run/docker.sock`, so it starts **sibling** containers on the
host. Paths inside the config therefore have to be host paths — that is what `PARENT_PWD` and
`SERVER_PATH_SHIFT` are for.

| Env var | Meaning |
| --- | --- |
| `MACHINE_IP` | LAN IP handed to testers on the same network (**fallback**, see below) |
| `PUBLIC_IP` | IP handed to testers coming from outside (**fallback**, see below) |
| `PARENT_PWD` | Host path of `ci/<flavour>/`, so bind mounts resolve |
| `SERVER_PATH_SHIFT` | Prefix for `server-types/`, `tmp/`, `logs/` (`.` in the container) |

Directory contract (all under `ci/<flavour>/`, all gitignored):

| Path | Contents |
| --- | --- |
| `server-types/<Type>/<version>.jar` | e.g. `Spigot/1.16.5.jar`, `Paper/1.20.jar`. Any folder name works — `CustomSpigot/1.20.4.jar` is a valid server type. |
| `usual-plugins/<Name>-<pluginVer>-<minMc>-<maxMc>.jar` | e.g. `WorldGuard-7.0.8-1.19-LATEST.jar`. Spaces in names become `_`. **At least one WatchWolf-Server jar must be here.** |
| `tmp/<millis>/` | Per-server scratch folder; deleted when the server stops. |
| `logs/<uuid>/{info.txt,latest.log}` | Kept after the server dies — useful when debugging a failed run. |

Ports: the manager is on **8000**; each server takes a **consecutive pair** starting at **8001** —
`N` maps to the container's `25565` (Minecraft) and `N+1` to `25566` (the WatchWolf Server socket).
`DockerizedServerInstantiator.getNextServerPort()` scans running containers for the first free pair.

## Conventions and gotchas

- **Test naming is enforced** by `ci/debug/validator.sh`: unit tests `*Should.java` under
  `src/test/java`; integration tests `IT*.java` under `src/integration-test/java` with `@Timeout`
  on the line before `public class`. Profiles: default = unit only, `-P integration-test` =
  integration only.
- **Integration tests really start Docker containers.** They need the server-types, usual-plugins
  and a working Docker socket. They are slow and not hermetic.
- **"Server started" is detected by scraping stdout** — `Server.java` matches
  `Done \(…\)! For help, type "help"`. A server flavour that prints something else will never
  fire `serverStarted`. Likewise `ThrowableServer` scrapes stack traces out of the console to
  raise `capturedException`.
- **The petition interface is generated in WW-Core.** `ServersManagerPetitions`,
  `ServerStartedEvent` and `CapturedExceptionEvent` come from
  `dev.watchwolf.core.rpc.stubs.serversmanager` — to add an operation, change the JSON definition
  in the WatchWolf repo, regenerate in WW-Core, then implement it here.
- `RequesteeIpGetter` works because `startServer` is invoked from inside `forwardCall`'s
  `synchronized` block, so "the last channel we read from" is the caller. Keep that invariant if
  you touch threading.
- **`ReachedAddressIpManager` decides the address `startServer` answers with**, preferring the one
  the requester demonstrably reached us on over the `MACHINE_IP` guess (which is
  `hostname -I | awk '{print $1}'` — a VirtualBox or VPN adapter as often as the right NIC). Which
  source was used is logged on every `startServer`.
- **The default `docker compose` deployment never uses that first source.** With `ports: 8000:8000`
  on a bridge network every connection arrives from the bridge gateway and our side of it is a
  `172.x` address nobody outside the bridge can route to — and the servers we start publish their
  ports on the *host* regardless. `HostNetworkDetector` recognises that and falls back to
  `MACHINE_IP`/`PUBLIC_IP`, so those stay **required** as things stand. Switch the compose file to
  `network_mode: host` for the reached-address path to take effect.
- **A server can be stopped individually** through `Server.stop()`, which runs the `stopper` its
  instantiator installed (`docker kill` for `DockerizedServerInstantiator`). `ThrowableServer`
  forwards it to the server it wraps.
- **The wrapper/wrapped listener split is subtle.** `ThrowableServer` subscribes *itself* to the
  wrapped server's message **and stopped** events (`setSubEventManagerAsSelf`), because the only
  thing that notices a container dying — `DockerContainerStoppedObserver` — holds the inner
  `Server`, while the RPC layer subscribes to the wrapper. `ThrowableServerShould` asserts each
  event fires exactly once on both subscriptions; keep those green if you touch the raise methods.
- `ci/debug/{server-types,usual-plugins,tmp,logs}` in a working copy may hold hundreds of MB of
  jars and worlds. They are gitignored — do not add them, and do not assume they exist.
- `src/tools/{SpigotBuilder,PaperBuilder}.sh` are legacy and are `source`d by `WatchWolfSetup.sh`
  in the WatchWolf repo. Their README asks for them to be ported into Java; until then, keep the
  function names (`getAllVersions`, `buildVersion`, `getAllPaperVersions`, `buildPaperVersion`).

## Git conventions

- **`dev` is the working branch.** Every WatchWolf repo integrates and releases from `dev`.
  `master` (`main` in the WatchWolf standard repo) is downstream of it — never commit there
  directly, and never open a PR against it.
- **One branch per change, named for its kind:** `fix/<topic>` for defects, `feature/<topic>` for
  new work. Branch from `dev`.
- **Always open a PR into `dev`.** Do not push straight to `dev`, even for a one-line change.
