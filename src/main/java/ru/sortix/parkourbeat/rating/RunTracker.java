package ru.sortix.parkourbeat.rating;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public class RunTracker {
    private @NonNull ModifierSet modifiers;

    private int score;
    /** Очки без множителя модификаторов — для честного сравнения (п.1 ТЗ). */
    private int rawScore;
    private int combo;
    private int maxCombo;

    private int perfectCount;
    private int goodCount;
    private int okCount;
    private int missCount;

    /**
     * Сколько раз игрок оборвал комбо, не промахнувшись по кольцу, - то есть отпустил Ctrl.
     * <p>
     * Считается отдельно от промахов: промах - это ошибка в прыжке, а обрыв комбо -
     * ошибка в беге. Но на оценку влияют оба, иначе отпущенный бег не стоил бы ничего.
     */
    private int comboBreaks;

    public RunTracker(@NonNull ModifierSet modifiers) {
        this.modifiers = modifiers.copy();
    }

    public int registerJump(@NonNull JumpResult result) {
        int gained = 0;
        if (result.isHit()) {
            gained = ScoreCalculator.award(result, this.combo);
            if (!this.modifiers.isActive(Modifier.PRACTICE)) {
                double multiplier = this.modifiers.getTotalMultiplier();
                int finalGained = (int) Math.round(gained * multiplier);
                this.score += finalGained;
                this.rawScore += gained;
            }
            this.combo++;
            if (this.combo > this.maxCombo) this.maxCombo = this.combo;
        } else {
            this.combo = 0;
        }

        switch (result) {
            case PERFECT: this.perfectCount++; break;
            case GOOD:    this.goodCount++; break;
            case OK:      this.okCount++; break;
            case MISS:    this.missCount++; break;
        }
        return gained;
    }

    public int getTotalJudged() {
        return this.perfectCount + this.goodCount + this.okCount + this.missCount;
    }

    /**
     * Точность по формуле osu!: (300*p + 100*g + 50*o) / (300 * total).
     * <p>
     * Раньше в числитель и знаменатель добавлялись два «бесплатных» +300
     * (старт и финиш). На коротких уровнях это давало заметную прибавку и
     * помогало вытягивать процент обратно наверх после ошибок — убрано.
     */
    public double getAccuracy() {
        // ОБРЫВ КОМБО СЧИТАЕТСЯ КАК ПУСТОЕ СУЖДЕНИЕ.
        //
        // Он попадает в знаменатель, но не в числитель - ровно как промах. Иначе
        // получалось странное: игрок отпускал бег, терял комбо и здоровье, а точность
        // оставалась стопроцентной, и забег закрывался на SS.
        int total = this.getTotalJudged() + this.comboBreaks;
        if (total <= 0) return 100.0D;
        double earned = 300.0D * this.perfectCount + 100.0D * this.goodCount + 50.0D * this.okCount;
        double max = 300.0D * total;
        return clamp(earned / max * 100.0D);
    }

    public double getDisplayAccuracy(double movementAccuracy01) {
        return this.getAccuracy();
    }

    @NonNull
    public AccuracyGrade getGrade() {
        return this.gradeFor(this.getAccuracy());
    }

    @NonNull
    public AccuracyGrade getGrade(double movementAccuracy01) {
        return this.getGrade();
    }

    /**
     * Оценка для произвольного значения точности (например, для смешанной
     * «точность прыжков + точность движения» из Game). Жёсткие потолки за
     * промахи и не-идеальные попадания применяются в любом случае.
     */
    @NonNull
    public AccuracyGrade gradeFor(double accuracyPercent) {
        return AccuracyGrade.evaluate(this.perfectCount, this.goodCount, this.okCount,
            this.missCount, this.comboBreaks, accuracyPercent);
    }

    /** Максимально достижимая оценка при текущем количестве ошибок. */
    @NonNull
    public AccuracyGrade getGradeCap() {
        return AccuracyGrade.hardCap(this.goodCount, this.okCount, this.missCount, this.comboBreaks);
    }

    /**
     * Сбросить текущее комбо. Максимум не трогается, промах не засчитывается.
     * Используется, когда игрок отпустил Ctrl: бежать перестал — комбо обнуляется.
     * <p>
     * Сам обрыв при этом запоминается и бьёт по точности и по потолку оценки: без
     * этого отпущенный бег не стоил игроку ничего, кроме полоски комбо, и забег с ним
     * закрывался на ту же SS, что и идеальный.
     */
    public void resetCombo() {
        // Комбо и так на нуле - рвать нечего. Иначе повторное отпускание Ctrl на месте
        // накручивало бы штрафы на ровном месте.
        if (this.combo > 0) this.comboBreaks++;
        this.combo = 0;
    }

    /** Итоговый множитель модификаторов забега. */
    public double getMultiplier() {
        return this.modifiers.getTotalMultiplier();
    }

    /**
     * Слепок забега на момент взятия чекпоинта.
     * <p>
     * Сохраняются не только очки и комбо, но и счётчики попаданий: иначе после отката
     * очки вернулись бы, а промах, который игрока и убил, продолжал бы висеть в
     * точности. Откат на чекпоинт — это возврат к состоянию, а не частичная амнистия.
     */
    public record Snapshot(int score, int rawScore, int combo, int maxCombo,
                           int perfectCount, int goodCount, int okCount, int missCount,
                           int comboBreaks) {
    }

    @NonNull
    public Snapshot snapshot() {
        return new Snapshot(this.score, this.rawScore, this.combo, this.maxCombo,
            this.perfectCount, this.goodCount, this.okCount, this.missCount, this.comboBreaks);
    }

    public void restore(@NonNull Snapshot snapshot) {
        this.score = snapshot.score();
        this.rawScore = snapshot.rawScore();
        this.combo = snapshot.combo();
        this.maxCombo = snapshot.maxCombo();
        this.perfectCount = snapshot.perfectCount();
        this.goodCount = snapshot.goodCount();
        this.okCount = snapshot.okCount();
        this.missCount = snapshot.missCount();
        this.comboBreaks = snapshot.comboBreaks();
    }

    public void reset() {
        this.score = 0;
        this.rawScore = 0;
        this.combo = 0;
        this.maxCombo = 0;
        this.perfectCount = 0;
        this.goodCount = 0;
        this.okCount = 0;
        this.missCount = 0;
        this.comboBreaks = 0;
    }

    private static double clamp(double value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }
}
