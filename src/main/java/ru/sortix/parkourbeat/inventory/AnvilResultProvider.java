package ru.sortix.parkourbeat.inventory;

import lombok.NonNull;
import org.bukkit.event.inventory.PrepareAnvilEvent;

/**
 * Меню на базе наковальни, которое само решает, что показать в слоте результата.
 * <p>
 * Ванильная наковальня оставляет слот результата пустым, если ничего не чинится и не
 * совмещается, - а значит, нажать в таком меню было бы не на что. Поэтому результат
 * собирается вручную на каждое нажатие клавиши.
 */
public interface AnvilResultProvider {
    void prepareAnvilResult(@NonNull PrepareAnvilEvent event);
}
