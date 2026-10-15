package ru.sortix.parkourbeat.digger;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Color;
import org.bukkit.Sound;
import ru.sortix.parkourbeat.utils.text.Theme;

/**
 * РАНГ УДАРА.
 * <p>
 * Ровно четыре исхода и никаких «рано/поздно» в тексте: игрок видит либо цифру
 * прибавки, либо промах. Цвета взяты из палитры плагина, теми же кодами, что и оценки
 * на обычных уровнях - чтобы жёлтый везде значил одно и то же. Отклонение в миллисекундах остаётся внутри судьи и идёт
 * только в автокалибровку.
 * <p>
 * Промах ставится в трёх случаях: руда сломана мимо окна, руда не сломана вообще,
 * и удар в пустоту при зажатой кнопке.
 */
@Getter
public enum DiggerHit {

    PERFECT(300, Theme.V_YELLOW + "&l+300", Color.fromRGB(0xFFD44A), Sound.BLOCK_NOTE_BLOCK_CHIME, 2.0f),
    GOOD(100, Theme.V_GREEN + "&l+100", Color.fromRGB(0x4AFF7A), Sound.BLOCK_NOTE_BLOCK_BELL, 1.6f),
    OK(50, Theme.V_GOLD + "&l+50", Color.fromRGB(0xFFF06A), Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f),
    MISS(0, Theme.V_RED + "&lМИСС", Color.fromRGB(0xFF3030), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.5f);

    /** Очки до умножения на руду и комбо. */
    private final int baseScore;
    private final @NonNull String display;
    private final @NonNull Color color;
    private final @NonNull Sound sound;
    private final float pitch;

    DiggerHit(int baseScore, @NonNull String display, @NonNull Color color, @NonNull Sound sound, float pitch) {
        this.baseScore = baseScore;
        this.display = display;
        this.color = color;
        this.sound = sound;
        this.pitch = pitch;
    }

    /**
     * ПОДПИСЬ, КОТОРАЯ НЕ ВРЁТ.
     * <p>
     * В перечислении «+300» было вписано строкой, а сами очки берутся из конфига. Стоит
     * кому-нибудь тронуть score_perfect - и над рудой продолжает всплывать «+300», пока
     * начисляется совсем другое число. Хуже того, при score_perfect = 0 всплывало
     * «+300», очков не прибавлялось вовсе, а точность падала в ноль: делить было не на
     * что. Именно так и выглядит «сломал ноту, +300, а ранг R».
     * <p>
     * Теперь подпись собирается из того же числа, которое реально начисляется.
     */
    @NonNull
    public String getDisplay() {
        if (this == MISS) return this.colorCode() + "&lПРОМАХ";
        return this.colorCode() + "&l+" + this.score();
    }

    /** Цветовой код ранга - тот же, что в жёстко заданной подписи. */
    @NonNull
    private String colorCode() {
        // Цвета берутся из ПАЛИТРЫ, а не кодами вроде &e. Палитра переводит их в
        // шестнадцатеричные значения темы, поэтому цвет остаётся тем же, каким его
        // задумали, и не переезжает вслед за ванильными кодами - именно из-за прямого
        // кода «+50» показывался белым вместо жёлтого.
        //
        // Идут по убыванию оценки и не повторяют цвета руд: подпись читается краем
        // глаза, и различать «идеально - хорошо - засчитано - мимо» надо одним взглядом.
        switch (this) {
            case PERFECT:
                return Theme.V_AQUA;
            case GOOD:
                return Theme.V_GREEN;
            case OK:
                return Theme.V_YELLOW;
            default:
                return Theme.V_RED;
        }
    }

    public boolean isHit() {
        return this != MISS;
    }

    /** Актуальные очки ранга: значения правятся в конфиге, поэтому берутся оттуда. */
    public int score() {
        switch (this) {
            case PERFECT:
                return DiggerTuning.SCORE_PERFECT;
            case GOOD:
                return DiggerTuning.SCORE_GOOD;
            case OK:
                return DiggerTuning.SCORE_OK;
            default:
                return 0;
        }
    }
}
