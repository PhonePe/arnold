# Arnold runner image, used by all runner modes (local worker, GitLab CI, GitHub Actions).
FROM python:3.12-slim

ARG OPENCODE_VERSION=1.14.48
ARG INSTALL_OPENCODE=true

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

RUN if [ "$INSTALL_OPENCODE" = "true" ]; then \
      arch="$(dpkg --print-architecture)"; \
      case "$arch" in amd64) oc=x64 ;; arm64) oc=arm64 ;; *) echo "unsupported arch $arch" && exit 1 ;; esac; \
      curl -fsSL "https://github.com/sst/opencode/releases/download/v${OPENCODE_VERSION}/opencode-linux-${oc}.tar.gz" \
        -o /tmp/opencode.tar.gz \
      && tar -xzf /tmp/opencode.tar.gz -C /usr/local/bin opencode \
      && rm /tmp/opencode.tar.gz && opencode --version; \
    fi

RUN useradd --create-home --uid 10001 runner
WORKDIR /app

ENV PIP_INDEX_URL=https://pypi.org/simple/ \
    PYTHONUNBUFFERED=1 \
    GIT_PYTHON_REFRESH=quiet

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY --chown=runner:runner . .
USER runner

ENTRYPOINT ["python", "/app/main.py"]
CMD ["worker"]
