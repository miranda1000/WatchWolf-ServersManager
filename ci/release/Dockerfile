FROM openjdk:17

RUN mkdir /servers
ADD ServersManager.jar /servers
WORKDIR /servers

ENTRYPOINT ["/bin/bash", "-c"]
CMD ["echo \"[v] MACHINE_IP: $MACHINE_IP\" ; echo \"[v] PUBLIC_IP: <REDACTED>\" ; echo \"[v] PARENT_PWD: $PARENT_PWD\" ; echo \"[v] SERVER_PATH_SHIFT: $SERVER_PATH_SHIFT\" ; java -cp ServersManager.jar dev/watchwolf/serversmanager/ServersManager"]