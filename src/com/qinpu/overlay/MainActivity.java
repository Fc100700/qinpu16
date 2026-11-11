/*
 * 琴谱浮窗 (Qinpu Overlay) - Android 版
 * 作者: Fc100700
 * GitHub: https://github.com/Fc100700
 */

package com.qinpu.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private static final int REQ_PICK_GRID_NEW = 1002;
    private static final int REQ_PICK_GRID_REIMPORT = 1003;
    private TextView btnGrant;
    private TextView btnPick;
    private TextView btnResume;
    private TextView btnRoot;
    private TextView btnUnlock;
    private TextView btnGridReimport;
    private TextView btnGridStyle;
    private SharedPreferences prefs;
    private TextView tvStatus;
    private TextView tvUnlockStatus;
    private LinearLayout setListBox;
    private List<SetStore.SetItem> sets = new ArrayList<>();
    private String pendingNewSet = null;
    private String pickTargetSet = null;
    private boolean night = false;
    private int cPageBg;
    private int cCardBg;
    private int cTitle;
    private int cSub;
    private int cLabel;
    private int cBody;
    private int cBtnBg;
    private int cBtnFg;
    private int cPriBg;
    private int cPriFg;
    private int cGood;
    private int cBad;
    private int cWarn;
    private TextView[] tabs;
    private View[] pages;

    private interface ColorCallback {
        void onColor(int color);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.prefs = getSharedPreferences("qinpu", 0);
        this.night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        resolvePalette();
        setContentView(buildUi());
        applyWindowColors();
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        refresh();
        updateUnlockStatus();
        Intent intent = getIntent();
        if (intent == null || !intent.getBooleanExtra("pick", false)) {
            return;
        }
        intent.removeExtra("pick");
        if (Settings.canDrawOverlays(this)) {
            SetStore.SetItem cur = currentSetItem();
            if (cur != null && cur.isGrid) {
                reimportGridFlow();
            } else {
                pickImagesForCurrentSet();
            }
        }
    }

    private void resolvePalette() {
        if (this.night) {
            this.cPageBg = 0xFF12161C;
            this.cCardBg = 0xFF1C232C;
            this.cTitle = 0xFFEAF0F7;
            this.cSub = 0xFF8B96A5;
            this.cLabel = 0xFF93A0B4;
            this.cBody = 0xFFC4CDDA;
            this.cBtnBg = 0xFF232C37;
            this.cBtnFg = 0xFFB9C6DA;
            this.cPriBg = 0xFF3D7BFD;
            this.cPriFg = 0xFFFFFFFF;
            this.cGood = 0xFF4CC38A;
            this.cBad = 0xFFFF6B6B;
            this.cWarn = 0xFFFFB020;
        } else {
            this.cPageBg = 0xFFF3F5FA;
            this.cCardBg = 0xFFFFFFFF;
            this.cTitle = 0xFF1B2430;
            this.cSub = 0xFF7A8699;
            this.cLabel = 0xFF8390A4;
            this.cBody = 0xFF39424E;
            this.cBtnBg = 0xFFE9EEF7;
            this.cBtnFg = 0xFF2F66D0;
            this.cPriBg = 0xFF2F6FE4;
            this.cPriFg = 0xFFFFFFFF;
            this.cGood = 0xFF1E9E5A;
            this.cBad = 0xFFD64545;
            this.cWarn = 0xFFB26A00;
        }
    }

    private void applyWindowColors() {
        try {
            getWindow().setStatusBarColor(this.cPageBg);
            getWindow().setNavigationBarColor(this.cPageBg);
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (this.night) {
                flags &= -8193;
            } else {
                flags |= 8192;
            }
            decor.setSystemUiVisibility(flags);
        } catch (Exception unused) {
        }
    }

    private int dlgTheme() {
        return this.night ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
    }

    private AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(this, dlgTheme());
    }

    private int dp(float f) {
        return Math.round(f * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setBackgroundColor(this.cPageBg);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.pages = new View[3];
        this.pages[0] = buildHomePage();
        this.pages[1] = buildSetsPage();
        this.pages[2] = buildHelpPage();
        for (int k = 0; k < 3; k++) {
            content.addView(this.pages[k], new FrameLayout.LayoutParams(-1, -1));
            this.pages[k].setVisibility(k == 0 ? 0 : 8);
        }
        root.addView(buildTabBar(), new LinearLayout.LayoutParams(-1, -2));
        selectTab(0);
        return root;
    }

    private View buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(0);
        bar.setBackgroundColor(this.cCardBg);
        bar.setPadding(dp(10.0f), dp(6.0f), dp(10.0f), dp(8.0f));
        String[] names = {"首页", "琴谱", "设置"};
        this.tabs = new TextView[3];
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            TextView textView = new TextView(this);
            textView.setText(names[k]);
            textView.setGravity(17);
            textView.setTextSize(14.0f);
            textView.setPadding(0, dp(10.0f), 0, dp(10.0f));
            textView.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.selectTab(kk);
                }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams.leftMargin = dp(4.0f);
            layoutParams.rightMargin = dp(4.0f);
            bar.addView(textView, layoutParams);
            this.tabs[k] = textView;
        }
        return bar;
    }

    private void selectTab(int i) {
        for (int k = 0; k < 3; k++) {
            if (this.tabs != null && this.tabs[k] != null) {
                styleTab(this.tabs[k], k == i);
            }
            if (this.pages != null && this.pages[k] != null) {
                this.pages[k].setVisibility(k == i ? 0 : 8);
            }
        }
    }

    private void styleTab(TextView textView, boolean z) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(z ? this.cPriBg : this.cCardBg);
        gradientDrawable.setCornerRadius(dp(12.0f));
        textView.setBackground(gradientDrawable);
        textView.setTextColor(z ? this.cPriFg : this.cSub);
        textView.setTypeface(z ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private ScrollView pageScroll() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(this.cPageBg);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(dp(18.0f), dp(18.0f), dp(18.0f), dp(20.0f));
        scrollView.addView(box, new FrameLayout.LayoutParams(-1, -2));
        scrollView.setTag(box);
        return scrollView;
    }

    private LinearLayout pageBox(ScrollView scrollView) {
        return (LinearLayout) scrollView.getTag();
    }

    private View buildHomePage() {
        ScrollView scrollView = pageScroll();
        LinearLayout box = pageBox(scrollView);
        TextView textView = new TextView(this);
        textView.setText("琴谱浮窗");
        textView.setTextSize(26.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(this.cTitle);
        box.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText("光遇练琴辅助 · 琴谱悬浮显示 · 触摸穿透 · 不透明度可调");
        textView2.setTextSize(13.0f);
        textView2.setTextColor(this.cSub);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(4.0f);
        box.addView(textView2, layoutParams);

        LinearLayout cardPerm = card(box, "悬浮窗权限");
        TextView textView3 = new TextView(this);
        this.tvStatus = textView3;
        textView3.setTextSize(15.0f);
        cardPerm.addView(this.tvStatus, cardChild());
        TextView textViewMkBtn = mkBtn("打开系统授权页", false);
        this.btnGrant = textViewMkBtn;
        textViewMkBtn.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName())));
            }
        });
        cardPerm.addView(this.btnGrant, cardChild(10));
        TextView textViewMkBtn2 = mkBtn("Root 一键授权（appops）", false);
        this.btnRoot = textViewMkBtn2;
        textViewMkBtn2.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.runSu("appops set " + MainActivity.this.getPackageName() + " SYSTEM_ALERT_WINDOW allow", "悬浮窗权限已授予");
            }
        });
        cardPerm.addView(this.btnRoot, cardChild(10));

        LinearLayout cardUnlock = card(box, "Root 穿透解锁（允许不透明度 >80%）");
        TextView textViewUnlockHint = new TextView(this);
        textViewUnlockHint.setText("Android 12+ 默认拦截高不透明度悬浮窗的触摸穿透。解锁后即使亮度拉满 100%，触摸也能穿透到游戏。Android 15/16 使用系统全局开关（无需重启）；Android 13/14 使用 device_config。");
        textViewUnlockHint.setTextSize(12.5f);
        textViewUnlockHint.setTextColor(this.cSub);
        textViewUnlockHint.setLineSpacing(dp(2.0f), 1.0f);
        cardUnlock.addView(textViewUnlockHint, cardChild());
        TextView textViewUnlock = new TextView(this);
        this.tvUnlockStatus = textViewUnlock;
        textViewUnlock.setTextSize(13.5f);
        textViewUnlock.setPadding(0, dp(8.0f), 0, 0);
        cardUnlock.addView(textViewUnlock, cardChild());
        TextView textViewMkBtn5 = mkBtn("Root 解除穿透限制（一键执行并校验）", true);
        this.btnUnlock = textViewMkBtn5;
        textViewMkBtn5.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.unlockPassThrough();
            }
        });
        cardUnlock.addView(this.btnUnlock, cardChild(10));

        TextView textViewMkBtn4 = mkBtn("继续使用上次琴谱", true);
        this.btnResume = textViewMkBtn4;
        textViewMkBtn4.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, (Class<?>) OverlayService.class);
                intent.setAction(OverlayService.ACTION_START);
                MainActivity.this.startForegroundService(intent);
                Toast.makeText(MainActivity.this, "悬浮窗已启动", 0).show();
                MainActivity.this.moveTaskToBack(true);
            }
        });
        LinearLayout.LayoutParams layoutParamsResume = new LinearLayout.LayoutParams(-1, -2);
        layoutParamsResume.topMargin = dp(18.0f);
        box.addView(this.btnResume, layoutParamsResume);

        TextView textView5 = new TextView(this);
        textView5.setText("v1.8 · 琴谱集 · 格子谱 · 适配 Android 8.0 – 16 / ColorOS · 深色模式");
        textView5.setTextSize(11.5f);
        textView5.setTextColor(this.cSub);
        textView5.setGravity(17);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = dp(16.0f);
        box.addView(textView5, layoutParams2);
        return scrollView;
    }

    private View buildSetsPage() {
        ScrollView scrollView = pageScroll();
        LinearLayout box = pageBox(scrollView);
        LinearLayout linearLayoutSet = card(box, "琴谱集（点名称直接切换 · 长按重命名/删除）");
        LinearLayout linearLayoutSetList = new LinearLayout(this);
        this.setListBox = linearLayoutSetList;
        linearLayoutSetList.setOrientation(1);
        linearLayoutSet.addView(linearLayoutSetList, cardChild());
        TextView textViewMkBtnSet = mkBtn("＋ 新建琴谱集（输入名称并选择图片）", true);
        textViewMkBtnSet.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.newSetFlow();
            }
        });
        linearLayoutSet.addView(textViewMkBtnSet, cardChild(10));
        TextView textViewMkBtnGrid = mkBtn("＋ 导入格子谱（文本文件，新建琴谱集）", true);
        textViewMkBtnGrid.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.importGridFlow();
            }
        });
        linearLayoutSet.addView(textViewMkBtnGrid, cardChild(10));

        LinearLayout cardCurrent = card(box, "当前琴谱集操作");
        TextView textViewMkBtn3 = mkBtn("重新选择当前琴谱集的图片（可多选 = 多页）", true);
        this.btnPick = textViewMkBtn3;
        textViewMkBtn3.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.pickImagesForCurrentSet();
            }
        });
        cardCurrent.addView(this.btnPick, cardChild());
        LinearLayout rowGrid = new LinearLayout(this);
        rowGrid.setOrientation(0);
        TextView textViewMkBtnGrid2 = mkBtn("重新导入格子谱", false);
        this.btnGridReimport = textViewMkBtnGrid2;
        textViewMkBtnGrid2.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.reimportGridFlow();
            }
        });
        rowGrid.addView(textViewMkBtnGrid2, weighted(1.0f));
        TextView textViewMkBtnGrid3 = mkBtn("样式与布局", false);
        this.btnGridStyle = textViewMkBtnGrid3;
        textViewMkBtnGrid3.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.editGridStyle();
            }
        });
        rowGrid.addView(textViewMkBtnGrid3, weighted(1.0f));
        cardCurrent.addView(rowGrid, cardChild(10));

        LinearLayout linearLayoutGrid = card(box, "格子谱格式说明");
        TextView textViewGridHint = new TextView(this);
        textViewGridHint.setText("每个组合固定 3行×5列，编号 1~15（左上为 1，按行数）。文件格式示例：['1,4,10', '1', '3']；也可每行写一组（1,4,10），或 15 位 01 掩码。可自定义行数/列数、四色、单个/左右/上下布局。");
        textViewGridHint.setTextSize(12.5f);
        textViewGridHint.setTextColor(this.cSub);
        textViewGridHint.setLineSpacing(dp(2.0f), 1.0f);
        linearLayoutGrid.addView(textViewGridHint, cardChild());
        return scrollView;
    }

    private View buildHelpPage() {
        ScrollView scrollView = pageScroll();
        LinearLayout box = pageBox(scrollView);
        LinearLayout linearLayoutCard3 = card(box, "使用说明");
        TextView textView4 = new TextView(this);
        textView4.setText("1. 授予悬浮窗权限后，新建琴谱集（输入名称并多选图片，可作多页琴谱）；随时切换琴谱集无需重新选图\n2. 格子谱：导入文本文件（格式 ['1,4,10', '1', '3']，编号 1~15 左上为1），本机渲染成透明底图片显示，可设行/列、四色、单个/左右/上下布局\n3. 应用自动退到后台，琴谱悬浮显示，触摸完全穿透到游戏\n4. 悬浮面板：不透明度滑块 · ◀▶翻页 · 缩放 · 移动模式（拖动/双指缩放，用完关闭恢复穿透）· 居中 · 换图 · 琴谱集切换\n5. 每套琴谱集独立记住位置、大小与图像调节（亮度/对比度/伽马），切换时自动恢复\n6. 面板标题栏可拖动；『—』收起成小圆点；『✕』点两次退出；通知栏也可控制\n7. 不透明度建议 ≤ 80%：未解锁时更高数值在部分机型上会拦截穿透触摸，可在首页执行 Root 一键解锁（Android 15/16 立即生效，无需重启）\n8. 完全离线（无网络权限）；若后台被杀，请在最近任务中锁定并允许自启动");
        textView4.setTextSize(13.0f);
        textView4.setTextColor(this.cBody);
        textView4.setLineSpacing(dp(3.0f), 1.0f);
        linearLayoutCard3.addView(textView4, cardChild());

        LinearLayout cardAbout = card(box, "关于");
        TextView textViewAbout = new TextView(this);
        textViewAbout.setText("琴谱浮窗 v1.8\n· 修复 Android 15/16 Root 穿透解锁使用旧命令不生效的问题\n· 主界面改为多页面（首页 / 琴谱 / 设置）\n· 适配系统深色模式\n· 面板警示逻辑与实际系统阈值联动");
        textViewAbout.setTextSize(13.0f);
        textViewAbout.setTextColor(this.cBody);
        textViewAbout.setLineSpacing(dp(3.0f), 1.0f);
        cardAbout.addView(textViewAbout, cardChild());
        return scrollView;
    }

    private LinearLayout card(LinearLayout linearLayout, String str) {
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(this.cCardBg);
        gradientDrawable.setCornerRadius(dp(16.0f));
        linearLayout2.setBackground(gradientDrawable);
        linearLayout2.setPadding(dp(16.0f), dp(14.0f), dp(16.0f), dp(16.0f));
        linearLayout2.setElevation(dp(1.5f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(16.0f);
        linearLayout.addView(linearLayout2, layoutParams);
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(12.5f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(this.cLabel);
        linearLayout2.addView(textView, new LinearLayout.LayoutParams(-1, -2));
        return linearLayout2;
    }

    private LinearLayout.LayoutParams cardChild() {
        return cardChild(0);
    }

    private LinearLayout.LayoutParams cardChild(int i) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(i);
        return layoutParams;
    }

    private TextView mkBtn(String str, boolean z) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextSize(15.0f);
        textView.setPadding(dp(12.0f), dp(11.0f), dp(12.0f), dp(11.0f));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(z ? this.cPriBg : this.cBtnBg);
        gradientDrawable.setCornerRadius(dp(12.0f));
        textView.setBackground(gradientDrawable);
        textView.setTextColor(z ? this.cPriFg : this.cBtnFg);
        textView.setTypeface(z ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return textView;
    }

    private void refresh() {
        boolean zCanDrawOverlays = Settings.canDrawOverlays(this);
        this.tvStatus.setText(zCanDrawOverlays ? "✓ 已获得悬浮窗权限" : "✗ 尚未授权，悬浮窗无法显示");
        this.tvStatus.setTextColor(zCanDrawOverlays ? this.cGood : this.cBad);
        this.btnGrant.setVisibility(zCanDrawOverlays ? 8 : 0);
        this.btnRoot.setVisibility(zCanDrawOverlays ? 8 : 0);
        setEnabled(this.btnPick, zCanDrawOverlays);
        loadSets();
        renderSets();
        SetStore.SetItem curItem = currentSetItem();
        boolean gridCur = curItem != null && curItem.isGrid;
        if (this.btnGridReimport != null) {
            setEnabled(this.btnGridReimport, zCanDrawOverlays && gridCur);
        }
        if (this.btnGridStyle != null) {
            setEnabled(this.btnGridStyle, zCanDrawOverlays && gridCur);
        }
        if (gridCur) {
            this.btnResume.setVisibility(0);
            this.btnResume.setText("继续使用“" + curItem.name + "”（格子谱 " + curItem.comboCount() + " 组）");
            setEnabled(this.btnResume, zCanDrawOverlays);
        } else {
            String string = this.prefs.getString("uris", "");
            int length = (string == null || string.isEmpty()) ? 0 : string.split("\n").length;
            this.btnResume.setVisibility(length > 0 ? 0 : 8);
            if (length > 0) {
                String current = SetStore.currentName(this.prefs, this.sets);
                this.btnResume.setText("继续使用“" + current + "”（" + length + " 张）");
            }
            setEnabled(this.btnResume, zCanDrawOverlays && length > 0);
        }
    }

    private void updateUnlockStatus() {
        if (this.tvUnlockStatus == null) {
            return;
        }
        float f = -1.0f;
        try {
            f = Settings.Global.getFloat(getContentResolver(), "maximum_obscuring_opacity_for_touch", 0.8f);
        } catch (Throwable unused) {
        }
        if (f < 0.0f) {
            this.tvUnlockStatus.setText("当前系统穿透阈值：未知（点下方按钮用 Root 查询并解锁）");
            this.tvUnlockStatus.setTextColor(this.cWarn);
        } else if (f >= 0.999f) {
            this.tvUnlockStatus.setText("当前系统穿透阈值：1.0 ✓ 已解锁，不透明度可拉满 100%");
            this.tvUnlockStatus.setTextColor(this.cGood);
        } else {
            this.tvUnlockStatus.setText("当前系统穿透阈值：" + formatThreshold(f) + "（低于 1.0 时高不透明度可能被拦截，可 Root 解锁）");
            this.tvUnlockStatus.setTextColor(this.cWarn);
        }
    }

    private String formatThreshold(float f) {
        if (Math.abs(f - 0.8f) < 0.001f) {
            return "0.8（系统默认）";
        }
        return String.format(java.util.Locale.US, "%.2f", Float.valueOf(f));
    }

    private void loadSets() {
        this.sets = SetStore.load(this.prefs);
    }

    private SetStore.SetItem currentSetItem() {
        String name = SetStore.currentName(this.prefs, this.sets);
        return name.isEmpty() ? null : SetStore.find(this.sets, name);
    }

    private void renderSets() {
        if (this.setListBox == null) {
            return;
        }
        this.setListBox.removeAllViews();
        if (this.sets.isEmpty()) {
            TextView textView = new TextView(this);
            textView.setText("还没有保存的琴谱集，点下方按钮新建");
            textView.setTextSize(13.0f);
            textView.setTextColor(this.cSub);
            this.setListBox.addView(textView);
            return;
        }
        String current = SetStore.currentName(this.prefs, this.sets);
        for (final SetStore.SetItem item : this.sets) {
            final boolean active = item.name.equals(current);
            String desc = item.isGrid ? ("格子谱 " + item.gridCombos.size() + " 组") : (item.uris.size() + " 张");
            TextView textView2 = new TextView(this);
            textView2.setText((active ? "✓ " : "") + item.name + "（" + desc + "）" + (active ? " · 使用中" : ""));
            textView2.setTextSize(14.5f);
            textView2.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            textView2.setPadding(dp(12.0f), dp(10.0f), dp(12.0f), dp(10.0f));
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(active ? this.cPriBg : this.cBtnBg);
            gradientDrawable.setCornerRadius(dp(12.0f));
            textView2.setBackground(gradientDrawable);
            textView2.setTextColor(active ? this.cPriFg : this.cBtnFg);
            textView2.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.11
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.useSet(item, true);
                }
            });
            textView2.setOnLongClickListener(new View.OnLongClickListener() { // from class: com.qinpu.overlay.MainActivity.12
                @Override // android.view.View.OnLongClickListener
                public final boolean onLongClick(View view) {
                    MainActivity.this.setOptions(item);
                    return true;
                }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.topMargin = dp(8.0f);
            this.setListBox.addView(textView2, layoutParams);
        }
    }

    private void useSet(SetStore.SetItem item, boolean z) {
        SetStore.activate(this.prefs, item);
        refresh();
        if (!z) {
            return;
        }
        Intent intent = new Intent(this, (Class<?>) OverlayService.class);
        intent.setAction(OverlayService.ACTION_START);
        startForegroundService(intent);
        Toast.makeText(this, "已切换：“" + item.name + "”（" + item.uris.size() + " 张）", 0).show();
        moveTaskToBack(true);
    }

    private void pickImagesForCurrentSet() {
        final SetStore.SetItem item = currentSetItem();
        if (item != null && item.isGrid) {
            dlg().setTitle("当前是格子谱").setItems(new String[]{"重新导入格子谱（换文件）", "改为图片琴谱（选择图片）"}, new DialogInterface.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.13
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    if (i == 0) {
                        MainActivity.this.reimportGridFlow();
                    } else {
                        MainActivity.this.pickTargetSet = item.name;
                        MainActivity.this.pendingNewSet = null;
                        MainActivity.this.launchPicker();
                    }
                }
            }).show();
            return;
        }
        this.pickTargetSet = item == null ? null : item.name;
        this.pendingNewSet = null;
        launchPicker();
    }

    private void newSetFlow() {
        final EditText editText = new EditText(this);
        editText.setHint("琴谱集名称");
        editText.setText("琴谱" + (this.sets.size() + 1));
        editText.setSelection(editText.getText().length());
        dlg().setTitle("新建琴谱集").setMessage("输入名称，下一步选择图片（可多选 = 多页）").setView(editText).setPositiveButton("选择图片", new DialogInterface.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.14
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                String string = editText.getText().toString().trim();
                if (string.isEmpty()) {
                    string = "琴谱" + (MainActivity.this.sets.size() + 1);
                }
                MainActivity.this.pendingNewSet = string;
                MainActivity.this.pickTargetSet = null;
                MainActivity.this.launchPicker();
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    private void setOptions(final SetStore.SetItem item) {
        final String[] options = item.isGrid ? new String[]{"重命名", "重新导入（换文件）", "样式与布局", "删除"} : new String[]{"重命名", "重新选择图片", "删除"};
        String desc = item.isGrid ? ("格子谱 " + item.gridCombos.size() + " 组") : (item.uris.size() + " 张");
        dlg().setTitle(item.name + "（" + desc + "）").setItems(options, new DialogInterface.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.15
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                if (item.isGrid) {
                    if (i == 0) {
                        MainActivity.this.renameSet(item);
                    } else if (i == 1) {
                        MainActivity.this.pickTargetSet = item.name;
                        MainActivity.this.launchGridPicker(REQ_PICK_GRID_REIMPORT);
                    } else if (i == 2) {
                        MainActivity.this.showGridConfigDialog(item, null, false);
                    } else {
                        MainActivity.this.deleteSet(item);
                    }
                    return;
                }
                if (i == 0) {
                    MainActivity.this.renameSet(item);
                } else if (i == 1) {
                    MainActivity.this.pickTargetSet = item.name;
                    MainActivity.this.pendingNewSet = null;
                    MainActivity.this.launchPicker();
                } else {
                    MainActivity.this.deleteSet(item);
                }
            }
        }).show();
    }

    private void renameSet(final SetStore.SetItem item) {
        final EditText editText = new EditText(this);
        editText.setText(item.name);
        editText.setSelection(editText.getText().length());
        dlg().setTitle("重命名琴谱集").setView(editText).setPositiveButton("确定", new DialogInterface.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.16
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                String string = editText.getText().toString().trim();
                if (string.isEmpty() || string.equals(item.name)) {
                    return;
                }
                String strUniqueName = SetStore.uniqueName(MainActivity.this.sets, string);
                String strCurrent = MainActivity.this.prefs.getString(SetStore.KEY_CURRENT, "");
                String strOld = item.name;
                item.name = strUniqueName;
                SetStore.save(MainActivity.this.prefs, MainActivity.this.sets);
                if (strCurrent.equals(strOld)) {
                    MainActivity.this.prefs.edit().putString(SetStore.KEY_CURRENT, strUniqueName).apply();
                }
                MainActivity.this.refresh();
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    private void deleteSet(final SetStore.SetItem item) {
        dlg().setTitle("删除琴谱集").setMessage("确定删除“" + item.name + "”吗？").setPositiveButton("删除", new DialogInterface.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.17
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.sets.remove(item);
                SetStore.save(MainActivity.this.prefs, MainActivity.this.sets);
                if (MainActivity.this.prefs.getString(SetStore.KEY_CURRENT, "").equals(item.name)) {
                    if (MainActivity.this.sets.isEmpty()) {
                        MainActivity.this.prefs.edit().putString(SetStore.KEY_URIS, "").remove(SetStore.KEY_CURRENT).apply();
                    } else {
                        SetStore.activate(MainActivity.this.prefs, MainActivity.this.sets.get(0));
                    }
                }
                MainActivity.this.syncGrants();
                MainActivity.this.refresh();
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    private void setEnabled(TextView textView, boolean z) {
        textView.setEnabled(z);
        textView.setAlpha(z ? 1.0f : 0.45f);
    }

    private void launchPicker() {
        try {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("image/*");
            intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
            startActivityForResult(intent, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统文件选择器：" + e.getMessage(), 1).show();
        }
    }

    private void importGridFlow() {
        this.pendingNewSet = null;
        this.pickTargetSet = null;
        launchGridPicker(REQ_PICK_GRID_NEW);
    }

    private void reimportGridFlow() {
        SetStore.SetItem item = currentSetItem();
        if (item == null || !item.isGrid) {
            Toast.makeText(this, "当前琴谱集不是格子谱", 0).show();
            return;
        }
        this.pendingNewSet = null;
        this.pickTargetSet = item.name;
        launchGridPicker(REQ_PICK_GRID_REIMPORT);
    }

    private void editGridStyle() {
        SetStore.SetItem item = currentSetItem();
        if (item == null || !item.isGrid) {
            Toast.makeText(this, "当前琴谱集不是格子谱", 0).show();
            return;
        }
        showGridConfigDialog(item, null, false);
    }

    private void launchGridPicker(int req) {
        try {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("*/*");
            startActivityForResult(intent, req);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统文件选择器：" + e.getMessage(), 1).show();
        }
    }

    private String readTextFile(Uri uri) {
        InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[8192];
            int i = 0;
            int read;
            while ((read = inputStream.read(bArr)) > 0) {
                i += read;
                if (i > 4194304) {
                    break;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
            return new String(byteArrayOutputStream.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception unused) {
                }
            }
        }
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, (Class<?>) OverlayService.class);
        intent.setAction(OverlayService.ACTION_START);
        startForegroundService(intent);
    }

    private void showGridConfigDialog(final SetStore.SetItem existing, final ArrayList<String> combos, final boolean isNew) {
        final SetStore.SetItem item = existing != null ? existing : new SetStore.SetItem("");
        final int[] layout = new int[]{item.gLayout};
        final int[] colors = new int[]{item.gBlank, item.gKey, item.gLine, item.gBorder};

        ScrollView scrollView = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(dp(16.0f), dp(4.0f), dp(16.0f), dp(4.0f));
        scrollView.addView(box, new FrameLayout.LayoutParams(-1, -2));

        final EditText etName;
        if (isNew) {
            box.addView(formLabel("名称"));
            etName = new EditText(this);
            etName.setText("格子谱" + (this.sets.size() + 1));
            etName.setSelection(etName.getText().length());
            box.addView(etName, new LinearLayout.LayoutParams(-1, -2));
        } else {
            etName = null;
        }

        box.addView(formLabel("每行组数 / 行数（行数填 0 = 自动不分页；行数 >0 时每页 = 行×列，多余组合自动分页，用面板 ◀▶ 翻页）"));
        LinearLayout rowDims = new LinearLayout(this);
        rowDims.setOrientation(0);
        rowDims.setGravity(16);
        final EditText etCols = numField(String.valueOf(item.gCols));
        final EditText etRows = numField(String.valueOf(item.gRows));
        rowDims.addView(etCols, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView tvX = new TextView(this);
        tvX.setText("  ×  ");
        tvX.setTextColor(this.cSub);
        rowDims.addView(tvX, new LinearLayout.LayoutParams(-2, -2));
        rowDims.addView(etRows, new LinearLayout.LayoutParams(0, -2, 1.0f));
        box.addView(rowDims, new LinearLayout.LayoutParams(-1, -2));

        box.addView(formLabel("布局"));
        final String[] layoutNames = {"单个", "左右", "上下"};
        final TextView[] layoutBtns = new TextView[3];
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(0);
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            TextView textView = mkBtn(layoutNames[k], k == layout[0]);
            textView.setTextSize(13.5f);
            textView.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.18
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    layout[0] = kk;
                    for (int m = 0; m < 3; m++) {
                        styleBtn(layoutBtns[m], m == kk);
                    }
                }
            });
            layoutBtns[k] = textView;
            rowLayout.addView(textView, weighted(1.0f));
        }
        box.addView(rowLayout, new LinearLayout.LayoutParams(-1, -2));

        box.addView(formLabel("颜色（点击色块修改）"));
        final String[] colorNames = {"空白格", "按键", "格子线", "组合边框"};
        final TextView[] swatches = new TextView[4];
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(0);
            row.setGravity(16);
            row.addView(formLabel(colorNames[k]), new LinearLayout.LayoutParams(0, -2, 1.0f));
            final TextView swatch = new TextView(this);
            swatches[k] = swatch;
            swatch.setText(hexOf(colors[k]));
            swatch.setTextSize(11.5f);
            swatch.setTextColor(contrast(colors[k]));
            swatch.setGravity(17);
            swatch.setPadding(dp(10.0f), dp(7.0f), dp(10.0f), dp(7.0f));
            swatch.setBackground(swatchDrawable(colors[k]));
            swatch.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.19
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.pickColor(colorNames[kk], colors[kk], new ColorCallback() { // from class: com.qinpu.overlay.MainActivity.19.1
                        @Override // com.qinpu.overlay.MainActivity.ColorCallback
                        public void onColor(int color) {
                            colors[kk] = color;
                            swatches[kk].setText(MainActivity.this.hexOf(color));
                            swatches[kk].setTextColor(MainActivity.this.contrast(color));
                            swatches[kk].setBackground(MainActivity.this.swatchDrawable(color));
                        }
                    });
                }
            });
            row.addView(swatch, new LinearLayout.LayoutParams(dp(118.0f), -2));
            box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        box.addView(formLabel("高级（格子大小 / 组间距% / 线宽 / 边框宽，单位 dp）"));
        LinearLayout rowAdv1 = new LinearLayout(this);
        rowAdv1.setOrientation(0);
        final EditText etCell = numField(String.valueOf(item.gCellDp));
        final EditText etGap = numField(String.valueOf(item.gGapPct));
        rowAdv1.addView(etCell, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowAdv1.addView(etGap, new LinearLayout.LayoutParams(0, -2, 1.0f));
        box.addView(rowAdv1, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout rowAdv2 = new LinearLayout(this);
        rowAdv2.setOrientation(0);
        final EditText etLineW = numField(String.valueOf(item.gLineDp));
        final EditText etBorderW = numField(String.valueOf(item.gBorderDp));
        rowAdv2.addView(etLineW, new LinearLayout.LayoutParams(0, -2, 1.0f));
        rowAdv2.addView(etBorderW, new LinearLayout.LayoutParams(0, -2, 1.0f));
        box.addView(rowAdv2, new LinearLayout.LayoutParams(-1, -2));

        dlg().setTitle(isNew ? "导入格子谱设置" : "样式与布局").setView(scrollView).setPositiveButton(isNew ? "保存并显示" : "保存", new DialogInterface.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.20
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                item.gCols = clampInt(intOf(etCols, 4), 1, 16);
                item.gRows = clampInt(intOf(etRows, 0), 0, 32);
                item.gCellDp = clampInt(intOf(etCell, 16), 4, 64);
                item.gGapPct = clampInt(intOf(etGap, 60), 0, 300);
                item.gLineDp = clampInt(intOf(etLineW, 1), 1, 10);
                item.gBorderDp = clampInt(intOf(etBorderW, 2), 1, 10);
                item.gLayout = layout[0];
                item.gBlank = colors[0];
                item.gKey = colors[1];
                item.gLine = colors[2];
                item.gBorder = colors[3];
                if (isNew) {
                    String strName = etName == null ? "" : etName.getText().toString().trim();
                    if (strName.isEmpty()) {
                        strName = "格子谱";
                    }
                    item.name = SetStore.uniqueName(MainActivity.this.sets, strName);
                    item.isGrid = true;
                    item.gridCombos.clear();
                    item.gridCombos.addAll(combos);
                    item.hasState = true;
                    item.stScale = layout[0] == 0 ? 0.72f : 1.0f;
                    MainActivity.this.sets.add(item);
                }
                SetStore.save(MainActivity.this.prefs, MainActivity.this.sets);
                SetStore.activate(MainActivity.this.prefs, item);
                MainActivity.this.refresh();
                MainActivity.this.startOverlayService();
                Toast.makeText(MainActivity.this, "已保存格子谱：“" + item.name + "”（" + item.gridCombos.size() + " 组）", 0).show();
                MainActivity.this.moveTaskToBack(true);
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pickColor(String str, int i, final ColorCallback colorCallback) {
        final int cur = i;
        final EditText editText = new EditText(this);
        editText.setText(hexOf(i));
        editText.setSelection(editText.getText().length());
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(16.0f), dp(4.0f), dp(16.0f), dp(4.0f));
        linearLayout.addView(editText, new LinearLayout.LayoutParams(-1, -2));
        final int[] presets = {-1, -16777216, -741246, -19144, -6141, -11751600, -2815, -13388315, -6529040, -6316129, -16777216, 0};
        LinearLayout[] rows = {new LinearLayout(this), new LinearLayout(this)};
        for (int k = 0; k < presets.length; k++) {
            final int color = presets[k];
            TextView textView = new TextView(this);
            textView.setBackground(swatchDrawable(color));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(30.0f), dp(30.0f));
            layoutParams.rightMargin = dp(6.0f);
            layoutParams.topMargin = dp(8.0f);
            textView.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.21
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    editText.setText(MainActivity.this.hexOf(color));
                }
            });
            rows[k / 6].addView(textView, layoutParams);
        }
        linearLayout.addView(rows[0]);
        linearLayout.addView(rows[1]);
        TextView textView = new TextView(this);
        textView.setText("支持 #RGB / #RRGGBB / #AARRGGBB，#00000000 全透明");
        textView.setTextSize(11.5f);
        textView.setTextColor(this.cSub);
        linearLayout.addView(textView);
        dlg().setTitle(str).setView(linearLayout).setPositiveButton("确定", new DialogInterface.OnClickListener() { // from class: com.qinpu.overlay.MainActivity.22
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                colorCallback.onColor(MainActivity.this.parseColorSafe(editText.getText().toString().trim(), cur));
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    private TextView formLabel(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(13.0f);
        textView.setTextColor(this.cBody);
        textView.setPadding(0, dp(8.0f), 0, dp(2.0f));
        return textView;
    }

    private EditText numField(String str) {
        EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setText(str);
        editText.setSelection(str.length());
        return editText;
    }

    private int intOf(EditText editText, int i) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (Exception unused) {
            return i;
        }
    }

    private int clampInt(int i, int i2, int i3) {
        return i < i2 ? i2 : (i > i3 ? i3 : i);
    }

    private void styleBtn(TextView textView, boolean z) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(z ? this.cPriBg : this.cBtnBg);
        gradientDrawable.setCornerRadius(dp(12.0f));
        textView.setBackground(gradientDrawable);
        textView.setTextColor(z ? this.cPriFg : this.cBtnFg);
        textView.setTypeface(z ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private LinearLayout.LayoutParams weighted(float f) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, f);
        layoutParams.leftMargin = dp(3.0f);
        layoutParams.rightMargin = dp(3.0f);
        return layoutParams;
    }

    private String hexOf(int i) {
        return String.format("#%08X", Integer.valueOf(i));
    }

    private int contrast(int i) {
        return ((int) ((0.299f * Color.red(i)) + (0.587f * Color.green(i)) + (0.114f * Color.blue(i)))) > 160 ? -14671840 : -1;
    }

    private GradientDrawable swatchDrawable(int i) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(dp(8.0f));
        gradientDrawable.setStroke(dp(1.0f), 1718038903);
        return gradientDrawable;
    }

    private int parseColorSafe(String str, int i) {
        try {
            String trim = str.trim();
            if (trim.startsWith("#")) {
                trim = trim.substring(1);
            }
            if (trim.length() == 3) {
                int parseInt = Integer.parseInt(trim.substring(0, 1), 16) * 17;
                int parseInt2 = Integer.parseInt(trim.substring(1, 2), 16) * 17;
                int parseInt3 = Integer.parseInt(trim.substring(2, 3), 16) * 17;
                return (-16777216) | (parseInt << 16) | (parseInt2 << 8) | parseInt3;
            }
            long parseLong = Long.parseLong(trim, 16);
            return trim.length() <= 6 ? (-16777216) | ((int) parseLong) : (int) parseLong;
        } catch (Exception unused) {
            return i;
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if ((i == REQ_PICK_GRID_NEW || i == REQ_PICK_GRID_REIMPORT) && i2 == -1 && intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            try {
                getContentResolver().takePersistableUriPermission(data, 1);
            } catch (Exception unused) {
            }
            String text = readTextFile(data);
            final ArrayList<String> parse = GridScore.parse(text);
            if (parse.isEmpty()) {
                Toast.makeText(this, "没有识别到按键组合，请检查文件内容（示例：['1,4,10', '1', '3']）", 1).show();
                return;
            }
            loadSets();
            if (i == REQ_PICK_GRID_REIMPORT) {
                SetStore.SetItem item = SetStore.find(this.sets, this.pickTargetSet);
                if (item == null) {
                    item = currentSetItem();
                }
                if (item == null) {
                    Toast.makeText(this, "找不到要更新的格子谱", 1).show();
                    return;
                }
                item.isGrid = true;
                item.gridCombos.clear();
                item.gridCombos.addAll(parse);
                this.pickTargetSet = null;
                SetStore.save(this.prefs, this.sets);
                SetStore.activate(this.prefs, item);
                refresh();
                startOverlayService();
                Toast.makeText(this, "已更新格子谱：“" + item.name + "”（" + parse.size() + " 组）", 0).show();
                moveTaskToBack(true);
            } else {
                showGridConfigDialog(null, parse, true);
            }
            return;
        }
        if (i == REQ_PICK && i2 == -1 && intent != null) {
            ArrayList<Uri> arrayList = new ArrayList<>();
            if (intent.getClipData() != null) {
                int itemCount = intent.getClipData().getItemCount();
                for (int i3 = 0; i3 < itemCount; i3++) {
                    Uri uri = intent.getClipData().getItemAt(i3).getUri();
                    if (uri != null) {
                        arrayList.add(uri);
                    }
                }
            } else if (intent.getData() != null) {
                arrayList.add(intent.getData());
            }
            if (arrayList.isEmpty()) {
                return;
            }
            for (Uri uri2 : arrayList) {
                try {
                    getContentResolver().takePersistableUriPermission(uri2, 1);
                } catch (Exception unused) {
                }
            }
            loadSets();
            SetStore.SetItem setItem = null;
            if (this.pendingNewSet != null) {
                setItem = new SetStore.SetItem(SetStore.uniqueName(this.sets, this.pendingNewSet));
                this.sets.add(setItem);
            } else {
                if (this.pickTargetSet != null) {
                    setItem = SetStore.find(this.sets, this.pickTargetSet);
                }
                if (setItem == null) {
                    setItem = currentSetItem();
                }
                if (setItem == null) {
                    setItem = new SetStore.SetItem(SetStore.uniqueName(this.sets, SetStore.FALLBACK_NAME));
                    this.sets.add(setItem);
                }
                setItem.uris.clear();
            }
            setItem.isGrid = false;
            setItem.gridCombos.clear();
            for (Uri uri3 : arrayList) {
                setItem.uris.add(uri3.toString());
            }
            this.pendingNewSet = null;
            this.pickTargetSet = null;
            SetStore.save(this.prefs, this.sets);
            SetStore.activate(this.prefs, setItem);
            syncGrants();
            refresh();
            Intent intent2 = new Intent(this, (Class<?>) OverlayService.class);
            intent2.setAction(OverlayService.ACTION_START);
            startForegroundService(intent2);
            Toast.makeText(this, "已保存到“" + setItem.name + "”（" + setItem.uris.size() + " 张），回游戏弹琴吧～", 0).show();
            moveTaskToBack(true);
        }
    }

    private void syncGrants() {
        HashSet<String> hashSet = new HashSet<>();
        for (SetStore.SetItem item : this.sets) {
            hashSet.addAll(item.uris);
        }
        for (UriPermission uriPermission : getContentResolver().getPersistedUriPermissions()) {
            Uri uri = uriPermission.getUri();
            if (!hashSet.contains(uri.toString())) {
                try {
                    getContentResolver().releasePersistableUriPermission(uri, 1);
                } catch (Exception unused) {
                }
            }
        }
    }

    private void unlockPassThrough() {
        Toast.makeText(this, "正在执行 Root 穿透解锁…", 0).show();
        new Thread(new Runnable() { // from class: com.qinpu.overlay.MainActivity.23
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.doUnlockPassThrough();
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doUnlockPassThrough() {
        boolean ok;
        String detail = "";
        float backValue = -1.0f;
        try {
            StringBuilder sb = new StringBuilder();
            if (Build.VERSION.SDK_INT >= 35) {
                sb.append("settings put global maximum_obscuring_opacity_for_touch 1.0; ");
            }
            sb.append("device_config put input_native_boot maximum_obscuring_opacity_for_touch 1.0");
            ok = Runtime.getRuntime().exec(new String[]{"su", "-c", sb.toString()}).waitFor() == 0;
            if (ok) {
                java.io.InputStream inputStream = Runtime.getRuntime().exec(new String[]{"su", "-c", "settings get global maximum_obscuring_opacity_for_touch"}).getInputStream();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bArr = new byte[256];
                int read;
                while ((read = inputStream.read(bArr)) > 0) {
                    byteArrayOutputStream.write(bArr, 0, read);
                }
                inputStream.close();
                String strValueOf = new String(byteArrayOutputStream.toByteArray(), "UTF-8").trim();
                detail = strValueOf;
                try {
                    backValue = Float.parseFloat(strValueOf);
                } catch (Exception unused) {
                }
            }
        } catch (Exception e) {
            ok = false;
            detail = String.valueOf(e.getMessage());
        }
        final boolean z = ok;
        final float f = backValue;
        final String str = detail;
        runOnUiThread(new Runnable() { // from class: com.qinpu.overlay.MainActivity.24
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.onUnlockResult(z, f, str);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onUnlockResult(boolean z, float f, String str) {
        if (!z) {
            Toast.makeText(this, "Root 执行失败（" + str + "），请确认已授予 Root 权限", 1).show();
        } else if (f >= 0.999f) {
            Toast.makeText(this, "已解除穿透限制（阈值 " + str + "，立即生效）", 1).show();
        } else if (Build.VERSION.SDK_INT >= 35) {
            Toast.makeText(this, "命令已执行，但读取阈值=" + str + "，如未生效请重启手机后重试", 1).show();
        } else {
            Toast.makeText(this, "已写入 device_config（Android 13/14 需重启后生效）", 1).show();
        }
        updateUnlockStatus();
    }

    private void runSu(final String str, final String str2) {
        Toast.makeText(this, "正在执行 Root 命令…", 0).show();
        new Thread(new Runnable() { // from class: com.qinpu.overlay.MainActivity.25
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.execSuCommand(str, str2);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void execSuCommand(String str, final String str2) {
        boolean b;
        String msg;
        try {
            b = Runtime.getRuntime().exec(new String[]{"su", "-c", str}).waitFor() == 0;
            msg = "";
        } catch (Exception e) {
            b = false;
            msg = String.valueOf(e.getMessage());
        }
        final boolean z = b;
        final String strValueOf = msg;
        runOnUiThread(new Runnable() { // from class: com.qinpu.overlay.MainActivity.26
            @Override // java.lang.Runnable
            public final void run() {
                if (z) {
                    Toast.makeText(MainActivity.this, str2, 1).show();
                } else {
                    Toast.makeText(MainActivity.this, "Root 执行失败（" + strValueOf + "），请改用系统授权页", 1).show();
                }
                MainActivity.this.refresh();
            }
        });
    }
}
