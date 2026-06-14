# syntax=docker/dockerfile:1.7
# A stock Paper server, untweaked beyond the authlib JVM args that point it at
# the mojang-proxy-server. Built on itzg/minecraft-server.
FROM itzg/minecraft-server:latest

# --- Server type / EULA ---
ENV EULA=TRUE \
    TYPE=PAPER \
    VERSION=1.21.8

# Minecraft Java server port, published to the host in compose.yaml.
EXPOSE 25565

# --- Route Mojang API calls through the proxy ---
# A server only calls the session host (hasJoined), the services host
# (signed-chat /publickeys, relayed by the proxy), and the account host
# (name->UUID lookups). It never contacts the auth host — that's the client
# login flow — so it isn't overridden here.
# minecraft.api.env=custom is REQUIRED, or the host overrides are ignored.
# "proxy" is the proxy service on the compose network (see compose.yaml).
ENV JVM_OPTS="-Dminecraft.api.env=custom -Dminecraft.api.session.host=http://proxy:8000 -Dminecraft.api.services.host=http://proxy:8000 -Dminecraft.api.account.host=http://proxy:8000"
