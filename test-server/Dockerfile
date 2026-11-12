FROM python:3.11-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends iputils-ping nodejs \
    && rm -rf /var/lib/apt/lists/*
COPY --from=ghcr.io/astral-sh/uv:0.8.4 /uv /usr/local/bin/uv

WORKDIR /app
COPY pyproject.toml uv.lock README.md ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
RUN uv sync --frozen --no-dev

RUN useradd --create-home mcp && mkdir -p /srv/mcp-files && chown mcp /srv/mcp-files
USER mcp
ENV MCP_TEST_HOST=0.0.0.0 \
    MCP_TEST_PORT=8000 \
    MCP_TEST_FILES_DIR=/srv/mcp-files \
    PATH=/app/.venv/bin:$PATH
EXPOSE 8000
ENTRYPOINT ["mcp-test-server"]
