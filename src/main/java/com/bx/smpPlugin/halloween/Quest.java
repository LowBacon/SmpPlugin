// SmpPlugin by LowBacon
package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.core.Reward;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record Quest(String id, String name, List<String> description, QuestType type, String target, int amount,
                    long candy, Reward reward, String icon) {

    public Quest {
        description = List.copyOf(description);
    }

    public boolean counts(QuestType action, String actionTarget) {
        if (action != type) {
            return false;
        }
        return !type.needsTarget() || target != null && target.equalsIgnoreCase(actionTarget);
    }

    public static List<Quest> readAll(ConfigurationSection section, List<String> problems) {
        List<Quest> quests = new ArrayList<>();
        if (section == null) {
            return quests;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection quest = section.getConfigurationSection(id);
            if (quest == null) {
                continue;
            }
            QuestType type = QuestType.parse(quest.getString("TYPE"));
            if (type == null) {
                problems.add("Quest '" + id + "' has an unknown TYPE " + quest.getString("TYPE") + ".");
                continue;
            }
            String target = quest.getString("TARGET");
            if (type.needsTarget() && (target == null || target.isBlank())) {
                problems.add("Quest '" + id + "' (" + type + ") needs a TARGET.");
                continue;
            }
            int amount = quest.getInt("AMOUNT", 0);
            if (amount <= 0) {
                problems.add("Quest '" + id + "' needs an AMOUNT above 0.");
                continue;
            }
            List<String> description = quest.getStringList("DESCRIPTION");
            quests.add(new Quest(id.toLowerCase(Locale.ROOT), quest.getString("NAME", "&6" + id), description, type,
                    target == null ? null : target.trim().toUpperCase(Locale.ROOT), amount,
                    Math.max(0L, quest.getLong("CANDY", 0L)), Reward.read(quest.getConfigurationSection("REWARD")),
                    quest.getString("ICON", "PAPER")));
        }
        return quests;
    }
}
