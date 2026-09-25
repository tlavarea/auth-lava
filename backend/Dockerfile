# auth-lava — container image.
#
# The app has run as a host process (`./mvnw spring-boot:run`) until now. This
# exists because PlateTune deploys it to the udms Docker stack as a second,
# separately-configured instance: own database, own cookie domain, own OAuth
# clients. Nothing here is PlateTune-specific — every difference between the two
# instances is environment, per application.yaml.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# The wrapper downloads Maven itself, so a plain JDK image is enough.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Resolve dependencies before the source is copied, so a code-only change does
# not re-download the world.
#
# Deliberately a plain layer rather than `--mount=type=cache`. A cache mount is
# faster locally but is *not* exported by BuildKit's registry or GitHub Actions
# cache backends, and CI is now the build that matters — every push would
# re-download Maven Central. Baking ~/.m2 into this layer means the GHA layer
# cache carries it, and it only re-runs when pom.xml actually changes. The layer
# is discarded with the build stage.
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
# Tests are not run here. CI already runs `./mvnw -B verify` on every push, and
# running them again inside the image build would need a live Postgres.
RUN ./mvnw -B -DskipTests package


FROM eclipse-temurin:25-jre
WORKDIR /app

# Ubuntu-based rather than -alpine or distroless, deliberately. This is the
# first production deployment of auth-lava and it will need debugging; a shell
# in the image is worth more than the megabytes. (The cloudflared container next
# door is distroless and cannot be exec'd into at all.)
COPY --from=build /src/target/*.jar /app/app.jar

# Non-root. No useradd needed: the Ubuntu base already ships uid/gid 1000 as
# `ubuntu`, so adding one fails with "UID 1000 is not unique".
#
# 1000 specifically, because that is $PUID on udms and the Docker secrets there
# are owned by it — a mismatched user cannot read its own JWT keys or database
# password. The compose file restates it as `user: $PUID:$PGID` so the coupling
# is visible from that side too, and so a host with a different PUID still works.
USER 1000:1000

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: this shares a host with ~35 other
# containers, so the ceiling should follow whatever limit the container is given
# rather than a number baked in here.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
