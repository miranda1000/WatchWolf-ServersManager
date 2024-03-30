# WatchWolf - ServersManager [![CodeFactor](https://www.codefactor.io/repository/github/rogermiranda1000/watchwolf-serversmanager/badge/dev)](https://www.codefactor.io/repository/github/rogermiranda1000/watchwolf-serversmanager/overview/dev)
Responsible to start the WatchWolf's Servers. For more information about WatchWolf check the [WatchWolf website](https://watchwolf.dev/).

The ServersManager is responsable of implementing WatchWolf ServersManager's protocols, and starting the Minecraft Servers whenever it's needed. This concrete implementation will use Docker (to ensure security), and it will be developed using Bash and the TCP port 8000.


## Dependencies

- [Docker](https://www.docker.com/get-started/)
- Installed Docker images: `docker pull openjdk:8`, `docker pull openjdk:16`, `docker pull openjdk:17`
- TBD

## Compile

- Run `mvn clean compile assembly:single -DskipTests=true`