package ua.com.ekka.devicetest;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.log4j.Logger;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;

public class TestTouchScreenActivity extends AppCompatActivity {

    private static final String TAG = TestTouchScreenActivity.class.getSimpleName();
    private Logger logger = Log4jHelper.getLogger(TAG);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.info("onCreate()");
        setContentView(R.layout.activity_touch_screen);

        getSupportActionBar().hide();
        SuCommandsHelper.executeCmd(SuCommandsHelper.CMD_SET_IMMERSIVE_MODE_ON, 0);
        SuCommandsHelper.executeCmd(SuCommandsHelper.CMD_USER_SETUP_COMPLETE_0, 0);

        ImageView imageViewTestTouchGrid = findViewById(R.id.imageview_test_touch_grid);
        Drawable drawableTestTouchGrid = getResources().getDrawable(R.drawable.grid_1280x800_80x80_black);
        if (MainActivity.sizeScreen.x == 1920)
            drawableTestTouchGrid = getResources().getDrawable(R.drawable.grid_1920x1080_60x60_black);
        imageViewTestTouchGrid.setImageDrawable(drawableTestTouchGrid);

        TextView textViewCursorCoordinates = findViewById(R.id.textview_cursor_coordinates);

        DrawingView drawingView = (DrawingView) findViewById(R.id.viewDraw);
        drawingView.setOnTouchListener((view, motionEvent) -> {
            int x = Math.round(motionEvent.getX());
            int y = Math.round(motionEvent.getY());
            textViewCursorCoordinates.setText(String.format("x: %d, y: %d", x, y));
            return false;
        });

        Button buttonClearDrawing = findViewById(R.id.button_clear_drawing);
        buttonClearDrawing.setOnClickListener(view -> {
            textViewCursorCoordinates.setText("");
            drawingView.mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            drawingView.invalidate();
        });
        Button buttonExitTouchTest = findViewById(R.id.button_exit_touch_test);
        buttonExitTouchTest.setOnClickListener(view -> {
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.info("onResume()");
        setImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.info("onDestroy()");
        SuCommandsHelper.executeCmd(SuCommandsHelper.CMD_SET_IMMERSIVE_MODE_OFF, 0);
        SuCommandsHelper.executeCmd(SuCommandsHelper.CMD_USER_SETUP_COMPLETE_1, 0);
    }

    /**
     * Hides navigation and status bar.
     */
    @TargetApi(19)
    private void setImmersiveMode() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                View decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                // Set the content to appear under the system bars so that the
                                // content doesn't resize when the system bars hide and show.
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                // Hide the nav bar and status bar
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN);
                decorView.invalidate();
            }
        });
    }
}

class DrawingView extends View {
    public Canvas mCanvas;
    private Bitmap mBitmap;
    private Paint mBitmapPaint;
    private Paint linePaint;
    private Path linePath;
    private Paint circlePaint;
    private Path circlePath;

    public DrawingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setDither(true);
        linePaint.setColor(Color.BLUE);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(6);
        linePath = new Path();

        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setColor(Color.RED);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeJoin(Paint.Join.MITER);
        circlePaint.setStrokeWidth(4f);
        circlePath = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.drawPath(linePath, linePaint);
        canvas.drawPath(circlePath, circlePaint);
    }

    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private void touchStart(float x, float y) {
        linePath.reset();
        linePath.moveTo(x, y);
        mX = x;
        mY = y;
        linePath.lineTo(mX, mY);

        circlePath.reset();
        circlePath.addCircle(mX, mY, 30, Path.Direction.CW);
    }

    private void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            linePath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;

            circlePath.reset();
            circlePath.addCircle(mX, mY, 30, Path.Direction.CW);
        }
    }

    private void touchUp() {
        linePath.lineTo(mX, mY);
        // commit the path to our offscreen
        mCanvas.drawPath(linePath, linePaint);
        // kill this so we don't double draw
        linePath.reset();

        circlePath.reset();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStart(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touchMove(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touchUp();
                invalidate();
                break;
        }
        return true;
    }
}