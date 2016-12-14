package android.widget;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.lang.reflect.Method;

/**
 * @hide
 */
public class TextDragPopupWindow {

    private final static String TAG = "TextDragPopupWindow";

    private final View mParent;
    private final OnDragListener mDragListerner;

    private final PopupWindow mContainer;
    private final Context mContext;
    private final View mContentView;
    private final TextView mText;
    private final int mTouchPointOffset;

    private boolean mDeferedHide;
    private Editor.DragLocalState mDragLocalState;

    public TextDragPopupWindow(View parent, OnDragListener dragListener) {
        mParent = parent;
        mParent.setOnDragListener(new OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_ENDED:
                        mParent.getViewRootImpl().setLocalDragState(null);
                        break;
                }
                return false;
            }
        });
        mDragListerner = dragListener;
        mContext = mParent.getContext();
        mContainer = new PopupWindow(mContext, null,
                com.android.internal.R.attr.textSelectHandleWindowStyle);
        mContainer.setSplitTouchEnabled(true);
        mContainer.setClippingEnabled(false);
        mContainer.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mContainer.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mContainer.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
        mContentView = LayoutInflater.from(mContext).inflate(
                com.android.internal.R.layout.smartisan_drag_text_popup, null);
        mContentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mDeferedHide = true;
                    if (mDragListerner != null) {
                        mDragListerner.onDrag(v, null);
                    }
                    mContentView.setPressed(true);

                    startDrag();
                }
                return true;
            }
        });
        // Add a outer layout to contain scaled ContentView;
        FrameLayout frameLayout = new FrameLayout(mContext);
        FrameLayout.LayoutParams flp = new
                FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        final int scaleMargin = mContext.getResources().getDimensionPixelOffset(com.android.internal.R.dimen.drag_scale_margin);
        flp.leftMargin = scaleMargin;
        flp.rightMargin = scaleMargin;
        frameLayout.addView(mContentView, flp);
        mContainer.setContentView(frameLayout);
        mText = (TextView) mContentView.findViewById(com.android.internal.R.id.drag_text_id);
        mTouchPointOffset = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.drag_touch_point_offset);
    }


    public static boolean isSideBarShowing() {
        try{
            Class<?> clz = Class.forName("smartisanos.util.SidebarUtils");
            Method method = clz.getMethod("isSidebarShowing", new Class[] { Context.class });
            Boolean result = (Boolean) method.invoke(clz, (Context) null);
            return result;
        } catch (Exception e){
            e.fillInStackTrace();
            Log.e(TAG, "Reflect isSideBarShowing() fails, e=" + e);
            return false;
        }
    }

    public int measureContent(String text) {
        mText.setText(text);
        final DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        mContentView.measure(
                View.MeasureSpec.makeMeasureSpec(displayMetrics.widthPixels,
                        View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(displayMetrics.heightPixels,
                        View.MeasureSpec.AT_MOST));
        mContentView.setPressed(false);
        return mContentView.getMeasuredHeight();
    }

    public int measureContent(String text, int start, int end) {
        if (mDragLocalState == null) {
            mDragLocalState = new Editor.DragLocalState((TextView) mParent, start, end);
            mDragLocalState.highlight = true;
        } else {
            mDragLocalState.start = start;
            mDragLocalState.end = end;
        }
        return measureContent(text);
    }

    public void show(int x, int y) {
        if (mContainer.isShowing()) {
            mContainer.update(x, y, -1, -1);
        } else {
            mContainer.showAtLocation(mParent, Gravity.NO_GRAVITY, x, y);
        }
    }

    public void hide() {
        if (mDeferedHide) {
            return;
        }
        if (mContainer.isShowing()) {
            mContainer.dismiss();
        }
    }

    private class BaseDragShadowBuilder extends View.DragShadowBuilder{
        protected Context mContext;

        public BaseDragShadowBuilder(View view){
            super(view);
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize,
                                           Point shadowTouchPoint) {
            final View view = getView();
            if (view != null) {
                shadowSize.set(view.getWidth(), view.getHeight());
                shadowTouchPoint.set(shadowSize.x / 2, shadowSize.y / 2 + mTouchPointOffset);
            } else {
                Log.e(TAG, "Asked for drag thumb metrics but no view");
            }
        }
    }

    private void startDrag() {
        ClipData cd = ClipData.newPlainText(null, mText.getText());
        int arrowOffset = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.drag_normal_arrow_point_offset);
        if (mDragLocalState != null) {
            mParent.getViewRootImpl().setLocalDragState(mDragLocalState);
        }
        mContentView.startDrag(cd, new BaseDragShadowBuilder(mContentView), null, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + mTouchPointOffset));
        mDeferedHide = false;
        hide();
    }
}