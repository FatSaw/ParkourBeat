package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;

/**
 * СЧЁТ ЗАБЕГА.
 * <p>
 * Очки = ранг × руда × комбо. Ранг даёт точность, руда - редкость ноты, комбо -
 * награда за то, что человек не сорвался. Множитель комбо растёт ступенями и
 * обнуляется на промахе: именно он делает срыв в середине дропа болезненным.
 */
@Getter
public class DiggerScore {

    private long score = 0L;

    private int combo = 0;
    private int maxCombo = 0;

    private int perfect = 0;
    private int good = 0;
    private int ok = 0;
    private int missed = 0;

    /** Удары в пустоту: не влияют на точность, но рушат комбо. */
    private int wastedSwings = 0;

    /** Сколько нот всего на карте - нужно для точности. */
    private final int totalNotes;

    public DiggerScore(int totalNotes) {
        this.totalNotes = Math.max(0, totalNotes);
    }

    /**
     * Текущий множитель комбо.
     * <p>
     * Удвоением (1, 2, 4, 8) или по единице (1, 2, 3, 4, 5) - зависит от
     * {@link DiggerTuning#COMBO_MULTIPLIER_MODE}. В обоих случаях ступень берётся
     * каждые {@link DiggerTuning#COMBO_STEP} попаданий подряд.
     */
    public int multiplier() {
        int step = Math.max(1, DiggerTuning.COMBO_STEP);
        int level = this.combo / step;

        if ("LINEAR".equals(DiggerTuning.COMBO_MULTIPLIER_MODE)) {
            return Math.min(1 + level, DiggerTuning.COMBO_MAX_MULTIPLIER);
        }

        int multiplier = 1;
        for (int i = 0; i < level; i++) {
            multiplier *= 2;
            if (multiplier >= DiggerTuning.COMBO_MAX_MULTIPLIER) break;
        }
        return Math.min(multiplier, DiggerTuning.COMBO_MAX_MULTIPLIER);
    }

    /** Сколько нот осталось до следующей ступени множителя. 0 - множитель уже максимальный. */
    public int toNextMultiplier() {
        if (this.multiplier() >= DiggerTuning.COMBO_MAX_MULTIPLIER) return 0;
        int step = Math.max(1, DiggerTuning.COMBO_STEP);
        return step - (this.combo % step);
    }

    /**
     * Засчитать удар.
     *
     * @return сколько очков реально начислено
     */
    public long apply(@NonNull DiggerHit hit, @NonNull DiggerOre ore) {
        if (!hit.isHit()) {
            this.registerMiss();
            return 0L;
        }

        int multiplier = this.multiplier();
        long gained = Math.round(hit.score() * ore.getScoreMultiplier() * multiplier);
        this.score += gained;

        this.combo++;
        if (this.combo > this.maxCombo) this.maxCombo = this.combo;

        switch (hit) {
            case PERFECT:
                this.perfect++;
                break;
            case GOOD:
                this.good++;
                break;
            default:
                this.ok++;
                break;
        }
        return gained;
    }

    /** Промах по ноте: она либо сломана мимо окна, либо не сломана вовсе. */
    public void registerMiss() {
        this.missed++;
        this.breakCombo();
    }

    /** Удар в пустоту. Точность не портит - она считается по нотам, а комбо рушит. */
    public void registerWastedSwing() {
        this.wastedSwings++;
        this.breakCombo();
    }

    private void breakCombo() {
        if (DiggerTuning.COMBO_RESET_ON_MISS) this.combo = 0;
        else this.combo /= 2;
    }

    public int judgedNotes() {
        return this.perfect + this.good + this.ok + this.missed;
    }

    /**
     * Точность в процентах: сумма набранных рангов от максимально возможной.
     * <p>
     * Пока ни одной ноты не разобрано - сто процентов, а не ноль. Иначе забег
     * начинается с оценки «провал» и первые секунды выглядят так, будто игрок уже всё
     * испортил, хотя он ещё ничего не сделал.
     */
    public double accuracy() {
        int judged = this.judgedNotes();
        if (judged == 0) return 100.0D;
        // ТОЧНОСТЬ СЧИТАЕТСЯ ПО СВОИМ ВЕСАМ, А НЕ ПО ОЧКАМ.
        //
        // Раньше здесь стояли те же числа, что и в начислении: 300, 100, 50. Но у очков
        // и у точности разные задачи. Очки обязаны сильно разводить идеальные попадания
        // и средние - иначе нет смысла целиться. Точность же отвечает на другой вопрос:
        // насколько чисто пройдена карта. И по этому счёту «чуть неточно» - это почти
        // попадание, а не треть попадания.
        //
        // На старых весах одно «хорошо» среди четырёх идеальных давало 86% и роняло
        // ранг с SS до A - при том что человек не промахнулся ни разу. На новых то же
        // самое даёт 98%, а до A придётся действительно намазать половину карты.
        double gained = this.perfect * 1.0D
            + this.good * DiggerTuning.ACCURACY_GOOD_WEIGHT
            + this.ok * DiggerTuning.ACCURACY_OK_WEIGHT;
        double max = judged;

        // ДЕЛИТЬ НЕ НА ЧТО - ЭТО НЕ ПРОВАЛ.
        //
        // Раньше здесь возвращался ноль, а ноль процентов - это ранг R. Достаточно было
        // выставить score_perfect в ноль, чтобы забег показывал R сразу после первого
        // же попадания, при том что попадание было идеальным. Оценивать нечем - значит
        // и претензий к игроку нет.
        if (max <= 0.0D) return 100.0D;
        return gained / max * 100.0D;
    }

    /**
     * Оценка забега.
     * <p>
     * Та же шкала и те же цвета, что на обычных уровнях: SS, S, A, B, C, D, R. Своя
     * буквенная лесенка тут была бы просто ещё одной, которую игроку надо запоминать
     * отдельно.
     */
    @NonNull
    public ru.sortix.parkourbeat.rating.AccuracyGrade grade() {
        ru.sortix.parkourbeat.rating.AccuracyGrade grade =
            ru.sortix.parkourbeat.rating.AccuracyGrade.byAccuracy(this.accuracy());

        // SS - ЭТО ИДЕАЛЬНОЕ ПРОХОЖДЕНИЕ, И НИЧЕГО КРОМЕ.
        //
        // По одним процентам SS возвращался обратно: стоило набрать достаточно идеальных
        // попаданий после неточности, и среднее снова переползало порог. Но «идеально»
        // на то и идеально - если человек однажды промазал мимо серединки окна, забег
        // уже не безупречен, и сколько бы он ни доигрывал чисто, этого не отменить.
        //
        // Поэтому одна-единственная неточность НАВСЕГДА опускает потолок до S. Проценты
        // при этом продолжают работать как раньше и внутри S тоже что-то значат.
        if (grade == ru.sortix.parkourbeat.rating.AccuracyGrade.SS && !this.isFlawless()) {
            return ru.sortix.parkourbeat.rating.AccuracyGrade.S;
        }
        return grade;
    }

    /**
     * Безупречен ли забег: все засчитанные удары идеальные и ни одного промаха.
     * <p>
     * Именно все, а не «почти все». Порога здесь нет намеренно - как только он
     * появляется, появляется и вопрос, почему он именно такой.
     */
    public boolean isFlawless() {
        return this.good == 0 && this.ok == 0 && this.missed == 0 && this.perfect > 0;
    }

    /** Буква оценки без цвета. */
    @NonNull
    public String rank() {
        return this.grade().getLetter();
    }

    /** Прошёл ли забег без единого промаха. */
    public boolean isFullCombo() {
        return this.missed == 0 && this.wastedSwings == 0 && this.judgedNotes() > 0;
    }
}
