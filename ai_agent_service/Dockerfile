FROM python:3.11.15-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    AI_AGENT_HOST=0.0.0.0 \
    AI_AGENT_PORT=8103 \
    RAG_INDEX_DIR=/data/rag_index

WORKDIR /app

COPY requirements.txt ./
RUN python -m pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY knowledge ./knowledge

RUN addgroup --system app && adduser --system --ingroup app app \
    && mkdir -p /data/rag_index \
    && chown -R app:app /app /data/rag_index

USER app
VOLUME ["/data/rag_index"]
EXPOSE 8103

CMD ["python", "-m", "app"]
