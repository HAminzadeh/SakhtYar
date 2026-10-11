FROM postgres:17-bookworm AS pgvector-builder

ARG PGVECTOR_VERSION=v0.8.1

# SAKHTYAR_PHASE515_APT_HTTPS_RETRY_V1
# Use HTTPS for Debian repositories inside Docker.
RUN set -eu; \
    for f in /etc/apt/sources.list /etc/apt/sources.list.d/*.list /etc/apt/sources.list.d/*.sources; do \
      if [ -f "$f" ]; then \
        sed -i -e 's|http://deb.debian.org|https://deb.debian.org|g' \
               -e 's|http://security.debian.org|https://security.debian.org|g' "$f"; \
      fi; \
    done
RUN apt-get -o Acquire::Retries=5 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 update \
    && apt-get -o Acquire::Retries=5 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 install -y --no-install-recommends \
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
