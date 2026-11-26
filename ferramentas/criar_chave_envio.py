"""Cria a chave de envio (upload) do Audio Roulette para a Play Store e já configura o GitHub.

O que faz:
1. Pede uma senha (duas vezes). Ela não aparece na tela e não fica salva em lugar nenhum do computador.
2. Cria a chave (RSA 2048, válida por 30 anos) no arquivo Documents\\Chaves\\audioroulette-envio.p12.
3. Guarda a chave, a senha e o nome dela ("audioroulette") nos segredos do repositório no GitHub,
   que é de onde a compilação assina o .aab.
4. Manda o GitHub compilar de novo, já com a chave nova.

Guarde o arquivo .p12 e a senha num lugar seguro (pen drive, Google Drive...): são eles que
assinam as atualizações do jogo na Play Store.

Rodar com um Python que tenha o pacote cryptography, por exemplo:
    ..\\WinZapp_Python\\.venv\\Scripts\\python.exe ferramentas\\criar_chave_envio.py
"""

import base64
import datetime
import getpass
import os
import subprocess
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

REPOSITORIO = "martimpcctrl/audioroulette2"
APELIDO = "audioroulette"
PASTA = os.path.join(os.path.expanduser("~"), "Documents", "Chaves")
ARQUIVO = os.path.join(PASTA, "audioroulette-envio.p12")


def pedir_senha() -> bytes:
    while True:
        senha = getpass.getpass("Digite a senha da chave nova (pelo menos 6 letras ou números) e aperte Enter: ")
        if len(senha) < 6:
            print("Senha curta demais. Tente de novo.")
            continue
        if senha != getpass.getpass("Digite a mesma senha de novo e aperte Enter: "):
            print("As duas senhas ficaram diferentes. Vamos de novo.")
            continue
        return senha.encode("utf-8")


def criar_chave(senha: bytes) -> bytes:
    chave = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    nome = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "Audio Roulette"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Audio Games"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, "BR"),
    ])
    agora = datetime.datetime.now(datetime.timezone.utc)
    certificado = (
        x509.CertificateBuilder()
        .subject_name(nome)
        .issuer_name(nome)
        .public_key(chave.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(agora - datetime.timedelta(days=1))
        .not_valid_after(agora + datetime.timedelta(days=365 * 30))
        .sign(chave, hashes.SHA256())
    )
    # cifra antiga e aceita por qualquer Java (o Gradle e o apksigner leem sem problema)
    cifra = (
        serialization.PrivateFormat.PKCS12.encryption_builder()
        .kdf_rounds(50000)
        .key_cert_algorithm(pkcs12.PBES.PBESv1SHA1And3KeyTripleDESCBC)
        .hmac_hash(hashes.SHA1())
        .build(senha)
    )
    return pkcs12.serialize_key_and_certificates(APELIDO.encode(), chave, certificado, None, cifra)


def guardar_segredo(nome: str, valor: str) -> None:
    r = subprocess.run(["gh", "secret", "set", nome, "-R", REPOSITORIO], input=valor, text=True, capture_output=True)
    if r.returncode != 0:
        raise SystemExit("Não consegui guardar o segredo " + nome + " no GitHub: " + r.stderr.strip())


def main() -> None:
    print("Criando a chave de envio do Audio Roulette.")
    if os.path.exists(ARQUIVO):
        print("Já existe uma chave em " + ARQUIVO + ". Para não perder essa, nada foi feito.")
        print("Se quiser mesmo uma nova, mude o nome ou tire esse arquivo de lá e rode de novo.")
        sys.exit(1)
    senha = pedir_senha()
    dados = criar_chave(senha)
    os.makedirs(PASTA, exist_ok=True)
    with open(ARQUIVO, "wb") as f:
        f.write(dados)
    print("Chave criada em " + ARQUIVO + ".")
    print("Guardando a chave no GitHub...")
    guardar_segredo("SIGNING_KEYSTORE_BASE64", base64.b64encode(dados).decode("ascii"))
    guardar_segredo("SIGNING_KEYSTORE_PASSWORD", senha.decode("utf-8"))
    guardar_segredo("SIGNING_KEY_ALIAS", APELIDO)
    print("Pronto, o GitHub já tem a chave nova.")
    r = subprocess.run(["gh", "workflow", "run", "build.yml", "-R", REPOSITORIO, "--ref", "main"], capture_output=True, text=True)
    if r.returncode == 0:
        print("A compilação com a chave nova começou no GitHub.")
    else:
        print("A chave está no GitHub, mas não consegui começar a compilação: " + r.stderr.strip())
    print("Agora guarde o arquivo audioroulette-envio.p12 e a senha num lugar seguro. Pode fechar esta janela.")


if __name__ == "__main__":
    main()
