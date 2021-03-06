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

package me.lucko.luckperms.bukkit.model;

import lombok.Getter;

import com.google.common.collect.ImmutableMap;

import me.lucko.luckperms.api.Tristate;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DefaultsProvider {

    @Getter
    private Map<String, Boolean> opDefaults = ImmutableMap.of();
    private final DummyPermissible opDummy = new DummyPermissible(this::refreshOp);
    
    @Getter
    private Map<String, Boolean> nonOpDefaults = ImmutableMap.of();
    private final DummyPermissible nonOpDummy = new DummyPermissible(this::refreshNonOp);

    public void refresh() {
        refreshOp();
        refreshNonOp();
    }

    private void refreshOp() {
        unregisterDefaults(opDefaults, opDummy);

        Map<String, Boolean> builder = new HashMap<>();
        calculateDefaults(builder, opDummy, true);

        opDefaults = ImmutableMap.copyOf(builder);
    }

    private void refreshNonOp() {
        unregisterDefaults(nonOpDefaults, nonOpDummy);

        Map<String, Boolean> builder = new HashMap<>();
        calculateDefaults(builder, nonOpDummy, false);

        nonOpDefaults = ImmutableMap.copyOf(builder);
    }

    public void close() {
        unregisterDefaults(opDefaults, opDummy);
        unregisterDefaults(nonOpDefaults, nonOpDummy);
    }

    public Tristate hasDefault(String permission, boolean isOp) {
        Map<String, Boolean> map = isOp ? opDefaults : nonOpDefaults;

        Boolean b = map.get(permission);
        return b == null ? Tristate.UNDEFINED : Tristate.fromBoolean(b);
    }

    public int size() {
        return opDefaults.size() + nonOpDefaults.size();
    }

    private static void unregisterDefaults(Map<String, Boolean> map, DummyPermissible p) {
        Set<String> perms = map.keySet();

        for (String name : perms) {
            Bukkit.getServer().getPluginManager().unsubscribeFromPermission(name, p);
        }

        Bukkit.getServer().getPluginManager().unsubscribeFromDefaultPerms(false, p);
        Bukkit.getServer().getPluginManager().unsubscribeFromDefaultPerms(true, p);
    }

    private static void calculateDefaults(Map<String, Boolean> map, DummyPermissible p, boolean op) {
        Set<Permission> defaults = Bukkit.getServer().getPluginManager().getDefaultPermissions(op);
        Bukkit.getServer().getPluginManager().subscribeToDefaultPerms(op, p);

        for (Permission perm : defaults) {
            String name = perm.getName().toLowerCase();
            map.put(name, true);
            Bukkit.getServer().getPluginManager().subscribeToPermission(name, p);
            calculateChildPermissions(map, p, perm.getChildren(), false);
        }
    }

    private static void calculateChildPermissions(Map<String, Boolean> map, DummyPermissible p, Map<String, Boolean> children, boolean invert) {
        for (Map.Entry<String, Boolean> e : children.entrySet()) {
            Permission perm = Bukkit.getServer().getPluginManager().getPermission(e.getKey());
            boolean value = e.getValue() ^ invert;
            String lName = e.getKey().toLowerCase();

            map.put(lName, value);
            Bukkit.getServer().getPluginManager().subscribeToPermission(e.getKey(), p);

            if (perm != null) {
                calculateChildPermissions(map, p, perm.getChildren(), !value);
            }
        }
    }

}
