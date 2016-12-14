/**
 * Copyright (c) 2016, The Smartisan Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package android.view.onestep;

import android.content.ClipData;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.os.RemoteException;
import android.view.View;

import com.smartisanos.internal.R;

import java.io.File;
/** {@hide} */
public class OneStepDragUtils {

    private static final class DragSource {
        public static final String APP = "0";
        public static final String IMAGE = "1";
        public static final String FILE = "2";
        public static final String CLIPBOARD = "3";
    }

    private static final class DragContent {
        public static final String IMAGE = "0";
        public static final String FILE = "1";
        public static final String TEXT = "2";
        public static final String LINK = "3";
    }

  public static void dragText(View view, Context context, CharSequence text) {
        dragText(view, context, text, null);
    }

    public static void dragText(View view, Context context, CharSequence text, Object myLocalState) {
        ClipData cd = ClipData.newPlainText(null, text);
        int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_normal_arrow_point_offset);
        int touchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        view.startDrag(cd, new TextDragShadowBuilder(context, text.toString()), myLocalState, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + touchPointOffset));
    }

    public static void dragText(View view, Context context, CharSequence text, Bitmap content, Bitmap avatar) {
        dragText(view, context, text, null, content, avatar);
    }

    public static void dragText(View view, Context context, CharSequence text, Bitmap background, Bitmap content, Bitmap avatar) {
        if (content == null && avatar == null) {
            dragText(view, context, text);
            return;
        }
        ClipData cd = ClipData.newPlainText(null, text);
        int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_card_arrow_point_offset);
        int touchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        view.startDrag(cd, new CardDragShadowBuilder(context, background, content, avatar), null, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + touchPointOffset));
    }

    public static void dragLink(View view, Context context, CharSequence link) {
        ClipData cd = ClipData.newPlainText(null, link);
        int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_normal_arrow_point_offset);
        int touchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        view.startDrag(cd, new LinkDragShadowBuilder(context, link.toString()), null, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + touchPointOffset));
    }

    public static void dragLink(View view, Context context, CharSequence link, Bitmap content, Bitmap avatar) {
        dragLink(view, context, link, null, content, avatar);
    }

    public static void dragLink(View view, Context context, CharSequence link, Bitmap background, Bitmap content, Bitmap avatar) {
        if (content == null && avatar == null) {
            dragLink(view, context, link);
            return;
        }
        ClipData cd = ClipData.newPlainText(null, link);
        int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_card_arrow_point_offset);
        int touchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        view.startDrag(cd, new CardDragShadowBuilder(context, background, content, avatar), null, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + touchPointOffset));
    }

    public static void dragFile(View view, Context context, File file, String mimeType) {
        dragFile(view, context, file, mimeType, file.getName());
    }

    public static void dragFile(View view, Context context, File file, String mimeType, Bitmap content, Bitmap avatar) {
        dragFile(view, context, file, mimeType, null, content, avatar);
    }

    public static void dragFile(View view, Context context, File file, String mimeType, Bitmap background, Bitmap content, Bitmap avatar) {
        if("text/plain".equals(mimeType)){
            mimeType = "application/*";
        }
        if (content == null && avatar == null) {
            dragFile(view, context, file, mimeType);
            return;
        }
        ClipData cd = new ClipData(null, new String[] { mimeType }, new ClipData.Item(Uri.fromFile(file)));
        int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_card_arrow_point_offset);
        int touchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        view.startDrag(cd, new CardDragShadowBuilder(context, background, content, avatar), null, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + touchPointOffset));
    }

    public static void dragFile(View view, Context context, File file, String mimeType, String displayname){
        if("text/plain".equals(mimeType)){
            mimeType = "application/*";
        }

        int resId = MimeUtils.getFileIconResId(mimeType, file);
        Bitmap fileIcon = null;
        if(resId != 0){
            Bitmap tmp = BitmapFactory.decodeResource(context.getResources(), resId);
            fileIcon = getScaledBitmap(tmp, 0.9f); // sorry for this magic number...
            tmp.recycle();
        }

        ClipData cd = new ClipData(null, new String[]{mimeType}, new ClipData.Item(Uri.fromFile(file)));
        int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_normal_arrow_point_offset);
        int touchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        view.startDrag(cd, new FileDragShadowBuilder(context, displayname, fileIcon), null, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + touchPointOffset));
    }

    public static void dragImage(View view, Context context, File file, String mimeType) {
        dragImage(view, context, null, file, mimeType);
    }

    public static void dragImage(View view, Context context, Bitmap content, File file, String mimeType) {
        ClipData cd = new ClipData(null, new String[]{mimeType}, new ClipData.Item(Uri.fromFile(file)));
        int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_image_arrow_point_offset);
        int touchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        if (content == null) {
            content = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (content == null) {
                return;
            }
        }
        view.startDrag(cd, new ImageDragShadowBuilder(context, content), null, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + touchPointOffset));
    }

    public static void dragMultipleImage(View view, Context context, File[] files, String[] mimeTypes) {
        dragMultipleImage(view, context, 0, files, mimeTypes);
    }

    public static void dragMultipleImage(View view, Context context, int index, File[] files, String[] mimeTypes) {
        dragMultipleImage(view, context, index, files, mimeTypes, 0);
    }

    public static void dragMultipleImage(View view, Context context, int index, File[] files, String[] mimeTypes, int showAnimDelay) {
        if (files.length <= 0) {
            return;
        }
        ClipData cd = new ClipData(null, mimeTypes, new ClipData.Item(Uri.fromFile(files[0])));
        for (int i = 1; i < files.length; ++i) {
            cd.addItem(new ClipData.Item(Uri.fromFile(files[i])));
        }

        int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_image_arrow_point_offset);
        int touchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        if(index < 0 || index >= files.length) {
            index = 0;
        }
        Bitmap content = BitmapFactory.decodeFile(files[index].getAbsolutePath());
        if (content == null) {
            return;
        }
        Bitmap background = null;
        if (files.length == 2) {
            background = BitmapFactory.decodeResource(context.getResources(), R.drawable.drag_image_background_2);
        } else if (files.length > 2) {
            background = BitmapFactory.decodeResource(context.getResources(), R.drawable.drag_image_background_3);
        }

        view.startDrag(cd, new ImageDragShadowBuilder(context, background, content), null, View.DRAG_FLAG_GLOBAL, 0, 0 - (arrowOffset + touchPointOffset), showAnimDelay);
    }

    private static Bitmap getScaledBitmap(Bitmap bmp, float scale){
        int width = (int) (bmp.getWidth() * scale);
        int height = (int) (bmp.getHeight() * scale);
        return Bitmap.createScaledBitmap(bmp, width, height, false);
    }

    private static class BaseDragShadowBuilder extends View.DragShadowBuilder{
        protected Context mContext;
        protected int mTouchPointOffset = 0;
        public BaseDragShadowBuilder(Context context){
            mContext = context;
            mTouchPointOffset = context.getResources().getDimensionPixelSize(R.dimen.drag_touch_point_offset);
        }
    }

    private static class SOSDragShadowBuilder extends BaseDragShadowBuilder{
        private NinePatchDrawable mBackground;
        private String mText;
        private int mMaxWidth, mMinWidth;

        private int mWidth = 0;
        private int mHeight = 0;
        private int mTextSize = 0;
        private int mTextColor = Color.WHITE;

        private Drawable mLinkIcon;
        private Bitmap mFileIcon;
        private Bitmap mFileIconShadow;
        private int mFileIconShadowCover;
        private int mFileIconSpace;
        private int mPaddingLeft;
        private int mPaddingRight;
        private int mPaddingIconText;
        private Paint mPaint = new Paint();

        public SOSDragShadowBuilder(Context context, String text, Drawable linkIcon, Bitmap fileIcon, int maxWidth, int minWidth, int paddingLeft, int paddingRight, int paddingIconText){
            super(context);
            mText = text;
            mLinkIcon = linkIcon;
            mFileIcon = fileIcon;
            mBackground = (NinePatchDrawable)context.getResources().getDrawable(R.drawable.drag_background);
            mTextSize = context.getResources().getDimensionPixelSize(R.dimen.drag_text_size);
            mTextColor = context.getResources().getColor(R.color.drag_text_color);
            mHeight = mBackground.getMinimumHeight();

            mPaint.setTextSize(mTextSize);
            mPaint.setColor(mTextColor);
            mPaint.setTextAlign(Align.CENTER);
            mPaint.setAntiAlias(true);
            // compute suitable width
            mMaxWidth = maxWidth;
            mMinWidth = minWidth;
            mPaddingLeft= paddingLeft;
            mPaddingRight = paddingRight;
            mPaddingIconText = paddingIconText;
            float curWidth = mPaddingLeft + (linkIcon != null ? linkIcon.getIntrinsicWidth() + mPaddingIconText: 0) + mPaint.measureText(mText) + paddingRight;
            if(curWidth > maxWidth){
                // cur mString !
                for(int i = 1; i <= mText.length(); ++ i){
                    String now = mText.substring(0, i) + "...";
                    if(mPaddingLeft + (linkIcon != null ? linkIcon.getIntrinsicWidth() + mPaddingIconText: 0) + mPaint.measureText(now) + paddingRight > maxWidth){
                        mText = mText.substring(0, i - 1) + "...";
                        mWidth = (int) (mPaddingLeft + (linkIcon != null ? linkIcon.getIntrinsicWidth() + mPaddingIconText: 0) + mPaint.measureText(mText) + paddingRight);
                        break;
                    }
                }
            }else if(curWidth < minWidth){
                mWidth = minWidth;
            }else{
                mWidth = (int) (curWidth + 0.5f);
            }

            // add file icon at last
            if(mFileIcon != null){
                mFileIconShadow = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.file_icon_shadow);
                mFileIconShadowCover = mContext.getResources().getDimensionPixelSize(R.dimen.drag_file_icon_shadow_cover);
                mFileIconSpace = mFileIconShadow.getWidth() - mFileIconShadowCover;
                mWidth += mFileIconSpace;
            }
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize,
                Point shadowTouchPoint) {
            if (mFileIcon != null) {
                shadowSize.set(mWidth, mHeight);
                shadowTouchPoint.set(shadowSize.x / 2 + mFileIcon.getWidth() / 2, shadowSize.y / 2 + mTouchPointOffset);
            }else{
                shadowSize.set(mWidth, mHeight);
                shadowTouchPoint.set(shadowSize.x / 2, shadowSize.y / 2 + mTouchPointOffset);
            }
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            int leftSpace = mFileIconSpace;
            if(mFileIcon != null){
                //draw file icon and shadow
                canvas.drawBitmap(mFileIconShadow, 0, (mHeight - mFileIconShadow.getHeight()) / 2, null);
                canvas.drawBitmap(mFileIcon, (mFileIconShadow.getWidth() - mFileIcon.getWidth()) / 2, (mHeight - mFileIcon.getHeight()) / 2, null);
            }

            // background
            mBackground.setBounds(leftSpace , 0, mWidth, mHeight);
            mBackground.draw(canvas);

            // draw icon
            if(mLinkIcon != null){
                int height = mLinkIcon.getIntrinsicHeight();
                int width = mLinkIcon.getIntrinsicWidth();
                int left = mPaddingLeft;
                int top = (mHeight - height) / 2;
                mLinkIcon.setBounds(leftSpace + left, top, left + width, top + height);
                mLinkIcon.draw(canvas);

                // text content
                FontMetrics fm = mPaint.getFontMetrics();
                float txt_height = fm.bottom - fm.top;
                float txt_width = mPaint.measureText(mText);
                int paddingLeft = (int) (mPaddingLeft + (mLinkIcon != null ? mLinkIcon.getIntrinsicWidth() + mPaddingIconText: 0));
                canvas.drawText(mText, leftSpace + paddingLeft + txt_width / 2, mHeight - (mHeight - txt_height) / 2 - fm.bottom , mPaint);
            } else {
                // text content, center !
                FontMetrics fm = mPaint.getFontMetrics();
                float height = fm.bottom - fm.top;
                float width = mPaint.measureText(mText);
                canvas.drawText(mText, leftSpace + (mWidth - leftSpace) / 2, mHeight - (mHeight - height) / 2 - fm.bottom, mPaint);
            }
        }
    }

    private static final class TextDragShadowBuilder extends SOSDragShadowBuilder{
        public TextDragShadowBuilder(Context context, String text){
            super(context, text, null, null,
                    context.getResources().getDimensionPixelSize(R.dimen.drag_layout_maxwidth),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_layout_minwidth),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_background_padding),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_background_padding),
                    0);
        }
    }

    private static class LinkDragShadowBuilder extends SOSDragShadowBuilder{
        public LinkDragShadowBuilder(Context context, String link){
            super(context, link, context.getResources().getDrawable(R.drawable.link_icon), null,
                    context.getResources().getDimensionPixelSize(R.dimen.drag_layout_maxwidth),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_layout_minwidth),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_background_padding),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_background_padding),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_link_icon_padding));
        }
    }

    private static final class FileDragShadowBuilder extends SOSDragShadowBuilder{
        public FileDragShadowBuilder(Context context, String filepath, Bitmap fileIcon){
            super(context, filepath, null, fileIcon,
                    context.getResources().getDimensionPixelSize(R.dimen.drag_layout_maxwidth),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_layout_minwidth),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_background_padding),
                    context.getResources().getDimensionPixelSize(R.dimen.drag_background_padding),
                    0);
        }
    }

    private static class ImageDragShadowBuilder extends BaseDragShadowBuilder {
        private Bitmap mBackground;
        private Bitmap mContent;

        private int mSize;

        public ImageDragShadowBuilder(Context context, Bitmap content) {
            this(context, null, content);
        }

        public ImageDragShadowBuilder(Context context, Bitmap background, Bitmap content) {
            super(context);
            mContent = content;
            if (background != null) {
                mBackground = background;
            } else {
                mBackground = BitmapFactory.decodeResource(context.getResources(), R.drawable.drag_image_background);
            }

            mContent = content;
            mSize = context.getResources().getDimensionPixelSize(R.dimen.drag_image_size);

            if (content.getWidth() > content.getHeight()) {
                Bitmap bm = Bitmap.createBitmap(mContent, (content.getWidth() - content.getHeight()) / 2, 0, content.getHeight(), content.getHeight());
                mContent.recycle();
                mContent = bm;
            } else if (content.getWidth() < content.getHeight()) {
                Bitmap bm = Bitmap.createBitmap(mContent, 0, (content.getHeight() - content.getWidth()) / 2, content.getWidth(), content.getWidth());
                mContent.recycle();
                mContent = bm;
            }

            if (mContent.getWidth() != mSize) {
                Bitmap bm = Bitmap.createScaledBitmap(mContent, mSize, mSize, false);
                mContent.recycle();
                mContent = bm;
            }
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize,
                Point shadowTouchPoint) {
            shadowSize.set(mBackground.getWidth(), mBackground.getHeight());
            shadowTouchPoint.set(shadowSize.x / 2, shadowSize.y / 2 + mTouchPointOffset);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            // draw image content
            int left = (canvas.getWidth() - mSize) / 2;
            int top = (canvas.getHeight() - mSize) / 2;
            canvas.drawBitmap(mContent, left, top, null);

            // draw background
            canvas.drawBitmap(mBackground, 0, 0, null);
        }
    }

    private static class CardDragShadowBuilder extends BaseDragShadowBuilder {
        private Bitmap mCardBackground;
        private Bitmap mContent;
        private Bitmap mAvatar;

        private float mPaddingAvatar;

        public CardDragShadowBuilder(Context context, Bitmap background, Bitmap content, Bitmap avatar) {
            super(context);
            if (background != null) {
                mCardBackground = background;
            } else {
                mCardBackground = BitmapFactory.decodeResource(context.getResources(), R.drawable.drag_card_bg);
            }
            if (content != null) {
                mContent = content;
            } else {
                mContent = BitmapFactory.decodeResource(context.getResources(), R.drawable.drag_card_content);
            }
            mAvatar = avatar;
            mPaddingAvatar = context.getResources().getDimensionPixelSize(R.dimen.drag_card_padding_avatar);
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize,
                Point shadowTouchPoint) {
            shadowSize.set(mCardBackground.getWidth(), mCardBackground.getHeight());
            shadowTouchPoint.set(shadowSize.x / 2, shadowSize.y / 2 + mTouchPointOffset);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            canvas.drawBitmap(mCardBackground, 0, 0, null);

            //draw content
            int left = (canvas.getWidth() - mContent.getWidth()) / 2;
            int top = (canvas.getHeight() - mContent.getHeight()) / 2;
            canvas.drawBitmap(mContent, left, top, null);

            //draw avatar
            if(mAvatar != null){
                left += mPaddingAvatar;
                top = (canvas.getHeight() - mAvatar.getHeight()) / 2;
                canvas.drawBitmap(mAvatar, left, top, null);
            }
        }
    }
}
