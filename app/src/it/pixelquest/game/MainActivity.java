package it.pixelquest.game;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import it.pixelquest.core.Input;

/** Single activity hosting the game view. Landscape, fullscreen, immersive. */
public class MainActivity extends Activity {
    private GameView view;
    private boolean kLeft, kRight, kUp, kDown, kWalk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GameView(this);
        setContentView(view);
        hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        view.resume();
    }

    @Override
    protected void onPause() {
        view.pause();
        super.onPause();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (view.game() != null) view.game().onBack();
    }

    void exitGame() {
        runOnUiThread(new Runnable() {
            public void run() {
                finish();
            }
        });
    }

    // ------------------------------------------------------- keyboard / gamepad
    private void updateAxes() {
        if (view.game() == null) return;
        Input in = view.game().input;
        float s = kWalk ? 0.6f : 1f;
        in.keyAx = ((kRight ? 1 : 0) - (kLeft ? 1 : 0)) * s;
        in.keyAy = (kDown ? 1 : 0) - (kUp ? 1 : 0);
    }

    private boolean key(int code, boolean down) {
        if (view.game() == null) return false;
        Input in = view.game().input;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_A: kLeft = down; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_D: kRight = down; break;
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_W: kUp = down; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_S: kDown = down; break;
            case KeyEvent.KEYCODE_SHIFT_LEFT: case KeyEvent.KEYCODE_SHIFT_RIGHT: kWalk = down; break;
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_SPACE: case KeyEvent.KEYCODE_Z:
            case KeyEvent.KEYCODE_K: case KeyEvent.KEYCODE_DPAD_CENTER:
                in.keyA = down;
                return true;
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_X: case KeyEvent.KEYCODE_J:
                in.keyB = down;
                return true;
            case KeyEvent.KEYCODE_BUTTON_X: case KeyEvent.KEYCODE_BUTTON_Y: case KeyEvent.KEYCODE_C: case KeyEvent.KEYCODE_L:
                in.keyC = down;
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1: case KeyEvent.KEYCODE_BUTTON_L1: case KeyEvent.KEYCODE_V:
                in.keyS = down;
                return true;
            case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_P:
                in.keyPause = down;
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                if (down) view.game().onBack();
                return true;
            default:
                return false;
        }
        updateAxes();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event);
        if (event.getRepeatCount() > 0 && key(keyCode, true)) return true;
        return key(keyCode, true) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyUp(keyCode, event);
        return key(keyCode, false) || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (view.game() != null && (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && e.getAction() == MotionEvent.ACTION_MOVE) {
            float x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y);
            float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(hx) > 0.5f) x = hx;
            if (Math.abs(hy) > 0.5f) y = hy;
            Input in = view.game().input;
            in.keyAx = Math.abs(x) < 0.2f ? 0 : x;
            in.keyAy = Math.abs(y) < 0.4f ? 0 : y;
            return true;
        }
        return super.onGenericMotionEvent(e);
    }
}
