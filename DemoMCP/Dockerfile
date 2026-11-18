FROM python:3.13-slim

# Utilisateur non-root : un serveur MCP exécute des actions pour un LLM, on limite les dégâts possibles
RUN useradd --create-home --uid 10001 mcp
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY --chown=mcp:mcp helpdesk/ ./helpdesk/
USER mcp

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health')"
CMD ["python", "helpdesk/server.py", "http"]
