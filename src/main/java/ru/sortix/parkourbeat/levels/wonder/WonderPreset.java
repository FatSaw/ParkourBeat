package ru.sortix.parkourbeat.levels.wonder;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Material;

/** Готовый эффект из библиотеки: строитель выбирает его в два клика и уже видит результат. */
@Getter
public class WonderPreset {
    private final @NonNull String id;
    private final @NonNull WonderCategory category;
    private final @NonNull Material icon;
    private final @NonNull String spec;
    private final @NonNull String params;
    private final int durationMillis;
    private final @NonNull WonderAnchor anchor;

    /** Название пресета на языке зрителя. */
    @NonNull
    public String getDisplay(String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "wonder.preset." + this.id + ".name");
    }

    /** Короткая подсказка о том, как эффект выглядит. */
    @NonNull
    public String getHint(String locale) {
        return ru.sortix.parkourbeat.utils.lang.Lang.raw(locale, "wonder.preset." + this.id + ".hint");
    }

    WonderPreset(@NonNull String id,
                 @NonNull WonderCategory category,
                 @NonNull Material icon,
                 @NonNull String spec,
                 @NonNull String params,
                 int durationMillis,
                 @NonNull WonderAnchor anchor
    ) {
        this.id = id;
        this.category = category;
        this.icon = icon;
        this.spec = spec;
        this.params = params;
        this.durationMillis = durationMillis;
        this.anchor = anchor;
    }

    @NonNull
    public WonderEffect toEffect(int startMillis) {
        WonderEffect effect = new WonderEffect(
            startMillis, startMillis + this.durationMillis, this.id, this.spec, this.params, this.anchor);
        if (this.anchor == WonderAnchor.PATH) {
            // Ноль блоков означал «прямо под ногами»: разглядеть такое на бегу невозможно
            effect.setDistance(16.0D);
            effect.setHeight(4.0D);
        } else if (this.anchor == WonderAnchor.OVERHEAD) {
            effect.setDistance(12.0D);
            effect.setHeight(7.0D);
        } else if (this.anchor == WonderAnchor.FOLLOW) {
            effect.setDistance(0.0D);
            effect.setHeight(1.0D);
        }
        if (this.id.equals("text_fly")) {
            effect.setDistance(34.0D);
            effect.setApproach(20.0D);
            effect.setHeight(5.0D);
        }
        if (this.id.equals("text_flyby") || this.id.equals("text_approach")) {
            effect.setDistance(40.0D);
            effect.setApproach(28.0D);
            effect.setHeight(5.0D);
        }
        return effect;
    }
}
