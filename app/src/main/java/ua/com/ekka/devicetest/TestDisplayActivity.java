package ua.com.ekka.devicetest;

import android.annotation.TargetApi;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.log4j.Logger;

import ua.com.ekka.devicetest.log.Log4jHelper;
import ua.com.ekka.devicetest.su.SuCommandsHelper;

public class TestDisplayActivity extends AppCompatActivity {

    private static final String TAG = TestDisplayActivity.class.getSimpleName();
    private Logger logger = Log4jHelper.getLogger(TAG);

    private ImageView imageViewTestDisplay;
    private int imageViewTestDisplayClickCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.info("onCreate()");
        setContentView(R.layout.activity_display);

        getSupportActionBar().hide();
        SuCommandsHelper.executeCmd(SuCommandsHelper.CMD_SET_IMMERSIVE_MODE_ON, 0);
        SuCommandsHelper.executeCmd(SuCommandsHelper.CMD_USER_SETUP_COMPLETE_0, 0);

        imageViewTestDisplay = findViewById(R.id.imageview_test_display);
        imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.white));
        imageViewTestDisplayClickCounter = 0;
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

    public void onImageViewTestDisplayClick(View view) {
        imageViewTestDisplayClickCounter++;
        switch (imageViewTestDisplayClickCounter) {
            case 1:
                imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.grey50_test));
                break;
            case 2:
                imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.black));
                break;
            case 3:
                imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.red_test));
                break;
            case 4:
                imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.green_test));
                break;
            case 5:
                imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.blue_test));
                break;
            case 6:
                imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.cyan_test));
                break;
            case 7:
                imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.magenta_test));
                break;
            case 8:
                imageViewTestDisplay.setBackgroundColor(getResources().getColor(R.color.yellow_test));
                break;
            case 9:
                Drawable drawableTestDisplay = getResources().getDrawable(R.drawable.display_1280x800_test);
                if (MainActivity.sizeScreen.x == 1920)
                    drawableTestDisplay = getResources().getDrawable(R.drawable.display_1920x1080_test);
                imageViewTestDisplay.setImageDrawable(drawableTestDisplay);
                break;
            default:
                finish();
        }
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