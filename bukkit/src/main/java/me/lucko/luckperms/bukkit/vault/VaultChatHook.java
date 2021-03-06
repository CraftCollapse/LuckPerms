/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.bukkit.vault;

import lombok.NonNull;

import me.lucko.luckperms.api.ChatMetaType;
import me.lucko.luckperms.api.Contexts;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.caching.MetaData;
import me.lucko.luckperms.common.caching.MetaAccumulator;
import me.lucko.luckperms.common.core.NodeFactory;
import me.lucko.luckperms.common.core.model.Group;
import me.lucko.luckperms.common.core.model.PermissionHolder;
import me.lucko.luckperms.common.core.model.User;
import me.lucko.luckperms.common.utils.ExtractedContexts;

import net.milkbowl.vault.chat.Chat;

import java.util.Map;

/**
 * LuckPerms Vault Chat implementation
 * All user lookups are cached.
 */
public class VaultChatHook extends Chat {
    private final VaultPermissionHook perms;

    VaultChatHook(VaultPermissionHook perms) {
        super(perms);
        this.perms = perms;
    }

    public String getName() {
        return perms.getName();
    }

    public boolean isEnabled() {
        return perms.isEnabled();
    }

    private void setMeta(PermissionHolder holder, String world, String node, String value) {
        String finalWorld = perms.isIgnoreWorld() ? null : world;
        if (holder == null || node.equals("")) return;

        perms.log("Setting meta: '" + node + "' for " + holder.getObjectName() + " on world " + world + ", server " + perms.getServer());

        perms.getScheduler().execute(() -> {
            holder.removeIf(n -> n.isMeta() && n.getMeta().getKey().equals(node));

            Node.Builder metaNode = NodeFactory.makeMetaNode(node, value).setValue(true);
            if (!perms.getServer().equalsIgnoreCase("global")) {
                metaNode.setServer(perms.getServer());
            }
            if (finalWorld != null && !finalWorld.equals("") && !finalWorld.equals("global")) {
                metaNode.setWorld(finalWorld);
            }

            holder.setPermission(metaNode.build());
            perms.save(holder);
        });
    }

    private void setChatMeta(ChatMetaType type, PermissionHolder holder, String value, String world) {
        String finalWorld = perms.isIgnoreWorld() ? null : world;
        if (holder == null) return;
        if (value.equals("")) return;

        perms.log("Setting " + type.name().toLowerCase() + " for " + holder.getObjectName() + " on world " + world + ", server " + perms.getServer());

        perms.getScheduler().execute(() -> {
            // remove all prefixes/suffixes directly set on the user/group
            holder.removeIf(type::matches);

            // find the max inherited priority & add 10
            MetaAccumulator metaAccumulator = holder.accumulateMeta(null, null, ExtractedContexts.generate(perms.createContextForWorldSet(finalWorld)));
            int priority = (type == ChatMetaType.PREFIX ? metaAccumulator.getPrefixes() : metaAccumulator.getSuffixes()).keySet().stream()
                    .mapToInt(e -> e).max().orElse(0) + 10;

            Node.Builder chatMetaNode = NodeFactory.makeChatMetaNode(type, priority, value);
            if (!perms.getServer().equalsIgnoreCase("global")) {
                chatMetaNode.setServer(perms.getServer());
            }
            if (finalWorld != null && !finalWorld.equals("") && !finalWorld.equals("global")) {
                chatMetaNode.setWorld(finalWorld);
            }

            holder.setPermission(chatMetaNode.build());
            perms.save(holder);
        });
    }

    private String getUserMeta(User user, String world, String node, String defaultValue) {
        if (user == null) return defaultValue;
        world = perms.isIgnoreWorld() ? null : world;

        perms.log("Getting meta: '" + node + "' for user " + user.getFriendlyName() + " on world " + world + ", server " + perms.getServer());

        String ret = user.getUserData().getMetaData(perms.createContextForWorldLookup(perms.getPlugin().getPlayer(user), world)).getMeta().get(node);
        return ret != null ? ret : defaultValue;
    }

    private String getUserChatMeta(ChatMetaType type, User user, String world) {
        if (user == null) return "";
        world = perms.isIgnoreWorld() ? null : world;

        perms.log("Getting " + type.name().toLowerCase() + " for user " + user.getFriendlyName() + " on world " + world + ", server " + perms.getServer());

        MetaData data = user.getUserData().getMetaData(perms.createContextForWorldLookup(perms.getPlugin().getPlayer(user), world));
        String ret = type == ChatMetaType.PREFIX ? data.getPrefix() : data.getSuffix();
        return ret != null ? ret : "";
    }

    private String getGroupMeta(Group group, String world, String node, String defaultValue) {
        world = perms.isIgnoreWorld() ? null : world;
        if (group == null || node.equals("")) return defaultValue;

        perms.log("Getting meta: '" + node + "' for group " + group.getName() + " on world " + world + ", server " + perms.getServer());

        for (Node n : group.mergePermissionsToList()) {
            if (!n.getValue()) continue;
            if (!n.isMeta()) continue;
            if (!n.shouldApplyWithContext(perms.createContextForWorldLookup(world).getContexts())) continue;

            Map.Entry<String, String> meta = n.getMeta();
            if (meta.getKey().equalsIgnoreCase(node)) {
                return meta.getValue();
            }
        }

        return defaultValue;
    }

    private String getGroupChatMeta(ChatMetaType type, Group group, String world) {
        world = perms.isIgnoreWorld() ? null : world;
        if (group == null) return "";

        perms.log("Getting " + type.name().toLowerCase() + " for group " + group + " on world " + world + ", server " + perms.getServer());

        int priority = Integer.MIN_VALUE;
        String meta = null;

        ExtractedContexts ec = ExtractedContexts.generate(Contexts.of(perms.createContextForWorldLookup(world).getContexts(), perms.isIncludeGlobal(), true, true, true, true, false));
        for (Node n : group.getAllNodes(ec)) {
            if (!n.getValue()) continue;
            if (type.shouldIgnore(n)) continue;
            if (!n.shouldApplyWithContext(perms.createContextForWorldLookup(world).getContexts())) continue;

            Map.Entry<Integer, String> value = type.getEntry(n);
            if (value.getKey() > priority) {
                meta = value.getValue();
                priority = value.getKey();
            }
        }

        return meta != null ? meta : "";
    }

    @Override
    public String getPlayerPrefix(String world, @NonNull String player) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        return getUserChatMeta(ChatMetaType.PREFIX, user, world);
    }

    @Override
    public void setPlayerPrefix(String world, @NonNull String player, @NonNull String prefix) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        setChatMeta(ChatMetaType.PREFIX, user, prefix, world);
    }

    @Override
    public String getPlayerSuffix(String world, @NonNull String player) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        return getUserChatMeta(ChatMetaType.SUFFIX, user, world);
    }

    @Override
    public void setPlayerSuffix(String world, @NonNull String player, @NonNull String suffix) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        setChatMeta(ChatMetaType.SUFFIX, user, suffix, world);
    }

    @Override
    public String getGroupPrefix(String world, @NonNull String group) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        return getGroupChatMeta(ChatMetaType.PREFIX, g, world);
    }

    @Override
    public void setGroupPrefix(String world, @NonNull String group, @NonNull String prefix) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        setChatMeta(ChatMetaType.PREFIX, g, prefix, world);
    }

    @Override
    public String getGroupSuffix(String world, @NonNull String group) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        return getGroupChatMeta(ChatMetaType.SUFFIX, g, world);
    }

    @Override
    public void setGroupSuffix(String world, @NonNull String group, @NonNull String suffix) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        setChatMeta(ChatMetaType.SUFFIX, g, suffix, world);
    }

    @Override
    public int getPlayerInfoInteger(String world, @NonNull String player, @NonNull String node, int defaultValue) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        try {
            return Integer.parseInt(getUserMeta(user, world, node, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void setPlayerInfoInteger(String world, @NonNull String player, @NonNull String node, int value) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        setMeta(user, world, node, String.valueOf(value));
    }

    @Override
    public int getGroupInfoInteger(String world, @NonNull String group, @NonNull String node, int defaultValue) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        try {
            return Integer.parseInt(getGroupMeta(g, world, node, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void setGroupInfoInteger(String world, @NonNull String group, @NonNull String node, int value) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        setMeta(g, world, node, String.valueOf(value));
    }

    @Override
    public double getPlayerInfoDouble(String world, @NonNull String player, @NonNull String node, double defaultValue) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        try {
            return Double.parseDouble(getUserMeta(user, world, node, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void setPlayerInfoDouble(String world, @NonNull String player, @NonNull String node, double value) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        setMeta(user, world, node, String.valueOf(value));
    }

    @Override
    public double getGroupInfoDouble(String world, @NonNull String group, @NonNull String node, double defaultValue) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        try {
            return Double.parseDouble(getGroupMeta(g, world, node, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void setGroupInfoDouble(String world, @NonNull String group, @NonNull String node, double value) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        setMeta(g, world, node, String.valueOf(value));
    }

    @Override
    public boolean getPlayerInfoBoolean(String world, @NonNull String player, @NonNull String node, boolean defaultValue) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        String s = getUserMeta(user, world, node, String.valueOf(defaultValue));
        if (!s.equalsIgnoreCase("true") && !s.equalsIgnoreCase("false")) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s);
    }

    @Override
    public void setPlayerInfoBoolean(String world, @NonNull String player, @NonNull String node, boolean value) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        setMeta(user, world, node, String.valueOf(value));
    }

    @Override
    public boolean getGroupInfoBoolean(String world, @NonNull String group, @NonNull String node, boolean defaultValue) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        String s = getGroupMeta(g, world, node, String.valueOf(defaultValue));
        if (!s.equalsIgnoreCase("true") && !s.equalsIgnoreCase("false")) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s);
    }

    @Override
    public void setGroupInfoBoolean(String world, @NonNull String group, @NonNull String node, boolean value) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        setMeta(g, world, node, String.valueOf(value));
    }

    @Override
    public String getPlayerInfoString(String world, @NonNull String player, @NonNull String node, String defaultValue) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        return getUserMeta(user, world, node, defaultValue);
    }

    @Override
    public void setPlayerInfoString(String world, @NonNull String player, @NonNull String node, String value) {
        final User user = perms.getPlugin().getUserManager().getByUsername(player);
        setMeta(user, world, node, value);
    }

    @Override
    public String getGroupInfoString(String world, @NonNull String group, @NonNull String node, String defaultValue) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        return getGroupMeta(g, world, node, defaultValue);
    }

    @Override
    public void setGroupInfoString(String world, @NonNull String group, @NonNull String node, String value) {
        final Group g = perms.getPlugin().getGroupManager().getIfLoaded(group);
        setMeta(g, world, node, value);
    }

}
