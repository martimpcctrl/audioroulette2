# Dockerfile - Ambiente de build limpo e reproduzível para o Audio Roulette.
# Usar isso em vez do WSL evita: cache global misturado, variáveis de ambiente
# do Windows vazando pro build, problemas de DNS específicos do WSL, e prompts
# de senha interativos que quebram quando a saída é redirecionada.

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# Mesmas dependências que já validamos como necessárias durante os builds no WSL
RUN apt-get update && apt-get install -y \
    python3 python3-pip python3-venv git zip unzip \
    openjdk-17-jdk autoconf libtool pkg-config \
    zlib1g-dev libncurses5-dev libncursesw5-dev cmake \
    libffi-dev libssl-dev wget curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Instala buildozer e cython (ambiente isolado do container, sem conflito com
# nenhum Python do sistema - o Ubuntu 22.04 nem tem a restricao PEP 668,
# entao nao precisamos de --break-system-packages aqui)
RUN pip3 install --upgrade pip buildozer cython

# Buildozer se recusa a rodar como root - cria um usuário normal
RUN useradd -ms /bin/bash builder

# Pré-cria a pasta de cache do buildozer com o dono certo. Quando o volume
# Docker for montado aqui pela primeira vez (vazio), ele herda essa
# permissão em vez de ficar com o dono padrão (root), que causaria
# "Permission denied" já que o container roda como usuário "builder".
RUN mkdir -p /home/builder/.buildozer && chown -R builder:builder /home/builder/.buildozer

USER builder
ENV PATH="/home/builder/.local/bin:${PATH}"

WORKDIR /app

# O comando real é passado pelo compilar.ps1 (docker run ... buildozer -v android debug)
CMD ["buildozer", "-v", "android", "debug"]
