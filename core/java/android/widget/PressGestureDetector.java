package android.widget;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewRootImpl;
import android.view.WindowManager.LayoutParams;
import android.view.View;
import android.view.ViewConfiguration;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.regex.Matcher;

import android.view.onestep.OneStepDragUtils;

/**
 * @hide
 */
public class PressGestureDetector {

    private static final boolean DBG = (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final String TAG = "PressGestureDetector";
    private static final String WECHAT_PKG_NAME = "com.tencent.mm";
    private static final String TEXTBOOM_PKG_NAME = "com.smartisanos.textboom";
    private static final String INCALLUI_PKG_NAME = "com.android.incallui";
    private static final float TOUCH_MOVE_BOUND = 80;
    private static final boolean ENABLE_FEATURE_DRAG_IMAGE = true;
    private boolean mCanDragImage = false;

    private final Context mContext;
    private final FrameLayout mDecorView;
    private final int mTouchSlop;
    private final TextBoom mTextBoom;

    private boolean mFindViewRestricted;

    private float mLongPressDownX = 0;
    private float mLongPressDownY = 0;
    private float mLongPressDownRawX = 0;
    private float mLongPressDownRawY = 0;
    private int mTouchPointerID = -1;
    private float mTextBoomArea = 1f;
    private float mImageBoomArea = 1f;
    private boolean mDiscardNextActionUp;

    private View mTouchedTextView = null;
    private boolean mIsPhoneLongClickSwipe = false;
    private boolean mTextBoomEntered = false;
    private long mTouchDownTime;
    boolean mCanTextBoom = false;
    boolean mCanImageBoom = false;
    boolean mIsInOcrBacklist = false;
    boolean mObserverRegistered = false;
    private boolean mShouldCheckWechatUrl;

    private static String SHARE_FILE_TMP_DIR = null;
    public static final String BOOM_AUTHORITY = "content://com.smartisanos.textboom.call_method";
    public static final String METHOD_STOP_OCR = "stop_ocr";

    private Runnable mDragTextRunnable = new Runnable() {
        @Override
        public void run() {
            if (mTouchedTextView == null || mTouchedTextView.getVisibility() != View.VISIBLE) {
                return;
            }
            if (mTextBoom.preparing) {
                return;
            }
            final String text = mTouchedTextView.getFindText();
            if (!TextUtils.isEmpty(text)) {
                if (!text.equals(mContext.getResources().getString(
                        com.android.internal.R.string.sidebar_wechat_sight))) {
                    showDragPopup(text);
                }
            }
        }
    };
    private Runnable mDragImageRunnable = new Runnable() {
        @Override
        public void run() {
            if (mTouchedTextView == null || mTouchedTextView.getVisibility() != View.VISIBLE) {
                return;
            }
            onImageViewLongPressed();
        }
    };

    private Runnable mImageBoomRunnable = new Runnable() {
        @Override
        public void run() {
            mTextBoom.preparing = false;
            try {
                Intent intent = new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                intent.setClassName(TEXTBOOM_PKG_NAME, TEXTBOOM_PKG_NAME + ".BoomOcrActivity");
                Context context = mContext;
                if (!(context instanceof Activity)) {
                    if (context instanceof ContextWrapper) {
                        context = ((ContextWrapper) context).getBaseContext();
                        if (!(context instanceof Activity)) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                }
                if (mCanImageBoom) {
                    intent.putExtra("caller_pkg", mContext.getPackageName());

                    int x = (int) mLongPressDownRawX;
                    int y = (int) mLongPressDownRawY;

                    int[] offset = new int[2];
                    ViewRootImpl viewRoot = mDecorView.getViewRootImpl();
                    boolean fullScreen = false;
                    if (viewRoot != null) {
                        viewRoot.getScreenSizeOffset(offset);
                        x -= offset[0];
                        y -= offset[1];
                        fullScreen = LayoutParams.FLAG_FULLSCREEN ==
                                (viewRoot.getWindowFlags() & LayoutParams.FLAG_FULLSCREEN);
                    } else {
                        offset[0] = offset[1] = 0;
                    }
                    intent.putExtra("boom_offsetx", offset[0]);
                    intent.putExtra("boom_offsety", offset[1]);
                    intent.putExtra("boom_startx", x);
                    intent.putExtra("boom_starty", y);
                    intent.putExtra("boom_fullscreen", fullScreen);
                }
                mContext.startActivity(intent, ActivityOptions.makeCustomAnimation(mContext, 0, 0).toBundle());
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Image boom Error e=" + e);
            }
            mDiscardNextActionUp = true;
        }
    };

    public PressGestureDetector(Context context, FrameLayout docerView) {
        mContext = context;
        mDecorView = docerView;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mDragSurfaceImageSize = context.getResources().getDimensionPixelSize(com.android.internal.R.dimen.drag_image_size);
        mTextBoom = new TextBoom();
    }

    private static final String WECHAT_ANO_CAN_DRAG_WIN = "com.tencent.mm/com.tencent.mm.plugin.sns.ui.SnsGalleryUI";
    public void decideDragImage(boolean isFullscreen, String winTitle){
        mCanDragImage = (isFullscreen || WECHAT_ANO_CAN_DRAG_WIN.equals(winTitle)) && matchPackage(WECHAT_PKG_NAME);
        if(ENABLE_FEATURE_DRAG_IMAGE && mCanDragImage){
            if(Environment.getUserRequired() == false){
                SHARE_FILE_TMP_DIR = Environment.getExternalStorageDirectory().getPath() + "/Android/data/sidebar";
                mCanDragImage = true;
            }else{
                mCanDragImage = false;
                Log.e(TAG, "Can not perform a drag image operation in " + winTitle + " : External Storage user required");
            }
            if (mContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                mCanDragImage = false;
                Log.e(TAG, "Can not perform a drag image operation in " + winTitle + " : Can not write external storage");
            }
        }
    }

    public boolean isLongPressSwipe() {
        return mIsPhoneLongClickSwipe;
    }

    public void onAttached(int windowType) {
        mFindViewRestricted = (windowType >= LayoutParams.FIRST_SUB_WINDOW);
        if (mFindViewRestricted) return;
        KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mFindViewRestricted = km.isKeyguardLocked();
        if (mFindViewRestricted) return;
        mFindViewRestricted = isLauncherApp() || matchPackage(TEXTBOOM_PKG_NAME) || matchPackage(INCALLUI_PKG_NAME);
        if (mFindViewRestricted) return;
        mIsInOcrBacklist = isInOcrBlacklist();
    }

    public void onDetached() {
        if (mFindViewRestricted) return;
        removeCallbacks();
        if (mObserverRegistered) {
            mTextBoom.unregisterObserver();
            mObserverRegistered = false;
        }

        if(mDecorView != null){
            mDecorView.removeCallbacks(mDragTimeoutRun);
        }
        if(mSaveBmpTask != null){
            mSaveBmpTask.cancel(true);
        }
    }

    public void handleBackKey() {
        if (!mFindViewRestricted) {
            removeCallbacks();
        }
    }

    public boolean dispatchTouchEvent(MotionEvent ev, boolean isHandling) {
        if (isHandling || mFindViewRestricted) {
            return false;
        }
        if (mDecorView.getParent() == mDecorView.getViewRootImpl()) {
            final int action = ev.getAction();
            final int actionMasked = action & MotionEvent.ACTION_MASK;
            switch (actionMasked) {
                case MotionEvent.ACTION_DOWN: {
                    if (!mObserverRegistered) {
                        mTextBoom.registerObserver();
                        mObserverRegistered = true;
                    }
                    mCanTextBoom = false;
                    mCanImageBoom = false;
                    mTextBoomEntered = false;
                    mDiscardNextActionUp = false;
                    mIsPhoneLongClickSwipe = false;
                    final int actionIndex = ev.getActionIndex();
                    mTouchPointerID = ev.getPointerId(actionIndex);
                    mLongPressDownX = ev.getX(actionIndex);
                    mLongPressDownY = ev.getY(actionIndex);
                    mLongPressDownRawX = ev.getRawX();
                    mLongPressDownRawY = ev.getRawY();

                    final boolean canDragText = isDragEnabledApp();
                    final boolean dragEnabled = (mCanDragImage || canDragText) && OneStepDragUtils.isSidebarShowing(mContext);
                    if (mTextBoom.enabled || dragEnabled) {
                        mTouchedTextView = mDecorView.dispatchFindView(mLongPressDownX, mLongPressDownY, dragEnabled && mCanDragImage);
                    }

                    removeDragRunnable();
                    if (dragEnabled && mTouchedTextView != null) {
                        if (mCanDragImage && mTouchedTextView instanceof ImageView) {
                            mDecorView.postDelayed(mDragImageRunnable, ViewConfiguration.getLongPressTimeout() + 150);
                        } else if (canDragText && mTouchedTextView instanceof TextView && !TextUtils.isEmpty(mTouchedTextView.getFindText())) {
                            mDecorView.postDelayed(mDragTextRunnable, ViewConfiguration.getLongPressTimeout() + 150);
                        }
                    }
                    checkTouchSizeForBoom(ev.getSize());
                    mShouldCheckWechatUrl = canCheckWechatUrl();
                    mTouchDownTime = SystemClock.uptimeMillis();
                }
                break;
                case MotionEvent.ACTION_MOVE: {
                    final int actionIndex = ev.getActionIndex();
                    final int pointerID = ev.getPointerId(actionIndex);
                    if (pointerID != mTouchPointerID) {
                        break;
                    }
                    float x = ev.getX(actionIndex);
                    float y = ev.getY(actionIndex);
                    if (Math.abs(y - mLongPressDownY) >= mTouchSlop) {
                        mShouldCheckWechatUrl = false;
                    }
                    if (!mDecorView.pointInView(x, y, mTouchSlop)
                            || Math.abs(mLongPressDownX - x) >= TOUCH_MOVE_BOUND
                            || Math.abs(mLongPressDownY - y) >= TOUCH_MOVE_BOUND) {
                        if (mTextBoom.preparing) {
                            Log.e(TAG, "Move to stop text boom");
                        }
                        if (mCanImageBoom) {
                            cancelImageBoom();
                        }
                        removeCallbacks();
                        break;
                    }
                    if (Math.abs(mLongPressDownX - x) >= mTouchSlop
                            || Math.abs(mLongPressDownY - y) >= mTouchSlop) {
                        removeDragRunnable();
                    }
                    if (!mTextBoomEntered) {
                        final long interval = SystemClock.uptimeMillis() - mTouchDownTime;
                        if (interval > 300) {
                            Log.e(TAG, "Time out for start text boom, interval=" + interval);
                            mTextBoomEntered = true;
                            break;
                        }
                        checkTouchSizeForBoom(ev.getSize());
                    }
                }
                break;
                case MotionEvent.ACTION_UP:
                    checkWechatUrlClicked(ev);
                    if (mCanImageBoom) {
                        long uptime = System.currentTimeMillis();
                        if (startImageBoomTime != 0 && UP_TIME > (uptime - startImageBoomTime)) {
                            Log.e(TAG, "touch event up stop_image_boom");
                            cancelImageBoom();
                            removeCallbacks();
                            //mDiscardNextActionUp = false;
                            break;
                        }
                    }
                    if (mDiscardNextActionUp) {
                        removeCallbacks();
                        return true;
                    }
                    removeCallbacks();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks();
                    break;
            }
        } else {
            removeCallbacks();
        }
        return false;
    }

    private boolean canCheckWechatUrl() {
        if (matchPackage(WECHAT_PKG_NAME) && mTouchedTextView instanceof TextView) {
            return !((TextView) mTouchedTextView).isTextEditable();
        } else {
            return false;
        }
    }

    private void checkWechatUrlClicked(MotionEvent ev) {
        if (mShouldCheckWechatUrl) {
            long interval = SystemClock.uptimeMillis() - mTouchDownTime;
            if (interval < ViewConfiguration.getLongPressTimeout()) {
                final int actionIndex = ev.getActionIndex();
                final int pointerID = ev.getPointerId(actionIndex);
                if (pointerID != mTouchPointerID) return;
                final float x = ev.getX(actionIndex);
                final float y = ev.getY(actionIndex);
                mDecorView.post(new Runnable() {
                    @Override
                    public void run() {
                        View touchedUpView = mDecorView.dispatchFindView(x, y, false);
                        if (touchedUpView instanceof TextView) {
                            TextView upTextView = (TextView) touchedUpView;
                            if (upTextView.isTextEditable()) return;
                            final int offset = upTextView.getFindTextIndex();
                            final CharSequence text = upTextView.getText();
                            Matcher m = Patterns.WEB_URL.matcher(text);
                            while (m.find()) {
                                final int ps = m.start();
                                final int pe = m.end();
                                if (ps <= offset && pe >= offset && Linkify.sUrlMatchFilter.acceptMatch(text, ps, pe)) {
                                    Intent intent = new Intent("org.chromium.chrome.browser.QuickOpenBroadcastReceiver");
                                    String url = text.subSequence(ps, pe).toString();
                                    Log.i(TAG, "pass link to browser : " + url);
                                    intent.putExtra("wxData", url);
                                    mContext.sendBroadcast(intent);
                                    return;
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    void checkTouchSizeForBoom(float touchSize) {
        if (touchSize > mTextBoomArea) {
            mTextBoomEntered = true;
            mCanTextBoom = false;
            if (canTextBoom()) {
                mIsPhoneLongClickSwipe = true;
                mCanTextBoom = true;
                mTextBoom.prepareTextBoom();
            }
        }
        if (!mCanTextBoom && touchSize > mImageBoomArea) {
            mTextBoomEntered = true;
            mCanImageBoom = false;
            tryImageBoom();
        }
    }

    private void tryImageBoom() {
        if (canImageBoom()) {
            mIsPhoneLongClickSwipe = true;
            mCanImageBoom = true;
            mTextBoom.prepareImageBoom();
            mTextBoom.startImageBoom();
        }
    }

    static final long UP_TIME = 600;
    long startImageBoomTime = 0;

    private boolean isLauncherApp() {
        final String pkgName = mContext.getPackageName();
        if (pkgName == null) {
            Log.e(TAG, "Can't get package name info, enable textboom by default");
            return false;
        }
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo == null) return false;
        return pkgName.equals(res.activityInfo.packageName);
    }

    private boolean isDragEnabledApp() {
        ApplicationInfo appInfo = mContext.getApplicationInfo();
        return appInfo != null && !appInfo.isSystemApp();
    }

    private class TextBoom {
        private static final float LOOP_START_SCALE = 2f;
        private static final float LOOP_END_SCALE = 0.4f;
        private static final float CIRCLE_START_SCALE = 0.4f;
        private static final float CIRCLE_END_SCALE = 15f;
        private static final String BIG_BANG_TEXT_TRIGGER_AREA = "big_bang_text_trigger_area";
        private static final String BIG_BANG_OCR_TRIGGER_AREA = "big_bang_ocr_trigger_area";
        private static final String BIG_BANG_OCR_ENABLE = "big_bang_ocr";

        private ImageView mLoopView;
        private ImageView mCircleView;
        private AnimatorSet mLoopAnimator;
        private FrameLayout mBoomIndictor;

        public boolean enabled = false;
        public boolean ocrEnabled = false;
        public boolean preparing = false;

        private ContentObserver mBoomTriggerAreaObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateDefaultArea();
            }
        };
        private ContentObserver mTextBoomTriggerAreaObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateTextBoomArea();
            }
        };
        private ContentObserver mImageBoomTriggerAreaObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateImageBoomArea();
            }
        };
        private ContentObserver mTextBoomEnabledObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateTextBoomEnabled();
                if (enabled) {
                    addIndicator();
                } else {
                    removeIndicator();
                }
            }
        };
        private ContentObserver mImageBoomEnabledObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateImageBoomEnabled();
            }
        };

        public void registerObserver() {
            updateDefaultArea();
            updateTextBoomEnabled();
            updateImageBoomEnabled();
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Global.getUriFor(Global.BOOM_TEXT_TRIGGER_AREA),
                    true, mBoomTriggerAreaObserver);
            resolver.registerContentObserver(Global.getUriFor(Global.TEXT_BOOM),
                    true, mTextBoomEnabledObserver);
            resolver.registerContentObserver(Global.getUriFor(BIG_BANG_OCR_ENABLE),
                    true, mImageBoomEnabledObserver);
            if (DBG) {
                updateTextBoomArea();
                updateImageBoomArea();
                resolver.registerContentObserver(Global.getUriFor(BIG_BANG_TEXT_TRIGGER_AREA),
                        true, mTextBoomTriggerAreaObserver);
                resolver.registerContentObserver(Global.getUriFor(BIG_BANG_OCR_TRIGGER_AREA),
                        true, mImageBoomTriggerAreaObserver);
            }
            if (enabled) {
                addIndicator();
            }
        }

        public void unregisterObserver() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(mBoomTriggerAreaObserver);
            resolver.unregisterContentObserver(mTextBoomEnabledObserver);
            resolver.unregisterContentObserver(mImageBoomEnabledObserver);
            if (DBG) {
                resolver.unregisterContentObserver(mTextBoomTriggerAreaObserver);
                resolver.unregisterContentObserver(mImageBoomTriggerAreaObserver);
            }
            if (enabled) {
                removeIndicator();
            }
        }

        private void updateDefaultArea() {
            int touchSizeMode = Global.getInt(mContext.getContentResolver(),
                    Global.BOOM_TEXT_TRIGGER_AREA, Global.BOOM_TEXT_TRIGGER_AREA_MIDDLE);
            String[] area = mContext.getResources().getStringArray(com.android.internal.R.array.text_boom_trigger_area);
            if (touchSizeMode >= 0 && touchSizeMode < area.length) {
                mTextBoomArea = Float.parseFloat(area[touchSizeMode]);
                mImageBoomArea = mTextBoomArea;
            }
        }

        private void updateTextBoomArea() {
            mTextBoomArea = Global.getFloat(mContext.getContentResolver(), BIG_BANG_TEXT_TRIGGER_AREA, mTextBoomArea);
            if (DBG) {
                Log.i(TAG, "Set text trigger area=" + mTextBoomArea);
            }
        }

        private void updateImageBoomArea() {
            mImageBoomArea = Global.getFloat(mContext.getContentResolver(), BIG_BANG_OCR_TRIGGER_AREA, mTextBoomArea);
            if (DBG) {
                Log.i(TAG, "Set ocr trigger area=" + mImageBoomArea);
            }
        }

        private void updateTextBoomEnabled() {
            enabled = Global.getInt(mContext.getContentResolver(), Global.TEXT_BOOM, 1) == 1;
        }

        private void updateImageBoomEnabled() {
            ocrEnabled = Global.getInt(mContext.getContentResolver(), BIG_BANG_OCR_ENABLE, 1) == 1;
        }

        private ImageView createImageView(int drawableRes) {
            ImageView imageView = new ImageView(mContext);
            imageView.setBackgroundResource(drawableRes);
            final DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            imageView.measure(
                    View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels,
                            View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(displayMetrics.heightPixels,
                            View.MeasureSpec.AT_MOST));
            imageView.setClipToOutline(false);
            return imageView;
        }

        private void addIndicator() {
            mLoopView = createImageView(com.android.internal.R.drawable.smartisan_boom_loop);
            mLoopView.setVisibility(View.INVISIBLE);
            mLoopView.setScaleX(LOOP_END_SCALE);
            mLoopView.setScaleY(LOOP_END_SCALE);
            mCircleView = createImageView(com.android.internal.R.drawable.smartisan_boom_circle);
            mCircleView.setVisibility(View.INVISIBLE);
            mCircleView.setScaleX(CIRCLE_END_SCALE);
            mCircleView.setScaleY(CIRCLE_END_SCALE);
            mBoomIndictor = new FrameLayout(mContext);
            final FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            flp.gravity = Gravity.TOP | Gravity.LEFT;
            mBoomIndictor.addView(mLoopView, flp);
            mBoomIndictor.addView(mCircleView, flp);
            mBoomIndictor.setBackgroundColor(Color.TRANSPARENT);
            mBoomIndictor.setVisibility(View.GONE);
            mDecorView.addView(mBoomIndictor);
        }

        private void removeIndicator() {
            mDecorView.removeView(mLoopView);
            mDecorView.removeView(mCircleView);
        }

        public void prepareTextBoom() {
            if (mBoomIndictor == null) return;
            startPreparingTextBoom();
            mBoomIndictor.bringToFront();
            mBoomIndictor.setVisibility(View.VISIBLE);
            mLoopView.bringToFront();
            mLoopView.setTranslationX(mLongPressDownX - mLoopView.getMeasuredWidth() / 2);
            mLoopView.setTranslationY(mLongPressDownY - mLoopView.getMeasuredHeight() / 2);
            mLoopView.setScaleX(LOOP_END_SCALE);
            mLoopView.setScaleY(LOOP_END_SCALE);
            mLoopView.setVisibility(View.VISIBLE);
            mLoopView.setAlpha(0f);
            startLoopAnimation();
        }

        public void prepareImageBoom() {
            startImageBoomTime = System.currentTimeMillis();
            startPreparingTextBoom();
            preparing = true;
        }

        private void startLoopAnimation() {
            if (mLoopAnimator == null) {
                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mLoopView, View.ALPHA, 0, 1f);
                alphaAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        if (animation.isRunning()) {
                            preparing = true;
                            if (mTouchedTextView == null) {
                                Log.w(TAG, "Cancel animation, mTouchedTextView has been reset to null");
                                stopTextBoom();
                                return;
                            }
                            if (null != mTouchedTextView && mTouchedTextView instanceof TextView) {
                                if (TextUtils.isEmpty(((TextView) mTouchedTextView).getTrimmedFoundText())) {
                                    Log.w(TAG, "Found text is empty for view=" + mTouchedTextView);
                                    stopTextBoom();
                                    return;
                                }
                            } else if (!canGetTextFromWebView()) {
                                if (DBG) {
                                    Log.d(TAG, "Found text is empty for webview=" + mTouchedTextView);
                                }
                                stopTextBoom();
                                tryImageBoom();
                                return;
                            }
                        }
                    }
                });
                alphaAnimator.setDuration(100);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(mLoopView, View.SCALE_X, LOOP_START_SCALE, LOOP_END_SCALE);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(mLoopView, View.SCALE_Y, LOOP_START_SCALE, LOOP_END_SCALE);
                scaleX.setDuration(300);
                scaleY.setDuration(300);
                mLoopAnimator = new AnimatorSet();
                mLoopAnimator.setStartDelay(100);
                mLoopAnimator.setInterpolator(new AccelerateInterpolator(1.5f));
                mLoopAnimator.playTogether(alphaAnimator, scaleX, scaleY);
                mLoopAnimator.addListener(new Animator.AnimatorListener() {
                    private boolean mIsCanceled = false;

                    @Override
                    public void onAnimationStart(Animator animation) {
                        mIsCanceled = false;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mIsCanceled) {
                            return;
                        }
                        mLoopView.setVisibility(View.INVISIBLE);
                        startTextBoom((int) mLongPressDownX, (int) mLongPressDownY);
                        mCircleView.bringToFront();
                        mCircleView.setVisibility(View.VISIBLE);
                        mCircleView.setTranslationX(mLongPressDownX - mCircleView.getMeasuredWidth() / 2);
                        mCircleView.setTranslationY(mLongPressDownY - mCircleView.getMeasuredHeight() / 2);
                        startCircleAnimation();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mIsCanceled = true;
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
            }
            mLoopAnimator.start();
        }

        private boolean canGetTextFromWebView() {
            if (mTouchedTextView == null) return false;
            String text = mTouchedTextView.getFindText();
            if (text == null) return false;
            for (int i = text.length() - 1; i >= 0; --i) {
                char c = text.charAt(i);
                if (!Character.isSpaceChar(c) && !Character.isWhitespace(c)) {
                    return true;
                }
            }
            return false;
        }

        private void startCircleAnimation() {
            Animator alpha = ObjectAnimator.ofFloat(mCircleView, View.ALPHA, 1f, 0);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(mCircleView, View.SCALE_X, CIRCLE_START_SCALE, CIRCLE_END_SCALE);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(mCircleView, View.SCALE_Y, CIRCLE_START_SCALE, CIRCLE_END_SCALE);
            AnimatorSet set = new AnimatorSet();
            set.setDuration(300);
            set.setInterpolator(new DecelerateInterpolator(1.5f));
            set.playTogether(alpha, scaleX, scaleY);
            set.start();
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mCircleView.setVisibility(View.INVISIBLE);
                    mCircleView.setScaleX(CIRCLE_START_SCALE);
                    mCircleView.setScaleY(CIRCLE_START_SCALE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
        }

        public void stopTextBoom() {
            preparing = false;
            mIsPhoneLongClickSwipe = false;
            if (mLoopView == null) {
                return;
            }
            if (mLoopAnimator != null) {
                mLoopAnimator.cancel();
            }
            if (mLoopView.getVisibility() == View.VISIBLE && mLoopView.getAlpha() > 0) {
                Animator alpha = ObjectAnimator.ofFloat(mLoopView, View.ALPHA, mLoopView.getAlpha(), 0);
                alpha.setDuration(100);
                alpha.setInterpolator(new DecelerateInterpolator(1.5f));
                alpha.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mLoopView.setVisibility(View.INVISIBLE);
                        mLoopView.setScaleX(LOOP_START_SCALE);
                        mLoopView.setScaleY(LOOP_START_SCALE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mLoopView.setAlpha(0f);
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
                alpha.start();
            } else {
                mLoopView.setVisibility(View.INVISIBLE);
                mLoopView.setScaleX(LOOP_START_SCALE);
                mLoopView.setScaleY(LOOP_START_SCALE);
            }
        }

        private void startPreparingTextBoom() {
            //
        }

        private void startTextBoom(int x, int y) {
            preparing = false;
            removeDragRunnable();
            try {
                if (mTouchedTextView == null) {
                    Log.w(TAG, "Don't start boom, mTouchedTextView has been reset to null");
                    return;
                }
                InputMethodManager imm = InputMethodManager.peekInstance();
                if (imm != null && imm.isActive(mTouchedTextView)) {
                    imm.hideSoftInputFromWindow(mTouchedTextView.getWindowToken(), 0);
                }

                final boolean isTextView = mTouchedTextView instanceof TextView;
                final String text;
                if (isTextView) {
                    text = ((TextView) mTouchedTextView).getTrimmedFoundText();
                } else {
                    text = mTouchedTextView.getFindText().toString();
                    mTouchedTextView.setFindText("");
                }
                if (text == null) {
                    Log.w(TAG, "Null text for " + mTouchedTextView);
                }
                Intent intent = new Intent();
                intent.setClassName(TEXTBOOM_PKG_NAME, TEXTBOOM_PKG_NAME + ".BoomActivity");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                intent.putExtra("boom_startx", x);
                intent.putExtra("boom_starty", y);
                intent.putExtra("boom_index", mTouchedTextView.getFindTextIndex());
                intent.putExtra("caller_pkg", mContext.getPackageName());
                intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                Context context = mContext;
                if (!(context instanceof Activity)) {
                    if (context instanceof ContextWrapper) {
                        context = ((ContextWrapper) context).getBaseContext();
                        if (!(context instanceof Activity)) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        }
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    }
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                context.startActivity(intent);
                if (context instanceof Activity) {
                    // Fix Wechat override transition issue
                    ((Activity) context).overridePendingTransition(0, 0);
                }
                mDiscardNextActionUp = true;
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Text boom Error e=" + e);
            }
        }

        private void startImageBoom() {
            Log.i(TAG, "startImageBoom");
            removeDragRunnable();
            mDecorView.postDelayed(mImageBoomRunnable, 400);
        }
    }

    private boolean canTextBoom() {
        if (mTextBoom.enabled && mTouchedTextView != null
                && mTouchedTextView.getVisibility() == View.VISIBLE) {
            if (mTouchedTextView instanceof ImageView) return false;
            final boolean isTextView = mTouchedTextView instanceof TextView;
            if (isTextView) {
                final String foundText = ((TextView) mTouchedTextView).getTrimmedFoundText();
                if (foundText == null || TextUtils.isEmpty(foundText.trim())) {
                    return false;
                }
            }
            if (isTextView) {
                final int variation = ((TextView) mTouchedTextView).getInputType()
                        & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
                return variation != (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
                        && variation != (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                        && variation != (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
            }
            return true;
        }
        return false;
    }

    private boolean canImageBoom() {
        return mTextBoom.enabled && mTextBoom.ocrEnabled && !mIsInOcrBacklist;
    }

    private void cancelImageBoom() {
        mDecorView.removeCallbacks(mImageBoomRunnable);
        startImageBoomTime = 0;
        mCanImageBoom = false;
        Uri uri = Uri.parse(BOOM_AUTHORITY);
        try {
            mDecorView.getContext().getContentResolver().call(uri, METHOD_STOP_OCR, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeDragRunnable() {
        mDecorView.removeCallbacks(mDragTextRunnable);
        mDecorView.removeCallbacks(mDragImageRunnable);
    }

    private void removeCallbacks() {
        mTextBoom.stopTextBoom();
        if (mTouchedTextView != null) {
            mTouchedTextView.setFindText("");
            mTouchedTextView = null;
        }
        mDiscardNextActionUp = false;
        removeDragRunnable();
    }

    private boolean isInOcrBlacklist() {
        String[] blackList = mContext.getResources().getStringArray(com.android.internal.R.array.ocr_black_list);
        for (String name : blackList) {
            if (name.equals(mContext.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchPackage(String pkgName) {
        return pkgName.equals(mContext.getPackageName());
    }

    private void showDragPopup(String text) {
        if (OneStepDragUtils.isSidebarShowing(mContext)) {
            OneStepDragUtils.dragText(mTouchedTextView, mContext, text);
        }
    }

    private static final int PENDING_CANCEL_ACTION_DURATION = 4000;
    static final String SHAREING_NAME_PRFIX = "bitmap_sharing_";
    private static final int MAX_CACHE_SIZE = 9;

    private static int mSubFixSaveId = 0;
    private int mDragSurfaceImageSize = 100;
    private SaveBmpTask mSaveBmpTask;

    public void onDragEnd(boolean acceptSucces){
        if(mSaveBmpTask != null && mDecorView != null){
            mDecorView.postDelayed(mDragTimeoutRun, acceptSucces ? PENDING_CANCEL_ACTION_DURATION : 0);
        }
    }

    private Runnable mDragTimeoutRun = new Runnable() {
        @Override
        public void run() {
            if(mSaveBmpTask != null){
                mSaveBmpTask.cancel(true);
            }
        }
    };

    private void onImageViewLongPressed(){
        try{
            if(mSaveBmpTask != null){
                if(mDecorView != null){
                    mDecorView.removeCallbacks(mDragTimeoutRun);
                }
                mSaveBmpTask.cancel(true);
                mSaveBmpTask = null;
                Log.w(TAG, "Last drag action not finished, Force abort and cancel last for safe");
                return;
            }

            if(mCanDragImage == false){
                return;
            }

            ImageView imageView = (ImageView)mTouchedTextView;
            Drawable drawable = imageView.getDrawable();
            if(drawable == null || (drawable instanceof BitmapDrawable) == false){
                return;
            }
            BitmapDrawable bmpDrawable = (BitmapDrawable)drawable;
            Bitmap bmpOri = bmpDrawable.getBitmap();
            if(bmpOri == null){
                return;
            }
            int scale = (bmpOri.getWidth() <= bmpOri.getHeight() ? bmpOri.getWidth():bmpOri.getHeight()) / mDragSurfaceImageSize;
            Bitmap bmpToDrawDrag;
            if(scale > 1){
                bmpToDrawDrag = Bitmap.createScaledBitmap(bmpOri, bmpOri.getWidth()/scale, bmpOri.getHeight()/scale, false);
            }else{
                bmpToDrawDrag = bmpOri.copy(bmpOri.getConfig(), false);
            }
            boolean cmpToAPngFile = bmpOri.getAllocationByteCount() < (1048576 * 8); // size < 8M : compress to png format
            File file = getNextFile(cmpToAPngFile ? "png" : "jpg");
            if(file == null){
                return;
            }
            if(file.exists()){
                file.delete();
            }
            if(dragImageView(bmpToDrawDrag, file, cmpToAPngFile ? "image/png" : "image/jpeg")){
                bmpToDrawDrag.recycle();
                mSaveBmpTask = new SaveBmpTask(bmpOri, file, cmpToAPngFile);
                mSaveBmpTask.execute();
            }else{
                bmpToDrawDrag.recycle();
            }
        }catch (Exception e){
            Log.w(TAG, "Drag Bitmap from a ImageView failed " + e);
        }
    }

    private File getNextFile(String suffix) {
        File dir = new File(SHARE_FILE_TMP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        if (!dir.exists()) {
            Log.e(TAG, "Fail to create dir " + dir.getAbsolutePath() + " !");
            return null;
        }
        int nextId = (mSubFixSaveId++) % MAX_CACHE_SIZE;
        return new File(dir, SHAREING_NAME_PRFIX + nextId + "." + suffix);
    }

    private boolean writeSaveBitmap(Bitmap bmp, File file, boolean pngCmp){
        if(bmp == null) return false;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            if(pngCmp){
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }else{
                if(bmp.getAllocationByteCount() < 50 * 1048576){ // size < 50M : compress to 100 quality jpg
                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
                }else{                                           // size >= 50M : compress to 80 quality jpg
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Save ImageView bitmap failed: " + e);
            return false;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private boolean dragImageView(Bitmap content, File file, String mimeType) {
        OneStepDragUtils.dragImage(mTouchedTextView, mContext, content, file, mimeType);
        return true;
    }

    class SaveBmpTask extends AsyncTask<Void, Integer, Boolean> {
        private Bitmap mBitmap;
        private File mFile;
        private static final String SUB_FIX="_tmp";
        private File mTmpFile;
        private boolean mPngCmp;

        public SaveBmpTask(Bitmap bmp, File f, boolean pngCmp){
            mBitmap = bmp;
            mFile = f;
            mPngCmp = pngCmp;
            mTmpFile = new File(mFile.toString() + SUB_FIX);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return writeSaveBitmap(mBitmap, mTmpFile, mPngCmp);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if(mDecorView != null){
                mDecorView.removeCallbacks(mDragTimeoutRun);
            }
            try{
                if(success){
                    if(mTmpFile != null){
                        mTmpFile.renameTo(mFile);
                    }
                }else{
                    if(mTmpFile != null){
                        mTmpFile.delete();
                    }
                }
            }catch (Exception e){
                Log.w(TAG, "Operation on file failed" + e);
            }finally {
                cleanTask();
            }
        }

        @Override
        protected void onCancelled(Boolean success) {
            try{
                Log.w(TAG, "Drag Bitmap Save Operation is Canceled Now");
                if(mTmpFile != null){
                    mTmpFile.delete();
                }
            }catch (Exception e){
                Log.w(TAG, "Operation on file failed" + e);
            }finally {
                cleanTask();
            }
        }

        private void cleanTask(){
            PressGestureDetector.this.mSaveBmpTask = null;
            if(mBitmap != null){
                mBitmap = null;
            }
        }
    }
}

