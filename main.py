# -*- coding: utf-8 -*-
"""
AUDIO ROULETTE - Um Audio Game inspirado em Buckshot Roulette
100% jogável por áudio, com direcionamento estéreo (pan) para acessibilidade.

Stack: Kivy (interface, gestos e áudio nativo via SDL2)

Controles (gestos de toque na tela):
    - Swipe ESQUERDA  -> Mirar em SI MESMO / Navegar no menu
    - Swipe DIREITA   -> Mirar no DEALER
    - Swipe CIMA      -> Item/opção seguinte
    - Swipe BAIXO     -> Item/opção anterior
    - Duplo Toque      -> ATIRAR / USAR ITEM / CONFIRMAR opção do menu

Autor: Gerado para projeto pessoal de Audio Game
"""

import os
import random
import math
from collections import deque

os.environ.setdefault("KIVY_NO_ARGS", "1")

from kivy.app import App
from kivy.uix.widget import Widget
from kivy.uix.label import Label
from kivy.uix.boxlayout import BoxLayout
from kivy.clock import Clock
from kivy.core.window import Window
from kivy.metrics import dp
from kivy.logger import Logger

from kivy.core.audio import SoundLoader

# ============================================================
#   CONFIGURAÇÃO GLOBAL
# ============================================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SOUND_DIR = os.path.join(BASE_DIR, "sounds")

ITEM_KEYS = ["cigarro", "cerveja", "serra", "lupa", "algemas"]
MAX_ITEMS_TOTAL = 8
TOTAL_FASES = 5

SWIPE_THRESHOLD = dp(60)
DOUBLE_TAP_TIME = 0.40
DOUBLE_TAP_DIST = dp(50)

PAN_SELF = -1.0
PAN_DEALER = 1.0
PAN_CENTER = 0.0

NARRATION_GAP = 0.12       # respiro entre falas na fila
DEFAULT_CLIP_LEN = 1.0     # duração assumida se o som não puder ser medido


# ============================================================
#   GERENCIADOR DE ÁUDIO (Kivy nativo - SDL2, 100% compatível com Android)
# ============================================================

class AudioManager:
    """
    Gerencia carregamento e reprodução de sons usando o áudio nativo do
    Kivy (kivy.core.audio / SDL2), sem depender de pygame - o que evita
    os problemas de compilação do pygame para Android.

    O direcionamento estéreo (pan) é feito de forma diferente daqui pra
    frente: em vez de ajustar volume por canal em tempo real, cada som
    já existe em 3 versões pré-gravadas com o panorâmico embutido no
    próprio arquivo: "<nome>__L.wav" (esquerda/você), "<nome>__C.wav"
    (centro) e "<nome>__R.wav" (direita/Dealer). O método escolhe a
    variante certa automaticamente a partir do valor de "pan" pedido.

    Duas formas de tocar som:
      - play(key, pan): toca IMEDIATAMENTE (para música de fundo/streams).
      - narrate(key, pan, on_complete): entra numa FILA serial. Cada som só
        começa depois que o anterior terminar de verdade (duração real
        medida pelo próprio Kivy), evitando que uma fala corte ou se
        sobreponha à outra. Use narrate() para TUDO que é falado/ouvido
        em sequência.
    """

    def __init__(self):
        self.enabled = True
        self.sounds = {}          # key -> kivy Sound
        self._music = None
        self._load_all()

        self._queue = deque()
        self._narrating = False

    def _variant_suffix(self, pan):
        if pan < -0.1:
            return "L"
        if pan > 0.1:
            return "R"
        return "C"

    def _resolve_key(self, key, pan):
        """Escolhe a variante __L/__C/__R certa, com fallback pro nome puro."""
        suffix = self._variant_suffix(pan)
        variant = f"{key}__{suffix}"
        if variant in self.sounds:
            return variant
        if key in self.sounds:
            return key
        for s in ("C", "L", "R"):
            alt = f"{key}__{s}"
            if alt in self.sounds:
                return alt
        return None

    def _load_all(self):
        if not os.path.isdir(SOUND_DIR):
            Logger.warning(f"AudioManager: pasta de sons não encontrada em {SOUND_DIR}")
            return

        for fname in os.listdir(SOUND_DIR):
            if fname.lower().endswith((".wav", ".ogg", ".mp3")):
                key = os.path.splitext(fname)[0]
                if key == "musica_fundo":
                    continue  # música de fundo é tratada à parte (streaming em loop)
                path = os.path.join(SOUND_DIR, fname)
                try:
                    snd = SoundLoader.load(path)
                    if snd is not None:
                        self.sounds[key] = snd
                except Exception as e:
                    Logger.warning(f"AudioManager: erro ao carregar {fname}: {e}")

        Logger.info(f"AudioManager: {len(self.sounds)} sons carregados.")

    def get_length(self, key, pan=PAN_CENTER):
        resolved = self._resolve_key(key, pan)
        snd = self.sounds.get(resolved) if resolved else None
        if snd is not None:
            try:
                length = snd.length
                if length and length > 0:
                    return length
            except Exception:
                pass
        return DEFAULT_CLIP_LEN

    def play(self, key, pan=PAN_CENTER, volume=1.0):
        """Toca um som IMEDIATAMENTE (sem fila). O pan escolhe a variante certa."""
        if not self.enabled:
            return None
        resolved = self._resolve_key(key, pan)
        if resolved is None:
            Logger.warning(f"AudioManager: som '{key}' não encontrado.")
            return None

        snd = self.sounds[resolved]
        try:
            snd.volume = max(0.0, min(1.0, volume))
            snd.stop()
            snd.play()
        except Exception as e:
            Logger.warning(f"AudioManager: erro ao tocar '{resolved}': {e}")
        return snd

    def narrate(self, key, pan=PAN_CENTER, on_complete=None):
        """Enfileira um som para tocar em sequência (nunca sobrepõe o anterior)."""
        self._queue.append((key, pan, on_complete))
        if not self._narrating:
            self._process_queue()

    def clear_queue(self):
        """Esvazia a fila de narração (usado ao trocar de tela/estado abruptamente)."""
        self._queue.clear()
        self._narrating = False

    def _process_queue(self, dt=None):
        if not self._queue:
            self._narrating = False
            return
        self._narrating = True
        key, pan, on_complete = self._queue.popleft()
        self.play(key, pan)
        duration = self.get_length(key, pan) + NARRATION_GAP

        def _advance(_dt):
            if on_complete:
                on_complete()
            self._process_queue()

        Clock.schedule_once(_advance, duration)

    def play_music(self, key, volume=0.22, loop=True):
        """Toca uma faixa de música de fundo em loop."""
        if not self.enabled:
            return
        for ext in (".ogg", ".wav", ".mp3"):
            path = os.path.join(SOUND_DIR, key + ext)
            if os.path.isfile(path):
                try:
                    self.stop_music()
                    snd = SoundLoader.load(path)
                    if snd is not None:
                        snd.volume = volume
                        snd.loop = loop
                        snd.play()
                        self._music = snd
                except Exception as e:
                    Logger.warning(f"AudioManager: erro ao tocar música '{key}': {e}")
                return

    def stop_music(self):
        if self._music is not None:
            try:
                self._music.stop()
            except Exception:
                pass
            self._music = None


# ============================================================
#   ENTIDADES DO JOGO
# ============================================================

class Fighter:
    def __init__(self, name, pan):
        self.name = name
        self.pan = pan
        self.max_hp = 3
        self.hp = 3
        self.items = {k: 0 for k in ITEM_KEYS}
        self.handcuffed = False
        self.sawed = False
        self.knows_shell = None

    def total_items(self):
        return sum(self.items.values())

    def is_alive(self):
        return self.hp > 0

    def reset_round_flags(self):
        self.sawed = False
        self.knows_shell = None


# ============================================================
#   MENU PRINCIPAL
# ============================================================

class MenuCore:
    OPTIONS = [
        ("jogar", "menu_jogar"),
        ("instrucoes", "menu_instrucoes"),
        ("sair", "menu_sair"),
    ]

    def __init__(self, audio: AudioManager, on_status_update, on_start_game, on_quit):
        self.audio = audio
        self.status = on_status_update
        self.on_start_game = on_start_game
        self.on_quit = on_quit
        self.index = 0
        self.active = False

    def open(self):
        self.active = True
        self.index = 0
        self.audio.clear_queue()
        self.status("MENU PRINCIPAL")
        self.audio.narrate("menu_titulo", PAN_CENTER)
        self._announce_current()

    def _announce_current(self):
        _, sound_key = self.OPTIONS[self.index]
        self.audio.narrate("blip_menu", PAN_CENTER)
        self.audio.narrate(sound_key, PAN_CENTER)

    def cycle(self, direction):
        if not self.active:
            return
        self.index = (self.index + direction) % len(self.OPTIONS)
        self._announce_current()

    def confirm(self):
        if not self.active:
            return
        choice, _ = self.OPTIONS[self.index]
        self.audio.narrate("blip_confirmar", PAN_CENTER)

        if choice == "jogar":
            self.active = False
            Clock.schedule_once(lambda dt: self.on_start_game(), 0.35)
        elif choice == "instrucoes":
            self._play_instructions()
        elif choice == "sair":
            Clock.schedule_once(lambda dt: self.on_quit(), 0.4)

    def _play_instructions(self):
        self.active = False
        self.status("INSTRUÇÕES")
        seq = ["instrucoes_1", "instrucoes_2", "instrucoes_3", "instrucoes_4", "instrucoes_5"]
        for k in seq[:-1]:
            self.audio.narrate(k, PAN_CENTER)

        def back_to_menu():
            self.open()

        self.audio.narrate(seq[-1], PAN_CENTER, on_complete=back_to_menu)


# ============================================================
#   NÚCLEO DO JOGO (Máquina de Estados) - com FASES
# ============================================================

class GameCore:
    def __init__(self, audio: AudioManager, on_status_update=None, on_campaign_end=None):
        self.audio = audio
        self.on_status_update = on_status_update or (lambda text: None)
        self.on_campaign_end = on_campaign_end or (lambda: None)

        self.player = Fighter("player", PAN_SELF)
        self.dealer = Fighter("dealer", PAN_DEALER)

        self.shells = []
        self.turn = "player"
        self.aim = None
        self.selected_index = 0
        self.busy = False
        self.game_over = False
        self.active = False

        self.fase = 1

        self.dealer_action_count = 0
        self.MAX_DEALER_ACTIONS = 8

    # -------------------- DIFICULDADE POR FASE --------------------

    def _dificuldade_fase(self):
        max_hp = min(3 + (self.fase - 1) // 2, 6)
        dealer_skill = min(0.45 + self.fase * 0.09, 0.92)
        item_min = max(1, 3 - self.fase // 3)
        return max_hp, dealer_skill, item_min

    # -------------------- SETUP / CAMPANHA --------------------

    def start_campaign(self):
        self.active = True
        self.game_over = False
        self.fase = 1
        self._start_fase()

    def _start_fase(self):
        max_hp, _, _ = self._dificuldade_fase()
        self.player.max_hp = max_hp
        self.player.hp = max_hp
        self.dealer.max_hp = max_hp
        self.dealer.hp = max_hp
        self.player.items = {k: 0 for k in ITEM_KEYS}
        self.dealer.items = {k: 0 for k in ITEM_KEYS}
        self.turn = "player"
        self.aim = None
        self.selected_index = 0
        self.game_over = False

        self.audio.play_music("musica_fundo", volume=0.20, loop=True)

        def depois_do_anuncio():
            self._begin_round()

        self.audio.narrate("fase_iniciando", PAN_CENTER)
        self.audio.narrate(f"numero_{min(self.fase, 8)}", PAN_CENTER, on_complete=depois_do_anuncio)

    def _begin_round(self):
        def depois_dos_itens():
            self.status(f"FASE {self.fase} | Vida: {self.player.hp}/{self.player.max_hp}")
            Clock.schedule_once(lambda dt: self.start_turn("player"), 0.5)

        def depois_da_recarga():
            self.give_items(on_complete=depois_dos_itens)

        self.reload_shells(on_complete=depois_da_recarga)

    def reload_shells(self, on_complete=None):
        total = random.randint(2, 8)
        real_count = random.randint(1, total - 1)
        blank_count = total - real_count
        self.shells = [True] * real_count + [False] * blank_count
        random.shuffle(self.shells)

        self.player.reset_round_flags()
        self.dealer.reset_round_flags()

        self.audio.narrate("recarregando", PAN_CENTER)
        self.audio.narrate(f"numero_{min(real_count, 8)}", PAN_CENTER)
        self.audio.narrate("balas_reais", PAN_CENTER)
        self.audio.narrate(f"numero_{min(blank_count, 8)}", PAN_CENTER)
        self.audio.narrate("balas_falsas", PAN_CENTER, on_complete=on_complete)

        Logger.info(f"[GameCore] Fase {self.fase} recarregado: {real_count} reais, {blank_count} falsas")

    def give_items(self, on_complete=None):
        _, _, item_min = self._dificuldade_fase()
        for fighter in (self.player, self.dealer):
            n = random.randint(item_min, 3)
            for _ in range(n):
                if fighter.total_items() >= MAX_ITEMS_TOTAL:
                    break
                item = random.choice(ITEM_KEYS)
                fighter.items[item] += 1
        self.audio.narrate("novos_itens", PAN_CENTER, on_complete=on_complete)

    def status(self, text):
        self.on_status_update(text)

    # -------------------- CONTROLE DE TURNO --------------------

    def start_turn(self, who):
        if self.game_over or not self.active:
            return
        self.turn = who
        self.aim = None
        self.selected_index = 0
        fighter = self.player if who == "player" else self.dealer

        if fighter.handcuffed:
            fighter.handcuffed = False
            self.audio.narrate("dealer_algemado" if who == "dealer" else "voce_algemado", PAN_CENTER)
            other = "dealer" if who == "player" else "player"
            Clock.schedule_once(lambda dt: self.start_turn(other), 0.4)
            return

        if who == "player":
            self.busy = False
            self.audio.narrate("sua_vez", PAN_SELF)
            self.status(f"FASE {self.fase} | SUA VEZ | Vida: {self.player.hp} | Dealer: {self.dealer.hp}")
        else:
            self.busy = True
            self.audio.narrate("vez_dealer", PAN_DEALER)
            self.status(f"FASE {self.fase} | Vez do Dealer | Vida: {self.player.hp} | Dealer: {self.dealer.hp}")
            self.dealer_action_count = 0
            Clock.schedule_once(lambda dt: self.dealer_take_action(), 1.0)

    def check_shells_empty_and_maybe_reload(self, callback=None, delay=0.3):
        if len(self.shells) == 0:
            Clock.schedule_once(lambda dt: self.reload_shells(on_complete=callback), delay)
            return True
        return False

    # -------------------- GESTOS DO JOGADOR --------------------

    def player_can_act(self):
        return self.active and (not self.game_over) and (not self.busy) and (self.turn == "player")

    def player_set_aim(self, target):
        if not self.player_can_act():
            return
        self.aim = target
        if target == "self":
            self.audio.narrate("mira_em_voce", PAN_SELF)
        else:
            self.audio.narrate("mira_no_dealer", PAN_DEALER)

    def player_cycle_item(self, direction):
        if not self.player_can_act():
            return
        self.selected_index = (self.selected_index + direction) % (len(ITEM_KEYS) + 1)
        if self.selected_index == 0:
            self.audio.narrate("item_nenhum", PAN_SELF)
            return
        item = ITEM_KEYS[self.selected_index - 1]
        self.audio.narrate(f"item_{item}", PAN_SELF)
        count = self.player.items[item]
        if count <= 0:
            self.audio.narrate("item_indisponivel", PAN_SELF)
        else:
            self.audio.narrate(f"numero_{min(count, 8)}", PAN_SELF)

    def player_confirm_action(self):
        if not self.player_can_act():
            self.audio.narrate("gesto_invalido", PAN_CENTER)
            return

        if self.selected_index == 0:
            if self.aim is None:
                self.audio.narrate("precisa_mirar", PAN_SELF)
                return
            self.fire_weapon(self.player, self.dealer, self.aim)
        else:
            item = ITEM_KEYS[self.selected_index - 1]
            self.use_item(self.player, self.dealer, item)

    # -------------------- ITENS --------------------

    def use_item(self, user: Fighter, other: Fighter, item):
        if user.items.get(item, 0) <= 0:
            self.audio.narrate("item_indisponivel", user.pan)
            return

        user.items[item] -= 1
        pan = user.pan

        if item == "cigarro":
            self.audio.narrate("usar_cigarro", pan)
            if user.hp < user.max_hp:
                user.hp += 1
                self.audio.narrate("vida_recuperada", pan)
            else:
                self.audio.narrate("sem_efeito", pan)

        elif item == "cerveja":
            self.audio.narrate("usar_cerveja", pan)
            if self.shells:
                ejected = self.shells.pop(0)
                snd = "revela_bala_real" if ejected else "revela_bala_falsa"
                self.audio.narrate(snd, pan)
                self.check_shells_empty_and_maybe_reload(delay=0.3)

        elif item == "serra":
            self.audio.narrate("usar_serra", pan)
            user.sawed = True
            self.audio.narrate("serra_aplicada", pan)

        elif item == "lupa":
            self.audio.narrate("usar_lupa", pan)
            if self.shells:
                real = self.shells[0]
                if user is self.player:
                    snd = "revela_bala_real" if real else "revela_bala_falsa"
                    self.audio.narrate(snd, pan)
                else:
                    user.knows_shell = real

        elif item == "algemas":
            self.audio.narrate("usar_algemas", pan)
            if not other.handcuffed:
                other.handcuffed = True
                self.audio.narrate("oponente_algemado", pan)
            else:
                self.audio.narrate("sem_efeito", pan)

        self.status(f"{user.name} usou {item}")

    # -------------------- DISPARO --------------------

    def fire_weapon(self, shooter: Fighter, other: Fighter, aim):
        if not self.shells:
            self.reload_shells(on_complete=lambda: self.fire_weapon(shooter, other, aim))
            return

        target = shooter if aim == "self" else other
        shell_real = self.shells.pop(0)
        damage = 2 if shooter.sawed else 1
        shooter.sawed = False

        self.audio.narrate("disparo_martelo", PAN_CENTER)

        def resolve():
            if shell_real:
                target.hp -= damage
                self.audio.narrate("tiro_real", target.pan)
                self.audio.narrate("dano_sofrido", target.pan)
                self.status(f"BALA REAL! {target.name} sofreu {damage} de dano. HP: {target.hp}")

                if target.hp <= 0:
                    self._end_fase(shooter)
                    return
                self._after_shot(shooter, aim, shell_real)
            else:
                self.audio.narrate("click_falso", target.pan)
                self.status(f"Clique vazio. {target.name} está seguro.")
                self._after_shot(shooter, aim, shell_real)

        Clock.schedule_once(lambda dt: resolve(), self.audio.get_length("disparo_martelo") + 0.3)

    def _after_shot(self, shooter, aim, was_real):
        self.check_shells_empty_and_maybe_reload()

        extra_turn = (aim == "self") and (was_real is False)

        if extra_turn:
            self.audio.narrate("turno_extra", shooter.pan)
            if shooter is self.player:
                Clock.schedule_once(lambda dt: self.start_turn("player"), 0.3)
            else:
                self.dealer_action_count += 1
                if self.dealer_action_count >= self.MAX_DEALER_ACTIONS:
                    Clock.schedule_once(lambda dt: self.start_turn("player"), 0.3)
                else:
                    Clock.schedule_once(lambda dt: self.dealer_take_action(), 1.0)
        else:
            next_turn = "dealer" if shooter is self.player else "player"
            Clock.schedule_once(lambda dt: self.start_turn(next_turn), 0.4)

    def _end_fase(self, shooter):
        self.busy = True
        self.audio.stop_music()

        if shooter is self.player:
            # jogador venceu esta fase
            def prosseguir():
                if self.fase >= TOTAL_FASES:
                    self.game_over = True
                    self.active = False
                    self.audio.narrate("jogo_completo", PAN_CENTER, on_complete=self.on_campaign_end)
                else:
                    self.fase += 1
                    self._start_fase()

            self.status(f"FASE {self.fase} CONCLUÍDA!")
            self.audio.narrate("fim_de_jogo_vitoria", PAN_CENTER)
            self.audio.narrate("fase_concluida", PAN_CENTER, on_complete=prosseguir)
        else:
            self.game_over = True
            self.active = False
            self.status("VOCÊ PERDEU A CAMPANHA")
            self.audio.narrate("fim_de_jogo_derrota", PAN_CENTER)
            self.audio.narrate("derrota_campanha", PAN_CENTER, on_complete=self.on_campaign_end)

    # -------------------- IA DO DEALER --------------------

    def dealer_take_action(self):
        if self.game_over or not self.active:
            return
        self.dealer_action_count += 1
        if self.dealer_action_count > self.MAX_DEALER_ACTIONS:
            Clock.schedule_once(lambda dt: self.start_turn("player"), 0.5)
            return

        if not self.shells:
            self.reload_shells(on_complete=lambda: self.dealer_take_action())
            return

        dealer = self.dealer
        player = self.player
        _, dealer_skill, _ = self._dificuldade_fase()

        if dealer.hp < dealer.max_hp and dealer.items["cigarro"] > 0 and random.random() < dealer_skill:
            self.use_item(dealer, player, "cigarro")
            Clock.schedule_once(lambda dt: self.dealer_take_action(), 1.0)
            return

        if dealer.knows_shell is None and dealer.items["lupa"] > 0 and random.random() < dealer_skill:
            self.use_item(dealer, player, "lupa")
            Clock.schedule_once(lambda dt: self.dealer_take_action(), 1.0)
            return

        if (not player.handcuffed) and dealer.items["algemas"] > 0 and random.random() < (dealer_skill * 0.5):
            self.use_item(dealer, player, "algemas")
            Clock.schedule_once(lambda dt: self.dealer_take_action(), 1.0)
            return

        if dealer.knows_shell is not None:
            if dealer.knows_shell is False:
                aim_choice = "self"
            else:
                aim_choice = "dealer"
                if dealer.items["serra"] > 0 and random.random() < dealer_skill:
                    self.use_item(dealer, player, "serra")
        else:
            real_left = sum(1 for s in self.shells if s)
            total_left = len(self.shells)
            prob_real = real_left / total_left if total_left else 0.5
            bias = 0.15 + (dealer_skill * 0.1)
            aim_choice = "dealer" if random.random() < prob_real * 0.8 + bias else "self"

        Clock.schedule_once(lambda dt: self.fire_weapon(dealer, player, aim_choice), 0.6)


# ============================================================
#   WIDGET DE INTERFACE (Gestos de Toque)
# ============================================================

class GameWidget(Widget):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.audio = AudioManager()

        self.status_label = Label(
            text="Toque para começar",
            font_size="20sp",
            halign="center",
            valign="middle",
            size_hint=(1, 1),
        )
        self.status_label.bind(size=self._update_label)
        self.add_widget(self.status_label)

        self.mode = "menu"  # "menu" ou "playing"

        self.menu = MenuCore(
            self.audio,
            on_status_update=self._set_status,
            on_start_game=self._start_game,
            on_quit=self._quit_app,
        )

        self.core = GameCore(
            self.audio,
            on_status_update=self._set_status,
            on_campaign_end=self._back_to_menu,
        )

        self._touch_start = None
        self._last_tap_time = 0
        self._last_tap_pos = None
        self._started = False

    def _update_label(self, *args):
        self.status_label.text_size = self.status_label.size

    def _set_status(self, text):
        self.status_label.text = text

    def _start_game(self):
        self.mode = "playing"
        self.core.start_campaign()

    def _back_to_menu(self):
        self.mode = "menu"
        self.core.active = False
        Clock.schedule_once(lambda dt: self.menu.open(), 0.5)

    def _quit_app(self):
        App.get_running_app().stop()

    # -------------------- TOQUES / GESTOS --------------------

    def on_touch_down(self, touch):
        if not self._started:
            self._started = True
            self.menu.open()
            return True

        self._touch_start = (touch.x, touch.y)
        return True

    def on_touch_up(self, touch):
        if self._touch_start is None:
            return True

        import time
        now = time.time()

        sx, sy = self._touch_start
        ex, ey = touch.x, touch.y
        dx = ex - sx
        dy = ey - sy
        dist = math.hypot(dx, dy)

        # --- Detecta DUPLO TOQUE ---
        if dist < DOUBLE_TAP_DIST:
            if self._last_tap_pos is not None and (now - self._last_tap_time) <= DOUBLE_TAP_TIME:
                dpx = math.hypot(ex - self._last_tap_pos[0], ey - self._last_tap_pos[1])
                if dpx < DOUBLE_TAP_DIST:
                    self._on_confirm()
                    self._last_tap_time = 0
                    self._last_tap_pos = None
                    self._touch_start = None
                    return True
            self._last_tap_time = now
            self._last_tap_pos = (ex, ey)
            self._touch_start = None
            return True

        # --- Detecta SWIPE ---
        if abs(dx) > abs(dy):
            if abs(dx) >= SWIPE_THRESHOLD:
                if self.mode == "playing":
                    self.core.player_set_aim("dealer" if dx > 0 else "self")
        else:
            if abs(dy) >= SWIPE_THRESHOLD:
                direction = 1 if dy > 0 else -1
                if self.mode == "menu":
                    self.menu.cycle(direction)
                elif self.mode == "playing":
                    self.core.player_cycle_item(direction)

        self._touch_start = None
        return True

    def _on_confirm(self):
        if self.mode == "menu":
            self.menu.confirm()
        elif self.mode == "playing":
            self.core.player_confirm_action()


# ============================================================
#   APLICATIVO KIVY
# ============================================================

class AudioRouletteApp(App):
    def build(self):
        self.title = "Audio Roulette"
        Window.clearcolor = (0.05, 0.05, 0.05, 1)
        root = BoxLayout(orientation="vertical")
        root.add_widget(GameWidget())
        return root


if __name__ == "__main__":
    AudioRouletteApp().run()
