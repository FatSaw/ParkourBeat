package ru.sortix.parkourbeat.inventory.type.editor;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.type.EditActivity;
import ru.sortix.parkourbeat.inventory.ParkourBeatInventory;
import ru.sortix.parkourbeat.item.ItemUtils;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;
import ru.sortix.parkourbeat.player.input.PlayersInputManager;
import ru.sortix.parkourbeat.ui.Glyphs;
import ru.sortix.parkourbeat.ui.Icons;
import ru.sortix.parkourbeat.ui.UiSounds;
import ru.sortix.parkourbeat.ui.lyrics.LyricCue;
import ru.sortix.parkourbeat.ui.lyrics.LyricsParser;
import ru.sortix.parkourbeat.ui.lyrics.LyricsPlayer;
import ru.sortix.parkourbeat.utils.text.PbText;

import java.util.ArrayList;
import java.util.List;

/**
 * ШОУ ИЗ СУБТИТРОВ (редактор).
 * <pre>
 *   ФАЙЛ   [загрузить по ссылке][что загружено][удалить]
 *   СТИЛЬ  [POP][SLIDE][RISE][ZOOM][NEON]
 *   [<-]
 * </pre>
 * Субтитры хранятся в настройках уровня (lyrics.cues / lyrics.style).
 */
public class LyricsMenu extends ParkourBeatInventory {
    private static final String[][] STYLE_TEXT = {
        {"Вспышка", "Pop", "Спалах", "Слово вспыхивает и раздувается", "The word flashes and grows", "Слово спалахує і роздувається"},
        {"Влёт сбоку", "Slide", "Виліт збоку", "Влетает сбоку к центру", "Slides in from the side", "Влітає збоку до центру"},
        {"Подъём", "Rise", "Підйом", "Поднимается снизу", "Rises from below", "Піднімається знизу"},
        {"В лицо", "Zoom", "В обличчя", "Летит издалека прямо в лицо", "Flies from far right into your face", "Летить здалеку прямо в обличчя"},
        {"Неон", "Neon", "Неон", "Неоновая надпись с пульсом", "Neon sign with a pulse", "Неоновий напис із пульсом"},
    };
    private final EditActivity activity;
    private final GameSettings settings;
    private boolean deleteArmed = false;

    public LyricsMenu(@NonNull ParkourBeat plugin, String lang, @NonNull EditActivity activity) {
        super(plugin, 6, lang, Glyphs.menuTitle((char) (0xE570 + Glyphs.localeSet(lang)),
            Icons.Icon.CHAT.component().append(Component.text(" "))
                .append(PbText.of("&f" + t(lang, "Шоу из субтитров", "Lyrics show", "Шоу із субтитрів")))));
        this.activity = activity;
        this.settings = activity.getLevel().getLevelSettings().getGameSettings();
        this.render();
    }

    private void render() {
        this.clearInventory();
        List<LyricCue> cues = cues(this.settings);

        this.setItem(13, this.item(8615, false, "<i:arrow_right> &f" + t(this.lang, "Загрузить субтитры", "Upload lyrics", "Завантажити субтитри"), List.of(
            "&7" + t(this.lang, "Пришлите в чат ссылку на трек Spotify", "Send a Spotify track link", "Надішліть у чат посилання на трек Spotify"),
            "&7" + t(this.lang, "или прямую ссылку на файл субтитров.", "or a direct link to a lyrics file.", "або пряме посилання на файл субтитрів."),
            "&8" + t(this.lang, "Залить файл: tmpfiles.org", "Upload a file: tmpfiles.org", "Залити файл: tmpfiles.org"),
            "",
            "<i:warn> &e" + t(this.lang, "Таймкоды должны быть ТОЧНЫМИ:", "Timecodes must be EXACT:", "Таймкоди мають бути ТОЧНИМИ:"),
            "&7" + t(this.lang, "в ритме важна каждая миллисекунда.", "every millisecond matters in rhythm.", "у ритмі важлива кожна мілісекунда."),
            "<i:star> &f" + t(this.lang, "Лучше по 1-2 слова на строку -", "Best with 1-2 words per line -", "Краще по 1-2 слова на рядок -"),
            "&7" + t(this.lang, "так шоу намного динамичнее.", "the show feels much more dynamic.", "так шоу набагато динамічніше."),
            "",
            "&8" + t(this.lang, "Форматы: LRC, SRT, VTT, ASS", "Formats: LRC, SRT, VTT, ASS", "Формати: LRC, SRT, VTT, ASS"),
            "",
            "<i:lmb> &7" + t(this.lang, "Нажмите, чтобы загрузить", "Click to upload", "Натисніть, щоб завантажити"))),
            e -> this.requestUrl(e.getPlayer()));

        List<String> info = new ArrayList<>();
        if (cues.isEmpty()) {
            info.add("&8" + t(this.lang, "Субтитры не загружены", "No lyrics uploaded", "Субтитри не завантажені"));
        } else {
            info.add("&7" + t(this.lang, "Фраз: ", "Lines: ", "Фраз: ") + "&f" + cues.size());
            info.add("&7" + t(this.lang, "С ", "From ", "З ") + "&f" + LyricsParser.format(cues.get(0).start)
                + " &7" + t(this.lang, "до ", "to ", "до ") + "&f" + LyricsParser.format(cues.get(cues.size() - 1).end));
            info.add("");
            for (int i = 0; i < Math.min(6, cues.size()); i++) {
                info.add("&8" + LyricsParser.format(cues.get(i).start) + " &f" + cues.get(i).text);
            }
            if (cues.size() > 6) info.add("&8...");
        }
        this.setItem(14, this.item(Icons.Button.BOOK.modelData(Icons.ButtonState.NORMAL), false,
            "<i:info> &f" + t(this.lang, "Что загружено", "Loaded", "Що завантажено"), info), null);

        this.setItem(15, this.item(Icons.Button.TRASH.modelData(Icons.ButtonState.NORMAL), this.deleteArmed,
            "&c" + t(this.lang, "Удалить субтитры", "Delete lyrics", "Видалити субтитри"),
            List.of(this.deleteArmed ? "&c" + t(this.lang, "Нажмите ещё раз для удаления", "Click again to delete", "Натисніть ще раз для видалення")
                : "<i:lmb> &7" + t(this.lang, "Нажмите, чтобы удалить", "Click to delete", "Натисніть, щоб видалити"))),
            cues.isEmpty() ? null : e -> {
                if (!this.deleteArmed) {
                    this.deleteArmed = true;
                    UiSounds.deny(e.getPlayer());
                    this.render();
                    return;
                }
                this.settings.setLyricsCues(new ArrayList<>());
                this.plugin.get(LevelsManager.class).saveGameSettings(this.settings);
                this.deleteArmed = false;
                UiSounds.click(e.getPlayer());
                this.render();
            });

        int current = this.settings.getLyricsStyle();
        for (int i = 0; i < 5; i++) {
            int style = i;
            boolean on = style == current;
            this.setItem(31 + i, this.item(8600 + i * 3, on, (on ? "<i:check> " : "") + "&f" + t(this.lang, STYLE_TEXT[i][0], STYLE_TEXT[i][1], STYLE_TEXT[i][2]),
                List.of("&7" + t(this.lang, STYLE_TEXT[i][3], STYLE_TEXT[i][4], STYLE_TEXT[i][5]), "",
                    on ? "<i:check> &a" + t(this.lang, "Выбрано", "Selected", "Обрано")
                        : "<i:lmb> &7" + t(this.lang, "Нажмите, чтобы выбрать", "Click to select", "Натисніть, щоб обрати"))),
                e -> {
                    this.settings.setLyricsStyle(style);
                    this.plugin.get(LevelsManager.class).saveGameSettings(this.settings);
                    UiSounds.toggle(e.getPlayer());
                    this.render();
                });
        }

        String[][] reveals = {
            {"Сразу", "Instant", "Одразу", "Фраза появляется целиком", "The whole line appears at once", "Фраза з'являється цілком"},
            {"По словам", "By words", "По словах", "Слова появляются по одному", "Words appear one by one", "Слова з'являються по одному"},
            {"По буквам", "Typing", "По літерах", "Печатается по букве - для длинных строк", "Typed letter by letter - for long lines", "Друкується по літері - для довгих рядків"}};
        int reveal = this.settings.getLyricsReveal();
        for (int i = 0; i < 3; i++) {
            int mode = i;
            boolean on = mode == reveal;
            this.setItem(40 + i, this.item(8618 + i * 3, on, (on ? "<i:check> " : "") + "&f" + t(this.lang, reveals[i][0], reveals[i][1], reveals[i][2]),
                List.of("&7" + t(this.lang, reveals[i][3], reveals[i][4], reveals[i][5]), "",
                    on ? "<i:check> &a" + t(this.lang, "Выбрано", "Selected", "Обрано")
                        : "<i:lmb> &7" + t(this.lang, "Нажмите, чтобы выбрать", "Click to select", "Натисніть, щоб обрати"))),
                e -> {
                    this.settings.setLyricsReveal(mode);
                    this.plugin.get(LevelsManager.class).saveGameSettings(this.settings);
                    UiSounds.toggle(e.getPlayer());
                    this.render();
                });
        }

        this.setItem(45, ItemUtils.modifyMeta(Icons.Button.ARROW_LEFT.item(Icons.ButtonState.NORMAL),
                m -> m.displayName(PbText.item("&f" + t(this.lang, "Назад", "Back", "Назад")))),
            e -> new EditorMainMenu(this.plugin, this.lang, this.activity).open(e.getPlayer()));
    }

    private void requestUrl(@NonNull Player player) {
        player.closeInventory();
        PlayersInputManager manager = this.plugin.get(PlayersInputManager.class);
        if (manager.isInputRequested(player)) return;
        player.sendMessage(PbText.of("<i:chat> &f" + t(this.lang,
            "Пришлите ссылку на трек Spotify или ссылку на файл субтитров (LRC, SRT, VTT, ASS).",
            "Send a Spotify track link or a link to a lyrics file (LRC, SRT, VTT, ASS).",
            "Надішліть посилання на трек Spotify або на файл субтитрів (LRC, SRT, VTT, ASS).")));
        player.sendMessage(PbText.of("<i:globe> &7" + t(this.lang,
            "Залить файл и получить ссылку быстрее всего на &fhttps://tmpfiles.org",
            "The quickest way to upload a file and get a link: &fhttps://tmpfiles.org",
            "Залити файл і отримати посилання найшвидше на &fhttps://tmpfiles.org")));
        player.sendMessage(PbText.of("<i:warn> &e" + t(this.lang,
            "Таймкоды должны быть точными до миллисекунды, лучше по 1-2 слова на строку.",
            "Timecodes must be exact to the millisecond, best with 1-2 words per line.",
            "Таймкоди мають бути точними до мілісекунди, краще по 1-2 слова на рядок.")));
        manager.requestChatInput(player, 20 * 60).thenAccept(message -> {
            if (message == null) return;
            String url = message.trim();
            this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
                List<LyricCue> parsed;
                String error = null;
                try {
                    parsed = LyricsParser.parse(LyricsParser.fetch(url));
                    if (parsed.isEmpty()) error = t(this.lang, "в файле не найдено ни одной строки с таймкодом",
                        "no timed lines found in the file", "у файлі не знайдено жодного рядка з таймкодом");
                } catch (Throwable t) {
                    parsed = new ArrayList<>();
                    error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                }
                List<LyricCue> result = parsed;
                String failure = error;
                this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                    if (failure != null) {
                        player.sendMessage(PbText.of("<i:cross> &c" + t(this.lang, "Не удалось загрузить: ", "Upload failed: ", "Не вдалося завантажити: ") + "&7" + failure));
                        UiSounds.deny(player);
                        return;
                    }
                    List<String> stored = new ArrayList<>();
                    for (LyricCue cue : result) stored.add(cue.serialize());
                    this.settings.setLyricsCues(stored);
                    this.plugin.get(LevelsManager.class).saveGameSettings(this.settings);
                    player.sendMessage(PbText.of("<i:check> &a" + t(this.lang, "Субтитры загружены: ", "Lyrics uploaded: ", "Субтитри завантажені: ")
                        + "&f" + result.size() + " " + t(this.lang, "фраз", "lines", "фраз")));
                    UiSounds.play(player, UiSounds.Kind.OPEN, 0.7F);
                    new LyricsMenu(this.plugin, this.lang, this.activity).open(player);
                });
            });
        });
    }

    /** Субтитры уровня из настроек. */
    @NonNull
    public static List<LyricCue> cues(@NonNull GameSettings settings) {
        List<LyricCue> out = new ArrayList<>();
        for (String line : settings.getLyricsCues()) {
            LyricCue cue = LyricCue.deserialize(line);
            if (cue != null) out.add(cue);
        }
        return out;
    }

    @NonNull
    private ItemStack item(int modelData, boolean selected, @NonNull String name, @NonNull List<String> lore) {
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(modelData + (selected ? 1 : 0));
        meta.displayName(PbText.item(name));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(l.isEmpty() ? Component.empty() : PbText.item(l));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    @NonNull
    private static String t(String lang, @NonNull String ru, @NonNull String en, @NonNull String uk) {
        int set = Glyphs.localeSet(lang);
        return set == 1 ? en : set == 2 ? uk : ru;
    }

    static {
        // чтобы стиль по индексу всегда был валиден
        if (LyricsPlayer.Style.values().length != STYLE_TEXT.length) throw new IllegalStateException("lyrics styles");
    }
}
