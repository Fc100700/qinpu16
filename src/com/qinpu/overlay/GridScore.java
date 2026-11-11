/*
 * 琴谱浮窗 (Qinpu Overlay) - Android 版
 * 作者: Fc100700
 * GitHub: https://github.com/Fc100700
 */

package com.qinpu.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.TreeSet;

/* JADX INFO: grid score (格子谱) parser and renderer.
 * File format: ['1,4,10', '1', '3']  (每个字符串是一个 3x5 组合，编号 1~15，左上为 1，行优先)
 * 也支持每行一个组合（1,4,10）或 15 位 01 掩码。 */
final class GridScore {
    static final int LAYOUT_SINGLE = 0;
    static final int LAYOUT_LR = 1;
    static final int LAYOUT_TB = 2;

    private GridScore() {
    }

    static ArrayList<String> parse(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        String text = raw.replace('\uFEFF', ' ').replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201C', '"').replace('\u201D', '"');
        ArrayList<String> quoted = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < text.length()) {
                    cur.append(text.charAt(i + 1));
                    i++;
                } else if (c == quote) {
                    quoted.add(cur.toString());
                    cur.setLength(0);
                    quote = 0;
                } else {
                    cur.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                cur.setLength(0);
            }
        }
        if (!quoted.isEmpty()) {
            for (String s : quoted) {
                out.add(normalizeCombo(s));
            }
            return out;
        }
        String stripped = text.replace('[', '\n').replace(']', '\n').replace(';', '\n');
        for (String line : stripped.split("[\r\n]+")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (t.endsWith(",")) {
                t = t.substring(0, t.length() - 1).trim();
            }
            out.add(normalizeCombo(t));
        }
        return out;
    }

    static String normalizeCombo(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.isEmpty() || t.equals("-") || t.equals("_")) {
            return "";
        }
        TreeSet<Integer> set = new TreeSet<>();
        if (t.matches("[01]{15}")) {
            for (int i = 0; i < 15; i++) {
                if (t.charAt(i) == '1') {
                    set.add(Integer.valueOf(i + 1));
                }
            }
        } else {
            for (String p : t.replaceAll("[^0-9]", " ").trim().split("\\s+")) {
                if (p.isEmpty()) {
                    continue;
                }
                try {
                    int v = Integer.parseInt(p);
                    if (v >= 1 && v <= 15) {
                        set.add(Integer.valueOf(v));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Integer v : set) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v.intValue());
        }
        return sb.toString();
    }

    static boolean[] maskOf(String combo) {
        boolean[] on = new boolean[16];
        if (combo == null) {
            return on;
        }
        for (String p : combo.split(",")) {
            try {
                int v = Integer.parseInt(p.trim());
                if (v >= 1 && v <= 15) {
                    on[v] = true;
                }
            } catch (Exception ignored) {
            }
        }
        return on;
    }

    private static int[] gridDims(int count, int cols, int rowsFixed) {
        if (count <= 0) {
            return new int[]{0, 0};
        }
        int c = Math.max(1, Math.min(16, cols));
        int r;
        if (rowsFixed > 0) {
            r = Math.min(32, rowsFixed);
            if (r * c < count) {
                c = (count + r - 1) / r;
            }
        } else {
            r = (count + c - 1) / c;
            c = (count + r - 1) / r;
        }
        return new int[]{c, r};
    }

    static int pageCount(SetStore.SetItem item) {
        if (item == null || item.gridCombos.isEmpty()) {
            return 0;
        }
        if (item.gRows <= 0) {
            return 1;
        }
        int per = perPage(item);
        return Math.max(1, (item.gridCombos.size() + per - 1) / per);
    }

    private static int perPage(SetStore.SetItem item) {
        return Math.max(1, Math.min(16, item.gCols)) * Math.min(32, Math.max(1, item.gRows));
    }

    static Bitmap render(Context ctx, SetStore.SetItem item, int screenW, int screenH, int page) {
        if (item == null || item.gridCombos.isEmpty()) {
            return null;
        }
        float density = ctx.getResources().getDisplayMetrics().density;
        int cell = Math.max(6, Math.round(item.gCellDp * density));
        int lineW = Math.max(1, Math.round(item.gLineDp * density));
        int borderW = Math.max(1, Math.round(item.gBorderDp * density));
        int gap = Math.max(0, Math.round(cell * (Math.max(0, Math.min(200, item.gGapPct)) / 100.0f)));
        int blockW = cell * 5;
        int blockH = cell * 3;
        int pad = borderW / 2 + 2;
        int n = item.gridCombos.size();
        int shortPx = Math.min(screenW, screenH);

        try {
            int pages = pageCount(item);
            int per = item.gRows > 0 ? perPage(item) : n;
            int pg = Math.max(0, Math.min(page, pages - 1));
            int pageStart = pg * per;
            int pageEnd = Math.min(n, pageStart + per);
            ArrayList<String> pageCombos = new ArrayList<>();
            for (int i = pageStart; i < pageEnd; i++) {
                pageCombos.add(item.gridCombos.get(i));
            }

            if (item.gLayout == LAYOUT_SINGLE) {
                int cols = Math.max(1, Math.min(16, item.gCols));
                int rows;
                if (item.gRows > 0) {
                    rows = Math.min(32, item.gRows);
                } else {
                    rows = Math.max(1, (pageCombos.size() + cols - 1) / cols);
                    cols = Math.max(1, (pageCombos.size() + rows - 1) / rows);
                }
                int cw = pad * 2 + cols * blockW + (cols - 1) * gap;
                int ch = pad * 2 + rows * blockH + (rows - 1) * gap;
                Bitmap bmp = Bitmap.createBitmap(Math.max(1, cw), Math.max(1, ch), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                for (int i = 0; i < pageCombos.size(); i++) {
                    int x = pad + (i % cols) * (blockW + gap);
                    int y = pad + (i / cols) * (blockH + gap);
                    drawBlock(canvas, x, y, pageCombos.get(i), item, cell, lineW, borderW);
                }
                return bmp;
            }

            int nomPer = item.gRows > 0 ? per : n;
            int nomA = (nomPer + 1) / 2;
            int nomB = nomPer - nomA;
            int countA = (pageCombos.size() + 1) / 2;
            int countB = pageCombos.size() - countA;
            int[] dimA = gridDims(nomA, item.gCols, 0);
            int[] dimB = gridDims(nomB, item.gCols, 0);

            int canvasW = shortPx;
            int canvasH = Math.max(64, Math.round(shortPx * (float) screenH / Math.max(1, screenW)));
            int maxW = item.gLayout == LAYOUT_LR ? (canvasW - pad * 4) / 2 : canvasW - pad * 4;
            int maxH = item.gLayout == LAYOUT_LR ? (canvasH - pad * 2) : Math.max(48, (canvasH - pad * 3) / 2);
            int cellA = fitCellH(fitCell(cell, dimA[0], item.gGapPct, maxW), dimA[1], item.gGapPct, maxH);
            int cellB = fitCellH(fitCell(cell, dimB[0], item.gGapPct, maxW), dimB[1], item.gGapPct, maxH);
            int gapA = Math.max(0, Math.round(cellA * (Math.max(0, Math.min(200, item.gGapPct)) / 100.0f)));
            int gapB = Math.max(0, Math.round(cellB * (Math.max(0, Math.min(200, item.gGapPct)) / 100.0f)));
            int wA = dimA[0] * cellA * 5 + Math.max(0, dimA[0] - 1) * gapA;
            int hA = dimA[1] * cellA * 3 + Math.max(0, dimA[1] - 1) * gapA;
            int wB = dimB[0] * cellB * 5 + Math.max(0, dimB[0] - 1) * gapB;
            int hB = dimB[1] * cellB * 3 + Math.max(0, dimB[1] - 1) * gapB;

            Bitmap bmp = Bitmap.createBitmap(Math.max(1, canvasW), Math.max(1, canvasH), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);

            if (item.gLayout == LAYOUT_LR) {
                drawGroup(canvas, pad, Math.max(pad, (canvasH - hA) / 2), pageCombos, 0, countA, dimA, item, cellA, lineW, borderW);
                if (countB > 0) {
                    drawGroup(canvas, canvasW - pad - wB, Math.max(pad, (canvasH - hB) / 2), pageCombos, countA, countB, dimB, item, cellB, lineW, borderW);
                }
            } else {
                drawGroup(canvas, (canvasW - wA) / 2, pad, pageCombos, 0, countA, dimA, item, cellA, lineW, borderW);
                if (countB > 0) {
                    drawGroup(canvas, (canvasW - wB) / 2, Math.max(pad, canvasH - pad - hB), pageCombos, countA, countB, dimB, item, cellB, lineW, borderW);
                }
            }
            return bmp;
        } catch (Throwable unused) {
            return null;
        }
    }

    private static int fitCell(int cell, int cols, int gapPct, int maxW) {
        if (cols <= 0 || maxW <= 0) {
            return cell;
        }
        float gapF = Math.max(0, Math.min(200, gapPct)) / 100.0f;
        float wanted = cols * 5 * cell + Math.max(0, cols - 1) * (cell * gapF);
        if (wanted <= maxW) {
            return cell;
        }
        float fitted = maxW / (cols * 5 + Math.max(0, cols - 1) * gapF);
        return Math.max(4, Math.round(fitted));
    }

    private static int fitCellH(int cell, int rows, int gapPct, int maxH) {
        if (rows <= 0 || maxH <= 0) {
            return cell;
        }
        float gapF = Math.max(0, Math.min(200, gapPct)) / 100.0f;
        float wanted = rows * 3 * cell + Math.max(0, rows - 1) * (cell * gapF);
        if (wanted <= maxH) {
            return cell;
        }
        float fitted = maxH / (rows * 3 + Math.max(0, rows - 1) * gapF);
        return Math.max(4, Math.round(fitted));
    }

    private static void drawGroup(Canvas canvas, int x0, int y0, ArrayList<String> combos, int start, int count, int[] dim, SetStore.SetItem item, int cell, int lineW, int borderW) {
        int cols = dim[0];
        int blockW = cell * 5;
        int blockH = cell * 3;
        int gap = Math.max(0, Math.round(cell * (Math.max(0, Math.min(200, item.gGapPct)) / 100.0f)));
        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;
            drawBlock(canvas, x0 + col * (blockW + gap), y0 + row * (blockH + gap), combos.get(start + i), item, cell, lineW, borderW);
        }
    }

    private static void drawBlock(Canvas canvas, int x0, int y0, String combo, SetStore.SetItem item, int cell, int lineW, int borderW) {
        boolean[] on = maskOf(combo);
        Paint fill = new Paint();
        fill.setAntiAlias(false);
        fill.setStyle(Paint.Style.FILL);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 5; c++) {
                fill.setColor(on[r * 5 + c + 1] ? item.gKey : item.gBlank);
                int x = x0 + c * cell;
                int y = y0 + r * cell;
                canvas.drawRect(x, y, x + cell, y + cell, fill);
            }
        }
        Paint stroke = new Paint();
        stroke.setAntiAlias(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(item.gLine);
        stroke.setStrokeWidth(lineW);
        for (int c = 1; c < 5; c++) {
            int x = x0 + c * cell;
            canvas.drawLine(x, y0, x, y0 + 3 * cell, stroke);
        }
        for (int r = 1; r < 3; r++) {
            int y = y0 + r * cell;
            canvas.drawLine(x0, y, x0 + 5 * cell, y, stroke);
        }
        stroke.setColor(item.gBorder);
        stroke.setStrokeWidth(borderW);
        float inset = borderW / 2.0f;
        canvas.drawRect(new RectF(x0 + inset, y0 + inset, x0 + 5 * cell - inset, y0 + 3 * cell - inset), stroke);
    }
}
