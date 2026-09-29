package ru.sortix.parkourbeat.levels.wonder;

import lombok.NonNull;
import org.bukkit.Material;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Библиотека готовых эффектов.
 * <p>
 * Каждый пресет — это уже настроенная строка для LightShow: строителю не нужно ничего знать
 * ни про формулы, ни про refresh. Числа подобраны так, чтобы эффект был виден на бегу
 * и при этом не стоил больше пары сотен пакетов в тик.
 */
public final class WonderPresets {

    private static final Map<String, WonderPreset> BY_ID = new LinkedHashMap<>();

    private WonderPresets() {
    }

    /**
     * Название и подсказка пресета лежат в lang.yml под {@code wonder.preset.<id>.name}
     * и {@code .hint}: список статический и собирается при загрузке класса, когда языка
     * ещё нет. Строку эффекта (spec/params) не трогаем - это содержимое, а не подпись.
     */
    private static void add(@NonNull String id,
                            @NonNull WonderCategory category,
                            @NonNull Material icon,
                            @NonNull String spec,
                            @NonNull String params,
                            int durationMillis,
                            @NonNull WonderAnchor anchor
    ) {
        BY_ID.put(id, new WonderPreset(id, category, icon, spec, params, durationMillis, anchor));
    }

    static {
        // ─────────────────────────────── НАДПИСИ
        add("text_fly", WonderCategory.TEXT, Material.NAME_TAG,
            "text:ВПЕРЁД @ px:0.3 font:pixel burst:true",
            "in:none out:fade outt:16t face:player cull:0 view:200", 4000, WonderAnchor.PATH);
        add("text_type", WonderCategory.TEXT, Material.WRITABLE_BOOK,
            "text:ВПЕРЁД @ px:0.28 font:pixel",
            "in:type int:18t out:fade outt:10t face:player", 3000, WonderAnchor.AHEAD);
        add("text_drop", WonderCategory.TEXT, Material.ANVIL,
            "text:ВПЕРЁД @ px:0.3 font:bold",
            "in:drop int:14t out:fall outt:12t face:player", 3000, WonderAnchor.AHEAD);
        add("text_two", WonderCategory.TEXT, Material.BOOK,
            "text:ПЕРВАЯ\\nВТОРАЯ @ px:0.24 font:pixel lgap:1",
            "in:fade int:10t out:fade outt:10t face:player align:center", 3500, WonderAnchor.AHEAD);
        add("text_neon", WonderCategory.TEXT, Material.GLOWSTONE,
            "text:НЕОН @ px:0.3 font:bold outline:true particle:end_rod",
            "in:scale int:10t out:implode outt:10t face:player", 3000, WonderAnchor.AHEAD);
        add("text_shake", WonderCategory.TEXT, Material.STRING,
            "text:БИТ @ px:0.32 font:bold rotz:6*sin(T*6)",
            "in:scale int:8t out:fade outt:8t face:player", 3000, WonderAnchor.AHEAD);
        add("text_count", WonderCategory.TEXT, Material.CLOCK,
            "text:3 @ px:0.6 font:bold",
            "in:scale int:6t out:implode outt:6t face:player", 900, WonderAnchor.AHEAD);
        add("text_arc", WonderCategory.TEXT, Material.OAK_SIGN,
            "text:GO @ px:0.35 font:bold",
            "in:fade int:8t out:fade outt:8t face:player", 3500, WonderAnchor.OVERHEAD);

        // ─────────────────────────────── НЕБО
        add("stars_fall", WonderCategory.SKY, Material.NETHER_STAR,
            "let a=noise(i*.13)*70;let c=noise(i*.71+9)*70;x=a;y=34+noise(i*.37+3)*10;z=c"
                + " @ steps:36 refresh:45 jitter:14 chance:0.55 vx:0.42+0.2*noise(i*.9+u) vy:-0.62"
                + " vz:0.18*noise(i*.5+u) trail:7 tgap:0.45",
            "face:north cull:0 view:128", 8000, WonderAnchor.OVERHEAD);
        add("stars_rain", WonderCategory.SKY, Material.PRISMARINE_SHARD,
            "let a=noise(i*.17)*46;let c=noise(i*.63+4)*46;x=a;y=26+noise(i*.29+7)*6;z=c"
                + " @ steps:28 refresh:50 jitter:10 chance:0.4 vx:0.1*noise(i*.8+u) vy:-0.28"
                + " vz:0.1*noise(i*.4+u) trail:3 tgap:0.35",
            "face:north cull:0 view:120", 12000, WonderAnchor.OVERHEAD);
        add("comet", WonderCategory.SKY, Material.FIREWORK_ROCKET,
            "x=0;y=0;z=0 @ steps:1 refresh:60 vx:0.7 vy:-0.35 vz:0 trail:16 tgap:0.5",
            "face:north cull:0 view:140", 4000, WonderAnchor.OVERHEAD);
        add("aurora", WonderCategory.SKY, Material.LIGHT_BLUE_STAINED_GLASS,
            "x=(u-0.5)*30;y=sin(u*7-T*3)*2.4;z=(t-0.5)*8"
                + " @ mode:surface t:0..1 u:0..1 steps:26 usteps:34 refresh:4",
            "cull:0 view:110", 9000, WonderAnchor.PATH);
        add("moon", WonderCategory.SKY, Material.CLOCK,
            "let th=0.927+t*1.41;let ph=-1.287+(t-pi)*0.8194;x=if(t<pi,6*cos(th),2.2+5*cos(ph));"
                + "y=if(t<pi,6*sin(th),5*sin(ph)) @ steps:220 refresh:14",
            "in:fade int:14t out:fade outt:14t", 7000, WonderAnchor.PATH);
        add("constellation", WonderCategory.SKY, Material.WHITE_DYE,
            "let a=noise(i*.31)*22;let b=noise(i*.53+3)*10;let c=noise(i*.77+8)*22;x=a;y=b;z=c"
                + " @ steps:40 refresh:18 chance:0.8",
            "cull:0 view:100", 8000, WonderAnchor.PATH);

        // ─────────────────────────────── ОГОНЬ
        add("fire_burst", WonderCategory.FIRE, Material.BLAZE_POWDER,
            "x=4*cos(t)*cos(u*3);y=4*sin(u*3);z=4*sin(t)*cos(u*3)"
                + " @ mode:surface u:-0.5..0.5 steps:24 usteps:10 particle:flame motion:out mspeed:0.35 refresh:3",
            "in:scale int:5t out:fade outt:8t", 1600, WonderAnchor.AHEAD);
        add("fire_wall", WonderCategory.FIRE, Material.CAMPFIRE,
            "x=(t-0.5)*14;y=0;z=0 @ t:0..1 steps:60 particle:flame motion:up mspeed:0.28 refresh:3",
            "face:player in:wipe int:10t out:fade outt:10t", 3000, WonderAnchor.AHEAD);
        add("fire_rings", WonderCategory.FIRE, Material.FIRE_CHARGE,
            "x=3.4*cos(t);y=3.4*sin(t);z=0 @ steps:120 particle:soul_fire refresh:6 rotz:T*40",
            "face:player in:scale int:8t out:implode outt:8t", 2600, WonderAnchor.AHEAD);
        add("sparks", WonderCategory.FIRE, Material.FIREWORK_STAR,
            "x=0;y=0;z=0 @ steps:18 refresh:6 vx:0.3*(noise(i*.7+u)-0.5) vy:0.55"
                + " vz:0.3*(noise(i*.9+u)-0.5) trail:3 tgap:0.3 jitter:0.4",
            "in:none out:fade outt:6t", 2000, WonderAnchor.AHEAD);
        add("ember_trail", WonderCategory.FIRE, Material.CHARCOAL,
            "let r=1.4;x=r*cos(t+T*2);y=0.1;z=r*sin(t+T*2)"
                + " @ steps:26 particle:flame motion:up mspeed:0.04 refresh:4",
            "", 6000, WonderAnchor.FOLLOW);

        // ─────────────────────────────── ТЕПЛО
        add("heart", WonderCategory.LOVE, Material.POPPY,
            "let s=0.32;x=s*16*sin(t)^3;y=s*(13*cos(t)-5*cos(2*t)-2*cos(3*t)-cos(4*t))"
                + " @ steps:200 refresh:12",
            "in:scale int:10t out:implode outt:10t face:player", 3200, WonderAnchor.AHEAD);
        add("heart_beat", WonderCategory.LOVE, Material.REDSTONE,
            "let k=1+0.14*sin(T*7);let s=0.32*k;x=s*16*sin(t)^3;"
                + "y=s*(13*cos(t)-5*cos(2*t)-2*cos(3*t)-cos(4*t)) @ steps:200 refresh:4",
            "in:scale int:8t out:fade outt:10t face:player", 5000, WonderAnchor.AHEAD);
        add("petals", WonderCategory.LOVE, Material.PINK_TULIP,
            "let a=noise(i*.19)*16;let c=noise(i*.57+2)*16;x=a;y=12+noise(i*.31+5)*4;z=c"
                + " @ steps:26 refresh:40 jitter:6 chance:0.6 particle:dust color:#FF80C0 psize:1"
                + " vx:0.05*noise(i*.6+u) vy:-0.16 vz:0.05*noise(i*.8+u)",
            "face:north cull:0", 9000, WonderAnchor.OVERHEAD);
        add("halo", WonderCategory.LOVE, Material.GOLD_NUGGET,
            "let r=0.75;x=r*cos(t+T*1.6);y=0;z=r*sin(t+T*1.6) @ steps:36 refresh:4",
            "offset:0,2.4,0", 6000, WonderAnchor.FOLLOW);

        // ─────────────────────────────── МАГИЯ
        add("portal_ring", WonderCategory.MAGIC, Material.END_PORTAL_FRAME,
            "x=3*cos(t);y=0;z=3*sin(t) @ steps:90 motion:up mspeed:0.35 refresh:2",
            "face:north", 4000, WonderAnchor.AHEAD);
        add("rune", WonderCategory.MAGIC, Material.ENCHANTING_TABLE,
            "let r=4*(0.62+0.38*cos(5*t));x=r*cos(t);y=r*sin(t) @ steps:300 refresh:12 rotz:T*18",
            "face:player in:spiral int:14t out:fade outt:10t", 4000, WonderAnchor.AHEAD);
        add("aura", WonderCategory.MAGIC, Material.DRAGON_BREATH,
            "let r=1.2;x=r*cos(t+T*2);y=0.1*sin(t*3+T*4);z=r*sin(t+T*2)"
                + " @ steps:44 particle:soul_fire motion:up mspeed:0.03 refresh:3",
            "offset:0,0.3,0", 7000, WonderAnchor.FOLLOW);
        add("eye", WonderCategory.MAGIC, Material.ENDER_EYE,
            // Взгляд ведём шумом, а не щелчками: получается человеческое движение,
            // когда глаз задерживается в стороне, а потом плавно уходит в другую.
            "let b=1-.9*max(0,1-abs(frac(T*.11)*44-1));x=9*sin(t);y=3.4*b*cos(t)*abs(cos(t))"
                + " @ steps:200 refresh:6"
                + "|let b=1-.9*max(0,1-abs(frac(T*.11)*44-1));let gx=4.2*noise(T*.55);"
                + "let gy=1.1*noise(T*.4+7);x=2*cos(t)+gx;y=(2*sin(t)+gy)*b @ steps:110 refresh:4"
                + "|let b=1-.9*max(0,1-abs(frac(T*.11)*44-1));let gx=4.2*noise(T*.55);"
                + "let gy=1.1*noise(T*.4+7);x=u*1.05*cos(t)+gx;y=(u*1.05*sin(t)+gy)*b"
                + " @ mode:fill u:0..1 usteps:4 steps:26 refresh:4",
            "in:fly int:24t flyd:20 out:fade outt:12t", 7000, WonderAnchor.PATH);
        add("crystal", WonderCategory.MAGIC, Material.PRISMARINE_CRYSTALS,
            "x=3*sin(u)*cos(t);y=3*cos(u);z=3*sin(u)*sin(t)"
                + " @ mode:surface u:0..3.1416 steps:16 usteps:10 refresh:5",
            "spin:0,45,0 in:scale int:10t out:implode outt:10t", 4000, WonderAnchor.AHEAD);

        // ─────────────────────────────── ФИГУРЫ
        add("ring", WonderCategory.SHAPE, Material.GOLD_INGOT,
            "x=4*cos(t);y=4*sin(t) @ steps:150 refresh:14",
            "face:player in:scale int:8t out:implode outt:8t", 2500, WonderAnchor.AHEAD);
        add("rose", WonderCategory.SHAPE, Material.RED_DYE,
            "x=5*cos(4*t)*cos(t);y=5*cos(4*t)*sin(t) @ steps:360 refresh:13",
            "face:player in:spiral int:14t out:fade outt:10t", 3500, WonderAnchor.AHEAD);
        add("star5", WonderCategory.SHAPE, Material.NETHER_STAR,
            "let r=4*(0.62+0.38*cos(5*t));x=r*cos(t);y=r*sin(t) @ steps:280 refresh:13",
            "face:player in:scale int:10t out:implode outt:8t", 3000, WonderAnchor.AHEAD);
        add("infinity", WonderCategory.SHAPE, Material.CHAIN,
            "x=6*cos(t);y=3*sin(t)*cos(t) @ steps:220 refresh:14",
            "face:player in:type int:14t out:wipe outt:10t", 3000, WonderAnchor.AHEAD);
        add("sphere", WonderCategory.SHAPE, Material.SLIME_BALL,
            "x=4*sin(u)*cos(t);y=4*cos(u);z=4*sin(u)*sin(t)"
                + " @ mode:surface u:0..3.1416 steps:28 usteps:16 refresh:13",
            "spin:0,25,0 in:explode int:14t out:scatter outt:12t", 4000, WonderAnchor.AHEAD);
        add("dna", WonderCategory.SHAPE, Material.LADDER,
            "x=2*cos(t);y=0.55*t;z=2*sin(t) @ t:0..18 steps:200 refresh:14"
                + "|x=2*cos(t+pi);y=0.55*t;z=2*sin(t+pi) @ t:0..18 steps:200 refresh:14",
            "face:north cull:40", 6000, WonderAnchor.AHEAD);

        // ─────────────────────────────── ДОРОГА
        add("gate", WonderCategory.PATH, Material.STONE_BRICK_WALL,
            "x=rectx(t,3.6,4.2);y=recty(t,3.6,4.2)+4.2 @ steps:200 refresh:14",
            "face:player in:wipe int:10t out:fade outt:10t", 3500, WonderAnchor.PATH);
        add("corridor", WonderCategory.PATH, Material.RAIL,
            "x=0;y=0;z=t @ mode:tube radius:1.5 sides:10 t:0..24 steps:120 refresh:20",
            "face:player cull:40", 6000, WonderAnchor.PATH);
        add("spiral_way", WonderCategory.PATH, Material.CHAIN_COMMAND_BLOCK,
            "x=1.4*cos(t*3);y=1.4*sin(t*3);z=t @ mode:tube radius:0.9 sides:8 t:0..20 steps:140 refresh:20",
            "face:player cull:40", 6000, WonderAnchor.PATH);
        add("side_lines", WonderCategory.PATH, Material.END_ROD,
            "x=-3.5;y=0.4;z=t @ t:0..26 steps:70 refresh:18"
                + "|x=3.5;y=0.4;z=t @ t:0..26 steps:70 refresh:18",
            "face:player cull:40", 7000, WonderAnchor.PATH);
        add("arch_row", WonderCategory.PATH, Material.STONE_BRICKS,
            "let n2=floor(u*4);x=3.2*cos(t*0.5);y=3.2*sin(t*0.5);z=n2*6"
                + " @ mode:surface t:0..6.2832 u:0..1 steps:40 usteps:3 refresh:16",
            "face:player cull:40", 6000, WonderAnchor.PATH);

        // ─────────────────────────────── УДАР
        add("hit_flash", WonderCategory.HIT, Material.GLOWSTONE_DUST,
            "x=5*cos(t);y=5*sin(t) @ steps:80 motion:out mspeed:0.5 refresh:2",
            "face:player out:fade outt:5t", 500, WonderAnchor.AHEAD);
        add("hit_pulse", WonderCategory.HIT, Material.NOTE_BLOCK,
            "let k=1+frac(T*0.5)*1.4;x=4*k*cos(t);y=4*k*sin(t) @ steps:90 refresh:3",
            "face:player", 8000, WonderAnchor.AHEAD);
        add("hit_shock", WonderCategory.HIT, Material.TNT,
            "let k=frac(T*0.7)*10;x=k*cos(t);y=0;z=k*sin(t) @ steps:110 refresh:3",
            "face:north", 4000, WonderAnchor.AHEAD);
        add("hit_beam", WonderCategory.HIT, Material.SPECTRAL_ARROW,
            "x=2*cos(t);y=2*sin(t) @ steps:50 motion:to_player mspeed:0.45 refresh:2",
            "face:player", 1500, WonderAnchor.AHEAD);

        // ─────────────────────────────── СЦЕНЫ: несколько эффектов одним пресетом
        add("scene_god_eye", WonderCategory.SCENE, Material.BEACON,
            "let b=1-.85*max(0,1-abs(frac(T*.13)*40-1));x=9*sin(t);y=3.2*b*cos(t)*abs(cos(t)) @ steps:170 refresh:8"
                + "|let b=1-.85*max(0,1-abs(frac(T*.13)*40-1));let a=step4(T*.45)*pi/2;let o=4*cos(a);"
                + "x=1.85*cos(t)+o;y=(1.85*sin(t)+.45*sin(a))*b @ steps:90 refresh:4"
                + "|let r=6*(0.62+0.38*cos(5*t));x=r*cos(t);y=r*sin(t)-9 @ steps:200 refresh:12 rotz:T*14"
                + "|let a=noise(i*.31)*26;let c=noise(i*.77+8)*20;x=a;y=noise(i*.53+3)*8+4;z=c @ steps:30 refresh:18 particle:enchant",
            "face:north in:fly int:26t flyd:22 out:fade outt:14t", 7000, WonderAnchor.OVERHEAD);
        add("scene_meteor_night", WonderCategory.SCENE, Material.NETHER_STAR,
            "let th=0.927+t*1.41;let ph=-1.287+(t-pi)*0.8194;x=if(t<pi,6*cos(th),2.2+5*cos(ph));"
                + "y=if(t<pi,6*sin(th),5*sin(ph)) @ steps:180 refresh:14"
                + "|let a=noise(i*.31)*30;x=a;y=noise(i*.53+3)*10;z=noise(i*.77+8)*30 @ steps:34 refresh:18"
                + "|let a=noise(i*.13)*60;x=a;y=26+noise(i*.37+3)*8;z=noise(i*.71+9)*60"
                + " @ steps:28 refresh:45 jitter:12 chance:0.5 vx:0.4 vy:-0.6 vz:0.15 trail:6 tgap:0.45",
            "face:north cull:0 view:128", 10000, WonderAnchor.OVERHEAD);
        add("scene_fire_gate", WonderCategory.SCENE, Material.CAMPFIRE,
            "x=rectx(t,3.6,4.2);y=recty(t,3.6,4.2)+4.2 @ steps:190 refresh:14"
                + "|x=-3.6;y=t*8;z=0 @ t:0..1 steps:26 particle:flame motion:up mspeed:0.25 refresh:3"
                + "|x=3.6;y=t*8;z=0 @ t:0..1 steps:26 particle:flame motion:up mspeed:0.25 refresh:3"
                + "|x=0;y=4;z=0 @ steps:12 refresh:4 particle:spark vx:0.2*(noise(i*.7+u)-0.5) vy:0.3 vz:0 jitter:1.5",
            "face:player in:wipe int:12t out:fade outt:10t", 4000, WonderAnchor.AHEAD);
        add("scene_city", WonderCategory.SCENE, Material.CYAN_STAINED_GLASS,
            // Здание это стопка горизонтальных прямоугольников. Верхние линии зажаты по высоте
            // растущим потолком, поэтому башня набирается снизу вверх, а не появляется целиком.
            "let n2=floor(u*5);let h=key(T,0,0,0.6+n2*0.35,1,9,1);let top=h*(9+n2*3);"
                + "let lv=t*(9+n2*3);let w=3+mod(n2,3);"
                + "x=-13-n2*7+rectx(t*0+u*0+frac(t*7)*6.2832,w,w)*0+rectx(frac(t*7)*6.2832,w,w);"
                + "y=min(lv,top);z=8+n2*9"
                + " @ mode:surface t:0..1 u:0..1 steps:70 usteps:5 refresh:14"
                + "|let n2=floor(u*5);let h=key(T,0,0,0.6+n2*0.35,1,9,1);let top=h*(9+n2*3);"
                + "let lv=t*(9+n2*3);let w=3+mod(n2+1,3);"
                + "x=13+n2*7+rectx(frac(t*7)*6.2832,w,w);"
                + "y=min(lv,top);z=8+n2*9"
                + " @ mode:surface t:0..1 u:0..1 steps:70 usteps:5 refresh:14"
                + "|x=-4.5;y=0.1;z=t @ t:0..70 steps:70 refresh:16 particle:dust color:#FF30C0 psize:1"
                + "|x=4.5;y=0.1;z=t @ t:0..70 steps:70 refresh:16 particle:dust color:#FF30C0 psize:1",
            "cull:70 view:140 out:fade outt:20t", 14000, WonderAnchor.PATH);
        add("scene_finale", WonderCategory.SCENE, Material.TOTEM_OF_UNDYING,
            "x=5*sin(u)*cos(t);y=5*cos(u);z=5*sin(u)*sin(t) @ mode:surface u:0..3.1416 steps:24 usteps:14 refresh:6"
                + "|x=7*cos(t);y=7*sin(t) @ steps:130 refresh:4 particle:totem"
                + "|x=0;y=0;z=0 @ steps:22 refresh:3 particle:spark vx:0.4*(noise(i*.7+u)-0.5) vy:0.5"
                + " vz:0.4*(noise(i*.9+u)-0.5) trail:3 tgap:0.3",
            "face:player spin:0,30,0 in:explode int:12t out:scatter outt:14t", 4500, WonderAnchor.AHEAD);
        add("scene_lyric", WonderCategory.SCENE, Material.NAME_TAG,
            "text:СЛОВА @ px:0.28 font:pixel"
                + "|x=(t-0.5)*14;y=-2.2;z=0 @ t:0..1 steps:60 refresh:12"
                + "|x=-7.4;y=-2.2;z=0 @ steps:10 refresh:4 particle:spark vy:0.25 jitter:0.6"
                + "|x=7.4;y=-2.2;z=0 @ steps:10 refresh:4 particle:spark vy:0.25 jitter:0.6",
            "face:player in:fly int:20t flyd:18 out:fade outt:16t", 3500, WonderAnchor.AHEAD);

        // ─────────────────────────────── добавка к обычным разделам
        add("text_approach", WonderCategory.TEXT, Material.ELYTRA,
            "text:ВПЕРЁД @ px:0.34 font:pixel refresh:3",
            "in:fade int:16t out:fade outt:20t face:player cull:0 view:200", 7000, WonderAnchor.AHEAD);
        add("text_letters", WonderCategory.TEXT, Material.FEATHER,
            "text:ВПЕРЁД @ px:0.3 font:pixel burst:true",
            "in:letters int:26t flyd:16 out:letters outt:18t face:player", 3600, WonderAnchor.PATH);
        add("text_pop", WonderCategory.TEXT, Material.SLIME_BALL,
            "text:ВПЕРЁД @ px:0.3 font:bold burst:true",
            "in:popletters int:20t out:fade outt:14t face:player", 3200, WonderAnchor.PATH);
        add("text_wave", WonderCategory.TEXT, Material.KELP,
            "text:ВПЕРЁД @ px:0.3 font:pixel burst:true wave:0.35,5 refresh:3",
            "in:typeletters int:16t out:fade outt:14t face:player", 4500, WonderAnchor.PATH);
        add("text_flyby", WonderCategory.TEXT, Material.ELYTRA,
            "text:ВПЕРЁД @ px:0.34 font:bold burst:true",
            "in:fade int:10t out:fade outt:14t face:loc cull:0 view:200", 6000, WonderAnchor.PATH);
        add("text_fire", WonderCategory.TEXT, Material.CAMPFIRE,
            "text:ВПЕРЁД @ px:0.3 font:bold particle:flame burst:true"
                + "|x=(t-0.5)*9;y=-1.4;z=0 @ t:0..1 steps:26 particle:flame motion:up mspeed:0.18 refresh:4",
            "in:fade int:12t out:fade outt:16t face:player", 3600, WonderAnchor.PATH);
        add("text_soul", WonderCategory.TEXT, Material.SOUL_LANTERN,
            "text:ВПЕРЁД @ px:0.3 font:pixel particle:soul_fire burst:true"
                + "|let a=noise(i*.31)*7;x=a;y=noise(i*.53+2)*3;z=0 @ steps:18 particle:soul refresh:6",
            "in:fade int:14t out:fade outt:18t face:player", 4000, WonderAnchor.PATH);
        add("text_magic", WonderCategory.TEXT, Material.ENCHANTING_TABLE,
            "text:ВПЕРЁД @ px:0.3 font:pixel particle:enchant burst:true"
                + "|x=6.5*cos(t);y=6.5*sin(t)*0.45;z=0 @ steps:120 particle:witch refresh:8 rotz:T*12",
            "in:scale int:14t out:implode outt:12t face:player", 4000, WonderAnchor.PATH);
        add("text_frame", WonderCategory.TEXT, Material.ITEM_FRAME,
            "text:ВПЕРЁД @ px:0.28 font:bold burst:true"
                + "|x=rectx(t,6,2.2);y=recty(t,6,2.2) @ steps:190 refresh:14",
            "in:wipe int:14t out:wipe outt:12t face:player", 4000, WonderAnchor.PATH);
        add("text_sparks", WonderCategory.TEXT, Material.FIREWORK_STAR,
            "text:ВПЕРЁД @ px:0.3 font:bold burst:true"
                + "|x=-7;y=0;z=0 @ steps:12 particle:spark refresh:4 vx:0.15 vy:0.3 jitter:0.5"
                + "|x=7;y=0;z=0 @ steps:12 particle:spark refresh:4 vx:-0.15 vy:0.3 jitter:0.5",
            "in:popletters int:20t out:fade outt:14t face:player", 3600, WonderAnchor.PATH);
        add("text_smoke", WonderCategory.TEXT, Material.COAL,
            "text:ВПЕРЁД @ px:0.32 font:bold burst:true"
                + "|let a=noise(i*.23)*8;x=a;y=-1.8;z=noise(i*.61+3)*2 @ steps:22 particle:smoke refresh:8 count:3 spread:0.4",
            "in:fade int:16t out:fade outt:18t face:player", 4200, WonderAnchor.PATH);
        add("text_portal", WonderCategory.TEXT, Material.OBSIDIAN,
            "text:ВПЕРЁД @ px:0.3 font:pixel particle:portal burst:true"
                + "|let r=5-t*0.15;x=r*cos(t*2);y=r*sin(t*2)*0.5;z=1.5 @ t:0..12 steps:90 particle:portal refresh:5",
            "in:spiral int:18t out:implode outt:14t face:player", 4200, WonderAnchor.PATH);
        add("text_hearts", WonderCategory.TEXT, Material.POPPY,
            "text:ВПЕРЁД @ px:0.3 font:bold burst:true"
                + "|let a=noise(i*.37)*8;x=a;y=noise(i*.53+1)*3;z=0 @ steps:12 particle:heart refresh:10 vy:0.06",
            "in:popletters int:22t out:fade outt:16t face:player", 4200, WonderAnchor.PATH);
        add("text_snow", WonderCategory.TEXT, Material.SNOWBALL,
            "text:ВПЕРЁД @ px:0.3 font:thin burst:true"
                + "|let a=noise(i*.29)*9;x=a;y=4;z=noise(i*.67+5)*2 @ steps:24 particle:snow refresh:6 vy:-0.12 jitter:1",
            "in:fade int:14t out:fade outt:18t face:player", 4500, WonderAnchor.PATH);
        add("text_wipe", WonderCategory.TEXT, Material.SHEARS,
            "text:ВПЕРЁД @ px:0.28 font:pixel", "in:wipe int:12t out:wipe outt:12t face:player", 3000, WonderAnchor.AHEAD);
        add("text_explode", WonderCategory.TEXT, Material.FLINT,
            "text:ВПЕРЁД @ px:0.28 font:bold", "in:explode int:16t out:scatter outt:14t face:player", 3200, WonderAnchor.AHEAD);
        add("text_hard", WonderCategory.TEXT, Material.STONE_BUTTON,
            "text:ВПЕРЁД @ px:0.3 font:bold refresh:2", "in:none out:none face:player", 1200, WonderAnchor.AHEAD);
        add("stars_swirl", WonderCategory.SKY, Material.PRISMARINE_SHARD,
            "let r=6-t*0.2;x=r*cos(t*2);y=t*0.6;z=r*sin(t*2) @ t:0..14 steps:200 refresh:6",
            "face:north cull:40", 7000, WonderAnchor.OVERHEAD);
        add("cloud_low", WonderCategory.SKY, Material.WHITE_WOOL,
            "let a=noise(i*.21)*24;x=a;y=noise(i*.43+2)*3;z=noise(i*.67+5)*24"
                + " @ steps:44 refresh:20 particle:cloud",
            "face:north cull:0", 9000, WonderAnchor.OVERHEAD);
        add("fire_snake", WonderCategory.FIRE, Material.MAGMA_CREAM,
            "x=1.6*sin(t*2+T*3);y=0.6+0.4*cos(t*3);z=t @ t:0..20 steps:130 particle:flame refresh:4",
            "face:player cull:40", 6000, WonderAnchor.AHEAD);
        add("fire_pillars", WonderCategory.FIRE, Material.NETHERRACK,
            "let n2=floor(u*4);x=if(n2<2,-4,4);y=t*6;z=n2*5 @ mode:surface t:0..1 u:0..1 steps:20 usteps:3"
                + " particle:flame motion:up mspeed:0.2 refresh:4",
            "face:player cull:40", 5000, WonderAnchor.AHEAD);
        add("love_ring", WonderCategory.LOVE, Material.RED_DYE,
            "let r=2.2;x=r*cos(t+T);y=0.6*sin(t*3+T*2);z=r*sin(t+T) @ steps:30 particle:heart refresh:6",
            "offset:0,1,0", 6000, WonderAnchor.FOLLOW);
        add("butterflies", WonderCategory.LOVE, Material.PINK_DYE,
            "let a=noise(i*.37+T*.3)*3;x=a;y=1+noise(i*.53+T*.4)*1.5;z=noise(i*.71+T*.2)*3"
                + " @ steps:14 refresh:3 particle:happy",
            "", 6000, WonderAnchor.FOLLOW);
        add("magic_circle", WonderCategory.MAGIC, Material.CAULDRON,
            "x=3.4*cos(t);y=0.05;z=3.4*sin(t) @ steps:120 refresh:12 rotz:0"
                + "|let r=2.2*(0.7+0.3*cos(6*t));x=r*cos(t);y=0.05;z=r*sin(t) @ steps:150 refresh:12 particle:enchant",
            "face:north", 5000, WonderAnchor.AHEAD);
        add("magic_beam", WonderCategory.MAGIC, Material.LIGHT_GRAY_STAINED_GLASS,
            "let r=0.6;x=r*cos(t);y=0;z=r*sin(t) @ steps:26 refresh:2 vy:0.9 trail:10 tgap:0.6",
            "face:north", 3000, WonderAnchor.AHEAD);
        add("shape_cube", WonderCategory.SHAPE, Material.SLIME_BLOCK,
            "x=rectx(t,3,3);y=recty(t,3,3);z=-3 @ steps:120 refresh:6"
                + "|x=rectx(t,3,3);y=recty(t,3,3);z=3 @ steps:120 refresh:6",
            "spin:0,35,0 in:scale int:10t out:implode outt:10t", 4000, WonderAnchor.AHEAD);
        add("shape_wave", WonderCategory.SHAPE, Material.WATER_BUCKET,
            "x=(t-0.5)*18;y=1.6*sin(t*10-T*5);z=0 @ t:0..1 steps:90 refresh:3",
            "face:player", 5000, WonderAnchor.AHEAD);
        add("shape_grid", WonderCategory.SHAPE, Material.IRON_BARS,
            "x=(cellx(i,9)-4)*2;y=(celly(i,9)-2)*2;z=0 @ steps:44 refresh:14",
            "face:player in:fade int:10t out:fade outt:10t", 3500, WonderAnchor.AHEAD);
        add("path_arrows", WonderCategory.PATH, Material.ARROW,
            "let k=frac(T*0.6+i*0.1)*20;x=0;y=0.3;z=k @ steps:12 refresh:3",
            "face:player cull:40", 6000, WonderAnchor.PATH);
        add("path_tunnel_rings", WonderCategory.PATH, Material.CHAIN,
            "let n2=floor(u*6);x=2.4*cos(t);y=2.4*sin(t);z=n2*4"
                + " @ mode:surface t:0..6.2832 u:0..1 steps:30 usteps:5 refresh:16",
            "face:player cull:40", 6000, WonderAnchor.PATH);
        add("hit_cross", WonderCategory.HIT, Material.STICK,
            "x=(t-0.5)*12;y=0;z=0 @ t:0..1 steps:40 refresh:2"
                + "|x=0;y=(t-0.5)*12;z=0 @ t:0..1 steps:40 refresh:2",
            "face:player in:none out:none", 400, WonderAnchor.AHEAD);
        add("hit_drop", WonderCategory.HIT, Material.ANVIL,
            "x=0;y=10;z=0 @ steps:14 refresh:2 vy:-1.2 trail:6 tgap:0.5"
                + "|let k=frac(T*1.2)*8;x=k*cos(t);y=0.1;z=k*sin(t) @ steps:70 refresh:3",
            "face:north", 1500, WonderAnchor.AHEAD);
        add("hit_sides", WonderCategory.HIT, Material.GLOWSTONE,
            "x=-5;y=1.5;z=0 @ steps:20 refresh:2 motion:out mspeed:0.4"
                + "|x=5;y=1.5;z=0 @ steps:20 refresh:2 motion:out mspeed:0.4",
            "face:player in:none out:fade outt:5t", 600, WonderAnchor.AHEAD);

    }

    @NonNull
    public static List<WonderPreset> all() {
        return new ArrayList<>(BY_ID.values());
    }

    @NonNull
    public static List<WonderPreset> byCategory(@NonNull WonderCategory category) {
        List<WonderPreset> out = new ArrayList<>();
        for (WonderPreset preset : BY_ID.values()) if (preset.getCategory() == category) out.add(preset);
        return out;
    }

    @Nullable
    public static WonderPreset byId(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static int amount() {
        return BY_ID.size();
    }
}
