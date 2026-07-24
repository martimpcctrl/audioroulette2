[app]

# (str) Título do seu app
title = Audio Roulette

# (str) Nome do pacote
package.name = audioroulette

# (str) Domínio do pacote (necessário para android/ios packaging)
package.domain = org.audiogame

# (str) Diretório de origem onde está o main.py
source.dir = .

# (list) Extensões de arquivo a incluir no APK
# IMPORTANTE: inclui .wav e .ogg para que os sons do jogo sejam empacotados
source.include_exts = py,png,jpg,kv,atlas,wav,ogg,mp3,ttf

# (list) Padrões de arquivos/pastas a incluir explicitamente (garante a pasta sounds/)
source.include_patterns = sounds/*,sounds/*.wav,sounds/*.ogg

# (list) Diretórios a excluir do empacotamento
source.exclude_dirs = tests,bin,.buildozer,.git,__pycache__

# (str) Versão do app
version = 1.0

# (list) Requisitos / bibliotecas Python necessárias
# python3, kivy para interface/gestos, pygame para o mixer de áudio estéreo
requirements = python3==3.11.9,hostpython3==3.11.9,kivy==2.3.0,pyjnius,android

# (str) Ícone customizado (opcional - descomente e ajuste se tiver um ícone)
# icon.filename = %(source.dir)s/icon.png

# (str) Orientação suportada
orientation = portrait

# (bool) Tela cheia
fullscreen = 0

# -----------------------------------------------------------------------
# CONFIGURAÇÕES ESPECÍFICAS DO ANDROID
# -----------------------------------------------------------------------

# (list) Permissões necessárias
# RECORD_AUDIO não é obrigatório (o jogo só toca áudio, não grava),
# mas é mantido comentado abaixo caso deseje evoluir para reconhecimento de voz.
android.permissions = INTERNET,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE,VIBRATE
# android.permissions = INTERNET,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE,VIBRATE,RECORD_AUDIO

# (int) API alvo do Android (target)
android.api = 33

# (int) API mínima suportada
android.minapi = 21

# (str) Versão do NDK do Android usada pelo python-for-android
android.ndk = 25b

# (int) SDK do Android usado no build
android.sdk = 33

# (bool) Aceitar automaticamente as licenças do SDK durante o build
android.accept_sdk_license = True

# (list) Arquiteturas-alvo (arm64-v8a cobre a maioria dos aparelhos modernos;
# armeabi-v7a garante compatibilidade com aparelhos mais antigos)
android.archs = arm64-v8a,armeabi-v7a

# (bool) Copiar biblioteca compartilhada em vez de fazer strip (ajuda debug)
android.copy_libs = 1

# (str) Nome do serviço de entrada (padrão)
android.entrypoint = org.kivy.android.PythonActivity

# (bool) Habilita o modo "wakelock" para impedir que a tela apague durante o jogo
android.wakelock = True

# (bool) Permite que o app rode mesmo com áudio em segundo plano
# (relevante pois o jogo depende 100% de áudio)
android.allow_backup = True

# (str) Tema do app Android (evita tela branca ao abrir)
# android.apptheme = "@android:style/Theme.NoTitleBar"

# (list) Bibliotecas nativas adicionais do p4a, caso necessário no futuro
# android.add_libs_armeabi_v7a = libs/android/*.so

# (bool) Usa a nova toolchain do python-for-android (recomendado)
p4a.bootstrap = sdl2

# -----------------------------------------------------------------------
# BUILD
# -----------------------------------------------------------------------

[buildozer]

# (int) Nível de log (0 = só erros, 1 = infos, 2 = debug com muitos detalhes)
log_level = 2

# (int) Mostra aviso se rodando como root (0 desabilita o aviso)
warn_on_root = 1

# (str) Diretório de build (cache de builds do buildozer)
build_dir = ./.buildozer

# (str) Diretório de saída do APK final
bin_dir = ./bin
