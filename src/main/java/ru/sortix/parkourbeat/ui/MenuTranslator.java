package ru.sortix.parkourbeat.ui;

import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перевод меню на английский «на лету».
 * <p>
 * В коде меню много русского текста прямо в строках (заголовки, кнопки, описания).
 * Чтобы не править сотни мест, перевод встроен в общий механизм меню (PluginInventory):
 * если у игрока английский, русские фразы в заголовке окна, названиях и описаниях
 * предметов заменяются по словарю. Словарь собран из самих строк кода, по фразам между
 * цветовыми кодами - поэтому переводятся и склеенные строки вида «Текущая: » + значение.
 * Фразы заменяются целиком (по границам слов), длинные - раньше коротких.
 * <p>
 * Украинский пока остаётся русским, как и было. Выключить: {@link #ENABLED}.
 */
public final class MenuTranslator {
    private MenuTranslator() {
    }

    public static volatile boolean ENABLED = true;

    private static final String[][] RU_EN = {
        {"Копия разрешена одна. Нажмите ещё раз, чтобы подтвердить.", "Only one copy is allowed. Click again to confirm."},
        {"Она приватная и без рейтинга. Открываю редактор копии...", "It is private and unrated. Opening the copy editor..."},
        {"Введите базовую высоту смерти в чат (целое число):", "Type the base death height in chat (whole number):"},
        {"Ресурспак не переносится: он привязан к оригиналу", "The resource pack is not copied: it belongs to the original"},
        {"Копируем уровень, это займёт несколько секунд...", "Copying the level, this will take a few seconds..."},
        {"можно будет включить в меню редактора если надо", "can be enabled in the editor menu if needed"},
        {"на разворотах, поэтому по умолчанию урона нет", "on turns, so there is no damage by default"},
        {"Синий путь - первый игрок, красный - второй", "Blue path - first player, red - second"},
        {"Мир можно сменить и потом, блоки останутся", "You can change the world later, blocks stay"},
        {"Пустота, каменная платформа и спавн на ней", "Void, a stone platform and a spawn on it"},
        {"Руду становится слышно, а не только видно.", "Ores can be heard, not only seen."},
        {"Двум трассам нужно место, чтобы разойтись", "Two tracks need room to split apart"},
        {"порталы, зоны, чекпоинты, трек и название", "portals, zones, checkpoints, track and name"},
        {"Строить можно во все стороны: по стенам,", "You can build in every direction: on walls,"},
        {"Карта на двоих: две трассы в одном мире", "A map for two: two tracks in one world"},
        {"На 360-уровне клиент сам снимает спринт", "On a 360 level the client drops sprint by itself"},
        {"Готовый список: рукописные, плакатные,", "Ready-made list: handwritten, poster,"},
        {"Дуэльная карта всегда 4 чанка в ширину", "A duel map is always 4 chunks wide"},
        {"ЛКМ - настроить, Shift + ПКМ - удалить", "LMB - configure, Shift + RMB - delete"},
        {"Нажмите, чтобы перейти к выбору режима", "Click to choose the mode"},
        {"Руны, порталы, кольца, коридоры, сцены", "Runes, portals, rings, corridors, scenes"},
        {"Там же руды, авторазметка и звук удара", "Also ores, auto-marking and hit sound"},
        {"Темп, скорость, кирка, размеры тоннеля", "Tempo, speed, pickaxe, tunnel size"},
        {"Это и есть копия: с неё копию не снять", "This is a copy: it can't be copied again"},
        {"Звездопады, кометы, сердца, лепестки", "Starfalls, comets, hearts, petals"},
        {"Копия будет приватной и без рейтинга", "The copy will be private and unrated"},
        {"Небо целиком, как на обычных уровнях", "Full sky, like on regular levels"},
        {"блоки, путь, световое шоу, маркеры,", "blocks, path, light show, markers,"},
        {"Нажмите ещё раз для создания копии", "Click again to create the copy"},
        {"Нажмите, чтобы открыть в редакторе", "Click to open in the editor"},
        {"Нажмите, чтобы перевернуть порядок", "Click to reverse the order"},
        {"Название можно будет сменить потом", "You can change the name later"},
        {"Пламя, взрывы, вспышки в такт биту", "Flames, explosions, flashes to the beat"},
        {"Включайте, если трасса рассчитана", "Enable it if the track is designed"},
        {"Затем нажмите на результат справа", "Then click the result on the right"},
        {"Копия с этого уровня уже создана.", "A copy of this level already exists."},
        {"Новый уровень со всем содержимым:", "A new level with all the content:"},
        {"Прыжки засчитываются только здесь", "Jumps only count here"},
        {"Сначала завершите тестовый забег.", "Finish the test run first."},
        {"Строить можно ровно в этих чанках", "You can build only in these chunks"},
        {"Что будет в руке на этом отрезке.", "What is held in hand on this segment."},
        {"Ширина площадки для строительства", "Width of the building area"},
        {"Двумерный уровень: кубик и линия", "Two-dimensional level: a cube and a line"},
        {"Дуэльные карты открываются после", "Duel maps unlock after"},
        {"Или пришлите свою прямую ссылку.", "Or send your own direct link."},
        {"Не удалось создать копию уровня.", "Couldn't create a copy of the level."},
        {"Обычный паркур по пути из частиц", "Classic parkour along a particle path"},
        {"Урон за отпущенный бег выключен,", "Damage for releasing sprint is off,"},
        {"Урон за отпущенный бег выключен.", "Damage for releasing sprint is off."},
        {"Площадка встанет высоко - на Y=", "The platform will be high - at Y="},
        {"Урон за отпущенный бег включён.", "Damage for releasing sprint is on."},
        {"пиксельные, с кириллицей и без.", "pixel ones, with and without Cyrillic."},
        {"Вернуться к тем, что уже стоят", "Back to the ones already placed"},
        {"Впишите название уровня сверху", "Type the level name at the top"},
        {"Кирка в руке на отрезке трека.", "Pickaxe in hand on a track segment."},
        {"Копия с этого уровня уже снята", "This level has already been copied"},
        {"Не удалось открыть наковальню.", "Couldn't open the anvil."},
        {"Небо, туман и общий вид уровня", "Sky, fog and the overall look"},
        {"Площадка встанет низко - на Y=", "The platform will be low - at Y="},
        {"Слова песни, названия, отсчёты", "Song lyrics, titles, countdowns"},
        {"Сменить можно в меню редактора", "Can be changed in the editor menu"},
        {"Нажмите, чтобы сменить размер", "Click to change the size"},
        {"Нажмите, чтобы сменить ширину", "Click to change the width"},
        {"пройденных уровней. Пройдено:", "completed levels. Completed:"},
        {"На скорость ломки не влияет.", "Doesn't affect breaking speed."},
        {"Нажмите, чтобы сменить режим", "Click to change the mode"},
        {"Нажмите, чтобы создать копию", "Click to create a copy"},
        {"Прямая ссылка на ttf или otf", "Direct link to a ttf or otf"},
        {"Нажмите, чтобы начать забег", "Click to start the run"},
        {"Нажмите, чтобы сменить небо", "Click to change the sky"},
        {"Нижняя половина неба тёмная", "The lower half of the sky is dark"},
        {"С копии копию снять нельзя.", "A copy can't be copied."},
        {"по потолку и под платформой", "on the ceiling and under the platform"},
        {"Вы побежите по этой трассе", "You will run this track"},
        {"Копия разрешена ровно одна", "Exactly one copy is allowed"},
        {"Любой цвет, но дороже всех", "Any color, but the most expensive"},
        {"Нажмите, чтобы переключить", "Click to toggle"},
        {"Нажмите, чтобы сменить мир", "Click to change the world"},
        {"Белые искры, основа всего", "White sparks, the basis of everything"},
        {"ЛКМ - выбрать и послушать", "LMB - select and listen"},
        {"Потом это уже не поменять", "Can't be changed later"},
        {"Руны из стола зачарований", "Enchanting table runes"},
        {"У каждой руды свой тембр.", "Each ore has its own tone."},
        {"Размер здесь не меняется", "The size can't be changed here"},
        {"Тестовый забег уже идёт.", "A test run is already in progress."},
        {"черновик, не отправлялся", "draft, not submitted"},
        {"Название должно быть от", "The name must be from"},
        {"Путей из частиц тут нет", "There are no particle paths here"},
        {"Нажмите, чтобы открыть", "Click to open"},
        {"Нажмите, чтобы создать", "Click to create"},
        {"Один звук на все руды.", "One sound for all ores."},
        {"Урон за отпущенный бег", "Damage for releasing sprint"},
        {"Shift + ПКМ - удалить", "Shift + RMB - delete"},
        {"text:ВПЕРЁД @ px:0.28", "text:FORWARD @ px:0.28"},
        {"Зелёные искры радости", "Green sparks of joy"},
        {"Некорректное название", "Invalid name"},
        {"Ширина всегда 4 чанка", "Always 4 chunks wide"},
        {"Настройки «Копателя»", "Digger settings"},
        {"Создать копию уровня", "Create a level copy"},
        {"блоков вокруг спавна", "blocks around the spawn"},
        {"Бирюзовый огонь душ", "Turquoise soul fire"},
        {"Праздничные блёстки", "Festive glitter"},
        {"пройденных уровней.", "completed levels."},
        {"- сбросить на авто", "- reset to auto"},
        {"За кого тестируем?", "Which side do we test?"},
        {"Фиолетовая ворожба", "Purple witchcraft"},
        {"на обычные правила", "for the regular rules"},
        {"проходит модерацию", "under moderation"},
        {"Кирки по таймкоду", "Pickaxes by timecode"},
        {"Назад в настройки", "Back to settings"},
        {"Назад на страницу", "Back to page"},
        {"(сначала старые)", "(oldest first)"},
        {"- задать вручную", "- set manually"},
        {"Кирка на отрезке", "Pickaxe on a segment"},
        {"Приватные уровни", "Private levels"},
        {"(сначала новые)", "(newest first)"},
        {"Дыхание дракона", "Dragon breath"},
        {"ЛКМ - настроить", "LMB - configure"},
        {"ЛКМ - следующая", "LMB - next"},
        {"Магический удар", "Magic hit"},
        {"Неверное число!", "Invalid number!"},
        {"Портальная пыль", "Portal dust"},
        {"Создать уровень", "Create level"},
        {"(по умолчанию)", "(default)"},
        {"Высота смерти:", "Death height:"},
        {"Звук попадания", "Hit sound"},
        {"Копия создана:", "Copy created:"},
        {"Лавовые брызги", "Lava splashes"},
        {"Магия и фигуры", "Magic and shapes"},
        {"Медленные души", "Slow souls"},
        {"Плотные облака", "Dense clouds"},
        {"Салютные искры", "Firework sparks"},
        {"блок не найден", "block not found"},
        {"Всего кьюсов:", "Total cues:"},
        {"Малый барабан", "Snare drum"},
        {"Новый уровень", "New level"},
        {"Огонь и удары", "Fire and hits"},
        {"Скачать шрифт", "Download font"},
        {"Точек на ней:", "Points on it:"},
        {"Зона падения", "Fall zone"},
        {"Небо и тепло", "Sky and warmth"},
        {"Нужно пройти", "You need to complete"},
        {"Резкие удары", "Sharp hits"},
        {"Светло-серый", "Light gray"},
        {"Тёплое пламя", "Warm flame"},
        {"Колокольчик", "Bell"},
        {"Своя ссылка", "Custom link"},
        {"Коричневый", "Brown"},
        {"Модерация:", "Moderation:"},
        {"Мои шрифты", "My fonts"},
        {"Наковальня", "Anvil"},
        {"По времени", "By time"},
        {"Сортировка", "Sorting"},
        {"Состояние:", "State:"},
        {"Фиолетовый", "Purple"},
        {"Бирюзовый", "Cyan"},
        {"Оранжевый", "Orange"},
        {"Пурпурный", "Magenta"},
        {"выключена", "off"},
        {"символов.", "characters."},
        {"Выключен", "Off"},
        {"Копатель", "Digger"},
        {"Лаймовый", "Lime"},
        {"Название", "Name"},
        {"Нужно от", "Requires"},
        {"Перезвон", "Chime"},
        {"Площадка", "Platform"},
        {"Показано", "Shown"},
        {"Сердечки", "Hearts"},
        {"Создать:", "Create:"},
        {"Трезубец", "Trident"},
        {"включена", "on"},
        {"пройдена", "completed"},
        {"символов", "characters"},
        {"Включён", "On"},
        {"Выбрано", "Selected"},
        {"Голубой", "Light blue"},
        {"Закрыть", "Close"},
        {"Зелёный", "Green"},
        {"Красный", "Red"},
        {"Надписи", "Text"},
        {"Начало:", "Start:"},
        {"Незерит", "Netherite"},
        {"Обычный", "Overworld"},
        {"По руде", "By ore"},
        {"Размер:", "Size:"},
        {"Розовый", "Pink"},
        {"Создан:", "Created:"},
        {"Тест за", "Test as"},
        {"уровней", "levels"},
        {"(авто)", "(auto)"},
        {"(своя)", "(custom)"},
        {"Автор:", "Author:"},
        {"Дальше", "Next"},
        {"Жёлтый", "Yellow"},
        {"Камень", "Stone"},
        {"Кирка:", "Pickaxe:"},
        {"Конец:", "End:"},
        {"Подбор", "Pickup"},
        {"Режим:", "Mode:"},
        {"Стекло", "Glass"},
        {"Чёрный", "Black"},
        {"Щелчок", "Click"},
        {"чанков", "chunks"},
        {"Белый", "White"},
        {"Бочка", "Barrel"},
        {"Взмах", "Swing"},
        {"Взрыв", "Explosion"},
        {"Дуэль", "Duel"},
        {"Назад", "Back"},
        {"Небо:", "Sky:"},
        {"Плинк", "Plink"},
        {"Серый", "Gray"},
        {"Синий", "Blue"},
        {"чанка", "chunks"},
        {"Край", "The End"},
        {"Крит", "Crit"},
        {"Мир:", "World:"},
        {"Ноты", "Notes"},
        {"Снег", "Snow"},
        {"Удар", "Hit"},
        {"чанк", "chunk"},
        {"Бас", "Bass"},
        {"Дым", "Smoke"},
        {"ЛКМ", "LMB"},
        {"ПКМ", "RMB"},
        {"Ад", "Nether"},
        {"От", "From"},
        {"до", "to"},
        {"из", "of"},
        {"ч.", "ch."}
    };

    private static final Pattern[] PATTERNS = new Pattern[RU_EN.length];

    static {
        for (int i = 0; i < RU_EN.length; i++) {
            PATTERNS[i] = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(RU_EN[i][0]) + "(?![\\p{L}])");
        }
    }

    private static final Pattern CYRILLIC = Pattern.compile("[\u0400-\u04FF]");

    private static boolean english(@Nullable String lang) {
        return ENABLED && Glyphs.localeSet(lang) == 1;
    }

    @NonNull
    public static String translate(@NonNull String text) {
        if (!CYRILLIC.matcher(text).find()) return text;
        String out = replace(text, 0);
        // Осталась кириллица - значит, в строке есть текст не из словаря (например, из
        // lang.yml без английского). Тогда короткие слова («до», «из») не трогаем, чтобы
        // не получилось «дождь to утра»: переводим только фразы длиннее 3 символов.
        if (CYRILLIC.matcher(out).find()) out = replace(text, 4);
        return out;
    }

    @NonNull
    private static String replace(@NonNull String text, int minLength) {
        String out = text;
        for (int i = 0; i < RU_EN.length; i++) {
            if (RU_EN[i][0].length() < minLength || !out.contains(RU_EN[i][0])) continue;
            out = PATTERNS[i].matcher(out).replaceAll(Matcher.quoteReplacement(RU_EN[i][1]));
        }
        return out;
    }

    @NonNull
    public static Component translate(@NonNull Component component, @Nullable String lang) {
        return english(lang) ? walk(component) : component;
    }

    @NonNull
    private static Component walk(@NonNull Component component) {
        Component result = component;
        if (component instanceof TextComponent) {
            String content = ((TextComponent) component).content();
            String translated = translate(content);
            if (!translated.equals(content)) result = ((TextComponent) component).content(translated);
        }
        List<Component> children = component.children();
        if (!children.isEmpty()) {
            List<Component> mapped = new ArrayList<>(children.size());
            for (Component child : children) mapped.add(walk(child));
            result = result.children(mapped);
        }
        return result;
    }

    /** Предмет меню: название и описание. Исходный предмет не меняется (копия). */
    @Nullable
    public static ItemStack translate(@Nullable ItemStack stack, @Nullable String lang) {
        if (stack == null || !english(lang) || !stack.hasItemMeta()) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || (!meta.hasDisplayName() && !meta.hasLore())) return stack;
        ItemStack copy = stack.clone();
        ItemMeta copyMeta = copy.getItemMeta();
        if (copyMeta.hasDisplayName() && copyMeta.displayName() != null) copyMeta.displayName(walk(copyMeta.displayName()));
        if (copyMeta.hasLore() && copyMeta.lore() != null) {
            List<Component> lore = new ArrayList<>();
            for (Component line : copyMeta.lore()) lore.add(walk(line));
            copyMeta.lore(lore);
        }
        copy.setItemMeta(copyMeta);
        return copy;
    }
}
