/*
 * 琴谱浮窗 (Qinpu Overlay) - Android 版
 * 作者: Fc100700
 * GitHub: https://github.com/Fc100700
 */

package com.qinpu.overlay;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SetStore {
    static final String KEY_SETS = "sets";
    static final String KEY_CURRENT = "currentSet";
    static final String KEY_URIS = "uris";
    static final String DEFAULT_NAME = "默认";
    static final String FALLBACK_NAME = "琴谱1";

    static class SetItem {
        String name;
        final ArrayList<String> uris = new ArrayList<>();
        boolean hasState;
        int stX = Integer.MIN_VALUE;
        int stY = Integer.MIN_VALUE;
        float stScale = 0.72f;
        int stAdjB = 0;
        int stAdjC = 0;
        float stAdjG = 1.0f;

        boolean isGrid;
        final ArrayList<String> gridCombos = new ArrayList<>();
        int gRows = 0;
        int gCols = 4;
        int gLayout = 0;
        int gBlank = -1;
        int gKey = -16777216;
        int gLine = -16777216;
        int gBorder = -16777216;
        int gCellDp = 16;
        int gGapPct = 60;
        int gLineDp = 1;
        int gBorderDp = 2;

        SetItem(String name) {
            this.name = name;
        }

        int comboCount() {
            return this.gridCombos.size();
        }
    }

    private SetStore() {
    }

    static List<SetItem> load(SharedPreferences prefs) {
        ArrayList<SetItem> list = new ArrayList<>();
        String raw = prefs.getString(KEY_SETS, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    String name = o.optString("name", "");
                    if (name.isEmpty()) {
                        continue;
                    }
                    SetItem item = new SetItem(name);
                    JSONArray us = o.optJSONArray("uris");
                    if (us != null) {
                        for (int j = 0; j < us.length(); j++) {
                            String u = us.optString(j, "");
                            if (!u.isEmpty()) {
                                item.uris.add(u);
                            }
                        }
                    }
                    if (o.has("scale")) {
                        item.hasState = true;
                        item.stX = o.optInt("x", Integer.MIN_VALUE);
                        item.stY = o.optInt("y", Integer.MIN_VALUE);
                        item.stScale = (float) o.optDouble("scale", 0.72d);
                        item.stAdjB = o.optInt("adjB", 0);
                        item.stAdjC = o.optInt("adjC", 0);
                        item.stAdjG = (float) o.optDouble("adjG", 1.0d);
                    }
                    item.isGrid = o.optBoolean("isGrid", false);
                    JSONArray gs = o.optJSONArray("grid");
                    if (gs != null) {
                        for (int j = 0; j < gs.length(); j++) {
                            item.gridCombos.add(gs.optString(j, ""));
                        }
                    }
                    item.gRows = o.optInt("gRows", 0);
                    item.gCols = o.optInt("gCols", 4);
                    item.gLayout = o.optInt("gLayout", 0);
                    item.gBlank = o.optInt("gBlank", -1);
                    item.gKey = o.optInt("gKey", -16777216);
                    item.gLine = o.optInt("gLine", -16777216);
                    item.gBorder = o.optInt("gBorder", -16777216);
                    item.gCellDp = o.optInt("gCell", 16);
                    item.gGapPct = o.optInt("gGap", 60);
                    item.gLineDp = o.optInt("gLineW", 1);
                    item.gBorderDp = o.optInt("gBorderW", 2);
                    if (item.isGrid && item.gridCombos.isEmpty()) {
                        item.isGrid = false;
                    }
                    list.add(item);
                }
            } catch (Exception ignored) {
            }
        }
        if (list.isEmpty()) {
            ArrayList<String> legacy = splitUris(prefs.getString(KEY_URIS, ""));
            if (!legacy.isEmpty()) {
                SetItem item = new SetItem(DEFAULT_NAME);
                item.uris.addAll(legacy);
                list.add(item);
                save(prefs, list);
                prefs.edit().putString(KEY_CURRENT, DEFAULT_NAME).apply();
            }
        }
        migrateCurrentState(prefs, list);
        return list;
    }

    private static void migrateCurrentState(SharedPreferences prefs, List<SetItem> list) {
        if (list.isEmpty()) {
            return;
        }
        SetItem item = find(list, currentName(prefs, list));
        if (item == null || item.hasState) {
            return;
        }
        item.hasState = true;
        item.stX = prefs.getInt("x", Integer.MIN_VALUE);
        item.stY = prefs.getInt("y", Integer.MIN_VALUE);
        item.stScale = prefs.getFloat("scale", 0.72f);
        item.stAdjB = prefs.getInt("adjB", 0);
        item.stAdjC = prefs.getInt("adjC", 0);
        item.stAdjG = prefs.getFloat("adjG", 1.0f);
        save(prefs, list);
    }

    static void save(SharedPreferences prefs, List<SetItem> list) {
        JSONArray arr = new JSONArray();
        for (SetItem item : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", item.name);
                JSONArray us = new JSONArray();
                for (String u : item.uris) {
                    us.put(u);
                }
                o.put("uris", us);
                if (item.hasState) {
                    o.put("x", item.stX);
                    o.put("y", item.stY);
                    o.put("scale", item.stScale);
                    o.put("adjB", item.stAdjB);
                    o.put("adjC", item.stAdjC);
                    o.put("adjG", item.stAdjG);
                }
                if (item.isGrid) {
                    o.put("isGrid", true);
                    JSONArray gs = new JSONArray();
                    for (String g : item.gridCombos) {
                        gs.put(g);
                    }
                    o.put("grid", gs);
                    o.put("gRows", item.gRows);
                    o.put("gCols", item.gCols);
                    o.put("gLayout", item.gLayout);
                    o.put("gBlank", item.gBlank);
                    o.put("gKey", item.gKey);
                    o.put("gLine", item.gLine);
                    o.put("gBorder", item.gBorder);
                    o.put("gCell", item.gCellDp);
                    o.put("gGap", item.gGapPct);
                    o.put("gLineW", item.gLineDp);
                    o.put("gBorderW", item.gBorderDp);
                }
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY_SETS, arr.toString()).apply();
    }

    static ArrayList<String> splitUris(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split("\n")) {
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    static SetItem find(List<SetItem> list, String name) {
        if (name == null) {
            return null;
        }
        for (SetItem item : list) {
            if (item.name.equals(name)) {
                return item;
            }
        }
        return null;
    }

    static String currentName(SharedPreferences prefs, List<SetItem> list) {
        String name = prefs.getString(KEY_CURRENT, "");
        if (find(list, name) != null) {
            return name;
        }
        return list.isEmpty() ? "" : list.get(0).name;
    }

    static void activate(SharedPreferences prefs, SetItem item) {
        String strPrev = prefs.getString(KEY_CURRENT, "");
        if (!strPrev.isEmpty() && !strPrev.equals(item.name)) {
            captureStateFromPrefs(prefs, strPrev);
        }
        List<SetItem> fresh = load(prefs);
        SetItem target = find(fresh, item.name);
        if (target == null) {
            target = item;
        }
        StringBuilder sb = new StringBuilder();
        for (String u : target.uris) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(u);
        }
        prefs.edit().putString(KEY_URIS, sb.toString()).putString(KEY_CURRENT, target.name).putInt("page", 0).apply();
        applyStateToPrefs(prefs, target);
    }

    static void applyStateToPrefs(SharedPreferences prefs, SetItem item) {
        SharedPreferences.Editor editorEdit = prefs.edit();
        if (item.hasState) {
            editorEdit.putInt("x", item.stX).putInt("y", item.stY).putFloat("scale", item.stScale).putInt("adjB", item.stAdjB).putInt("adjC", item.stAdjC).putFloat("adjG", item.stAdjG);
        } else {
            editorEdit.putInt("x", Integer.MIN_VALUE).putInt("y", Integer.MIN_VALUE).putFloat("scale", 0.72f).putInt("adjB", 0).putInt("adjC", 0).putFloat("adjG", 1.0f);
        }
        editorEdit.apply();
    }

    static void captureStateFromPrefs(SharedPreferences prefs, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        List<SetItem> list = load(prefs);
        SetItem item = find(list, name);
        if (item == null) {
            return;
        }
        item.hasState = true;
        item.stX = prefs.getInt("x", Integer.MIN_VALUE);
        item.stY = prefs.getInt("y", Integer.MIN_VALUE);
        item.stScale = prefs.getFloat("scale", 0.72f);
        item.stAdjB = prefs.getInt("adjB", 0);
        item.stAdjC = prefs.getInt("adjC", 0);
        item.stAdjG = prefs.getFloat("adjG", 1.0f);
        save(prefs, list);
    }

    static String uniqueName(List<SetItem> list, String base) {
        String name = base;
        int n = 2;
        while (find(list, name) != null) {
            name = base + " " + n;
            n++;
        }
        return name;
    }
}
