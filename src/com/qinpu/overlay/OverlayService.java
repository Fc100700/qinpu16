/*
 * 琴谱浮窗 (Qinpu Overlay) - Android 版
 * 作者: Fc100700
 * GitHub: https://github.com/Fc100700
 */

package com.qinpu.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* JADX INFO: loaded from: classes.dex */
public class OverlayService extends Service {
    public static final String ACTION_START = "com.qinpu.overlay.START";
    public static final String ACTION_STOP = "com.qinpu.overlay.STOP";
    public static final String ACTION_TILE = "com.qinpu.overlay.TILE";
    public static final String ACTION_TOGGLE = "com.qinpu.overlay.TOGGLE";
    public static volatile boolean overlayActive = false;
    private LinearLayout adjSection;
    private ExecutorService adjustExec;
    private Bitmap bmp;
    private TextView btnAdjToggle;
    private TextView btnEdge;
    private TextView btnHide;
    private TextView btnMove;
    private TextView btnRect;
    private TextView dot;
    private boolean dotAdded;
    private WindowManager.LayoutParams dotP;
    private boolean editorAdded;
    private WindowManager.LayoutParams editorP;
    private FrameLayout editorRoot;
    private MaskEditorView editorView;
    private boolean imgAdded;
    private boolean imgVisible = true;
    private WindowManager.LayoutParams imgP;
    private ImageView imgView;
    private long lastExitTap;
    private LinearLayout panel;
    private boolean panelAdded;
    private WindowManager.LayoutParams panelP;
    private ScrollView panelScroll;
    private SharedPreferences prefs;
    private TextView btnSet;
    private boolean setPickerAdded;
    private View setPickerRoot;
    private WindowManager.LayoutParams setPickerP;
    private View rectMask;
    private boolean rectMaskAdded;
    private WindowManager.LayoutParams rectMaskP;
    private ScaleGestureDetector scaleDetector;
    private SeekBar seekAlpha;
    private SeekBar seekBright;
    private SeekBar seekContrast;
    private SeekBar seekGamma;
    private Bitmap srcBmp;
    private TextView tvAlpha;
    private TextView tvBright;
    private TextView tvContrast;
    private TextView tvGamma;
    private TextView tvPage;
    private WindowManager wm;
    private final List<Uri> uris = new ArrayList();
    private SetStore.SetItem curSetItem;
    private boolean gridMode = false;
    private int page = 0;
    private float alpha = 0.75f;
    private float scale = 0.72f;
    private float curAspect = 1.5f;
    private int screenW = 1080;
    private int screenH = 2400;
    private int savedX = Integer.MIN_VALUE;
    private int savedY = Integer.MIN_VALUE;
    private int logicalRotation = 90;
    private int viewRotation = 0;
    private boolean moveMode = false;
    private boolean edgeOn = false;
    private boolean rectOn = false;
    private float efL = 0.11f;
    private float efT = 0.25f;
    private float efR = 0.89f;
    private float efB = 0.75f;
    private float rfL = 0.25f;
    private float rfT = 0.14f;
    private float rfR = 0.75f;
    private float rfB = 0.34f;
    private final float maskAlpha = 0.05f;
    private final View[] strips = new View[4];
    private final WindowManager.LayoutParams[] stripPs = new WindowManager.LayoutParams[4];
    private int editingMask = 0;
    private int adjBright = 0;
    private int adjContrast = 0;
    private float adjGamma = 1.0f;
    private int adjustGen = 0;
    private final Object bmpLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable adjustTask = new Runnable() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda4
        @Override // java.lang.Runnable
        public final void run() {
            OverlayService.this.applyAdjust();
        }
    };
    private int lastAppliedRotation = -1;
    private final Runnable rotationSettleTask = new Runnable() { // from class: com.qinpu.overlay.OverlayService.17
        @Override // java.lang.Runnable
        public final void run() {
            if (!OverlayService.this.imgAdded) {
                return;
            }
            if (OverlayService.this.displayRotation() != OverlayService.this.lastAppliedRotation) {
                OverlayService.this.readScreenSize();
                OverlayService.this.applyImageRect();
            }
        }
    };
    private boolean adjOpen = false;
    private final View.OnTouchListener imgTouch = new View.OnTouchListener() { // from class: com.qinpu.overlay.OverlayService.1
        private float lx;
        private float ly;
        private boolean moved;

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (!OverlayService.this.moveMode || !OverlayService.this.imgAdded) {
                return false;
            }
            OverlayService.this.scaleDetector.onTouchEvent(motionEvent);
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                this.lx = motionEvent.getRawX();
                this.ly = motionEvent.getRawY();
                this.moved = false;
                return true;
            }
            if (actionMasked == 2 && !OverlayService.this.scaleDetector.isInProgress()) {
                OverlayService.this.imgP.x += (int) (motionEvent.getRawX() - this.lx);
                OverlayService.this.imgP.y += (int) (motionEvent.getRawY() - this.ly);
                this.lx = motionEvent.getRawX();
                this.ly = motionEvent.getRawY();
                this.moved = true;
                OverlayService.this.clampImgPos();
                try {
                    OverlayService.this.wm.updateViewLayout(OverlayService.this.imgView, OverlayService.this.imgP);
                } catch (Exception unused) {
                }
                return true;
            }
            if (actionMasked == 1 || actionMasked == 3) {
                if (this.moved) {
                    this.moved = false;
                    OverlayService.this.saveState();
                }
            }
            return true;
        }
    };
    private final View.OnTouchListener panelDrag = new View.OnTouchListener() { // from class: com.qinpu.overlay.OverlayService.2
        private float lx;
        private float ly;

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                if (OverlayService.this.panelScroll != null) {
                    OverlayService.this.panelScroll.requestDisallowInterceptTouchEvent(true);
                }
                this.lx = motionEvent.getRawX();
                this.ly = motionEvent.getRawY();
                return true;
            }
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    OverlayService.this.panelP.x += (int) (motionEvent.getRawX() - this.lx);
                    OverlayService.this.panelP.y += (int) (motionEvent.getRawY() - this.ly);
                    this.lx = motionEvent.getRawX();
                    this.ly = motionEvent.getRawY();
                    OverlayService.this.panelP.x = Math.max(0, Math.min(OverlayService.this.panelP.x, OverlayService.this.screenW - OverlayService.this.dp(60.0f)));
                    OverlayService.this.panelP.y = Math.max(0, Math.min(OverlayService.this.panelP.y, OverlayService.this.screenH - OverlayService.this.dp(60.0f)));
                    try {
                        OverlayService.this.wm.updateViewLayout(OverlayService.this.panelScroll, OverlayService.this.panelP);
                    } catch (Exception unused) {
                    }
                    return true;
                }
                if (actionMasked != 3) {
                    return false;
                }
            }
            if (OverlayService.this.panelScroll != null) {
                OverlayService.this.panelScroll.requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
    };
    private final View.OnTouchListener rectMaskDrag = new View.OnTouchListener() { // from class: com.qinpu.overlay.OverlayService.3
        long downAt;
        float downX;
        float downY;
        boolean dragging;
        int startX;
        int startY;

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                this.downAt = SystemClock.elapsedRealtime();
                this.downX = motionEvent.getRawX();
                this.downY = motionEvent.getRawY();
                this.startX = OverlayService.this.rectMaskP.x;
                this.startY = OverlayService.this.rectMaskP.y;
                this.dragging = false;
                return true;
            }
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    if (!this.dragging) {
                        if (SystemClock.elapsedRealtime() - this.downAt < 250) {
                            return true;
                        }
                        this.dragging = true;
                        OverlayService.this.rectMaskP.alpha = 0.25f;
                    }
                    OverlayService.this.rectMaskP.x = OverlayService.this.clamp(Math.round((this.startX + motionEvent.getRawX()) - this.downX), 0, Math.max(0, OverlayService.this.screenW - OverlayService.this.rectMaskP.width));
                    OverlayService.this.rectMaskP.y = OverlayService.this.clamp(Math.round((this.startY + motionEvent.getRawY()) - this.downY), 0, Math.max(0, OverlayService.this.screenH - OverlayService.this.rectMaskP.height));
                    try {
                        OverlayService.this.wm.updateViewLayout(OverlayService.this.rectMask, OverlayService.this.rectMaskP);
                    } catch (Exception unused) {
                    }
                    return true;
                }
                if (actionMasked != 3) {
                    return false;
                }
            }
            if (this.dragging) {
                OverlayService.this.rectMaskP.alpha = 0.05f;
                OverlayService.this.rfL = OverlayService.clamp01(OverlayService.this.rectMaskP.x / OverlayService.this.screenW);
                OverlayService.this.rfT = OverlayService.clamp01(OverlayService.this.rectMaskP.y / OverlayService.this.screenH);
                OverlayService.this.rfR = OverlayService.clamp01((OverlayService.this.rectMaskP.x + OverlayService.this.rectMaskP.width) / OverlayService.this.screenW);
                OverlayService.this.rfB = OverlayService.clamp01((OverlayService.this.rectMaskP.y + OverlayService.this.rectMaskP.height) / OverlayService.this.screenH);
                OverlayService.this.saveRectFrac();
                try {
                    OverlayService.this.wm.updateViewLayout(OverlayService.this.rectMask, OverlayService.this.rectMaskP);
                } catch (Exception unused2) {
                }
                OverlayService.this.toast("矩形屏蔽已移动");
            }
            return true;
        }
    };

    /* JADX INFO: Access modifiers changed from: private */
    public int clamp(int i, int i2, int i3) {
        return i < i2 ? i2 : i > i3 ? i3 : i;
    }

    static /* synthetic */ boolean lambda$19(View view, MotionEvent motionEvent) {
        return true;
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        this.prefs = getSharedPreferences("qinpu", 0);
        this.wm = (WindowManager) getSystemService("window");
        readScreenSize();
        this.adjustExec = Executors.newSingleThreadExecutor();
        startNotif();
    }

    private void readScreenSize() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                this.screenW = this.wm.getCurrentWindowMetrics().getBounds().width();
                this.screenH = this.wm.getCurrentWindowMetrics().getBounds().height();
            } else {
                this.screenW = getResources().getDisplayMetrics().widthPixels;
                this.screenH = getResources().getDisplayMetrics().heightPixels;
            }
        } catch (Throwable unused) {
            this.screenW = getResources().getDisplayMetrics().widthPixels;
            this.screenH = getResources().getDisplayMetrics().heightPixels;
        }
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        String action = (intent == null || intent.getAction() == null) ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            removeAll();
            stopForeground(1);
            stopSelf();
            return 2;
        }
        if (ACTION_TOGGLE.equals(action)) {
            if (this.imgAdded) {
                togglePanel();
            }
            return 1;
        }
        if (ACTION_TILE.equals(action)) {
            if (this.imgAdded) {
                removeAll();
            } else {
                loadPrefs();
                if (!hasContent()) {
                    Toast.makeText(this, "还没有琴谱，请先在应用中选择图片或导入格子谱", 0).show();
                } else {
                    addWindows();
                }
            }
            return 1;
        }
        loadPrefs();
        if (!hasContent()) {
            Toast.makeText(this, "还没有琴谱，请先在应用中选择图片或导入格子谱", 0).show();
        } else if (!this.imgAdded) {
            addWindows();
        } else {
            int iMax = Math.max(0, Math.min(this.page, Math.max(0, pageCount() - 1)));
            this.page = iMax;
            restoreImgPos();
            showPage(iMax);
        }
        return 1;
    }

    private void loadPrefs() {
        List<SetStore.SetItem> listLoad = SetStore.load(this.prefs);
        this.curSetItem = SetStore.find(listLoad, SetStore.currentName(this.prefs, listLoad));
        this.gridMode = this.curSetItem != null && this.curSetItem.isGrid;
        this.uris.clear();
        String string = this.prefs.getString("uris", "");
        if (string != null && !string.isEmpty()) {
            for (String str : string.split("\n")) {
                if (!str.isEmpty()) {
                    this.uris.add(Uri.parse(str));
                }
            }
        }
        this.alpha = this.prefs.getFloat("alpha", 0.75f);
        this.scale = this.prefs.getFloat("scale", 0.72f);
        this.page = this.prefs.getInt("page", 0);
        this.savedX = this.prefs.getInt("x", Integer.MIN_VALUE);
        this.savedY = this.prefs.getInt("y", Integer.MIN_VALUE);
        this.logicalRotation = this.prefs.getInt("logicalRotation", 90);
        if (this.logicalRotation != 90 && this.logicalRotation != 270) {
            this.logicalRotation = 90;
        }
        this.edgeOn = this.prefs.getBoolean("edgeOn", false);
        this.rectOn = this.prefs.getBoolean("rectOn", false);
        this.efL = loadFrac("efL", "edgeL", 0.11f, this.screenW);
        this.efR = loadFrac("efR", "edgeR", 0.89f, this.screenW);
        this.efT = loadFrac("efT", "edgeT", 0.25f, this.screenH);
        this.efB = loadFrac("efB", "edgeB", 0.75f, this.screenH);
        this.rfL = loadFrac("rfL", "rL", 0.25f, this.screenW);
        this.rfR = loadFrac("rfR", "rR", 0.75f, this.screenW);
        this.rfT = loadFrac("rfT", "rT", 0.14f, this.screenH);
        this.rfB = loadFrac("rfB", "rB", 0.34f, this.screenH);
        this.adjBright = this.prefs.getInt("adjB", 0);
        this.adjContrast = this.prefs.getInt("adjC", 0);
        this.adjGamma = this.prefs.getFloat("adjG", 1.0f);
        updateSetButton();
        syncAdjUI();
        if (this.seekAlpha != null) {
            this.seekAlpha.setProgress(Math.round(this.alpha * 100.0f) - 5);
        }
        updateAlphaLabel();
    }

    private boolean hasContent() {
        if (!this.uris.isEmpty()) {
            return true;
        }
        return this.gridMode && this.curSetItem != null && !this.curSetItem.gridCombos.isEmpty();
    }

    private void updateSetButton() {
        if (this.btnSet != null) {
            this.btnSet.setText("琴谱集：" + SetStore.currentName(this.prefs, SetStore.load(this.prefs)) + " ▾");
        }
    }

    private float loadFrac(String str, String str2, float f, int i) {
        if (this.prefs.contains(str)) {
            return clamp01(this.prefs.getFloat(str, f));
        }
        if (str2 == null || !this.prefs.contains(str2)) {
            return f;
        }
        float fClamp01 = clamp01(this.prefs.getInt(str2, Math.round(f * i)) / Math.max(1, i));
        this.prefs.edit().putFloat(str, fClamp01).remove(str2).apply();
        return fClamp01;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float clamp01(float f) {
        return Math.max(0.0f, Math.min(1.0f, f));
    }

    private void addWindows() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "缺少悬浮窗权限，请打开应用授权", 1).show();
            return;
        }
        this.page = Math.max(0, Math.min(this.page, Math.max(0, pageCount() - 1)));
        ImageView imageView = new ImageView(this);
        this.imgView = imageView;
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        this.imgP = layoutParams;
        layoutParams.type = 2038;
        this.imgP.format = -3;
        this.imgP.gravity = 8388659;
        this.imgP.flags = 16777528;
        this.imgP.windowAnimations = 0;
        this.imgP.alpha = Math.max(0.05f, Math.min(this.alpha, 1.0f));
        applyImageRect();
        this.scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() { // from class: com.qinpu.overlay.OverlayService.4
            @Override // android.view.ScaleGestureDetector.SimpleOnScaleGestureListener, android.view.ScaleGestureDetector.OnScaleGestureListener
            public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
                OverlayService overlayService = OverlayService.this;
                overlayService.setScale(overlayService.scale * scaleGestureDetector.getScaleFactor());
                return true;
            }
        });
        this.imgView.setOnTouchListener(this.imgTouch);
        try {
            this.wm.addView(this.imgView, this.imgP);
            this.imgAdded = true;
            this.imgVisible = true;
            buildPanel();
            WindowManager.LayoutParams layoutParams3 = new WindowManager.LayoutParams();
            this.panelP = layoutParams3;
            layoutParams3.type = 2038;
            this.panelP.format = -3;
            this.panelP.gravity = 8388659;
            this.panelP.flags = 16777256;
            this.panelP.width = dp(272.0f);
            this.panelP.x = Math.max(dp(4.0f), this.screenW - dp(282.0f));
            this.panelP.y = dp(this.screenH >= this.screenW ? 96.0f : 12.0f);
            fitPanelHeight();
            try {
                this.wm.addView(this.panelScroll, this.panelP);
                this.panelAdded = true;
            } catch (Exception unused) {
            }
            buildDot();
            showPage(this.page);
            if (this.edgeOn) {
                applyEdgeStrips();
            }
            if (this.rectOn) {
                addRectMask();
            }
            overlayActive = true;
        } catch (Exception e) {
            Toast.makeText(this, "添加悬浮窗失败：" + e.getMessage(), 1).show();
        }
    }

    private void buildPanel() {
        LinearLayout linearLayout = new LinearLayout(this);
        this.panel = linearLayout;
        linearLayout.setOrientation(1);
        this.panel.setBackground(roundBg(-233563098, 16, 872415231));
        this.panel.setPadding(dp(12.0f), dp(10.0f), dp(12.0f), dp(12.0f));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setOnTouchListener(this.panelDrag);
        TextView textView = new TextView(this);
        textView.setText("琴谱浮窗");
        textView.setTextColor(-5715974);
        textView.setTextSize(14.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        linearLayout2.addView(textView, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textViewHideBtn = miniBtn("隐藏");
        this.btnHide = textViewHideBtn;
        textViewHideBtn.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService.23
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.toggleImgVisible();
            }
        });
        linearLayout2.addView(textViewHideBtn);
        TextView textViewMiniBtn = miniBtn("—");
        textViewMiniBtn.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m45lambda$1$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayout2.addView(textViewMiniBtn);
        TextView textViewMiniBtn2 = miniBtn("✕");
        textViewMiniBtn2.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m55lambda$2$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayout2.addView(textViewMiniBtn2);
        this.panel.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout linearLayoutSetRow = row();
        TextView textViewSetBtn = btn("琴谱集：…");
        this.btnSet = textViewSetBtn;
        textViewSetBtn.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService.14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.toggleSetPicker();
            }
        });
        linearLayoutSetRow.addView(textViewSetBtn, new LinearLayout.LayoutParams(-1, -2));
        this.panel.addView(linearLayoutSetRow, rowParams(8));
        updateSetButton();
        LinearLayout linearLayoutRow = row();
        linearLayoutRow.addView(label("透明度"), new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textView2 = new TextView(this);
        this.tvAlpha = textView2;
        textView2.setTextSize(13.0f);
        this.tvAlpha.setTextColor(-1);
        linearLayoutRow.addView(this.tvAlpha, new LinearLayout.LayoutParams(-2, -2));
        this.panel.addView(linearLayoutRow, rowParams(10));
        SeekBar seekBar = new SeekBar(this);
        this.seekAlpha = seekBar;
        seekBar.setMax(95);
        this.seekAlpha.setProgress(Math.round(this.alpha * 100.0f) - 5);
        this.seekAlpha.setProgressTintList(ColorStateList.valueOf(-10177034));
        this.seekAlpha.setThumbTintList(ColorStateList.valueOf(-4464901));
        this.seekAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.qinpu.overlay.OverlayService.5
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar2) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar2, int i, boolean z) {
                OverlayService.this.alpha = (i + 5) / 100.0f;
                if (OverlayService.this.imgAdded) {
                    OverlayService.this.imgP.alpha = OverlayService.this.alpha;
                    try {
                        OverlayService.this.wm.updateViewLayout(OverlayService.this.imgView, OverlayService.this.imgP);
                    } catch (Exception unused) {
                    }
                }
                OverlayService.this.updateAlphaLabel();
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar2) {
                OverlayService.this.saveState();
            }
        });
        this.panel.addView(this.seekAlpha, rowParams(4));
        LinearLayout linearLayoutRow2 = row();
        TextView textViewBtn = btn("◀ 上一页");
        textViewBtn.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m59lambda$3$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow2.addView(textViewBtn, hp(1.0f));
        TextView textView3 = new TextView(this);
        this.tvPage = textView3;
        textView3.setTextSize(13.0f);
        this.tvPage.setTextColor(-1379585);
        this.tvPage.setGravity(17);
        linearLayoutRow2.addView(this.tvPage, hp(0.65f));
        TextView textViewBtn2 = btn("下一页 ▶");
        textViewBtn2.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda15
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m60lambda$4$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow2.addView(textViewBtn2, hp(1.0f));
        this.panel.addView(linearLayoutRow2, rowParams(10));
        LinearLayout linearLayoutRow3 = row();
        TextView textViewBtn3 = btn("缩小");
        textViewBtn3.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda16
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m61lambda$5$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow3.addView(textViewBtn3, hp(1.0f));
        TextView textViewBtn4 = btn("放大");
        textViewBtn4.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda17
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m62lambda$6$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow3.addView(textViewBtn4, hp(1.0f));
        this.panel.addView(linearLayoutRow3, rowParams(6));
        LinearLayout linearLayoutRow4 = row();
        TextView textViewBtn5 = btn("移动：关");
        this.btnMove = textViewBtn5;
        textViewBtn5.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda18
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m63lambda$7$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow4.addView(this.btnMove, hp(1.55f));
        TextView textViewBtn6 = btn("居中");
        textViewBtn6.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda19
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m64lambda$8$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow4.addView(textViewBtn6, hp(1.0f));
        this.panel.addView(linearLayoutRow4, rowParams(6));
        LinearLayout linearLayoutRow5 = row();
        TextView textViewBtn7 = btn("换图（重新选择图片）");
        textViewBtn7.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda20
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m65lambda$9$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow5.addView(textViewBtn7, new LinearLayout.LayoutParams(-1, -2));
        this.panel.addView(linearLayoutRow5, rowParams(6));
        LinearLayout linearLayoutRow6 = row();
        TextView textViewBtn8 = btn("图像调节 ▸");
        this.btnAdjToggle = textViewBtn8;
        textViewBtn8.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda21
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m46lambda$10$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow6.addView(this.btnAdjToggle, hp(1.4f));
        TextView textViewBtn9 = btn("重置");
        textViewBtn9.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m47lambda$11$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow6.addView(textViewBtn9, hp(1.0f));
        this.panel.addView(linearLayoutRow6, rowParams(6));
        LinearLayout linearLayout3 = new LinearLayout(this);
        this.adjSection = linearLayout3;
        linearLayout3.setOrientation(1);
        this.adjSection.setVisibility(8);
        LinearLayout linearLayoutRow7 = row();
        linearLayoutRow7.addView(label("亮度"), new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textViewAdjValue = adjValue();
        this.tvBright = textViewAdjValue;
        linearLayoutRow7.addView(textViewAdjValue, new LinearLayout.LayoutParams(-2, -2));
        this.adjSection.addView(linearLayoutRow7, rowParams(6));
        SeekBar seekBarAdjSeek = adjSeek(200, this.adjBright + 100, new SeekBar.OnSeekBarChangeListener() { // from class: com.qinpu.overlay.OverlayService.6
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar2) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar2, int i, boolean z) {
                if (z) {
                    OverlayService.this.adjBright = i - 100;
                    OverlayService.this.syncAdjLabels();
                    OverlayService.this.scheduleAdjust();
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar2) {
                OverlayService.this.saveState();
            }
        });
        this.seekBright = seekBarAdjSeek;
        this.adjSection.addView(seekBarAdjSeek, rowParams(4));
        LinearLayout linearLayoutRow8 = row();
        linearLayoutRow8.addView(label("对比度"), new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textViewAdjValue2 = adjValue();
        this.tvContrast = textViewAdjValue2;
        linearLayoutRow8.addView(textViewAdjValue2, new LinearLayout.LayoutParams(-2, -2));
        this.adjSection.addView(linearLayoutRow8, rowParams(6));
        SeekBar seekBarAdjSeek2 = adjSeek(200, this.adjContrast + 100, new SeekBar.OnSeekBarChangeListener() { // from class: com.qinpu.overlay.OverlayService.7
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar2) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar2, int i, boolean z) {
                if (z) {
                    OverlayService.this.adjContrast = i - 100;
                    OverlayService.this.syncAdjLabels();
                    OverlayService.this.scheduleAdjust();
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar2) {
                OverlayService.this.saveState();
            }
        });
        this.seekContrast = seekBarAdjSeek2;
        this.adjSection.addView(seekBarAdjSeek2, rowParams(4));
        LinearLayout linearLayoutRow9 = row();
        linearLayoutRow9.addView(label("伽马"), new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textViewAdjValue3 = adjValue();
        this.tvGamma = textViewAdjValue3;
        linearLayoutRow9.addView(textViewAdjValue3, new LinearLayout.LayoutParams(-2, -2));
        this.adjSection.addView(linearLayoutRow9, rowParams(6));
        SeekBar seekBarAdjSeek3 = adjSeek(280, Math.round((this.adjGamma - 0.2f) * 100.0f), new SeekBar.OnSeekBarChangeListener() { // from class: com.qinpu.overlay.OverlayService.8
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar2) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar2, int i, boolean z) {
                if (z) {
                    OverlayService.this.adjGamma = (i / 100.0f) + 0.2f;
                    OverlayService.this.syncAdjLabels();
                    OverlayService.this.scheduleAdjust();
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar2) {
                OverlayService.this.saveState();
            }
        });
        this.seekGamma = seekBarAdjSeek3;
        this.adjSection.addView(seekBarAdjSeek3, rowParams(4));
        this.panel.addView(this.adjSection, new LinearLayout.LayoutParams(-1, -2));
        syncAdjLabels();
        LinearLayout linearLayoutRow10 = row();
        TextView textViewBtn10 = btn("边缘屏蔽 关");
        this.btnEdge = textViewBtn10;
        textViewBtn10.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m48lambda$12$comqinpuoverlayOverlayService(view);
            }
        });
        this.btnEdge.setOnLongClickListener(new View.OnLongClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda10
            @Override // android.view.View.OnLongClickListener
            public final boolean onLongClick(View view) {
                return OverlayService.this.m49lambda$13$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow10.addView(this.btnEdge, hp(1.0f));
        TextView textViewBtn11 = btn("矩形屏蔽 关");
        this.btnRect = textViewBtn11;
        textViewBtn11.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m50lambda$14$comqinpuoverlayOverlayService(view);
            }
        });
        this.btnRect.setOnLongClickListener(new View.OnLongClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda12
            @Override // android.view.View.OnLongClickListener
            public final boolean onLongClick(View view) {
                return OverlayService.this.m51lambda$15$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayoutRow10.addView(this.btnRect, hp(1.0f));
        this.panel.addView(linearLayoutRow10, rowParams(6));
        updateMaskButtons();
        updateAlphaLabel();
        this.panel.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));
        ScrollView scrollView = new ScrollView(this);
        this.panelScroll = scrollView;
        scrollView.setVerticalScrollBarEnabled(true);
        this.panelScroll.addView(this.panel);
    }

    /* JADX INFO: renamed from: lambda$1$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m45lambda$1$comqinpuoverlayOverlayService(View view) {
        togglePanel();
    }

    /* JADX INFO: renamed from: lambda$2$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m55lambda$2$comqinpuoverlayOverlayService(View view) {
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        if (jElapsedRealtime - this.lastExitTap < 2200) {
            removeAll();
            stopForeground(1);
            stopSelf();
        } else {
            this.lastExitTap = jElapsedRealtime;
            toast("再点一次 ✕ 退出悬浮窗");
        }
    }

    /* JADX INFO: renamed from: lambda$3$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m59lambda$3$comqinpuoverlayOverlayService(View view) {
        turnPage(-1);
    }

    /* JADX INFO: renamed from: lambda$4$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m60lambda$4$comqinpuoverlayOverlayService(View view) {
        turnPage(1);
    }

    /* JADX INFO: renamed from: lambda$5$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m61lambda$5$comqinpuoverlayOverlayService(View view) {
        setScale(this.scale / 1.12f);
    }

    /* JADX INFO: renamed from: lambda$6$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m62lambda$6$comqinpuoverlayOverlayService(View view) {
        setScale(this.scale * 1.12f);
    }

    /* JADX INFO: renamed from: lambda$7$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m63lambda$7$comqinpuoverlayOverlayService(View view) {
        setMoveMode(!this.moveMode);
    }

    /* JADX INFO: renamed from: lambda$8$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m64lambda$8$comqinpuoverlayOverlayService(View view) {
        centerImage();
    }

    /* JADX INFO: renamed from: lambda$9$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m65lambda$9$comqinpuoverlayOverlayService(View view) {
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        intent.addFlags(268435456);
        intent.putExtra("pick", true);
        startActivity(intent);
    }

    /* JADX INFO: renamed from: lambda$10$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m46lambda$10$comqinpuoverlayOverlayService(View view) {
        toggleAdjSection();
    }

    /* JADX INFO: renamed from: lambda$11$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m47lambda$11$comqinpuoverlayOverlayService(View view) {
        this.adjBright = 0;
        this.adjContrast = 0;
        this.adjGamma = 1.0f;
        syncAdjUI();
        scheduleAdjust();
        saveState();
        toast("已恢复默认图像效果");
    }

    /* JADX INFO: renamed from: lambda$12$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m48lambda$12$comqinpuoverlayOverlayService(View view) {
        setEdgeOn(!this.edgeOn);
    }

    /* JADX INFO: renamed from: lambda$13$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ boolean m49lambda$13$comqinpuoverlayOverlayService(View view) {
        openEditor(1);
        return true;
    }

    /* JADX INFO: renamed from: lambda$14$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m50lambda$14$comqinpuoverlayOverlayService(View view) {
        setRectOn(!this.rectOn);
    }

    /* JADX INFO: renamed from: lambda$15$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ boolean m51lambda$15$comqinpuoverlayOverlayService(View view) {
        openEditor(2);
        return true;
    }

    private void fitPanelHeight() {
        if (this.panel == null || this.panelScroll == null || this.panelP == null) {
            return;
        }
        this.panel.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(80.0f), this.panelP.width), 1073741824), View.MeasureSpec.makeMeasureSpec(0, 0));
        int iMin = Math.min(this.panel.getMeasuredHeight() + dp(6.0f), Math.max(dp(120.0f), this.screenH - dp(24.0f)));
        if (this.panelP.height != iMin) {
            this.panelP.height = iMin;
            if (this.panelAdded) {
                try {
                    this.wm.updateViewLayout(this.panelScroll, this.panelP);
                } catch (Exception unused) {
                }
            }
        }
    }

    private void buildDot() {
        TextView textView = new TextView(this);
        this.dot = textView;
        textView.setText("♪");
        this.dot.setTextSize(22.0f);
        this.dot.setTextColor(-1);
        this.dot.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-870636496);
        gradientDrawable.setShape(1);
        gradientDrawable.setStroke(dp(1.0f), 1090519039);
        this.dot.setBackground(gradientDrawable);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(dp(46.0f), dp(46.0f), 2038, 40, -3);
        this.dotP = layoutParams;
        layoutParams.gravity = 8388659;
        int i = this.prefs.getInt("dotX", Integer.MIN_VALUE);
        int i2 = this.prefs.getInt("dotY", Integer.MIN_VALUE);
        if (i == Integer.MIN_VALUE || i2 == Integer.MIN_VALUE) {
            this.dotP.x = Math.max(dp(4.0f), this.screenW - dp(58.0f));
            this.dotP.y = dp(180.0f);
        } else {
            this.dotP.x = clamp(i, 0, Math.max(0, this.screenW - dp(46.0f)));
            this.dotP.y = clamp(i2, 0, Math.max(0, this.screenH - dp(46.0f)));
        }
        this.dot.setOnTouchListener(new View.OnTouchListener() { // from class: com.qinpu.overlay.OverlayService.9
            float downX;
            float downY;
            boolean dragged;
            int startX;
            int startY;

            /* JADX WARN: Code restructure failed: missing block: B:8:0x000e, code lost:
            
                if (r8 != 3) goto L16;
             */
            @Override // android.view.View.OnTouchListener
            /*
                Code decompiled incorrectly, please refer to instructions dump.
            */
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int actionMasked = motionEvent.getActionMasked();
                if (actionMasked == 0) {
                    this.downX = motionEvent.getRawX();
                    this.downY = motionEvent.getRawY();
                    this.startX = OverlayService.this.dotP.x;
                    this.startY = OverlayService.this.dotP.y;
                    this.dragged = false;
                    return true;
                }
                if (actionMasked != 1) {
                    if (actionMasked == 2) {
                        float rawX = motionEvent.getRawX() - this.downX;
                        float rawY = motionEvent.getRawY() - this.downY;
                        if (this.dragged || Math.abs(rawX) >= OverlayService.this.dp(8.0f) || Math.abs(rawY) >= OverlayService.this.dp(8.0f)) {
                            this.dragged = true;
                            OverlayService.this.dotP.x = OverlayService.this.clamp(Math.round(this.startX + rawX), 0, Math.max(0, OverlayService.this.screenW - OverlayService.this.dp(46.0f)));
                            OverlayService.this.dotP.y = OverlayService.this.clamp(Math.round(this.startY + rawY), 0, Math.max(0, OverlayService.this.screenH - OverlayService.this.dp(46.0f)));
                            if (OverlayService.this.dotAdded) {
                                OverlayService.this.wm.updateViewLayout(OverlayService.this.dot, OverlayService.this.dotP);
                            }
                            return true;
                        }
                    }
                    return false;
                }
                if (this.dragged) {
                    OverlayService.this.prefs.edit().putInt("dotX", OverlayService.this.dotP.x).putInt("dotY", OverlayService.this.dotP.y).apply();
                } else {
                    OverlayService.this.togglePanel();
                }
                return true;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateAlphaLabel() {
        if (this.tvAlpha == null) {
            return;
        }
        int iRound = Math.round(this.alpha * 100.0f);
        if (this.alpha > maxObscuringOpacity() + 0.001f) {
            this.tvAlpha.setText(iRound + "% ⚠");
            this.tvAlpha.setTextColor(-18611);
            return;
        }
        this.tvAlpha.setText(iRound + "%");
        this.tvAlpha.setTextColor(-1);
    }

    private float maxObscuringOpacity() {
        try {
            return Settings.Global.getFloat(getContentResolver(), "maximum_obscuring_opacity_for_touch", 0.8f);
        } catch (Throwable unused) {
            return 0.8f;
        }
    }

    private void setMoveMode(boolean z) {
        this.moveMode = z;
        if (this.imgAdded) {
            if (z) {
                this.imgP.flags &= -17;
            } else {
                this.imgP.flags |= 16;
            }
            try {
                this.wm.updateViewLayout(this.imgView, this.imgP);
            } catch (Exception unused) {
            }
            TextView textView = this.btnMove;
            if (textView != null) {
                textView.setText(z ? "移动：开" : "移动：关");
                this.btnMove.setBackground(roundBg(z ? -9675545 : -13878960, 9, z ? -2130706433 : 822083583));
            }
            toast(z ? "移动模式：拖动图片、双指缩放；完成后点『移动：关』恢复穿透" : "已恢复触摸穿透（不可交互）");
        }
    }

    private int pageCount() {
        if (this.gridMode && this.curSetItem != null) {
            return Math.max(1, GridScore.pageCount(this.curSetItem));
        }
        return this.uris.size();
    }

    private void turnPage(int i) {
        int size = pageCount();
        if (size <= 1) {
            toast(this.gridMode ? "格子谱只有一页（可在「样式与布局」里设置行数分页）" : "只有 1 张图片");
            return;
        }
        int idx = (((this.page + i) % size) + size) % size;
        this.page = idx;
        showPage(idx);
    }

    private void showPage(int i) {
        Bitmap bitmap;
        Bitmap bitmap2;
        if ((this.uris.isEmpty() && !this.gridMode) || !this.imgAdded) {
            return;
        }
        int iTotal = Math.max(1, pageCount());
        int iMax = Math.max(0, Math.min(i, iTotal - 1));
        this.page = iMax;
        Bitmap bitmapDecode;
        if (this.gridMode) {
            bitmapDecode = GridScore.render(this, this.curSetItem, this.screenW, this.screenH, iMax);
        } else {
            bitmapDecode = decode(this.uris.get(iMax));
        }
        if (bitmapDecode == null) {
            toast(this.gridMode ? "格子谱为空或渲染失败" : "图片读取失败（可能已被移动或删除）");
            return;
        }
        this.adjustGen++;
        synchronized (this.bmpLock) {
            bitmap = this.bmp;
            if (bitmap == null || bitmap == this.srcBmp) {
                bitmap = null;
            }
            bitmap2 = this.srcBmp;
            this.srcBmp = bitmapDecode;
            this.bmp = bitmapDecode;
        }
        this.imgView.setImageBitmap(bitmapDecode);
        if (bitmap != null) {
            try {
                bitmap.recycle();
            } catch (Exception unused) {
            }
        }
        if (bitmap2 != null) {
            try {
                bitmap2.recycle();
            } catch (Exception unused2) {
            }
        }
        this.curAspect = ((float) this.srcBmp.getHeight()) / ((float) this.srcBmp.getWidth());
        applyImageRect();
        TextView textView = this.tvPage;
        if (textView != null) {
            textView.setText((this.page + 1) + "/" + iTotal);
        }
        applyAdjust();
        saveState();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setScale(float f) {
        float fMin = Math.min(2.5f, Math.max(0.15f, f));
        this.scale = fMin;
        if (!this.imgAdded || this.bmp == null) {
            return;
        }
        applyImageRect();
        saveState();
    }

    private void centerImage() {
        if (this.imgAdded) {
            WindowManager.LayoutParams layoutParams = this.imgP;
            layoutParams.x = Math.max(0, (this.screenW - layoutParams.width) / 2);
            WindowManager.LayoutParams layoutParams2 = this.imgP;
            layoutParams2.y = Math.max(0, (this.screenH - layoutParams2.height) / 2);
            try {
                this.wm.updateViewLayout(this.imgView, this.imgP);
            } catch (Exception unused) {
            }
            saveState();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void clampImgPos() {
        int i = this.imgP.width;
        int i2 = this.imgP.height;
        this.imgP.x = Math.max((-i) + dp(60.0f), Math.min(this.imgP.x, this.screenW - dp(60.0f)));
        WindowManager.LayoutParams layoutParams = this.imgP;
        layoutParams.y = Math.max(0, Math.min(layoutParams.y, this.screenH - dp(60.0f)));
    }

    private void restoreImgPos() {
        applyImageRect();
    }

    private int displayRotation() {
        try {
            return this.wm.getDefaultDisplay().getRotation();
        } catch (Throwable unused) {
            return 0;
        }
    }

    private void applyImageRect() {
        if (this.imgP == null) {
            return;
        }
        int iRotation = displayRotation();
        boolean zLandscape = iRotation == 1 || iRotation == 3;
        if (zLandscape) {
            int iDeg = iRotation == 1 ? 90 : 270;
            this.logicalRotation = iDeg;
            if (this.prefs.getInt("logicalRotation", 0) != iDeg) {
                this.prefs.edit().putInt("logicalRotation", iDeg).apply();
            }
        }
        int iShort = Math.min(this.screenW, this.screenH);
        int iLong = Math.max(this.screenW, this.screenH);
        int iWidthLogical;
        if (this.gridMode && this.curSetItem != null && this.curSetItem.gLayout != GridScore.LAYOUT_SINGLE) {
            iWidthLogical = Math.round(this.screenW * this.scale);
        } else {
            iWidthLogical = Math.round(iShort * this.scale);
        }
        int iHeightLogical = Math.max(dp(40.0f), Math.round(iWidthLogical * this.curAspect));
        if (this.savedX == Integer.MIN_VALUE) {
            this.savedX = Math.max(0, (iLong - iWidthLogical) / 2);
            this.savedY = dp(48.0f);
        }
        if (zLandscape) {
            this.viewRotation = 0;
            this.imgP.width = iWidthLogical;
            this.imgP.height = iHeightLogical;
            this.imgP.x = this.savedX;
            this.imgP.y = this.savedY;
        } else {
            this.viewRotation = (((this.logicalRotation - (iRotation * 90)) % 360) + 360) % 360;
            boolean zSideA = (this.logicalRotation == 90) == (iRotation == 0);
            this.imgP.width = iHeightLogical;
            this.imgP.height = iWidthLogical;
            if (zSideA) {
                this.imgP.x = iShort - (this.savedY + iHeightLogical);
                this.imgP.y = this.savedX;
            } else {
                this.imgP.x = this.savedY;
                this.imgP.y = iLong - this.savedX - iWidthLogical;
            }
        }
        if (this.gridMode && this.curSetItem != null && this.curSetItem.gLayout == GridScore.LAYOUT_LR) {
            this.imgP.x = Math.round((this.screenW - this.imgP.width) / 2.0f);
            this.imgP.y = 0;
        }
        updateImageMatrix();
        this.lastAppliedRotation = iRotation;
        if (this.imgAdded && this.imgView != null) {
            try {
                this.wm.updateViewLayout(this.imgView, this.imgP);
            } catch (Exception unused) {
            }
        }
    }

    private void updateImageMatrix() {
        if (this.imgView == null || this.bmp == null || this.imgP == null) {
            return;
        }
        int iBitmapW = this.bmp.getWidth();
        int iBitmapH = this.bmp.getHeight();
        if (iBitmapW <= 0 || iBitmapH <= 0 || this.imgP.width <= 0 || this.imgP.height <= 0) {
            return;
        }
        float fScaleX;
        float fScaleY;
        if (this.viewRotation == 90 || this.viewRotation == 270) {
            fScaleX = this.imgP.height / ((float) iBitmapW);
            fScaleY = this.imgP.width / ((float) iBitmapH);
        } else {
            fScaleX = this.imgP.width / ((float) iBitmapW);
            fScaleY = this.imgP.height / ((float) iBitmapH);
        }
        Matrix matrix = new Matrix();
        matrix.setTranslate((-iBitmapW) / 2.0f, (-iBitmapH) / 2.0f);
        matrix.postScale(fScaleX, fScaleY);
        matrix.postRotate(this.viewRotation);
        matrix.postTranslate(this.imgP.width / 2.0f, this.imgP.height / 2.0f);
        this.imgView.setImageMatrix(matrix);
    }

    private void captureLogicalPos() {
        if (this.imgP == null) {
            return;
        }
        int iRotation = displayRotation();
        if (iRotation == 1 || iRotation == 3) {
            this.savedX = this.imgP.x;
            this.savedY = this.imgP.y;
            return;
        }
        boolean zSideA = (this.logicalRotation == 90) == (iRotation == 0);
        if (zSideA) {
            this.savedX = this.imgP.y;
            this.savedY = Math.min(this.screenW, this.screenH) - this.imgP.x - this.imgP.width;
        } else {
            this.savedX = Math.max(this.screenW, this.screenH) - this.imgP.y - this.imgP.height;
            this.savedY = this.imgP.x;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void togglePanel() {
        TextView textView;
        try {
            if (this.panelAdded) {
                safeRemove(this.panelScroll);
                this.panelAdded = false;
                if (this.dotAdded || (textView = this.dot) == null) {
                    return;
                }
                this.wm.addView(textView, this.dotP);
                this.dotAdded = true;
            } else {
                if (this.panelScroll == null) {
                    return;
                }
                if (this.dotAdded) {
                    safeRemove(this.dot);
                    this.dotAdded = false;
                }
                fitPanelHeight();
                this.wm.addView(this.panelScroll, this.panelP);
                this.panelAdded = true;
            }
        } catch (Exception unused) {
        }
    }

    private void toggleImgVisible() {
        if (!this.imgAdded) {
            return;
        }
        if (this.imgVisible) {
            this.imgVisible = false;
            safeRemove(this.imgView);
            removeEdgeStrips();
            if (this.rectMaskAdded) {
                safeRemove(this.rectMask);
                this.rectMaskAdded = false;
            }
            updateHideButton();
            toast("琴谱已隐藏，点『显示』恢复");
            return;
        }
        try {
            this.wm.addView(this.imgView, this.imgP);
        } catch (Exception e) {
            toast("显示琴谱失败：" + e.getMessage());
            return;
        }
        this.imgVisible = true;
        applyImageRect();
        if (this.edgeOn) {
            applyEdgeStrips();
        }
        if (this.rectOn) {
            addRectMask();
        }
        bringPanelDotTop();
        updateHideButton();
        toast("琴谱已显示");
    }

    private void updateHideButton() {
        if (this.btnHide == null) {
            return;
        }
        this.btnHide.setText(this.imgVisible ? "隐藏" : "显示");
        this.btnHide.setTextColor(this.imgVisible ? -4602666 : -10177034);
    }

    private void toggleSetPicker() {
        if (this.setPickerAdded) {
            closeSetPicker();
            return;
        }
        final List<SetStore.SetItem> list = SetStore.load(this.prefs);
        if (list.isEmpty()) {
            toast("还没有琴谱集，请先在应用中新建并选择图片");
            return;
        }
        String strCurrentName = SetStore.currentName(this.prefs, list);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackground(roundBg(-233563098, 16, 872415231));
        linearLayout.setPadding(dp(12.0f), dp(10.0f), dp(12.0f), dp(12.0f));
        TextView textView = new TextView(this);
        textView.setText("切换琴谱集");
        textView.setTextColor(-5715974);
        textView.setTextSize(14.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        linearLayout.addView(textView, new LinearLayout.LayoutParams(-1, -2));
        for (final SetStore.SetItem item : list) {
            boolean zActive = item.name.equals(strCurrentName);
            String desc = item.isGrid ? ("格子谱 " + item.gridCombos.size() + " 组") : (item.uris.size() + " 张");
            TextView textViewBtn = btn((zActive ? "✓ " : "") + item.name + "（" + desc + "）");
            if (zActive) {
                textViewBtn.setTextColor(-1);
                textViewBtn.setBackground(roundBg(-12751904, 9, -2130706433));
            }
            textViewBtn.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService.15
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    OverlayService.this.switchSet(item);
                }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.topMargin = dp(8.0f);
            linearLayout.addView(textViewBtn, layoutParams);
        }
        TextView textViewMiniBtn = miniBtn("✕ 关闭");
        textViewMiniBtn.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService.16
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.closeSetPicker();
            }
        });
        LinearLayout linearLayout2 = row();
        linearLayout2.addView(textViewMiniBtn);
        linearLayout.addView(linearLayout2, rowParams(10));
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.addView(linearLayout);
        int iHeight = Math.max(dp(140.0f), Math.min(this.screenH - dp(24.0f), (list.size() * dp(52.0f)) + dp(132.0f)));
        int iWidth = Math.max(dp(200.0f), Math.min(dp(280.0f), this.screenW - dp(16.0f)));
        WindowManager.LayoutParams layoutParams2 = new WindowManager.LayoutParams(iWidth, iHeight, 2038, 16777256, -3);
        layoutParams2.gravity = 8388659;
        if (this.panelAdded) {
            layoutParams2.x = Math.max(0, Math.min(this.panelP.x, this.screenW - iWidth));
            layoutParams2.y = Math.max(0, Math.min(this.panelP.y + dp(96.0f), this.screenH - dp(80.0f)));
        } else {
            layoutParams2.x = Math.max(dp(4.0f), this.screenW - iWidth - dp(8.0f));
            layoutParams2.y = dp(120.0f);
        }
        this.setPickerRoot = scrollView;
        this.setPickerP = layoutParams2;
        try {
            this.wm.addView(scrollView, layoutParams2);
            this.setPickerAdded = true;
        } catch (Exception e) {
            toast("打开琴谱集列表失败：" + e.getMessage());
        }
    }

    private void closeSetPicker() {
        if (this.setPickerAdded) {
            safeRemove(this.setPickerRoot);
            this.setPickerAdded = false;
        }
    }

    private void switchSet(SetStore.SetItem setItem) {
        if (setItem.uris.isEmpty() && !setItem.isGrid) {
            toast("“" + setItem.name + "”还没有内容");
            return;
        }
        SetStore.activate(this.prefs, setItem);
        loadPrefs();
        this.page = 0;
        showPage(0);
        updateSetButton();
        closeSetPicker();
        toast("已切换：" + setItem.name + "（" + (setItem.isGrid ? "格子谱 " + setItem.gridCombos.size() + " 组" : setItem.uris.size() + " 张") + "）");
    }

    private void removeAll() {
        overlayActive = false;
        closeSetPicker();
        if (this.imgAdded) {
            safeRemove(this.imgView);
            this.imgAdded = false;
        }
        this.imgVisible = true;
        if (this.panelAdded) {
            safeRemove(this.panelScroll);
            this.panelAdded = false;
        }
        if (this.dotAdded) {
            safeRemove(this.dot);
            this.dotAdded = false;
        }
        removeEdgeStrips();
        if (this.rectMaskAdded) {
            safeRemove(this.rectMask);
            this.rectMaskAdded = false;
        }
        if (this.editorAdded) {
            safeRemove(this.editorRoot);
            this.editorAdded = false;
        }
        this.editorView = null;
        this.editorRoot = null;
        this.moveMode = false;
        this.adjustGen++;
        this.mainHandler.removeCallbacks(this.adjustTask);
        synchronized (this.bmpLock) {
            Bitmap bitmap = this.bmp;
            if (bitmap != null && bitmap != this.srcBmp) {
                try {
                    bitmap.recycle();
                } catch (Exception unused) {
                }
            }
            Bitmap bitmap2 = this.srcBmp;
            if (bitmap2 != null) {
                try {
                    bitmap2.recycle();
                } catch (Exception unused2) {
                }
            }
            this.bmp = null;
            this.srcBmp = null;
        }
    }

    private void safeRemove(View view) {
        try {
            this.wm.removeView(view);
        } catch (Exception unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void saveState() {
        SharedPreferences.Editor editorEdit = this.prefs.edit();
        boolean zAutoLayout = this.gridMode && this.curSetItem != null && this.curSetItem.gLayout == GridScore.LAYOUT_LR;
        if (this.imgP != null && this.imgAdded && !zAutoLayout) {
            captureLogicalPos();
            editorEdit.putInt("x", this.savedX).putInt("y", this.savedY);
        }
        editorEdit.putFloat("alpha", this.alpha).putFloat("scale", this.scale).putInt("page", this.page);
        editorEdit.putInt("adjB", this.adjBright).putInt("adjC", this.adjContrast).putFloat("adjG", this.adjGamma);
        editorEdit.apply();
        String strCurrent = this.prefs.getString(SetStore.KEY_CURRENT, "");
        if (!strCurrent.isEmpty()) {
            SetStore.captureStateFromPrefs(this.prefs, strCurrent);
        }
    }

    private void toggleAdjSection() {
        ScrollView scrollView;
        boolean z = !this.adjOpen;
        this.adjOpen = z;
        LinearLayout linearLayout = this.adjSection;
        if (linearLayout != null) {
            linearLayout.setVisibility(z ? 0 : 8);
        }
        TextView textView = this.btnAdjToggle;
        if (textView != null) {
            textView.setText(this.adjOpen ? "图像调节 ▾" : "图像调节 ▸");
        }
        if (!this.adjOpen && (scrollView = this.panelScroll) != null) {
            scrollView.scrollTo(0, 0);
        }
        fitPanelHeight();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void scheduleAdjust() {
        this.mainHandler.removeCallbacks(this.adjustTask);
        this.mainHandler.postDelayed(this.adjustTask, 150L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyAdjust() {
        final Bitmap bitmap;
        if (!this.imgAdded || (bitmap = this.srcBmp) == null) {
            return;
        }
        final int i = this.adjustGen + 1;
        this.adjustGen = i;
        final boolean z = this.adjBright == 0 && this.adjContrast == 0 && Math.abs(this.adjGamma - 1.0f) < 0.005f;
        this.adjustExec.execute(new Runnable() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                OverlayService.this.m52lambda$16$comqinpuoverlayOverlayService(i, bitmap, z);
            }
        });
    }

    /* JADX INFO: renamed from: lambda$16$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m52lambda$16$comqinpuoverlayOverlayService(final int i, final Bitmap bitmap, boolean z) {
        final Bitmap bitmapCreateBitmap;
        try {
            if (i == this.adjustGen && !bitmap.isRecycled()) {
                if (z) {
                    bitmapCreateBitmap = bitmap;
                } else {
                    synchronized (this.bmpLock) {
                        if (bitmap.isRecycled()) {
                            return;
                        }
                        int width = bitmap.getWidth();
                        int height = bitmap.getHeight();
                        int i2 = width * height;
                        int[] iArr = new int[i2];
                        bitmap.getPixels(iArr, 0, width, 0, 0, width, height);
                        int[] iArrBuildLut = buildLut();
                        for (int i3 = 0; i3 < i2; i3++) {
                            int i4 = iArr[i3];
                            iArr[i3] = iArrBuildLut[i4 & 255] | ((-16777216) & i4) | (iArrBuildLut[(i4 >> 16) & 255] << 16) | (iArrBuildLut[(i4 >> 8) & 255] << 8);
                        }
                        bitmapCreateBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmapCreateBitmap.setPixels(iArr, 0, width, 0, 0, width, height);
                    }
                }
                this.mainHandler.post(new Runnable() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda5
                    @Override // java.lang.Runnable
                    public final void run() {
                        OverlayService.this.m53lambda$17$comqinpuoverlayOverlayService(i, bitmapCreateBitmap, bitmap);
                    }
                });
            }
        } catch (Throwable unused) {
            this.mainHandler.post(new Runnable() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda6
                @Override // java.lang.Runnable
                public final void run() {
                    OverlayService.this.m54lambda$18$comqinpuoverlayOverlayService();
                }
            });
        }
    }

    /* JADX INFO: renamed from: lambda$17$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m53lambda$17$comqinpuoverlayOverlayService(int i, Bitmap bitmap, Bitmap bitmap2) {
        if (i != this.adjustGen || !this.imgAdded) {
            if (bitmap == bitmap2 || bitmap.isRecycled()) {
                return;
            }
            try {
                bitmap.recycle();
                return;
            } catch (Exception unused) {
                return;
            }
        }
        synchronized (this.bmpLock) {
            Bitmap bitmap3 = this.bmp;
            if (bitmap3 != null && bitmap3 != this.srcBmp && bitmap3 != bitmap) {
                try {
                    bitmap3.recycle();
                } catch (Exception unused2) {
                }
            }
            this.bmp = bitmap;
        }
        this.imgView.setImageBitmap(bitmap);
        updateImageMatrix();
    }

    /* JADX INFO: renamed from: lambda$18$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m54lambda$18$comqinpuoverlayOverlayService() {
        toast("图像调节失败，请重试");
    }

    private int[] buildLut() {
        int[] iArr = new int[256];
        float f = (this.adjContrast / 100.0f) + 1.0f;
        if (f < 0.1f) {
            f = 0.1f;
        }
        float f2 = (this.adjBright / 100.0f) * 0.5f;
        float fMax = 1.0f / Math.max(0.05f, this.adjGamma);
        for (int i = 0; i < 256; i++) {
            iArr[i] = Math.round(((float) Math.pow(Math.max(0.0f, Math.min(1.0f, (((i / 255.0f) - 0.5f) * f) + 0.5f + f2)), fMax)) * 255.0f);
        }
        return iArr;
    }

    private void syncAdjUI() {
        SeekBar seekBar = this.seekBright;
        if (seekBar != null) {
            seekBar.setProgress(this.adjBright + 100);
        }
        SeekBar seekBar2 = this.seekContrast;
        if (seekBar2 != null) {
            seekBar2.setProgress(this.adjContrast + 100);
        }
        SeekBar seekBar3 = this.seekGamma;
        if (seekBar3 != null) {
            seekBar3.setProgress(Math.round((this.adjGamma - 0.2f) * 100.0f));
        }
        syncAdjLabels();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void syncAdjLabels() {
        TextView textView = this.tvBright;
        if (textView != null) {
            int i = this.adjBright;
            textView.setText((i > 0 ? "+" : "") + i);
        }
        TextView textView2 = this.tvContrast;
        if (textView2 != null) {
            int i2 = this.adjContrast;
            textView2.setText((i2 <= 0 ? "" : "+") + i2);
        }
        TextView textView3 = this.tvGamma;
        if (textView3 != null) {
            textView3.setText(String.format(Locale.US, "%.2f", Float.valueOf(this.adjGamma)));
        }
    }

    private TextView adjValue() {
        TextView textView = new TextView(this);
        textView.setTextSize(13.0f);
        textView.setTextColor(-1);
        return textView;
    }

    private SeekBar adjSeek(int i, int i2, SeekBar.OnSeekBarChangeListener onSeekBarChangeListener) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(i);
        seekBar.setProgress(i2);
        seekBar.setProgressTintList(ColorStateList.valueOf(-10177034));
        seekBar.setThumbTintList(ColorStateList.valueOf(-4464901));
        seekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        return seekBar;
    }

    private WindowManager.LayoutParams maskP(int i, int i2, int i3, int i4) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(i3, i4, 2038, 296, -3);
        layoutParams.gravity = 8388659;
        layoutParams.x = i;
        layoutParams.y = i2;
        layoutParams.alpha = 0.05f;
        return layoutParams;
    }

    private View blockerView() {
        View view = new View(this);
        view.setBackgroundColor(-16777216);
        view.setOnTouchListener(new View.OnTouchListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda22
            @Override // android.view.View.OnTouchListener
            public final boolean onTouch(View view2, MotionEvent motionEvent) {
                return OverlayService.lambda$19(view2, motionEvent);
            }
        });
        return view;
    }

    private void applyEdgeStrips() {
        removeEdgeStrips();
        int iRound = Math.round(this.efL * this.screenW);
        int iRound2 = Math.round(this.efR * this.screenW);
        int iRound3 = Math.round(this.efT * this.screenH);
        int iRound4 = Math.round(this.efB * this.screenH);
        int iClamp = clamp(iRound, 0, this.screenW);
        int iClamp2 = clamp(iRound2, 0, this.screenW);
        int iClamp3 = clamp(iRound3, 0, this.screenH);
        int iClamp4 = clamp(iRound4, 0, this.screenH);
        if (iClamp2 - iClamp < dp(40.0f)) {
            int i = (iClamp + iClamp2) / 2;
            int iMax = Math.max(0, i - dp(20.0f));
            iClamp2 = Math.min(this.screenW, i + dp(20.0f));
            iClamp = iMax;
        }
        if (iClamp4 - iClamp3 < dp(40.0f)) {
            int i2 = (iClamp3 + iClamp4) / 2;
            int iMax2 = Math.max(0, i2 - dp(20.0f));
            iClamp4 = Math.min(this.screenH, i2 + dp(20.0f));
            iClamp3 = iMax2;
        }
        int i3 = this.screenW;
        int[][] iArr = {new int[]{0, 0, i3, iClamp3}, new int[]{0, iClamp4, i3, this.screenH}, new int[]{0, iClamp3, iClamp, iClamp4}, new int[]{iClamp2, iClamp3, i3, iClamp4}};
        for (int i4 = 0; i4 < 4; i4++) {
            int[] iArr2 = iArr[i4];
            int i5 = iArr2[2] - iArr2[0];
            int i6 = iArr2[3] - iArr2[1];
            if (i5 > 0 && i6 > 0) {
                View[] viewArr = this.strips;
                if (viewArr[i4] == null) {
                    viewArr[i4] = blockerView();
                }
                WindowManager.LayoutParams[] layoutParamsArr = this.stripPs;
                int[] iArr3 = iArr[i4];
                layoutParamsArr[i4] = maskP(iArr3[0], iArr3[1], i5, i6);
                try {
                    this.wm.addView(this.strips[i4], this.stripPs[i4]);
                } catch (Exception unused) {
                }
            }
        }
        bringPanelDotTop();
    }

    private void removeEdgeStrips() {
        for (int i = 0; i < 4; i++) {
            View view = this.strips[i];
            if (view != null) {
                safeRemove(view);
                this.stripPs[i] = null;
            }
        }
    }

    private void saveEdgeFrac() {
        this.prefs.edit().putFloat("efL", this.efL).putFloat("efT", this.efT).putFloat("efR", this.efR).putFloat("efB", this.efB).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void saveRectFrac() {
        this.prefs.edit().putFloat("rfL", this.rfL).putFloat("rfT", this.rfT).putFloat("rfR", this.rfR).putFloat("rfB", this.rfB).apply();
    }

    private void setEdgeOn(boolean z) {
        this.edgeOn = z;
        this.prefs.edit().putBoolean("edgeOn", z).apply();
        if (z) {
            this.efL = clamp01(this.prefs.getFloat("efL", this.efL));
            this.efR = clamp01(this.prefs.getFloat("efR", this.efR));
            this.efT = clamp01(this.prefs.getFloat("efT", this.efT));
            this.efB = clamp01(this.prefs.getFloat("efB", this.efB));
            if (this.imgVisible) {
                applyEdgeStrips();
            }
            toast("边缘屏蔽已开启（恢复上次区域），长按按钮可重新框选");
        } else {
            removeEdgeStrips();
            toast("边缘屏蔽已关闭（区域已记忆，再开会恢复原样）");
        }
        updateMaskButtons();
    }

    private void addRectMask() {
        if (this.rectMaskAdded) {
            safeRemove(this.rectMask);
            this.rectMaskAdded = false;
        }
        int iClamp = clamp(Math.round(this.rfL * this.screenW), 0, this.screenW);
        int iClamp2 = clamp(Math.round(this.rfR * this.screenW), 0, this.screenW);
        int iClamp3 = clamp(Math.round(this.rfT * this.screenH), 0, this.screenH);
        int iClamp4 = clamp(Math.round(this.rfB * this.screenH), 0, this.screenH);
        if (iClamp2 - iClamp < dp(30.0f)) {
            int i = (iClamp + iClamp2) / 2;
            int iMax = Math.max(0, i - dp(15.0f));
            iClamp2 = Math.min(this.screenW, i + dp(15.0f));
            iClamp = iMax;
        }
        if (iClamp4 - iClamp3 < dp(30.0f)) {
            int i2 = (iClamp3 + iClamp4) / 2;
            int iMax2 = Math.max(0, i2 - dp(15.0f));
            iClamp4 = Math.min(this.screenH, i2 + dp(15.0f));
            iClamp3 = iMax2;
        }
        if (this.rectMask == null) {
            View viewBlockerView = blockerView();
            this.rectMask = viewBlockerView;
            viewBlockerView.setOnTouchListener(this.rectMaskDrag);
        }
        WindowManager.LayoutParams layoutParamsMaskP = maskP(iClamp, iClamp3, iClamp2 - iClamp, iClamp4 - iClamp3);
        this.rectMaskP = layoutParamsMaskP;
        try {
            this.wm.addView(this.rectMask, layoutParamsMaskP);
            this.rectMaskAdded = true;
        } catch (Exception unused) {
        }
        bringPanelDotTop();
    }

    private void setRectOn(boolean z) {
        this.rectOn = z;
        this.prefs.edit().putBoolean("rectOn", z).apply();
        if (z) {
            this.rfL = clamp01(this.prefs.getFloat("rfL", this.rfL));
            this.rfR = clamp01(this.prefs.getFloat("rfR", this.rfR));
            this.rfT = clamp01(this.prefs.getFloat("rfT", this.rfT));
            this.rfB = clamp01(this.prefs.getFloat("rfB", this.rfB));
            if (this.imgVisible) {
                addRectMask();
            }
            toast("矩形屏蔽已开启（恢复上次区域）· 长按屏蔽块可拖动");
        } else {
            if (this.rectMaskAdded) {
                safeRemove(this.rectMask);
                this.rectMaskAdded = false;
            }
            toast("矩形屏蔽已关闭（区域已记忆，再开会恢复原样）");
        }
        updateMaskButtons();
    }

    private void bringPanelDotTop() {
        if (this.panelAdded) {
            safeRemove(this.panelScroll);
            try {
                this.wm.addView(this.panelScroll, this.panelP);
            } catch (Exception unused) {
            }
        }
        if (this.dotAdded) {
            safeRemove(this.dot);
            try {
                this.wm.addView(this.dot, this.dotP);
            } catch (Exception unused2) {
            }
        }
        if (this.editorAdded) {
            safeRemove(this.editorRoot);
            try {
                this.wm.addView(this.editorRoot, this.editorP);
            } catch (Exception unused3) {
            }
        }
    }

    private void updateMaskButtons() {
        TextView textView = this.btnEdge;
        if (textView != null) {
            textView.setText("边缘屏蔽 ".concat(this.edgeOn ? "开" : "关"));
            TextView textView2 = this.btnEdge;
            boolean z = this.edgeOn;
            textView2.setBackground(roundBg(z ? -11903039 : -13878960, 9, z ? -2130706433 : 822083583));
        }
        TextView textView3 = this.btnRect;
        if (textView3 != null) {
            textView3.setText("矩形屏蔽 ".concat(this.rectOn ? "开" : "关"));
            TextView textView4 = this.btnRect;
            boolean z2 = this.rectOn;
            textView4.setBackground(roundBg(z2 ? -11903039 : -13878960, 9, z2 ? -2130706433 : 822083583));
        }
    }

    private void openEditor(int i) {
        if (this.editorAdded) {
            closeEditor(false);
        }
        this.editingMask = i;
        boolean z = i == 1;
        MaskEditorView maskEditorView = new MaskEditorView();
        this.editorView = maskEditorView;
        maskEditorView.setRect(Math.round((z ? this.efL : this.rfL) * this.screenW), Math.round((z ? this.efT : this.rfT) * this.screenH), Math.round((z ? this.efR : this.rfR) * this.screenW), Math.round((z ? this.efB : this.rfB) * this.screenH));
        this.editorView.setEdgeMode(z);
        FrameLayout frameLayout = new FrameLayout(this);
        this.editorRoot = frameLayout;
        frameLayout.setBackgroundColor(218103808);
        this.editorRoot.addView(this.editorView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setBackground(roundBg(-232773060, 12, 1090519039));
        linearLayout.setPadding(dp(4.0f), dp(4.0f), dp(4.0f), dp(4.0f));
        TextView textViewMiniBtn = miniBtn("✓ 保存并开启");
        textViewMiniBtn.setTextColor(-4589878);
        textViewMiniBtn.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m56lambda$20$comqinpuoverlayOverlayService(view);
            }
        });
        TextView textViewMiniBtn2 = miniBtn("✕ 取消");
        textViewMiniBtn2.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m57lambda$21$comqinpuoverlayOverlayService(view);
            }
        });
        TextView textViewMiniBtn3 = miniBtn("关闭屏蔽");
        textViewMiniBtn3.setTextColor(-12846);
        textViewMiniBtn3.setOnClickListener(new View.OnClickListener() { // from class: com.qinpu.overlay.OverlayService$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                OverlayService.this.m58lambda$22$comqinpuoverlayOverlayService(view);
            }
        });
        linearLayout.addView(textViewMiniBtn);
        linearLayout.addView(textViewMiniBtn2);
        linearLayout.addView(textViewMiniBtn3);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-2, -2, 49);
        layoutParams.topMargin = dp(54.0f);
        this.editorRoot.addView(linearLayout, layoutParams);
        WindowManager.LayoutParams layoutParams2 = new WindowManager.LayoutParams(-1, -1, 2038, 296, -3);
        this.editorP = layoutParams2;
        layoutParams2.gravity = 8388659;
        this.editorP.x = 0;
        this.editorP.y = 0;
        try {
            this.wm.addView(this.editorRoot, this.editorP);
            this.editorAdded = true;
        } catch (Exception unused) {
            toast("编辑器打开失败");
        }
    }

    /* JADX INFO: renamed from: lambda$20$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m56lambda$20$comqinpuoverlayOverlayService(View view) {
        closeEditor(true);
    }

    /* JADX INFO: renamed from: lambda$21$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m57lambda$21$comqinpuoverlayOverlayService(View view) {
        closeEditor(false);
    }

    /* JADX INFO: renamed from: lambda$22$com-qinpu-overlay-OverlayService, reason: not valid java name */
    /* synthetic */ void m58lambda$22$comqinpuoverlayOverlayService(View view) {
        if (this.editingMask == 1) {
            setEdgeOn(false);
        } else {
            setRectOn(false);
        }
        closeEditor(false);
    }

    private void closeEditor(boolean z) {
        MaskEditorView maskEditorView;
        if (this.editorAdded) {
            safeRemove(this.editorRoot);
            this.editorAdded = false;
        }
        if (z && (maskEditorView = this.editorView) != null && this.editingMask != 0) {
            RectF rect = maskEditorView.getRect();
            if (this.editingMask == 1) {
                this.efL = clamp01(rect.left / Math.max(1, this.screenW));
                this.efT = clamp01(rect.top / Math.max(1, this.screenH));
                this.efR = clamp01(rect.right / Math.max(1, this.screenW));
                this.efB = clamp01(rect.bottom / Math.max(1, this.screenH));
                saveEdgeFrac();
                setEdgeOn(true);
            } else {
                this.rfL = clamp01(rect.left / Math.max(1, this.screenW));
                this.rfT = clamp01(rect.top / Math.max(1, this.screenH));
                this.rfR = clamp01(rect.right / Math.max(1, this.screenW));
                this.rfB = clamp01(rect.bottom / Math.max(1, this.screenH));
                saveRectFrac();
                setRectOn(true);
            }
        }
        this.editorView = null;
        this.editorRoot = null;
        this.editingMask = 0;
    }

    private class MaskEditorView extends View {
        private final Paint border;
        private boolean edgeMode;
        private int handle;
        private final Paint handleP;
        private float lastX;
        private float lastY;
        private final RectF rect;
        private final Paint scrim;
        private final Paint textP;

        MaskEditorView() {
            super(OverlayService.this);
            this.rect = new RectF();
            this.edgeMode = true;
            this.handle = 0;
            Paint paint = new Paint(1);
            this.scrim = paint;
            Paint paint2 = new Paint(1);
            this.border = paint2;
            Paint paint3 = new Paint(1);
            this.handleP = paint3;
            Paint paint4 = new Paint(1);
            this.textP = paint4;
            paint.setColor(1711750028);
            paint2.setColor(-10177034);
            paint2.setStyle(Paint.Style.STROKE);
            paint2.setStrokeWidth(OverlayService.this.dp(2.0f));
            paint3.setColor(-4464901);
            paint4.setColor(-855638017);
            paint4.setTextSize(OverlayService.this.dp(12.0f));
        }

        void setRect(int i, int i2, int i3, int i4) {
            this.rect.set(i, i2, i3, i4);
            invalidate();
        }

        void setEdgeMode(boolean z) {
            this.edgeMode = z;
            invalidate();
        }

        RectF getRect() {
            return this.rect;
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            if (width <= 0.0f || height <= 0.0f || this.rect.isEmpty()) {
                return;
            }
            if (this.edgeMode) {
                canvas.drawRect(0.0f, 0.0f, width, this.rect.top, this.scrim);
                canvas.drawRect(0.0f, this.rect.bottom, width, height, this.scrim);
                canvas.drawRect(0.0f, this.rect.top, this.rect.left, this.rect.bottom, this.scrim);
                canvas.drawRect(this.rect.right, this.rect.top, width, this.rect.bottom, this.scrim);
            } else {
                canvas.drawRect(this.rect, this.scrim);
            }
            canvas.drawRect(this.rect, this.border);
            float fDp = OverlayService.this.dp(64.0f);
            float fDp2 = OverlayService.this.dp(5.0f);
            float fCenterX = this.rect.centerX();
            float fCenterY = this.rect.centerY();
            float f = fDp / 2.0f;
            float f2 = fCenterX - f;
            float f3 = fCenterX + f;
            canvas.drawRoundRect(f2, this.rect.top - fDp2, f3, this.rect.top + fDp2, fDp2, fDp2, this.handleP);
            canvas.drawRoundRect(f2, this.rect.bottom - fDp2, f3, this.rect.bottom + fDp2, fDp2, fDp2, this.handleP);
            float f4 = fCenterY - f;
            float f5 = fCenterY + f;
            canvas.drawRoundRect(this.rect.left - fDp2, f4, this.rect.left + fDp2, f5, fDp2, fDp2, this.handleP);
            canvas.drawRoundRect(this.rect.right - fDp2, f4, this.rect.right + fDp2, f5, fDp2, fDp2, this.handleP);
            String str = this.edgeMode ? "蓝色区域将被屏蔽（矩形外侧）· 拖边线调大小，拖中间移动" : "蓝色区域将被屏蔽（矩形内部）· 拖边线调大小，拖中间移动";
            canvas.drawText(str, (width - this.textP.measureText(str)) / 2.0f, height - OverlayService.this.dp(20.0f), this.textP);
        }

        private int hit(float f, float f2) {
            float fDp = OverlayService.this.dp(22.0f);
            if (Math.abs(f - this.rect.left) < fDp && f2 > this.rect.top - fDp && f2 < this.rect.bottom + fDp) {
                return 3;
            }
            if (Math.abs(f - this.rect.right) < fDp && f2 > this.rect.top - fDp && f2 < this.rect.bottom + fDp) {
                return 4;
            }
            if (Math.abs(f2 - this.rect.top) < fDp && f > this.rect.left - fDp && f < this.rect.right + fDp) {
                return 1;
            }
            if (Math.abs(f2 - this.rect.bottom) >= fDp || f <= this.rect.left - fDp || f >= this.rect.right + fDp) {
                return this.rect.contains(f, f2) ? 5 : 0;
            }
            return 2;
        }

        @Override // android.view.View
        public boolean onTouchEvent(MotionEvent motionEvent) {
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                this.handle = hit(motionEvent.getX(), motionEvent.getY());
                this.lastX = motionEvent.getX();
                this.lastY = motionEvent.getY();
                return true;
            }
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    float x = motionEvent.getX() - this.lastX;
                    float y = motionEvent.getY() - this.lastY;
                    this.lastX = motionEvent.getX();
                    this.lastY = motionEvent.getY();
                    float fDp = OverlayService.this.dp(48.0f);
                    float width = getWidth();
                    float height = getHeight();
                    int i = this.handle;
                    if (i == 1) {
                        RectF rectF = this.rect;
                        rectF.top = Math.max(0.0f, Math.min(rectF.top + y, this.rect.bottom - fDp));
                    } else if (i == 2) {
                        RectF rectF2 = this.rect;
                        rectF2.bottom = Math.max(rectF2.top + fDp, Math.min(this.rect.bottom + y, height));
                    } else if (i == 3) {
                        RectF rectF3 = this.rect;
                        rectF3.left = Math.max(0.0f, Math.min(rectF3.left + x, this.rect.right - fDp));
                    } else if (i == 4) {
                        RectF rectF4 = this.rect;
                        rectF4.right = Math.max(rectF4.left + fDp, Math.min(this.rect.right + x, width));
                    } else if (i == 5) {
                        this.rect.offset(x, y);
                        if (this.rect.left < 0.0f) {
                            RectF rectF5 = this.rect;
                            rectF5.offset(-rectF5.left, 0.0f);
                        }
                        if (this.rect.top < 0.0f) {
                            RectF rectF6 = this.rect;
                            rectF6.offset(0.0f, -rectF6.top);
                        }
                        if (this.rect.right > width) {
                            RectF rectF7 = this.rect;
                            rectF7.offset(width - rectF7.right, 0.0f);
                        }
                        if (this.rect.bottom > height) {
                            RectF rectF8 = this.rect;
                            rectF8.offset(0.0f, height - rectF8.bottom);
                        }
                    }
                    invalidate();
                    return true;
                }
                if (actionMasked != 3) {
                    return true;
                }
            }
            this.handle = 0;
            return true;
        }
    }

    private Bitmap decode(Uri uri) {
        try {
            InputStream inputStreamOpenInputStream = getContentResolver().openInputStream(uri);
            if (inputStreamOpenInputStream == null) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            int i = 1;
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStreamOpenInputStream, null, options);
            try {
                inputStreamOpenInputStream.close();
            } catch (Exception unused) {
            }
            while (true) {
                if (options.outWidth / i <= 4096 && options.outHeight / i <= 4096) {
                    break;
                }
                i *= 2;
            }
            InputStream inputStreamOpenInputStream2 = getContentResolver().openInputStream(uri);
            if (inputStreamOpenInputStream2 == null) {
                return null;
            }
            BitmapFactory.Options options2 = new BitmapFactory.Options();
            options2.inSampleSize = i;
            Bitmap bitmapDecodeStream = BitmapFactory.decodeStream(inputStreamOpenInputStream2, null, options2);
            try {
                inputStreamOpenInputStream2.close();
            } catch (Exception unused2) {
            }
            return bitmapDecodeStream;
        } catch (Exception unused3) {
            return null;
        }
    }

    private void startNotif() {
        NotificationManager notificationManager = (NotificationManager) getSystemService("notification");
        NotificationChannel notificationChannel = new NotificationChannel("qinpu_overlay", "琴谱浮窗服务", 2);
        notificationChannel.setShowBadge(false);
        notificationManager.createNotificationChannel(notificationChannel);
        Notification notificationBuild = new Notification.Builder(this, "qinpu_overlay").setSmallIcon(R.drawable.ic_stat).setContentTitle("琴谱浮窗").setContentText("悬浮显示中 · 点此打开应用").setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, (Class<?>) MainActivity.class), 67108864)).setOngoing(true).addAction(new Notification.Action.Builder((Icon) null, "显示/隐藏面板", PendingIntent.getService(this, 2, new Intent(this, (Class<?>) OverlayService.class).setAction(ACTION_TOGGLE), 67108864)).build()).addAction(new Notification.Action.Builder((Icon) null, "退出", PendingIntent.getService(this, 3, new Intent(this, (Class<?>) OverlayService.class).setAction(ACTION_STOP), 67108864)).build()).build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notificationBuild, 1073741824);
        } else {
            startForeground(1, notificationBuild);
        }
    }

    @Override // android.app.Service, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        closeSetPicker();
        readScreenSize();
        if (this.imgAdded) {
            if (this.gridMode) {
                showPage(this.page);
            } else {
                applyImageRect();
            }
            this.mainHandler.removeCallbacks(this.rotationSettleTask);
            this.mainHandler.postDelayed(this.rotationSettleTask, 350L);
            this.mainHandler.postDelayed(this.rotationSettleTask, 1000L);
        }
        if (this.panelAdded) {
            WindowManager.LayoutParams layoutParams = this.panelP;
            layoutParams.x = Math.max(0, Math.min(layoutParams.x, this.screenW - dp(80.0f)));
            if (this.screenW > this.screenH) {
                WindowManager.LayoutParams layoutParams2 = this.panelP;
                layoutParams2.y = Math.min(layoutParams2.y, dp(12.0f));
            }
            WindowManager.LayoutParams layoutParams3 = this.panelP;
            layoutParams3.y = Math.max(0, Math.min(layoutParams3.y, this.screenH - dp(80.0f)));
            fitPanelHeight();
            try {
                this.wm.updateViewLayout(this.panelScroll, this.panelP);
            } catch (Exception unused) {
            }
        }
        if (this.edgeOn && this.imgVisible) {
            applyEdgeStrips();
        }
        if (this.rectOn && this.imgVisible) {
            addRectMask();
        }
    }

    @Override // android.app.Service
    public void onDestroy() {
        saveState();
        removeAll();
        ExecutorService executorService = this.adjustExec;
        if (executorService != null) {
            executorService.shutdownNow();
            this.adjustExec = null;
        }
        super.onDestroy();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int dp(float f) {
        return Math.round(f * getResources().getDisplayMetrics().density);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toast(String str) {
        Toast.makeText(this, str, 0).show();
    }

    private GradientDrawable roundBg(int i, int i2, int i3) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(dp(i2));
        if (i3 != 0) {
            gradientDrawable.setStroke(dp(1.0f), i3);
        }
        return gradientDrawable;
    }

    private LinearLayout row() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setLayoutParams(rowParams(10));
        return linearLayout;
    }

    private LinearLayout.LayoutParams rowParams(int i) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(i);
        return layoutParams;
    }

    private LinearLayout.LayoutParams hp(float f) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, f);
        layoutParams.leftMargin = dp(3.0f);
        layoutParams.rightMargin = dp(3.0f);
        return layoutParams;
    }

    private TextView label(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-4602666);
        textView.setTextSize(13.0f);
        textView.setGravity(16);
        return textView;
    }

    private TextView btn(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setGravity(17);
        textView.setTextColor(-1379585);
        textView.setTextSize(13.0f);
        textView.setBackground(roundBg(-13878960, 9, 822083583));
        textView.setPadding(dp(6.0f), dp(9.0f), dp(6.0f), dp(9.0f));
        return textView;
    }

    private TextView miniBtn(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(-4602666);
        textView.setTextSize(14.0f);
        textView.setGravity(17);
        textView.setBackground(roundBg(-13878960, 8, 0));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.leftMargin = dp(6.0f);
        textView.setPadding(dp(10.0f), dp(4.0f), dp(10.0f), dp(5.0f));
        textView.setLayoutParams(layoutParams);
        return textView;
    }
}
