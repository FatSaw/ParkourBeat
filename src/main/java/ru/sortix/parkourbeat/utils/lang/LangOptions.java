package ru.sortix.parkourbeat.utils.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public enum LangOptions {
    command_usage, level_convertation_multiple, level_convertation_one_success, level_convertation_one_fail, level_editor_success_start, level_editor_success_stop, level_editor_test_fail_start, level_editor_test_fail_stop, level_editor_test_success_start, level_editor_test_success_stop, level_editor_cantedit_notowner, level_editor_cantedit_moderated, level_editor_cantedit_failload, level_editor_cantedit_editingnow, level_editor_cantedit_playersonlevel, level_editor_cantedit_failstart, level_editor_cantedit_failteleport, level_editor_delete_success, level_editor_delete_fail, level_editor_delete_already, level_editor_delete_useitem, level_editor_delete_notowner, level_play_resourcepackstatus_accepted, level_play_resourcepackstatus_failed, level_play_resourcepackstatus_declined, level_play_resourcepackstatus_success, level_play_title_preparing, level_play_title_stopped, level_play_title_fall, level_play_title_pressrun, level_play_title_complete, level_play_title_notsprinting, level_play_title_death, level_play_title_wrongangle, level_play_title_wrongdirection, level_play_unavilable, level_play_failload, level_play_alreadyinworld, level_play_failteleport, level_play_title_moveback, level_play_accuracy, level_play_progress, level_play_music_notice, level_spectate_success, level_spectate_failload, level_spectate_alreadyinworld, level_prepare_spawninvalid_notify, level_prepare_spawninvalid_prevent, level_prepare_track_unavilable, level_prepare_track_sendfail, inventory_regularitems_close, inventory_regularitems_next, inventory_regularitems_previous, inventory_createlevel_title, inventory_createlevel_overworld, inventory_createlevel_nether, inventory_createlevel_theend, inventory_createlevel_cancel, inventory_createlevel_nopermission, inventory_createlevel_create_edit_start, inventory_createlevel_create_edit_unavilable, inventory_createlevel_create_edit_fail, inventory_createlevel_create_fail, inventory_editormain_title, inventory_editormain_particlecolor_name, inventory_editormain_particlecolor_lore, inventory_editormain_particlecolor_unavilable, inventory_editormain_particlecolor_timetochange, inventory_editormain_particlecolor_timeout, inventory_editormain_particlecolor_invalidhex, inventory_editormain_particlecolor_selectedcolor, inventory_editormain_selectsong_name, inventory_editormain_selectsong_notracklore, inventory_editormain_selectsong_lore, inventory_editormain_spawnpoint_name, inventory_editormain_spawnpoint_lore, inventory_editormain_spawnpoint_fail, inventory_editormain_spawnpoint_success, inventory_editormain_privacy_name, inventory_editormain_privacy_lore, inventory_editormain_resetpoints_name, inventory_editormain_resetpoints_lore, inventory_editormain_resetpoints_reset, inventory_editormain_exit_name, inventory_editormain_exit_lore, inventory_editormain_exit_canceled, inventory_editormain_delete_name, inventory_editormain_delete_lore, inventory_editormain_physic_name, inventory_editormain_physic_lore_turnon, inventory_editormain_physic_lore_turnoff, inventory_editormain_physic_turn_on, inventory_editormain_physic_turn_off, inventory_editorprivacy_title, inventory_editorprivacy_back, inventory_editorprivacy_visibility_name, inventory_editorprivacy_visibility_lore_public, inventory_editorprivacy_visibility_lore_private, inventory_editorprivacy_visibility_cantchange_moderated, inventory_editorprivacy_visibility_cantchange_blocked, inventory_editorprivacy_visibility_changedto_public, inventory_editorprivacy_visibility_changedto_private, inventory_editorprivacy_rename_name, inventory_editorprivacy_rename_lore, inventory_editorprivacy_rename_unavilable, inventory_editorprivacy_rename_timetochange, inventory_editorprivacy_rename_timeout, inventory_editorprivacy_rename_length_min, inventory_editorprivacy_rename_length_max, inventory_editorprivacy_rename_changed, inventory_editorprivacy_moderation_name, inventory_editorprivacy_moderation_lore_notmoderated, inventory_editorprivacy_moderation_lore_onmoderation, inventory_editorprivacy_moderation_lore_moderated, inventory_editorprivacy_moderation_requested_visibilitynotchanged, inventory_editorprivacy_moderation_requested_visibilitychanged, inventory_editorprivacy_moderation_moderated, inventory_editorsong_title, inventory_editorsong_nomusic_name, inventory_editorsong_nomusic_lore_selected, inventory_editorsong_nomusic_lore_notselected, inventory_editorsong_selectmusic_name, inventory_editorsong_selectmusic_lore_selected, inventory_editorsong_selectmusic_lore_notselected, inventory_editorsong_splitmode_name, inventory_editorsong_splitmode_lore_notrack, inventory_editorsong_splitmode_lore_pieces, inventory_editorsong_splitmode_lore_single_toggleavilable, inventory_editorsong_splitmode_lore_single_toggleunavilable, inventory_editorsong_resourcepackstatus_declined, inventory_editorsong_resourcepackstatus_failed, inventory_levellist_title, inventory_levellist_selectlevel_name, inventory_levellist_selectlevel_lore_uuid, inventory_levellist_selectlevel_lore_moderation_notmoderated, inventory_levellist_selectlevel_lore_moderation_onmoderation, inventory_levellist_selectlevel_lore_moderation_moderated, inventory_levellist_selectlevel_lore_displaymode_self_public, inventory_levellist_selectlevel_lore_displaymode_self_private, inventory_levellist_selectlevel_lore_displaymode_techinfo_public, inventory_levellist_selectlevel_lore_displaymode_techinfo_private, inventory_levellist_selectlevel_lore_uniqueid_number, inventory_levellist_selectlevel_lore_uniqueid_name, inventory_levellist_selectlevel_lore_creator_name, inventory_levellist_selectlevel_lore_creator_uuid, inventory_levellist_selectlevel_lore_creationtime_time, inventory_levellist_selectlevel_lore_creationtime_format, inventory_levellist_selectlevel_lore_track_is, inventory_levellist_selectlevel_lore_track_no, inventory_levellist_selectlevel_lore_actions_default, inventory_levellist_selectlevel_lore_actions_moderator, inventory_levellist_selectlevel_lore_actions_owner, inventory_levellist_selectlevel_lore_actions_tech, inventory_levellist_displaymode_moderation_selected, inventory_levellist_displaymode_moderation_unselected, inventory_levellist_displaymode_unranked_selected, inventory_levellist_displaymode_unranked_unselected, inventory_levellist_displaymode_ranked_selected, inventory_levellist_displaymode_ranked_unselected, inventory_levellist_displaymode_self_selected, inventory_levellist_displaymode_self_unselected, inventory_levellist_displaymode_self_createlevel, inventory_levellist_copyleveluuid, inventory_moderationrequest_title, inventory_moderationrequest_remove_name, inventory_moderationrequest_remove_lore, inventory_moderationrequest_removed, inventory_moderationrequest_moderated, inventory_moderationrequest_cancel_name, inventory_moderationrequest_cancel_lore, inventory_moderationconfirm_title, inventory_moderationconfirm_nopermission, inventory_moderationconfirm_notonmoderation, inventory_moderationconfirm_approved, inventory_moderationconfirm_rejected, inventory_moderationconfirm_saveerror, inventory_moderationconfirm_reject_name, inventory_moderationconfirm_reject_lore, inventory_moderationconfirm_approve_name, inventory_moderationconfirm_approve_lore, inventory_moderationconfirm_edit_name, inventory_moderationconfirm_cancel_name, inventory_moderationconfirm_cancel_lore, item_editor_test, item_editor_parameters, item_editor_points_item_name, item_editor_points_item_lore, item_editor_points_added, item_editor_points_removed, item_editor_points_minimumtwo,
    level_editor_permissionbypass, level_editor_cantedit_onmoderation, level_editor_coeditor_joined, level_editor_coeditor_left, level_editor_test_success_stoptime, level_play_noaccess, level_spectate_noaccess, inventory_levellist_selectlevel_lore_coeditors, inventory_editormain_lightshow_name, inventory_editormain_lightshow_lore, inventory_editormain_coeditors_name, inventory_editormain_coeditors_lore, inventory_editorlightshow_title, inventory_editorlightshow_back, inventory_editorlightshow_basesky_name, inventory_editorlightshow_basesky_lore, inventory_editorlightshow_cues_name, inventory_editorlightshow_cues_lore, inventory_editorlightshow_skychanged, inventory_editorsky_title, inventory_editorsky_back, inventory_editorsky_lore_selected, inventory_editorsky_lore_notselected, inventory_editorcues_title, inventory_editorcues_back, inventory_editorcues_add_name, inventory_editorcues_add_lore, inventory_editorcues_add_unavilable, inventory_editorcues_add_request, inventory_editorcues_add_timeout, inventory_editorcues_add_invalid, inventory_editorcues_add_duplicate, inventory_editorcues_add_limit, inventory_editorcues_add_success, inventory_editorcues_entry_name, inventory_editorcues_entry_lore, inventory_editorcues_empty_name, inventory_editorcues_empty_lore, inventory_editorcue_title, inventory_editorcue_back, inventory_editorcue_unavilable, inventory_editorcue_sky_name, inventory_editorcue_sky_lore, inventory_editorcue_time_name, inventory_editorcue_time_lore, inventory_editorcue_time_request, inventory_editorcue_time_invalid, inventory_editorcue_time_timeout, inventory_editorcue_time_success, inventory_editorcue_sharpness_name, inventory_editorcue_sharpness_lore, inventory_editorcue_sharpness_changed, inventory_editorcue_delete_name, inventory_editorcue_delete_lore, inventory_editorcue_deleted, inventory_editorcoeditors_title, inventory_editorcoeditors_back, inventory_editorcoeditors_notowner, inventory_editorcoeditors_add_name, inventory_editorcoeditors_add_lore, inventory_editorcoeditors_add_unavilable, inventory_editorcoeditors_add_request, inventory_editorcoeditors_add_timeout, inventory_editorcoeditors_add_notfound, inventory_editorcoeditors_add_already, inventory_editorcoeditors_add_owner, inventory_editorcoeditors_add_limit, inventory_editorcoeditors_add_success, inventory_editorcoeditors_entry_name, inventory_editorcoeditors_entry_lore, inventory_editorcoeditors_empty_name, inventory_editorcoeditors_empty_lore, inventory_editorcoeditors_removed, inventory_editorcoeditors_notify_added, inventory_editorcoeditors_notify_removed, lightshow_sky_day, lightshow_sky_evening, lightshow_sky_night, lightshow_sky_orange, lightshow_sky_redpink, lightshow_sky_purple, lightshow_sky_softwhite, lightshow_sharpness_sharp, lightshow_sharpness_smooth, lightshow_bossbar_yellow, lightshow_bossbar_pink, lightshow_bossbar_red, lightshow_bossbar_green, lightshow_bossbar_blue, lightshow_bossbar_purple, lightshow_bossbar_white, inventory_editormain_bossbar_name, inventory_editormain_bossbar_lore, inventory_editormain_bossbarchanged, inventory_editorbossbar_title, inventory_editorbossbar_back, inventory_editorbossbar_lore_selected, inventory_editorbossbar_lore_notselected,
    inventory_editorcue_start_name, inventory_editorcue_start_lore, inventory_editorcue_start_request, inventory_editorcue_start_invalid, inventory_editorcue_start_timeout, inventory_editorcue_start_success, inventory_editorcue_end_name, inventory_editorcue_end_lore, inventory_editorcue_end_request, inventory_editorcue_end_invalid, inventory_editorcue_end_timeout, inventory_editorcue_end_success, inventory_editorcue_wand_name, inventory_editorcue_wand_lore, inventory_editorcues_entry_selected, inventory_editorlightshow_bosscues_name, inventory_editorlightshow_bosscues_lore, inventory_editorbosscues_title, inventory_editorbosscues_back, inventory_editorbosscues_add_name, inventory_editorbosscues_add_lore, inventory_editorbosscues_add_unavilable, inventory_editorbosscues_add_request, inventory_editorbosscues_add_timeout, inventory_editorbosscues_add_invalid, inventory_editorbosscues_add_limit, inventory_editorbosscues_add_success, inventory_editorbosscues_entry_name, inventory_editorbosscues_entry_lore, inventory_editorbosscues_empty_name, inventory_editorbosscues_empty_lore, inventory_editorbosscue_title, inventory_editorbosscue_back, inventory_editorbosscue_unavilable, inventory_editorbosscue_color_name, inventory_editorbosscue_color_lore, inventory_editorbosscue_time_name, inventory_editorbosscue_time_lore, inventory_editorbosscue_time_request, inventory_editorbosscue_time_invalid, inventory_editorbosscue_time_timeout, inventory_editorbosscue_time_success, inventory_editorbosscue_delete_name, inventory_editorbosscue_delete_lore, inventory_editorbosscue_deleted, item_editor_lightshowwand_name, item_editor_lightshowwand_lore, level_editor_wand_nothingaimed, level_editor_wand_outside, level_editor_wand_startset, level_editor_wand_endset, inventory_editorcues_entry_nvwarning, inventory_editorcue_sky_nvwarning, inventory_editorcue_sky_nvchanged, inventory_moderationconfirm_unrank_name, inventory_moderationconfirm_unrank_lore, inventory_moderationconfirm_unranked, lightshow_flashspeed_x1, lightshow_flashspeed_x2, lightshow_flashspeed_x3, lightshow_weather_auto, lightshow_weather_clear, lightshow_weather_rain, lightshow_biome_plains, lightshow_biome_snowy, lightshow_biome_desert, lightshow_biome_swamp, lightshow_biome_jungle, lightshow_biome_darkforest, lightshow_biome_badlands, lightshow_biome_nether, lightshow_biome_crimson, lightshow_biome_warped, lightshow_biome_soulsand, lightshow_biome_basalt, lightshow_biome_theend, inventory_editorelement_add_name, inventory_editorelement_add_lore, inventory_editorelement_back, inventory_editorelement_empty_name, inventory_editorelement_empty_lore, inventory_editorelement_limit, inventory_editorelement_unavilable, inventory_editorelement_invalid, inventory_editorelement_timeout, inventory_editorelement_added, inventory_editorelement_deleted, inventory_editorelement_start_name, inventory_editorelement_start_lore, inventory_editorelement_start_request, inventory_editorelement_end_name, inventory_editorelement_end_lore, inventory_editorelement_end_request, inventory_editorelement_startset, inventory_editorelement_endset, inventory_editorelement_delete_name, inventory_editorelement_delete_lore, inventory_editorelement_wand_name, inventory_editorelement_wand_lore, inventory_editorlightshow_baseweather_name, inventory_editorlightshow_baseweather_lore, inventory_editorlightshow_weatherchanged, inventory_editorlightshow_cycles_name, inventory_editorlightshow_cycles_lore, inventory_editorlightshow_flashes_name, inventory_editorlightshow_flashes_lore, inventory_editorlightshow_weathers_name, inventory_editorlightshow_weathers_lore, inventory_editorlightshow_biomes_name, inventory_editorlightshow_biomes_lore, inventory_editorcycles_title, inventory_editorcycles_entry_name, inventory_editorcycles_entry_lore, inventory_editorcycle_title, inventory_editorcycle_speed_name, inventory_editorcycle_speed_lore, inventory_editorcycle_speed_request, inventory_editorcycle_speed_success, inventory_editorflashes_title, inventory_editorflashes_entry_name, inventory_editorflashes_entry_lore, inventory_editorflash_title, inventory_editorflash_speed_name, inventory_editorflash_speed_lore, inventory_editorflash_speed_changed, inventory_editorweathers_title, inventory_editorweathers_entry_name, inventory_editorweathers_entry_lore, inventory_editorweather_title, inventory_editorweather_type_name, inventory_editorweather_type_lore, inventory_editorweather_type_changed, inventory_editorbiomes_title, inventory_editorbiomes_entry_name, inventory_editorbiomes_entry_lore, inventory_editorbiome_title, inventory_editorbiome_type_name, inventory_editorbiome_type_lore, inventory_editorbiome_applied, inventory_editorbiomeselect_title, inventory_editorbiomeselect_back, inventory_editorbiomeselect_lore_selected, inventory_editorbiomeselect_lore_notselected,
    glow_color_darkred, glow_color_red, glow_color_gold, glow_color_yellow, glow_color_darkgreen, glow_color_green, glow_color_darkaqua, glow_color_aqua, glow_color_darkblue, glow_color_blue, glow_color_darkpurple, glow_color_lightpurple, glow_color_white, glow_color_gray, glow_color_darkgray, glow_color_black, glow_mode_static, glow_mode_blink, glow_mode_rgbslow, glow_mode_rgbfast, glow_zonetime_lightshow, glow_zonetime_day, glow_zonetime_night, item_editor_glowbarrier_name, item_editor_glowbarrier_lore, item_editor_plainbarrier_name, item_editor_plainbarrier_lore, inventory_editormain_glow_name, inventory_editormain_glow_lore, inventory_editorglow_title, inventory_editorglow_back, inventory_editorglow_color_lore, inventory_editorglow_mode_name, inventory_editorglow_mode_lore, inventory_editorglow_plain_name, inventory_editorglow_plain_lore, inventory_editorglow_given, inventory_editorlightshow_levelbiome_name, inventory_editorlightshow_levelbiome_lore, inventory_editorlightshow_levelbiomeset, inventory_editorbiome_rain_name, inventory_editorbiome_rain_lore_on, inventory_editorbiome_rain_lore_off, inventory_editorbiome_daytime_name, inventory_editorbiome_daytime_lore, level_editor_glowbarrier_limit, level_editor_glowbarrier_plain, level_editor_glowbarrier_glowing, level_editor_wand_noselection, level_editor_wand_given,
    inventory_editormain_preview_name, inventory_editormain_preview_lore_on, inventory_editormain_preview_lore_off, inventory_editormain_preview_turnedon, inventory_editormain_preview_turnedoff,
    inventory_editormain_particledistance_name, inventory_editormain_particledistance_lore, inventory_editormain_particledistance_request, inventory_editormain_particledistance_invalid, inventory_editormain_particledistance_timeout, inventory_editormain_particledistance_unavilable, inventory_editormain_particledistance_success, inventory_editorglow_distance_name, inventory_editorglow_distance_lore, inventory_editorglow_distance_request, inventory_editorglow_distance_invalid, inventory_editorglow_distance_timeout, inventory_editorglow_distance_unavilable, inventory_editorglow_distance_success, item_editor_glowwand_name, item_editor_glowwand_lore, level_editor_glowwand_title, level_editor_glowwand_subtitle, direction_up, direction_down, direction_north, direction_south, direction_west, direction_east,
    inventory_editorlightshow_jumps_name, inventory_editorlightshow_jumps_lore, inventory_editorjumps_title, inventory_editorjumps_entry_name, inventory_editorjumps_entry_lore, inventory_editorjump_title, inventory_editorjump_mode_name, inventory_editorjump_mode_lore, inventory_editorjump_effect_name, inventory_editorjump_effect_lore_on, inventory_editorjump_effect_lore_off, inventory_editorlightshow_pcolors_name, inventory_editorlightshow_pcolors_lore, inventory_editorpcolors_title, inventory_editorpcolors_entry_name, inventory_editorpcolors_entry_lore, inventory_editorpcolor_title, inventory_editorpcolor_color_name, inventory_editorpcolor_color_lore, inventory_editorpcolor_request, inventory_editorpcolor_request_unavailable, inventory_editorpcolor_request_timeout, inventory_editorpcolor_request_invalid, inventory_editorjump_sound_name, inventory_editorjump_sound_lore_on, inventory_editorjump_sound_lore_off, inventory_editorjumpsound_title, inventory_editorjumpsound_entry_name, inventory_editorjumpsound_entry_lore, inventory_editorjumpsound_entry_selected, inventory_editorjumpsound_back, inventory_editorcompletion_title, inventory_editorcompletion_entry_name, inventory_editorcompletion_entry_lore, inventory_editorcompletion_entry_selected, inventory_editorcompletion_back, inventory_editorlightshow_win_name, inventory_editorlightshow_lose_name, inventory_editorjumpsound_names_swing, inventory_editorjumpsound_names_lightswing, inventory_editorjumpsound_names_explosion, inventory_editorjumpsound_names_woodhit, inventory_editorjumpsound_names_extend, inventory_editorjumpsound_names_quietbreak, inventory_editorjumpsound_names_dulljump, inventory_editorcompletion_particles_none, inventory_editorcompletion_particles_spores, inventory_editorcompletion_particles_dragonbreath, inventory_editorcompletion_particles_greendecay, inventory_editorcompletion_particles_witchflew, inventory_editorcompletion_particles_obsidian, inventory_editorcompletion_particles_blood, inventory_editorcompletion_particles_whiteflash, inventory_editorcompletion_particles_eyes, inventory_editorcompletion_particles_goldsplit, inventory_editorjump_effects_timepush, inventory_editorjump_effects_air, inventory_editorjump_effects_fire, inventory_editorjump_effects_sound, inventory_editorjump_modes_sequential, inventory_editorjump_modes_random, inventory_editormain_bossbar_hidden, inventory_editormain_bossbar_shown, inventory_editormain_borderpush_name, inventory_editormain_borderpush_lore, inventory_editormain_borderpush_request, inventory_editormain_borderpush_unavailable, inventory_editormain_borderpush_timeout, inventory_editormain_borderpush_invalid,
    worldedit_guard_denied, inventory_editorcoeditors_limit_trusted, inventory_editorcoeditors_limit_default,

    scoreboard_separator,
    scoreboard_idle_nickname, scoreboard_idle_score, scoreboard_idle_accuracy, scoreboard_idle_maxcombo,
    scoreboard_idle_map, scoreboard_idle_ping, scoreboard_idle_tps, scoreboard_idle_ip,
    scoreboard_play_progress, scoreboard_play_time, scoreboard_play_accuracy, scoreboard_play_combo,
    scoreboard_play_score, scoreboard_play_map, scoreboard_play_ping, scoreboard_play_tps, scoreboard_idle_mapnone, tablist_header, tablist_footer, inventory_createlevel_request_name, inventory_createlevel_timeout, inventory_createlevel_invalid_name,
    inventory_createlevel_generating, inventory_createlevel_dimension_overworld, inventory_createlevel_dimension_nether,
    inventory_createlevel_dimension_theend, inventory_createlevel_finished, inventory_edit_session_title, inventory_edit_session_params_name, inventory_edit_session_params_lore,
    inventory_edit_session_levels_name, inventory_edit_session_levels_lore,
    inventory_editormain_infiniterun_name, inventory_editormain_infiniterun_lore_on,
    inventory_editormain_infiniterun_lore_off, inventory_editormain_infiniterun_turnedon,
    inventory_editormain_infiniterun_turnedoff, inventory_levellist_item_name, inventory_levellist_item_lore,
    inventory_leveldetails_title, inventory_leveldetails_stats_name, inventory_leveldetails_stats_lore,
    inventory_leveldetails_play_name, inventory_leveldetails_play_lore, inventory_leveldetails_rate_name, inventory_leveldetails_rate_lore,
    inventory_levelrate_title, inventory_levellist_sort_name, inventory_levellist_sort_lore,
    inventory_levellist_sort_diff_asc, inventory_levellist_sort_diff_desc,
    inventory_levellist_sort_date_new, inventory_levellist_sort_date_old,
    inventory_levellist_displaymode_self_backtoplay, inventory_levellist_item_actions_self,
    inventory_levellist_feedback_name, inventory_levellist_feedback_lore,
    inventory_feedback_title, inventory_feedback_item_lore, inventory_levelrate_success_mod, inventory_levelrate_success_player,
    inventory_levelrate_error_player, inventory_levelrate_reset,
    level_editor_unmoderated_by_edit;;

    public static String[] locales;

    private final static String defaultlang = new String();

    private final static HashMap<String, String> replacelang = new HashMap<String, String>();

    public static void loadLang(File langfile) {
        byte[] buf = null;

        try {
            buf = new byte[0];
            InputStream in = LangOptions.class.getClassLoader().getResourceAsStream(langfile.getName());
            if (in != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(65536);
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    out.write(chunk, 0, read);
                }
                in.close();
                buf = out.toByteArray();
            }
        } catch (IOException e) {
        }

        SimpleConfiguration sc = new SimpleConfiguration(buf);
        String replacelangkey = "replacelang\0", localisationkey = "localisation\0", localisation = "default", localisation0 = localisationkey.concat(localisation).concat("\0");
        String[] replacelangkeys = sc.getSubKeys(replacelangkey);
        int i = replacelangkeys.length;
        replacelang.clear();
        while(--i > -1) {
            String replacekey = replacelangkeys[i].replace("\r", ""), replacekey0 = replacelangkey.concat(replacelangkeys[i]);
            String value = sc.getStringOrDefault(replacekey0, replacekey).replace("\r", "");
            replacelang.put(replacekey, value);
        }

        for (LangOptions lang : values()) {
            lang.text.clear();
            String langname = lang.name();
            String msg = sc.getStringOrDefault(localisation0.concat(langname.replace("_", "\0")), langname);
            msg = msg.replace("\r", "").replace("\\n", "\n");
            lang.text.put(defaultlang, msg);
        }
        ArrayList<String> alllocales = new ArrayList<>();
        alllocales.add(defaultlang);
        String[] localisationkeys = sc.getSubKeys(localisationkey);
        i = localisationkeys.length;
        while(--i > -1) {
            localisation = localisationkeys[i].replace("\r", "");
            alllocales.add(localisation);
            if(localisation.equals("default")) continue;
            localisation0 = localisationkey.concat(localisationkeys[i]).concat("\0");
            for (LangOptions lang : values()) {
                String msg = sc.getStringOrDefault(localisation0.concat(lang.name().replace("_", "\0")), null);
                if(msg==null) continue;
                msg = msg.replace("\r", "").replace("\\n", "\n");
                lang.text.put(localisation, msg);
            }
        }
        LangOptions.locales = alllocales.toArray(new String[alllocales.size()]);
    }

    private final Map<String, String> text = new HashMap<String, String>();

    public void sendMsg(CommandSender target, Placeholders... placeholders) {
        String locale = target instanceof Player ? getLocale((Player) target) : "default";
        Component msg = this.getComponent(locale, placeholders);
        target.sendMessage(msg);
    }
    public void sendMsgActionbar(Player target, Placeholders... placeholders) {
        String locale = getLocale((Player) target);
        Component msg = this.getComponent(locale, placeholders);
        target.sendActionBar(msg);
    }
    public Component getComponent(Player target, Placeholders... placeholders) {
        String locale = getLocale((Player) target);
        return this.getComponent(locale, placeholders);
    }

    public List<Component> getComponents(String locale, Placeholders... placeholders) {
        locale = replaceLocale(locale);
        String msg = null;

        msg = text.get(locale);
        if(msg==null) {
            msg = text.get(defaultlang);
        }
        if(msg==null || msg.isEmpty()) {
            return null;
        }

        String[] lines = msg.split("\n");
        List<Component> components = new ArrayList<>(lines.length);
        for(int i = 0;i < lines.length;++i) {
            String line = lines[i];
            if(line.isEmpty()) {
                components.add(Component.empty());
                continue;
            }
            for (Placeholders placeholder : placeholders) {
                line = line.replace(placeholder.placeholder, placeholder.value);
            }
            char startchar = line.charAt(0), endchar = line.charAt(line.length() - 1);
            if (startchar == '[' && endchar == ']' || startchar == '{' && endchar == '}') {
                components.add(GsonComponentSerializer.gson().deserialize(line));
                continue;
            }
            components.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
        }
        return components;
    }

    public String get(String locale, Placeholders... placeholders) {
        locale = replaceLocale(locale);
        String msg = null;

        msg = text.get(locale);
        if(msg==null) {
            msg = text.get(defaultlang);
        }

        if(msg==null || msg.isEmpty()) {
            return null;
        }

        for (Placeholders placeholder : placeholders) {
            msg = msg.replace(placeholder.placeholder, placeholder.value);
        }
        return msg;
    }

    public Component getComponent(String locale, Placeholders...placeholders) {
        String msg = this.get(locale, placeholders);
        char startchar = msg.charAt(0), endchar = msg.charAt(msg.length() - 1);
        if (startchar == '[' && endchar == ']' || startchar == '{' && endchar == '}') {
            return GsonComponentSerializer.gson().deserialize(msg);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
    }

    public static String replaceLocale(String locale) {
        String locale0 = replacelang.get(locale);
        return locale0 == null ? locale : locale0;
    }

    private static String getLocale(Player player) {
        return player.getLocale().toLowerCase();
    }

    public static class Placeholders {
        protected final String placeholder;
        protected final String value;

        public Placeholders(String placeholder, String value) {
            this.placeholder = placeholder;
            this.value = value;
        }
    }

    public static class FilteredPlaceholders extends Placeholders {

        private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

        public FilteredPlaceholders(String placeholder, String value) {
            super(placeholder, filterValue(value));
        }

        private static String filterValue(String value) {
            char[] chars = value.toCharArray();
            int i = chars.length;
            int j = (i << 2) + (i << 1);
            byte[] filtered = new byte[j];
            while(--i > -1) {
                int ch = chars[i];
                filtered[--j] = HEX[ch & 0x0F];
                byte r = 3;
                while(--r > -1) {
                    ch >>>= 4;
                    filtered[--j] = HEX[ch & 0x0F];
                }
                filtered[--j] = 'u';
                filtered[--j] = '\\';
            }
            return new String(filtered, StandardCharsets.US_ASCII);
        }
    }
}
