"""Gera o ícone do Audio Roulette (no app e na loja), a imagem de destaque e as capturas de tela.

O desenho: um cartucho de espingarda (corpo vermelho-ferrugem, base dourada) com ondas de som dos
dois lados, nas cores do site do jogo (marrom-escuro e dourado).

Uso:
    py -3.12 ferramentas\\gerar_imagens.py <pasta das imagens da loja>
Também grava os ícones do app em app/src/main/res/.
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

FUNDO = (27, 24, 21)
FUNDO_2 = (44, 38, 32)
DOURADO = (201, 162, 75)
DOURADO_ESCURO = (150, 117, 48)
FERRUGEM = (192, 90, 61)
FERRUGEM_ESCURA = (140, 60, 40)
TINTA = (237, 230, 218)
APAGADO = (168, 154, 134)
FONTES = "C:/Windows/Fonts/"
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(RAIZ, "app", "src", "main", "res")


def fonte(nome, tamanho):
    return ImageFont.truetype(FONTES + nome, tamanho)


def desenhar_simbolo(d, cx, cy, escala):
    """Cartucho e ondas de som, centrados em (cx, cy); escala 1.0 = cabe num quadrado de ~620 px."""
    s = escala

    def r(v):
        return int(round(v * s))

    # corpo do cartucho
    larg, alt = r(200), r(400)
    topo = cy - r(250)
    d.rounded_rectangle([cx - larg // 2, topo, cx + larg // 2, topo + alt], radius=r(40), fill=FERRUGEM)
    # dobras da ponta (o fechamento do cartucho)
    for i in range(-2, 3):
        x = cx + i * r(34)
        d.line([x, topo + r(14), x, topo + r(70)], fill=FERRUGEM_ESCURA, width=max(2, r(10)))
    # faixas do corpo
    d.rectangle([cx - larg // 2, topo + r(230), cx + larg // 2, topo + r(250)], fill=FERRUGEM_ESCURA)
    # base dourada (o latão)
    base_topo = topo + alt - r(10)
    d.rounded_rectangle([cx - r(116), base_topo, cx + r(116), base_topo + r(110)], radius=r(16), fill=DOURADO)
    d.rectangle([cx - r(116), base_topo + r(70), cx + r(116), base_topo + r(84)], fill=DOURADO_ESCURO)
    # espoleta
    d.ellipse([cx - r(22), base_topo + r(88) - r(10), cx + r(22), base_topo + r(88) + r(20)], fill=DOURADO_ESCURO)
    # ondas de som dos dois lados
    meio = cy + r(10)
    for k, raio in enumerate((r(170), r(235), r(300))):
        w = max(3, r(26 - k * 4))
        caixa = [cx - raio, meio - raio, cx + raio, meio + raio]
        d.arc(caixa, start=150, end=210, fill=DOURADO, width=w)   # esquerda
        d.arc(caixa, start=-30, end=30, fill=DOURADO, width=w)    # direita


def icone(tamanho, fundo=True, zona=1.0):
    """Ícone quadrado. zona < 1 encolhe o desenho (a área segura do ícone adaptativo do Android)."""
    grande = 1024
    img = Image.new("RGBA", (grande, grande), FUNDO + (255,) if fundo else (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if fundo:
        d.ellipse([112, 112, 912, 912], fill=FUNDO_2)
    desenhar_simbolo(d, grande // 2, grande // 2, 1.28 * zona)
    return img.resize((tamanho, tamanho), Image.LANCZOS)


def quebrar(texto, f, largura, d):
    linhas, atual = [], ""
    for palavra in texto.split():
        teste = (atual + " " + palavra).strip()
        if d.textlength(teste, font=f) <= largura:
            atual = teste
        else:
            linhas.append(atual)
            atual = palavra
    if atual:
        linhas.append(atual)
    return linhas


def texto_centrado(d, linhas, f, cy, cor, largura_total, espaco=1.25):
    altura = f.size * espaco
    y = cy - altura * len(linhas) / 2
    for linha in linhas:
        w = d.textlength(linha, font=f)
        d.text(((largura_total - w) / 2, y), linha, font=f, fill=cor)
        y += altura


def destaque(caminho):
    img = Image.new("RGB", (1024, 500), FUNDO)
    d = ImageDraw.Draw(img)
    d.ellipse([40, 30, 480, 470], fill=FUNDO_2)
    desenhar_simbolo(d, 260, 250, 0.62)
    titulo = fonte("georgiab.ttf", 82)
    d.text((500, 140), "Audio", font=titulo, fill=DOURADO)
    d.text((500, 225), "Roulette", font=titulo, fill=DOURADO)
    sub = fonte("georgia.ttf", 34)
    d.text((504, 340), "Roleta russa só com som", font=sub, fill=TINTA)
    d.text((504, 384), "Feito para jogar sem enxergar", font=fonte("georgia.ttf", 28), fill=APAGADO)
    img.save(caminho)


def captura(caminho, legenda, tela, detalhe):
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), FUNDO)
    d = ImageDraw.Draw(img)
    f_leg = fonte("georgiab.ttf", 76)
    texto_centrado(d, quebrar(legenda, f_leg, w - 140, d), f_leg, 300, DOURADO, w)
    # a tela do jogo (preta, com o texto branco no meio, como no app)
    x0, y0, x1, y1 = 90, 560, w - 90, 1640
    d.rounded_rectangle([x0 - 14, y0 - 14, x1 + 14, y1 + 14], radius=60, fill=(60, 52, 44))
    d.rounded_rectangle([x0, y0, x1, y1], radius=48, fill=(0, 0, 0))
    f_tela = fonte("segoeui.ttf", 56)
    linhas = []
    for parte in tela.split("\n"):
        linhas += quebrar(parte, f_tela, x1 - x0 - 120, d)
    altura = f_tela.size * 1.3
    y = (y0 + y1) / 2 - altura * len(linhas) / 2
    for linha in linhas:
        lw = d.textlength(linha, font=f_tela)
        d.text(((w - lw) / 2, y), linha, font=f_tela, fill=(255, 255, 255))
        y += altura
    f_det = fonte("georgia.ttf", 44)
    texto_centrado(d, quebrar(detalhe, f_det, w - 160, d), f_det, 1790, TINTA, w)
    img.save(caminho)


def icones_do_app():
    densidades = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for nome, fator in densidades.items():
        pasta = os.path.join(RES, "mipmap-" + nome)
        os.makedirs(pasta, exist_ok=True)
        icone(int(48 * fator)).save(os.path.join(pasta, "ic_launcher.png"))
        # frente do ícone adaptativo: 108 dp, com o desenho dentro da área segura
        icone(int(108 * fator), fundo=False, zona=0.62).save(os.path.join(pasta, "ic_launcher_foreground.png"))
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@color/ic_launcher_background" />\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
           '</adaptive-icon>\n')
    for nome in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, nome), "w", encoding="utf-8") as f:
            f.write(xml)
    with open(os.path.join(RES, "values", "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '    <color name="ic_launcher_background">#1B1815</color>\n</resources>\n')


def main():
    pasta = sys.argv[1]
    os.makedirs(pasta, exist_ok=True)
    icone(512).convert("RGB").save(os.path.join(pasta, "Ícone da loja (512x512).png"))
    destaque(os.path.join(pasta, "Imagem de destaque (1024x500).png"))
    captura(os.path.join(pasta, "Captura de tela 1 - menu principal.png"),
            "Roleta russa toda narrada por voz",
            "MENU PRINCIPAL",
            "Campanha, modo rápido, modo infinito e online. Deslize para escolher.")
    captura(os.path.join(pasta, "Captura de tela 2 - sua vez na partida.png"),
            "Atire no Dealer ou arrisque em você",
            "Fase 1 | SUA VEZ | Vida: 3 | Dealer: 3",
            "Deslize para os lados para mirar e toque duas vezes para atirar.")
    captura(os.path.join(pasta, "Captura de tela 3 - usando um item.png"),
            "Nove itens para virar o jogo",
            "Você usou Lupa\nÉ uma bala real.",
            "Lupa, cerveja, serra, algemas, celular, inversor, cigarro, remédio e adrenalina.")
    captura(os.path.join(pasta, "Captura de tela 4 - modo online.png"),
            "Jogue online com um amigo",
            "Online | Você: 2 | Ana: 1",
            "Cada um no seu celular. Entre na sala com um código de quatro números.")
    icones_do_app()
    print("Imagens geradas em", pasta)


if __name__ == "__main__":
    main()
