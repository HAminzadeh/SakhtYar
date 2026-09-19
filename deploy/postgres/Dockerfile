FROM postgres:17-bookworm AS pgvector-builder

ARG PGVECTOR_VERSION=v0.8.1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       build-essential \
       ca-certificates \
       git \
       postgresql-server-dev-17 \
    && git clone --depth 1 --branch ${PGVECTOR_VERSION} \
       https://github.com/pgvector/pgvector.git /tmp/pgvector \
    && cd /tmp/pgvector \
    && make \
    && make install DESTDIR=/tmp/pgvector-install \
    && rm -rf /var/lib/apt/lists/*

FROM postgis/postgis:17-3.5

COPY --from=pgvector-builder \
  /tmp/pgvector-install/usr/lib/postgresql/17/lib/vector.so \
  /usr/lib/postgresql/17/lib/vector.so

COPY --from=pgvector-builder \
  /tmp/pgvector-install/usr/share/postgresql/17/extension/vector* \
  /usr/share/postgresql/17/extension/
