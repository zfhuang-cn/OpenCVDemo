package com.ant.idcard.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.ant.idcard.R;

import java.util.Collection;
import java.util.HashSet;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 * @date: 2021/6/8
 */
public class ViewfinderView extends View {
    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY = 10L;
    private static final int OPAQUE = 0xFF;
    private static final int CORNER_RECT_WIDTH = 8;  //扫描区边角的宽
    private static final int CORNER_RECT_HEIGHT = 40; //扫描区边角的高
    private static final int SCANNER_LINE_MOVE_DISTANCE = 5;  //扫描线移动距离
    private static final int SCANNER_LINE_HEIGHT = 10;  //扫描线宽度

    private Paint paint;
    private Bitmap resultBitmap;
    //模糊区域颜色
    private int maskColor;
    private int resultColor;
    //扫描区域边框颜色
    private int frameColor;
    //扫描线颜色
    private int laserColor;
    //四角颜色
    private int cornerColor;
    //扫描点的颜色
    private int scannerAlpha;
    //扫描区域提示文本
    private String labelText;
    //扫描区域提示文本颜色
    private int labelTextColor;
    private float labelTextSize;

    public static int scannerStart = 0;
    public static int scannerEnd = 0;

    private Rect framingRect;

    public ViewfinderView(Context context) {
        super(context);
    }

    public ViewfinderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        //初始化自定义属性信息
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ViewfinderView);
        laserColor = array.getColor(R.styleable.ViewfinderView_laser_color, 0x00FF00);
        cornerColor = array.getColor(R.styleable.ViewfinderView_corner_color, 0x00FF00);
        frameColor = array.getColor(R.styleable.ViewfinderView_frame_color, 0xFFFFFF);
        maskColor = array.getColor(R.styleable.ViewfinderView_mask_color, 0x60000000);
        resultColor = array.getColor(R.styleable.ViewfinderView_result_color, 0xB0000000);
        labelTextColor = array.getColor(R.styleable.ViewfinderView_label_text_color, 0x90FFFFFF);
        labelText = array.getString(R.styleable.ViewfinderView_label_text);
        labelTextSize = array.getFloat(R.styleable.ViewfinderView_label_text_size, 36f);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint();
        paint.setAntiAlias(true);
        scannerAlpha = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Rect frame = getFramingRect();
        if (frame == null) {
            return;
        }
        if(scannerStart == 0 || scannerEnd == 0) {
            scannerStart = frame.top;
            scannerEnd = frame.bottom;
        }

        int width = canvas.getWidth();
        int height = canvas.getHeight();
        // Draw the exterior (i.e. outside the framing rect) darkened
        drawExterior(canvas, frame, width, height);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(OPAQUE);
//            resultBitmap.getWidth()
            canvas.drawBitmap(resultBitmap, 0, 0, paint);
        } else {
            // Draw a two pixel solid black border inside the framing rect
            drawFrame(canvas, frame);
            // 绘制边角
            drawCorner(canvas, frame);
            //绘制提示信息
            drawTextInfo(canvas, frame);
            // Draw a red "laser scanner" line through the middle to show decoding is active
            drawLaserScanner(canvas, frame);

            //指定重绘区域，该方法会在子线程中执行
            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);
        }
    }

    private Rect getFramingRect() {
        if (framingRect == null) {
            //修改之后
            int width = getWidth() * 9 / 10;
            int height = width * 540 / 856;
            int leftOffset = (getWidth() - width) / 2;
            int topOffset = (getHeight() - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
        }
        return framingRect;
    }

    //绘制文本
    private void drawTextInfo(Canvas canvas, Rect frame) {
        paint.setColor(labelTextColor);
        paint.setTextSize(labelTextSize);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(labelText, frame.left + frame.width() / 2, frame.top +frame.height()/2 , paint);
    }

    //绘制边角
    private void drawCorner(Canvas canvas, Rect frame) {
        paint.setColor(cornerColor);
        //左上
        canvas.drawRect(frame.left, frame.top, frame.left + CORNER_RECT_WIDTH,frame.top + CORNER_RECT_HEIGHT, paint);
        canvas.drawRect(frame.left, frame.top, frame.left + CORNER_RECT_HEIGHT,frame.top + CORNER_RECT_WIDTH, paint);
        //右上
        canvas.drawRect(frame.right - CORNER_RECT_WIDTH, frame.top, frame.right,frame.top + CORNER_RECT_HEIGHT, paint);
        canvas.drawRect(frame.right - CORNER_RECT_HEIGHT, frame.top, frame.right,frame.top + CORNER_RECT_WIDTH, paint);
        //左下
        canvas.drawRect(frame.left, frame.bottom - CORNER_RECT_WIDTH,frame.left + CORNER_RECT_HEIGHT, frame.bottom, paint);
        canvas.drawRect(frame.left, frame.bottom - CORNER_RECT_HEIGHT,frame.left + CORNER_RECT_WIDTH, frame.bottom, paint);
        //右下
        canvas.drawRect(frame.right - CORNER_RECT_WIDTH, frame.bottom - CORNER_RECT_HEIGHT,frame.right, frame.bottom, paint);
        canvas.drawRect(frame.right - CORNER_RECT_HEIGHT, frame.bottom - CORNER_RECT_WIDTH,frame.right, frame.bottom, paint);
    }

    // 绘制扫描区边框 Draw a two pixel solid black border inside the framing rect
    private void drawFrame(Canvas canvas, Rect frame) {
        paint.setColor(frameColor);
        canvas.drawRect(frame.left, frame.top, frame.right + 1, frame.top + 2, paint);
        canvas.drawRect(frame.left, frame.top + 2, frame.left + 2, frame.bottom - 1, paint);
        canvas.drawRect(frame.right - 1, frame.top, frame.right + 1, frame.bottom - 1, paint);
        canvas.drawRect(frame.left, frame.bottom - 1, frame.right + 1, frame.bottom + 1, paint);
    }

    //绘制扫描线
    private void drawLaserScanner(Canvas canvas, Rect frame) {
        paint.setColor(laserColor);
        //扫描线闪烁效果
    paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
    scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
    int middle = frame.height() / 2 + frame.top;
    canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);
        //线性渐变
        LinearGradient linearGradient = new LinearGradient(
                frame.left, scannerStart,
                frame.left, scannerStart + SCANNER_LINE_HEIGHT,
                shadeColor(laserColor),
                laserColor,
                Shader.TileMode.MIRROR);

        RadialGradient radialGradient = new RadialGradient(
                (float) (frame.left + frame.width() / 2),
                (float) (scannerStart + SCANNER_LINE_HEIGHT / 2),
                360f,
                laserColor,
                shadeColor(laserColor),
                Shader.TileMode.MIRROR);

        paint.setShader(radialGradient);
        if (scannerStart <= scannerEnd) {
            //矩形
      canvas.drawRect(frame.left, scannerStart, frame.right, scannerStart +  SCANNER_LINE_HEIGHT, paint);
            //椭圆
            RectF rectF = new RectF(frame.left + 2 * SCANNER_LINE_HEIGHT, scannerStart,
                    frame.right - 2 * SCANNER_LINE_HEIGHT, scannerStart + SCANNER_LINE_HEIGHT);
            canvas.drawOval(rectF, paint);
            scannerStart += SCANNER_LINE_MOVE_DISTANCE;
        } else {
            scannerStart = frame.top;
        }
        paint.setShader(null);
    }

    //处理颜色模糊
    public int shadeColor(int color) {
        String hax = Integer.toHexString(color);
        String result = "20" + hax.substring(2);
        return Integer.valueOf(result, 16);
    }

    // 绘制模糊区域 Draw the exterior (i.e. outside the framing rect) darkened
    private void drawExterior(Canvas canvas, Rect frame, int width, int height) {
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }
}