package ru.sortix.parkourbeat.utils.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
	command_usage, level_convertation_multiple, level_convertation_one_success, level_convertation_one_fail, level_editor_success_start, level_editor_success_stop, level_editor_test_fail_start, level_editor_test_fail_stop, level_editor_test_success_start, level_editor_test_success_stop, level_editor_cantedit_notowner, level_editor_cantedit_moderated, level_editor_cantedit_failload, level_editor_cantedit_editingnow, level_editor_cantedit_playersonlevel, level_editor_cantedit_failstart, level_editor_cantedit_failteleport, level_editor_delete_success, level_editor_delete_fail, level_editor_delete_already, level_editor_delete_useitem, level_editor_delete_notowner, level_play_resourcepackstatus_accepted, level_play_resourcepackstatus_failed, level_play_resourcepackstatus_declined, level_play_resourcepackstatus_success, level_play_title_preparing, level_play_title_stopped, level_play_title_fall, level_play_title_pressrun, level_play_title_complete, level_play_title_notsprinting, level_play_title_death, level_play_title_wrongangle, level_play_title_wrongdirection, level_play_unavilable, level_play_failload, level_play_alreadyinworld, level_play_failteleport, level_play_title_moveback, level_play_accuracy, level_play_progress, level_spectate_success, level_spectate_failload, level_spectate_alreadyinworld, level_prepare_spawninvalid_notify, level_prepare_spawninvalid_prevent, level_prepare_track_unavilable, level_prepare_track_sendfail, inventory_regularitems_close, inventory_regularitems_next, inventory_regularitems_previous, inventory_createlevel_title, inventory_createlevel_overworld, inventory_createlevel_nether, inventory_createlevel_theend, inventory_createlevel_cancel, inventory_createlevel_nopermission, inventory_createlevel_create_edit_start, inventory_createlevel_create_edit_unavilable, inventory_createlevel_create_edit_fail, inventory_createlevel_create_fail, inventory_editormain_title, inventory_editormain_particlecolor_name, inventory_editormain_particlecolor_lore, inventory_editormain_particlecolor_unavilable, inventory_editormain_particlecolor_timetochange, inventory_editormain_particlecolor_timeout, inventory_editormain_particlecolor_invalidhex, inventory_editormain_particlecolor_selectedcolor, inventory_editormain_selectsong_name, inventory_editormain_selectsong_notracklore, inventory_editormain_selectsong_lore, inventory_editormain_spawnpoint_name, inventory_editormain_spawnpoint_lore, inventory_editormain_spawnpoint_fail, inventory_editormain_spawnpoint_success, inventory_editormain_privacy_name, inventory_editormain_privacy_lore, inventory_editormain_resetpoints_name, inventory_editormain_resetpoints_lore, inventory_editormain_resetpoints_reset, inventory_editormain_exit_name, inventory_editormain_exit_lore, inventory_editormain_exit_canceled, inventory_editormain_delete_name, inventory_editormain_delete_lore, inventory_editormain_physic_name, inventory_editormain_physic_lore_turnon, inventory_editormain_physic_lore_turnoff, inventory_editormain_physic_turn_on, inventory_editormain_physic_turn_off, inventory_editorprivacy_title, inventory_editorprivacy_back, inventory_editorprivacy_visibility_name, inventory_editorprivacy_visibility_lore_public, inventory_editorprivacy_visibility_lore_private, inventory_editorprivacy_visibility_cantchange_moderated, inventory_editorprivacy_visibility_cantchange_blocked, inventory_editorprivacy_visibility_changedto_public, inventory_editorprivacy_visibility_changedto_private, inventory_editorprivacy_rename_name, inventory_editorprivacy_rename_lore, inventory_editorprivacy_rename_unavilable, inventory_editorprivacy_rename_timetochange, inventory_editorprivacy_rename_timeout, inventory_editorprivacy_rename_length_min, inventory_editorprivacy_rename_length_max, inventory_editorprivacy_rename_changed, inventory_editorprivacy_moderation_name, inventory_editorprivacy_moderation_lore_notmoderated, inventory_editorprivacy_moderation_lore_onmoderation, inventory_editorprivacy_moderation_lore_moderated, inventory_editorprivacy_moderation_requested_visibilitynotchanged, inventory_editorprivacy_moderation_requested_visibilitychanged, inventory_editorprivacy_moderation_moderated, inventory_editorsong_title, inventory_editorsong_nomusic_name, inventory_editorsong_nomusic_lore_selected, inventory_editorsong_nomusic_lore_notselected, inventory_editorsong_selectmusic_name, inventory_editorsong_selectmusic_lore_selected, inventory_editorsong_selectmusic_lore_notselected, inventory_editorsong_splitmode_name, inventory_editorsong_splitmode_lore_notrack, inventory_editorsong_splitmode_lore_pieces, inventory_editorsong_splitmode_lore_single_toggleavilable, inventory_editorsong_splitmode_lore_single_toggleunavilable, inventory_editorsong_resourcepackstatus_declined, inventory_editorsong_resourcepackstatus_failed, inventory_levellist_title, inventory_levellist_selectlevel_name, inventory_levellist_selectlevel_lore_uuid, inventory_levellist_selectlevel_lore_moderation_notmoderated, inventory_levellist_selectlevel_lore_moderation_onmoderation, inventory_levellist_selectlevel_lore_moderation_moderated, inventory_levellist_selectlevel_lore_displaymode_self_public, inventory_levellist_selectlevel_lore_displaymode_self_private, inventory_levellist_selectlevel_lore_displaymode_techinfo_public, inventory_levellist_selectlevel_lore_displaymode_techinfo_private, inventory_levellist_selectlevel_lore_uniqueid_number, inventory_levellist_selectlevel_lore_uniqueid_name, inventory_levellist_selectlevel_lore_creator_name, inventory_levellist_selectlevel_lore_creator_uuid, inventory_levellist_selectlevel_lore_creationtime_time, inventory_levellist_selectlevel_lore_creationtime_format, inventory_levellist_selectlevel_lore_track_is, inventory_levellist_selectlevel_lore_track_no, inventory_levellist_selectlevel_lore_actions_default, inventory_levellist_selectlevel_lore_actions_moderator, inventory_levellist_selectlevel_lore_actions_owner, inventory_levellist_selectlevel_lore_actions_tech, inventory_levellist_displaymode_moderation_selected, inventory_levellist_displaymode_moderation_unselected, inventory_levellist_displaymode_unranked_selected, inventory_levellist_displaymode_unranked_unselected, inventory_levellist_displaymode_ranked_selected, inventory_levellist_displaymode_ranked_unselected, inventory_levellist_displaymode_self_selected, inventory_levellist_displaymode_self_unselected, inventory_levellist_displaymode_self_createlevel, inventory_levellist_copyleveluuid, inventory_moderationrequest_title, inventory_moderationrequest_remove_name, inventory_moderationrequest_remove_lore, inventory_moderationrequest_removed, inventory_moderationrequest_moderated, inventory_moderationrequest_cancel_name, inventory_moderationrequest_cancel_lore, inventory_moderationconfirm_title, inventory_moderationconfirm_nopermission, inventory_moderationconfirm_notonmoderation, inventory_moderationconfirm_approved, inventory_moderationconfirm_rejected, inventory_moderationconfirm_saveerror, inventory_moderationconfirm_reject_name, inventory_moderationconfirm_reject_lore, inventory_moderationconfirm_approve_name, inventory_moderationconfirm_approve_lore, inventory_moderationconfirm_edit_name, inventory_moderationconfirm_cancel_name, inventory_moderationconfirm_cancel_lore, item_editor_test, item_editor_parameters, item_editor_points_item_name, item_editor_points_item_lore, item_editor_points_added, item_editor_points_removed, item_editor_points_minimumtwo;
	
	public static String[] locales;
	
	private final static String defaultlang = new String();
	
	private final static HashMap<String, String> replacelang = new HashMap<String, String>();
	
	public static void loadLang(File langfile) {
		byte[] buf = null;
		
		try {
			buf = new byte[32768];
			InputStream in = LangOptions.class.getClassLoader().getResourceAsStream(langfile.getName());
			if (in != null) {
				buf = Arrays.copyOf(buf, in.read(buf));
				in.close();
			}
		} catch (IOException e) {
		}
		
		SimpleConfiguration sc = new SimpleConfiguration(buf);
		String replacelangkey = "replacelang\0", localisationkey = "localisation\0", localisation = "default", localisation0 = localisationkey.concat(localisation).concat("\0");
		String[] replacelangkeys = sc.getSubKeys(replacelangkey);
		int i = replacelangkeys.length;
		replacelang.clear();
		while(--i > -1) {
			String replacekey = replacelangkeys[i], replacekey0 = replacelangkey.concat(replacekey);
			String value = sc.getStringOrDefault(replacekey0, replacekey);
			replacelang.put(replacekey, value);
		}
		
		for (LangOptions lang : values()) {
			lang.text.clear();
			String langname = lang.name();
			String msg = sc.getStringOrDefault(localisation0.concat(langname.replace("_", "\0")), langname);
			msg = msg.replace("\\n", "\n");
			lang.text.put(defaultlang, msg);
		}
		ArrayList<String> alllocales = new ArrayList<>();
		alllocales.add(defaultlang);
		String[] localisationkeys = sc.getSubKeys(localisationkey);
		i = localisationkeys.length;
		while(--i > -1) {
			localisation = localisationkeys[i];
			alllocales.add(localisation);
			if(localisation.equals("default")) continue;
			localisation0 = localisationkey.concat(localisation).concat("\0");
			for (LangOptions lang : values()) {
				String msg = sc.getStringOrDefault(localisation0.concat(lang.name().replace("_", "\0")), null);
				if(msg==null) continue;
				msg = msg.replace("\\n", "\n");
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
	
}