package com.android.server.wm;

import java.io.IOException;

import android.graphics.Color;
import android.os.PowerManager;
import android.os.RemoteException;
import java.io.InputStream;
import android.content.Intent;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.IWindow;
import android.view.Surface;
import android.view.View;
import android.view.WindowManagerPolicy;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.WindowManager;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.view.ViewRootImpl;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.util.DisplayMetrics;
import android.util.Slog;
import com.android.server.UiThread;
import com.android.server.wallpaper.WallpaperManagerService;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public class ThumbModeHelper implements WindowManagerPolicy.ThumbModeFuncs{

    public static final boolean DEBUG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    public static final String TAG = "ONE-HANDED";
    private static ThumbModeHelper sThumbModeHelper;
    private WindowManagerService mService;
    private boolean mNeedSetToThumbModeAnim = false;
    private boolean mNeedSetToNormalModeAnim = false;
    private boolean mNeedAdjustFrameToThumbMode = false;
    private boolean mNeedAdjustFrameNormalMode = false;
    private int mThumbAction = WindowManagerPolicy.ThumbModeFuncs.ACTION_NONE;
    private boolean mSysDoneToThumbMode = false;
    private boolean mSysDoneToReturnNormalModeS = true;
    private boolean mAnimNeed = false;
    private static boolean mEnableSidebar = false;
    private float mInterceptTouchEventSize = 100;
    private static final int INTERVAL_POLLING_DO_THUMB_ANIM = 50;
    private Context mContext;
    private WallpaperManagerService mWallpaperManager;
    private boolean mCurrentRotationHori;
    private BitmapDrawable mThumbWallpaperBmpDraw;
    private int mCacheThumbWallBDResId;
    private int mCachedThumbWallTransMode;
    private int mThumbWallpaperResId;

    private ImageView mThumbModeWallpaper;
    public static int WIDTH_SCREEN = 0;
    public static int HEIGHT_SCREEN = 0;
    public static int SCALE_HORI_THUMB_OFFSET_S = 0;
    public static int SCALE_VERT_THUMB_OFFSET_S = 0;
    public static final int HEIGHT_THUMB_SHADOW = 46;
    public static int HEIGHT_THUMB_OFFSET = 798;
    public static int OFFSET_THUMB_WALLPAPER = 112;
    public static int HEIGHT_THUMB_WALLPAPER_SCALE_MODE = 0;
    public static final String THUMB_WALLPAPER_WINDOW_TITLE = "com.smartisanos.ThumbWallpaper";
    public static final String SCREEN_SHOT_WINDOW_TITLE = "com.android.systemui.screenshot.screenshotAnimation";
    public static final String SHOT_SCREEN_SHOT_WINDOW_TITLE = "com.android.systemui.screenshot.screenshotAnimation_quick";
    public static final String INCALL_UI_WINDOW_TITLE = "com.android.incallui/com.android.incallui.InCallScreen";
    public static final String SMARTISAN_POWER_MENU_TITLE = "com.android.internal.policy.impl.SmaritsanPowerMenu";
    public static final String REMOTE_HELPER_WINDOW_TITLE = "com.smartisanos.handinhand/com.smartisanos.handinhand.activity.ProviderActivity";
    public static final String REMOTE_HELPER_CLIENT_WINDOW_TITLE = "com.smartisanos.handinhand";
    public static final String REMOTE_HELPER_CONTROLED_CLIENT_WINDOW_TITLE = "com.smartisanos.handinhand.ControlledFloattingView";
    public static final String SCROLL_SCREEN_SHOT__WINDOW_TITLE = "scroll_screenshot_edit";
    public static final String POWER_SAVE_LAUNCHER_TITLE = "com.smartisanos.powersaving.launcher/com.smartisanos.powersaving.launcher.Launcher";
    public static final String SM_LAUNCHER_WINDOW_TITLE = "com.smartisanos.launcher/com.smartisanos.launcher.Launcher";

    public static final int DURATION_THUMB_ANIM_MOVE_S = 200;
    public static final int DURATION_THUMB_ANIM_MOVE_S_RST = 200;
    public static final int OFFSET_THUMB_SHADOW = 46;
    private int mThumbStates;

    public static int THUMB_WINDOW_STATE_NORMAL = 0;
    public static int THUMB_WINDOW_STATE_LEFTBOTTOM_SIDEBAR = 2;
    public static int THUMB_WINDOW_STATE_RIGHTBOTTOM_SIDEBAR = 3;

    int mActionState = PERFORM_THUMB_ACTION_IDLE;
    public static int PERFORM_THUMB_ACTION_IDLE = 0;
    public static int PERFORM_THUMB_ACTION_PENDING_DOWN = 1;
    public static int PERFORM_THUMB_ACTION_FINISHED_DOWN = 2;
    public static int PERFORM_THUMB_ACTION_PENDING_RESET = 3;
    private boolean mResetTimeout = false;
    private OnThumbEventsCallbacks mThumbEventsListener;

    private ContentObserver mThumbSwitcherObserverSidebar = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateEnableModeConfig(mContext);
        }
    };

    public static ThumbModeHelper getInstance(){
        return sThumbModeHelper;
    }

    public static void init(Context context, WindowManagerService mService){
        if (sThumbModeHelper == null) {
            sThumbModeHelper = new ThumbModeHelper(context, mService);
        }
    }

    private ThumbModeHelper(Context context, WindowManagerService mService){
        this.mContext = context;
        this.mService = mService;
        mWallpaperManager = (WallpaperManagerService)ServiceManager.getService("wallpaper");

        context.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("side_bar_mode"), true,
                mThumbSwitcherObserverSidebar);

        updateEnableModeConfig(context);
    }

    private void updateEnableModeConfig(Context context){
        int thumbModeEnableSidebar = Settings.Global.getInt(
                context.getContentResolver(), "side_bar_mode", 1);

        boolean enableS = thumbModeEnableSidebar == 1;

        if(enableS != mEnableSidebar){
            mEnableSidebar = enableS;
            mService.updateThumbGestureDetectListener(mEnableSidebar );
            updateThumbModeEnableState();
        }
    }

    private void clearThumbWallpaperRes(){
        UiThread.getHandler().post(new Runnable() {

            @Override
            public void run() {
                if(mThumbModeWallpaper != null){
                    mThumbModeWallpaper.setBackground(null);
                    mThumbModeWallpaper = null;
                    mCachedThumbWallTransMode = THUMB_WALL_NORMAL;
                }
            }
        });
    }

    public boolean needAnimatedMove(){
        return mAnimNeed;
    }

    public boolean needSetToThumbAnim() {
        return mNeedSetToThumbModeAnim;
    }

    public boolean needSetToNormalAnim(){
        return mNeedSetToNormalModeAnim;
    }

    public boolean needAdjustFrameToThumb(){
        return mNeedAdjustFrameToThumbMode;
    }

    public boolean needAdjustFrameToNoraml() {
        return mNeedAdjustFrameNormalMode;
    }

    public synchronized void requestTraversalToThumbMode(final int action){
        if(UiThread.getHandler().hasCallbacks(doResetWindowStateRunnable)){
            if(DEBUG){
                Slog.d(TAG, "Ignore request do ACTIONS " + action + " Cause has pending reset run");
            }
            return;
        }
        boolean notBusyAnimating = mService.mAppTransition
                .isAppTransitionTypeNone() == true
                && mService.mAppTransition.isRunning() == false
                && mService.mAppTransition.isReady() == false
                && mService.mClosingApps.size() == 0
                && mService.mOpeningApps.size() == 0;
        if(DEBUG) {
            Slog.d(TAG, "notBusyAnimating = " + notBusyAnimating + ", action = " + action + ", mThumbAction = " + mThumbAction + ", mThumbStates  = " + Integer.toHexString(mThumbStates));
        }

        if (isSystemInCanMoveState(action)) {
            if (notBusyAnimating && isThumbModeEnable(action)
                    && action != WindowManagerPolicy.ThumbModeFuncs.ACTION_RESET
                    && ( mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) == 0) {
                showThumbWallpaper(action);
                if(isAppTransitionRunning()){
                    removeThumbModeWallpaperWindow();
                    return;
                }
                UiThread.getHandler().removeCallbacks(doResetWindowStateRunnable);
                mNeedSetToThumbModeAnim = true;
                mNeedSetToNormalModeAnim = false;
                mNeedAdjustFrameToThumbMode = true;
                mNeedAdjustFrameNormalMode = false;
                mThumbAction = action;
                mActionState = PERFORM_THUMB_ACTION_PENDING_DOWN;
                mAnimNeed = true;
                if (action == WindowManagerPolicy.ThumbModeFuncs.ACTION_FROM_TOP_LEFT_PULL_DOWN_SIDEBAR) {
                    mThumbStates |= ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR;
                    mThumbStates &= ~ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR;
                    mThumbStates |= ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR;
                } else if (action == WindowManagerPolicy.ThumbModeFuncs.ACTION_FROM_TOP_RIGHT_PULL_DOWN_SIDEBAR) {
                    mThumbStates |= ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR;
                    mThumbStates |= ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR;
                    mThumbStates &= ~ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR;
                }
                dispatchThumbStates();
                mService.requestTraversal();
                postAnimToThumbMode();
            } else if (action == WindowManagerPolicy.ThumbModeFuncs.ACTION_RESET
                    && (mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0) {
                if(DEBUG){
                    Slog.d(TAG, "Request do ACTION_RESET");
                }
                showThumbWallpaper(action);
                resetWindowState();
            }
        }
    }

    private boolean isAppTransitionRunning(){
        return mService.mAppTransition.isRunning();
    }

    public void updatePerformThumbState(int lastState){
        if(lastState == PERFORM_THUMB_ACTION_PENDING_DOWN){
            mActionState = PERFORM_THUMB_ACTION_FINISHED_DOWN;
        }else if (lastState == PERFORM_THUMB_ACTION_PENDING_RESET){
            mActionState = PERFORM_THUMB_ACTION_IDLE;
        }
    }

    public int getPendingThumbAction(){
        return mThumbAction;
    }

    private boolean isSystemInCanMoveState(int action){
        if(mService == null || !mService.mSystemReady){
            return false;
        }
        if(action == ACTION_RESET){
            return true;
        }
        return !isDisableThumbModeWinShowing();
    }

    private Runnable doResetWindowStateRunnable = new Runnable(){
        @Override
        public void run() {
            resetWindowState();
        }
    };

    public boolean isSysInSidebarMode(){
        return (mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0;
    }

    // include one-handed mode and sidebar mode
    public boolean isSysInThumbMode(){
        return (mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0;
    }

    private Runnable resetTimeoutRun = new Runnable(){

        @Override
        public void run() {
            mResetTimeout = true;
        }
    };

    public void resetWindowState(){

        if((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0){
            mSysDoneToReturnNormalModeS = false;
        }
        if(!UiThread.getHandler().hasCallbacks(resetTimeoutRun)){
            UiThread.getHandler().postDelayed(resetTimeoutRun, 400);
        }
        if(!mService.isAnimating() || mResetTimeout){
            if(DEBUG){
                if(mResetTimeout){
                    Slog.d(TAG, "Request do ACTION_RESET, time out, go!");
                }else{
                    Slog.d(TAG, "Request do ACTION_RESET, good to go");
                }
            }
            UiThread.getHandler().removeCallbacks(resetTimeoutRun);
            mResetTimeout = false;
            UiThread.getHandler().post(new Runnable(){
                @Override
                public void run() {
                    if((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0){

                        mSysDoneToReturnNormalModeS = false;
                        mNeedSetToThumbModeAnim = false;
                        mNeedSetToNormalModeAnim = true;
                        mNeedAdjustFrameToThumbMode = false;
                        mNeedAdjustFrameNormalMode = true;
                        mAnimNeed = true;
                        mSysDoneToThumbMode = false;
                        mThumbAction = WindowManagerPolicy.ThumbModeFuncs.ACTION_RESET;
                        mActionState = PERFORM_THUMB_ACTION_PENDING_RESET;
                        mThumbStates &= ~ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR;
                        mThumbStates &= ~ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR;
                        mThumbStates &= ~ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR;
                        postAnimToNormalMode();
                        dispatchThumbStates();
                        if(!mService.isAnimating()){
                            mService.requestTraversal();
                        }
                    }
                }
            });
        }else{
            if(DEBUG){
                Slog.d(TAG, "Request do ACTION_RESET, animating! pending it");
            }
            UiThread.getHandler().removeCallbacks(doResetWindowStateRunnable);
            UiThread.getHandler().postDelayed(doResetWindowStateRunnable, INTERVAL_POLLING_DO_THUMB_ANIM);
        }
    }

    public void postAnimToNormalMode(){
        UiThread.getHandler().removeCallbacks(postAnimToNormalModeRun);
        UiThread.getHandler().postDelayed(postAnimToNormalModeRun, 500);
        removeThumbModeWallpaperWindow();
    }

    public void postAnimToThumbMode(){
        mSysDoneToThumbMode = false;
        UiThread.getHandler().removeCallbacks(postAnimToThumbModeRun);
        UiThread.getHandler().postDelayed(postAnimToThumbModeRun, 500);
    }

    private Runnable postAnimToThumbModeRun = new Runnable(){

        @Override
        public void run() {
            mSysDoneToThumbMode = true;
        }
    };

    private Runnable postAnimToNormalModeRun = new Runnable(){

        @Override
        public void run() {
            mSysDoneToReturnNormalModeS = true;
            UiThread.getHandler().post(new Runnable(){
                @Override
                public void run() {
                    mService.requestTraversal();
                }
            });
        }
    };

    private Runnable showThumbWallpaperShadow = new Runnable(){

        @Override
        public void run() {
            if (mThumbModeWallpaper != null) {
                mThumbModeWallpaper
                        .setScaleType(ImageView.ScaleType.FIT_XY);
                mThumbModeWallpaper.setPadding(0, HEIGHT_THUMB_OFFSET
                        - HEIGHT_THUMB_SHADOW, 0, OFFSET_THUMB_WALLPAPER);
                mThumbModeWallpaper.setImageResource(com.android.internal.R.drawable.shadow_thumb_mode);
            }
        }
    };

    private Runnable hideThumbWallpaper = new Runnable(){

        @Override
        public void run() {
            if(mThumbModeWallpaper == null){
                return;
            }
            mThumbModeWallpaper.setVisibility(View.GONE);
            mThumbModeWallpaper.setImageBitmap(null);
        }
    };

    public boolean isSysDoneToReturnNormalModeS(){
        return mSysDoneToReturnNormalModeS;
    }

    public void disableAnimatedMove() {
        mAnimNeed = false;
        mNeedSetToThumbModeAnim = false;
    }

    public boolean isThumbModeEnable(){
        return  (mThumbStates & ViewRootImpl.BIT_THUMB_MODE_ENABLE_SIDEBAR) != 0;
    }

    public boolean isThumbModeEnable(int action){
        if(action == WindowManagerPolicy.ThumbModeFuncs.ACTION_RESET){
            return true;
        }else if(action == WindowManagerPolicy.ThumbModeFuncs.ACTION_FROM_TOP_LEFT_PULL_DOWN_SIDEBAR
                || action == WindowManagerPolicy.ThumbModeFuncs.ACTION_FROM_TOP_RIGHT_PULL_DOWN_SIDEBAR){
            return (mThumbStates & ViewRootImpl.BIT_THUMB_MODE_ENABLE_SIDEBAR) != 0;
        }
        return false;
    }

    private void updateThumbModeEnableState(){
        int lastThumbStates = mThumbStates;
        if(mCurrentRotationHori){
            mThumbStates &= ~ViewRootImpl.BIT_THUMB_MODE_ENABLE_SIDEBAR;
        }else{
            if(mEnableSidebar){
                mThumbStates |= ViewRootImpl.BIT_THUMB_MODE_ENABLE_SIDEBAR;
            }else{
                mThumbStates &= ~ViewRootImpl.BIT_THUMB_MODE_ENABLE_SIDEBAR;
                if((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0){
                    resetWindowState();
                }
            }
        }
        if(lastThumbStates != mThumbStates){
            dispatchThumbStates();
        }
    }

    public float getInterceptTouchSize() {
        return mInterceptTouchEventSize;
    }

    public void setRotation(int rot){
        mCurrentRotationHori = (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270);
        updateThumbModeEnableState();
    }


    private static final int sidebarWallResid = com.android.internal.R.drawable.sidebar_background;

    private BitmapDrawable getThumbWallpaper(Context context, int resId, boolean tileMode, int transMode){
        boolean matchCache = mCachedThumbWallTransMode == transMode && mCacheThumbWallBDResId == resId && mThumbWallpaperBmpDraw != null;
        if(matchCache){
            return mThumbWallpaperBmpDraw;
        }
        Bitmap thumbBmp = null;
        InputStream is = context.getResources().openRawResource(resId);
        if (is != null) {
            int width = WIDTH_SCREEN;
            int height = HEIGHT_THUMB_WALLPAPER_SCALE_MODE;

            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                Bitmap bm = BitmapFactory.decodeStream(is, null, options);
                thumbBmp = generateTileModeBitmap(context, bm, width, height, tileMode, transMode);
            } catch (OutOfMemoryError e) {
                Slog.w(TAG, "Can't decode stream", e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        if(thumbBmp != null){
            mCachedThumbWallTransMode = transMode;
            mCacheThumbWallBDResId = resId;
            mThumbWallpaperBmpDraw = new BitmapDrawable(thumbBmp);
        }
        return mThumbWallpaperBmpDraw;
    }

    Bitmap generateTileModeBitmap(Context context, Bitmap bm, int width, int height, boolean tileModeRepeat, int transMode) {
        if (bm == null) {
            return null;
        }

        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        bm.setDensity(metrics.noncompatDensityDpi);

        /*if (width <= 0 || height <= 0
                || (bm.getWidth() == width && bm.getHeight() == height)) {
            return bm;
        }*/

        // This is the final bitmap we want to return.
        try {
            Bitmap newbm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            newbm.setDensity(metrics.noncompatDensityDpi);

            Canvas c = new Canvas(newbm);
            Rect targetRect = new Rect();
            targetRect.right = bm.getWidth();
            targetRect.bottom = bm.getHeight();

            int deltaw = width - targetRect.right;
            int deltah = height - targetRect.bottom;

            if (deltaw > 0 || deltah > 0) {
                // We need to scale up so it covers the entire area.
                float scale;
                if (deltaw > deltah) {
                    scale = width / (float)targetRect.right;
                } else {
                    scale = height / (float)targetRect.bottom;
                }
                targetRect.right = (int)(targetRect.right*scale);
                targetRect.bottom = (int)(targetRect.bottom*scale);
                deltaw = width - targetRect.right;
                deltah = height - targetRect.bottom;
            }
            targetRect.offset(deltaw/2, deltah/2);



            Paint paint = new Paint();
            paint.setFilterBitmap(true);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));

            if(tileModeRepeat){
                BitmapShader bs = new BitmapShader(bm, TileMode.REPEAT, TileMode.REPEAT);
                paint.setShader(bs);
                Matrix m = new Matrix();
                m.postTranslate(targetRect.left, targetRect.top);
                paint.getShader().setLocalMatrix(m);
                c.drawRect(targetRect,paint);
            }else{
                c.drawBitmap(bm, 0, 0, paint);
            }

            if(transMode != THUMB_WALL_NORMAL){
                Rect rect = getThumbWallTranRect(transMode);
                Paint paintTrans = new Paint(Paint.ANTI_ALIAS_FLAG);
                paintTrans.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
                paintTrans.setColor(Color.TRANSPARENT);
                paintTrans.setAlpha(255);
                c.drawRect(rect, paintTrans);
            }

            bm.recycle();
            c.setBitmap(null);
            return newbm;
        } catch (OutOfMemoryError e) {
            Slog.w(TAG, "Can't generate default bitmap", e);
            return bm;
        }
    }

    private Rect getThumbWallTranRect(int transMode){
        int l = 0;
        int t = 0;
        int r = 0;
        int b = 0;
        switch(transMode){
            case THUMB_WALL_L_TRANS_S:
                 l = 0;
                 t = SCALE_VERT_THUMB_OFFSET_S;
                 r = WIDTH_SCREEN-SCALE_HORI_THUMB_OFFSET_S;
                 b = HEIGHT_SCREEN;
                 break;
            case THUMB_WALL_R_TRANS_S:
                 l = SCALE_HORI_THUMB_OFFSET_S;
                 t = SCALE_VERT_THUMB_OFFSET_S;
                 r = WIDTH_SCREEN;
                 b = HEIGHT_SCREEN;
                 break;
            default:
                 break;
        }
        return new Rect(l, t, r, b);
    }

    public void clearThumbWallpaper(){
        UiThread.getHandler().post(new Runnable() {

            @Override
            public void run() {
                if(mThumbModeWallpaper != null){
                    mThumbModeWallpaper.setBackground(null);
                    mThumbModeWallpaper.setImageBitmap(null);
                    WindowManager wm = (WindowManager) mContext
                            .getSystemService(Context.WINDOW_SERVICE);
                    try{
                        wm.removeView(mThumbModeWallpaper);
                    }catch(Exception e){
                        // thumb wallpaper already removed
                    }
                    mThumbModeWallpaper = null;
                }
            }
        });
    }

    private static final int THUMB_WALL_NORMAL = 0;
    private static final int THUMB_WALL_L_TRANS_S = 1;
    private static final int THUMB_WALL_R_TRANS_S = 2;


    private void showThumbWallpaper(final int action) {
        if(!isThumbModeEnable(action) ||
                ( (mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR) != 0) ||
                    !isSystemInCanMoveState(action)){
            return;
        }
        int transMode = THUMB_WALL_NORMAL;
        if(action == ACTION_FROM_TOP_LEFT_PULL_DOWN_SIDEBAR){
            transMode = THUMB_WALL_L_TRANS_S;
        }else if(action == ACTION_FROM_TOP_RIGHT_PULL_DOWN_SIDEBAR){
            transMode = THUMB_WALL_R_TRANS_S;
        }
        final int transModeFinal = transMode;

        UiThread.getHandler().removeCallbacks(hideThumbWallpaper);
        UiThread.getHandler().postAtFrontOfQueue(new Runnable() {

            @Override
            public void run() {
                if(mThumbModeWallpaper == null){
                    WindowManager wm = (WindowManager) mContext
                            .getSystemService(Context.WINDOW_SERVICE);

                    mThumbModeWallpaper = new ImageView(mContext);

                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
                    lp.gravity = Gravity.LEFT | Gravity.TOP;
                    lp.width = WIDTH_SCREEN;
                    lp.height = HEIGHT_THUMB_WALLPAPER_SCALE_MODE;
                    lp.format = PixelFormat.RGBA_8888;
                    lp.setTitle(THUMB_WALLPAPER_WINDOW_TITLE);
                    lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
                    lp.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    lp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_EXT_FORCE_FULL_SCREEN;
                    lp.token = getWindowOfType(WindowManager.LayoutParams.TYPE_WALLPAPER).mClient.asBinder();
                    lp.windowAnimations = com.android.internal.R.style.Animation_Thumbwallpaper;
                    wm.addView(mThumbModeWallpaper, lp);
                }

                BitmapDrawable wall = getThumbWallpaper(mContext, sidebarWallResid , true, transModeFinal);
                mThumbModeWallpaper.setBackground(wall);
                mThumbModeWallpaper.setVisibility(View.VISIBLE);
            }
        });
    }

    WindowState getWindowOfType(int type){
        WindowList windows = mService.getDefaultWindowListLocked();
        final int N = windows.size();
        int i = N;
        while (i > 0) {
            i--;
            WindowState w;
            try{
                w = windows.get(i);
            }catch(IndexOutOfBoundsException e){
                return null;
            }
            if (w != null && type == w.getAttrs().type) {
                return w;
            }
        }
        return null;
    }

    void removeThumbModeWallpaperWindow() {
        UiThread.getHandler().post(hideThumbWallpaper);
    }

    boolean isDisableThumbModeWinShowing(){
        WindowList windows = mService.getDefaultWindowListLocked();
        final int N = windows.size();
        int i = N;
        while (i > 0) {
            i--;
            WindowState w;
            try{
                w = windows.get(i);
            }catch(IndexOutOfBoundsException e){
                return false;
            }
            if (w != null &&
                    (SCREEN_SHOT_WINDOW_TITLE.equals(w.getAttrs().getTitle())
                            || SHOT_SCREEN_SHOT_WINDOW_TITLE.equals(w.getAttrs().getTitle())
                            || INCALL_UI_WINDOW_TITLE.equals(w.getAttrs().getTitle())
                            || SMARTISAN_POWER_MENU_TITLE.equals(w.getAttrs().getTitle())
                            || REMOTE_HELPER_CLIENT_WINDOW_TITLE.equals(w.getAttrs().getTitle())
                            || REMOTE_HELPER_CONTROLED_CLIENT_WINDOW_TITLE.equals(w.getAttrs().getTitle())
                            || SCROLL_SCREEN_SHOT__WINDOW_TITLE.equals(w.getAttrs().getTitle())
                            || POWER_SAVE_LAUNCHER_TITLE.equals(w.getAttrs().getTitle())
                            || w.getAttrs().type == WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL)) {
                if(w.isVisibleLw()){
                    return true;
                }
            }
        }
        return false;
    }

    void updateWindowStateWhenFocusChanged(WindowState old, WindowState cur){
        if(isSysInThumbMode()){
            if(cur != null && cur.mAttrs != null &&
                    (cur.mAttrs.type == WindowManager.LayoutParams.TYPE_BOOT_PROGRESS)){
                postResetWindowState();
            }
        }
    }

    void postResetWindowState(){
        UiThread.getHandler().postDelayed(new Runnable() {

            @Override
            public void run() {
                if(isSysInThumbMode()){
                    resetWindowState();
                }
            }
        }, 500);
    }

    public static boolean isNormalPopupWindow(WindowState win){
        return win != null &&
                win.mAttrs.getTitle().toString().startsWith("PopupWindow") &&
                win.mAttrs.type == WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
    }

    void setThumbOffset(int scaleModeVertOffsetS, int scaleModeHoriOffsetS, int screenWidth, int screenHeight){
        SCALE_HORI_THUMB_OFFSET_S = scaleModeHoriOffsetS;
        SCALE_VERT_THUMB_OFFSET_S = scaleModeVertOffsetS;
        WIDTH_SCREEN = screenWidth;
        HEIGHT_SCREEN = screenHeight;
        HEIGHT_THUMB_WALLPAPER_SCALE_MODE = screenHeight;
    }

    int getThumbStates(){
        return mThumbStates;
    }

    int mLastThumbState;

    void dispatchThumbStates(){
        if(mLastThumbState != mThumbStates){
            if ((mLastThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR ) != 0 
                    && (mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR ) == 0) {
                if(mThumbEventsListener != null){
                    mThumbEventsListener.onExitSidebarMode();
                }
            } else if ((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR ) != 0 
                    && (mLastThumbState & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_SIDEBAR ) == 0) {
                if(mThumbEventsListener != null){
                    mThumbEventsListener.onEnterSidebarMode(mThumbStates);
                }
            }
            mLastThumbState = mThumbStates;
            mService.dispatchThumbStateToPolicy(mThumbStates);
        }
    }

    void dispatchThumbStatesOnlyWin(WindowState win) {
        if(win != null && win.isAlive()){
            if (win != null && win.mClient != null) {
                dispatchThumbStatesAsynced(win.mClient, mThumbStates);
            }
        }
    }

    private void dispatchThumbStatesAsynced(IWindow c, int s) {
        final IWindow client = c;
        final int state = s;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(client == null) return;
                try {
                    client.dispatchThumbModeStates(state);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Dispatch thumb states failed, ", e);
                }
            }
        }).start();
    }

    public static final int THUMB_ANIM_TO_LEFT_BOTTOM = 4;
    public static final int THUMB_ANIM_TO_RIGHT_BOTTOM = 5;
    public static final int THUMB_ANIM_TO_LEFT_BOTTOM_NOTFULL = 6;
    public static final int THUMB_ANIM_TO_RIGHT_BOTTOM_NOTFULL = 7;
    public static final int THUMB_ANIM_RESET_LEFT_BOTTOM_S = 9;
    public static final int THUMB_ANIM_RESET_RIGHT_BOTTOM_S = 10;
    public static final int THUMB_ANIM_RESET_LEFT_BOTTOM_NOTFULL_S= 11;
    public static final int THUMB_ANIM_RESET_RIGHT_BOTTOM_NOTFULL_S = 12;

    public Animation getThumbAnimation(int animType){
        Animation anim = null;
        float scale = 0;
        switch (animType){
            case THUMB_ANIM_TO_LEFT_BOTTOM:
                scale =mService.mScaleThumbScaleS;
                anim = new ScaleAnimation(
                        1 / scale,
                        1.0f,
                        1 / scale,
                        1.0f,
                        Animation.ABSOLUTE,
                        0,
                        Animation.ABSOLUTE,
                        HEIGHT_SCREEN * scale);
                anim.setDuration(ThumbModeHelper.DURATION_THUMB_ANIM_MOVE_S);
                return anim;
            case THUMB_ANIM_TO_RIGHT_BOTTOM:
                scale =mService.mScaleThumbScaleS;
                anim = new ScaleAnimation(
                        1 / scale,
                        1.0f,
                        1 / scale,
                        1.0f,
                        Animation.ABSOLUTE,
                        WIDTH_SCREEN * scale,
                        Animation.ABSOLUTE,
                        HEIGHT_SCREEN * scale);
                anim.setDuration(ThumbModeHelper.DURATION_THUMB_ANIM_MOVE_S);

                return anim;
            case THUMB_ANIM_TO_LEFT_BOTTOM_NOTFULL:
                scale = mService.mScaleThumbScaleS;
                anim = new ScaleAnimation(
                        1 / scale,
                        1.0f,
                        1 / scale,
                        1.0f,
                        Animation.RELATIVE_TO_SELF,
                        0.0f,
                        Animation.RELATIVE_TO_SELF,
                        1.0f);
                anim.setDuration(ThumbModeHelper.DURATION_THUMB_ANIM_MOVE_S);

                return anim;
            case THUMB_ANIM_TO_RIGHT_BOTTOM_NOTFULL:
                scale =  mService.mScaleThumbScaleS;
                anim = new ScaleAnimation(
                        1 / scale,
                        1.0f,
                        1 / scale,
                        1.0f,
                        Animation.RELATIVE_TO_SELF,
                        1.0f,
                        Animation.RELATIVE_TO_SELF,
                        1.0f);
                anim.setDuration(ThumbModeHelper.DURATION_THUMB_ANIM_MOVE_S);

                return anim;
            case THUMB_ANIM_RESET_LEFT_BOTTOM_S:
                scale = mService.mScaleThumbScaleS;
                anim = new ScaleAnimation(
                        scale,
                        1.0f,
                        scale,
                        1.0f,
                        Animation.ABSOLUTE,
                        0,
                        Animation.ABSOLUTE, HEIGHT_SCREEN);
                anim.setDuration(ThumbModeHelper.DURATION_THUMB_ANIM_MOVE_S_RST);
                return anim;
            case THUMB_ANIM_RESET_RIGHT_BOTTOM_S:
                scale = mService.mScaleThumbScaleS;
                anim = new ScaleAnimation(
                        scale,
                        1.0f,
                        scale,
                        1.0f,
                        Animation.ABSOLUTE,
                        WIDTH_SCREEN,
                        Animation.ABSOLUTE, HEIGHT_SCREEN);
                anim.setDuration(ThumbModeHelper.DURATION_THUMB_ANIM_MOVE_S_RST);
                return anim;
            case THUMB_ANIM_RESET_LEFT_BOTTOM_NOTFULL_S:
                scale = mService.mScaleThumbScaleS;
                anim = new ScaleAnimation(
                        scale,
                        1.0f,
                        scale,
                        1.0f,
                        Animation.RELATIVE_TO_SELF,
                        0,
                        Animation.RELATIVE_TO_SELF, 1);
                anim.setDuration(ThumbModeHelper.DURATION_THUMB_ANIM_MOVE_S_RST);
                return anim;
            case THUMB_ANIM_RESET_RIGHT_BOTTOM_NOTFULL_S:
                scale = mService.mScaleThumbScaleS;
                anim = new ScaleAnimation(
                        scale,
                        1.0f,
                        scale,
                        1.0f,
                        Animation.RELATIVE_TO_SELF,
                        1,
                        Animation.RELATIVE_TO_SELF, 1);
                anim.setDuration(ThumbModeHelper.DURATION_THUMB_ANIM_MOVE_S_RST);
                return anim;
            default: return null;
        }
    }

    public void setThumbEventsListener(OnThumbEventsCallbacks callback){
        mThumbEventsListener = callback;
    }

    public Rect getAppScreenRect(int dw, int dh, int statusBarHeight) {
        int left = 0;
        int top = statusBarHeight;
        int right = dw;
        int bottom = dh;
        if ((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR) != 0) {
            top = SCALE_VERT_THUMB_OFFSET_S + (int)(statusBarHeight * ((WIDTH_SCREEN-SCALE_HORI_THUMB_OFFSET_S)/(float)WIDTH_SCREEN));
            left = SCALE_HORI_THUMB_OFFSET_S;
        } else if ((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR) != 0){
            top = SCALE_VERT_THUMB_OFFSET_S + (int)(statusBarHeight * ((WIDTH_SCREEN-SCALE_HORI_THUMB_OFFSET_S)/(float)WIDTH_SCREEN));
            right = dw - SCALE_HORI_THUMB_OFFSET_S;
        }
        return new Rect(left, top, right, bottom);
    }

    public boolean getCropRect(Rect outRect) {
        if((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR) != 0){
            outRect.left = 0;
            outRect.top = SCALE_VERT_THUMB_OFFSET_S;
            outRect.right = WIDTH_SCREEN - SCALE_HORI_THUMB_OFFSET_S;
            outRect.bottom = HEIGHT_SCREEN;
        } else if((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR) != 0) {
            outRect.left = SCALE_HORI_THUMB_OFFSET_S;
            outRect.top = SCALE_VERT_THUMB_OFFSET_S;
            outRect.right = WIDTH_SCREEN;
            outRect.bottom = HEIGHT_SCREEN;
        } else {
            outRect.left = outRect.top = outRect.right = outRect.bottom = 0;
        }
        return true;
    }

    public void clampSurfaceRect(Rect clipRect, int l, int t, int w, int h, boolean isCamPreview, float hScale, float vScale) {
        if(isCamPreview == false){
            if(mSysDoneToThumbMode == false) return;
        }
        if((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_L_CORNER_SIDEBAR) != 0){
            int offset = (int)(l + w*hScale - WIDTH_SCREEN*mService.mScaleThumbScaleS);
            if(offset > 0){
                clipRect.right -= offset/hScale;
                if(clipRect.right - clipRect.left == 0){
                    clipRect.right += 1;
                }
            }
            if(t < mService.mScaleThumbYOffsetS){
                clipRect.top += (mService.mScaleThumbYOffsetS - t)/vScale;
                if(clipRect.top - clipRect.bottom == 0){
                    clipRect.top -= 1;
                }
            }
        }else if((mThumbStates & ViewRootImpl.BIT_WINDOW_IN_THUMB_MODE_TYPE_R_CORNER_SIDEBAR) != 0) {
            int offset = SCALE_HORI_THUMB_OFFSET_S - l;
            if (offset > 0) {
                clipRect.left += offset / hScale + 0.5f;
                if (clipRect.right - clipRect.left == 0) {
                    clipRect.left -= 1;
                }
            }
            if (t < mService.mScaleThumbYOffsetS) {
                clipRect.top += (mService.mScaleThumbYOffsetS - t) / vScale;
                if (clipRect.top - clipRect.bottom == 0) {
                    clipRect.top -= 1;
                }
            }
        }
    }

    public interface OnThumbEventsCallbacks {
        void onEnterSidebarMode(int state);
        void onExitSidebarMode();
    }

    // real scale used to scale window
    public float getSidebarScale(){
        float width = mService.mDisplayMetrics.widthPixels;
        int sidebarWidthPx = mContext.getResources().getDimensionPixelSize(
                                com.android.internal.R.dimen.sidebar_width);
        return 1 - ((sidebarWidthPx-0.5f)/width);
    }

    // scale only used to determine position offset
    public float getSidebarScaleSmall(){
        float width = mService.mDisplayMetrics.widthPixels;
        int sidebarWidthPx = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.sidebar_width);
        return 1 - (sidebarWidthPx/width);
    }

    public boolean isSidebarHasFocus(){
        return mService.mCurrentFocus != null &&
                (mService.mCurrentFocus.getAttrs().type == WindowManager.LayoutParams.TYPE_SIDEBAR_TOOLS
                    || (mService.mCurrentFocus.mAttachedWindow != null
                            && mService.mCurrentFocus.mAttachedWindow.getAttrs().type == WindowManager.LayoutParams.TYPE_SIDEBAR_TOOLS));
    }

}
