package it.pixelquest.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import it.pixelquest.core.Audio;
import it.pixelquest.core.Game;
import it.pixelquest.core.Save;

/**
 * Hosts the platform-independent {@link Game}: runs a fixed 60 Hz update loop on its own thread,
 * blits the low-resolution framebuffer scaled up with nearest-neighbour filtering, forwards
 * multi-touch input and streams the software mixer to an AudioTrack.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private final MainActivity activity;
    private volatile Game game;
    private Thread loop, audioThread;
    private volatile boolean running, surfaceReady;
    private Bitmap frame;
    private final Paint paint = new Paint();
    private final Rect src = new Rect(), dst = new Rect();
    private volatile float scale = 1, offX, offY;
    private int surfW, surfH;
    private AudioTrack track;

    public GameView(Context ctx) {
        super(ctx);
        activity = (MainActivity) ctx;
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        paint.setDither(false);
    }

    Game game() {
        return game;
    }

    private Game ensureGame(int w, int h) {
        if (game == null) {
            final SharedPreferences prefs = getContext().getSharedPreferences("pixelquest", Context.MODE_PRIVATE);
            Save.Storage st = new Save.Storage() {
                public String get(String k) {
                    return prefs.getString(k, null);
                }

                public void put(String k, String v) {
                    prefs.edit().putString(k, v).apply();
                }
            };
            Game g = new Game(498, 224, st);
            if (w > 0 && h > 0) g.resizePhysical(w, h);
            game = g;
        }
        return game;
    }

    // ------------------------------------------------------------- lifecycle
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfW = width;
        surfH = height;
        surfaceReady = true;
        if (width > height) {
            Game g = ensureGame(width, height);
            synchronized (this) {
                g.resizePhysical(width, height);
                layout(g);
            }
        }
        startThreads();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopThreads();
    }

    void resume() {
        if (surfaceReady) startThreads();
    }

    void pause() {
        stopThreads();
        if (game != null) game.onPauseApp();
    }

    private void layout(Game g) {
        float s = Math.min(surfW / (float) g.W, surfH / (float) g.H);
        scale = s;
        int dw = Math.round(g.W * s), dh = Math.round(g.H * s);
        offX = (surfW - dw) / 2f;
        offY = (surfH - dh) / 2f;
        dst.set((int) offX, (int) offY, (int) offX + dw, (int) offY + dh);
        src.set(0, 0, g.W, g.H);
        if (frame == null || frame.getWidth() != g.W || frame.getHeight() != g.H)
            frame = Bitmap.createBitmap(g.W, g.H, Bitmap.Config.ARGB_8888);
    }

    private synchronized void startThreads() {
        if (running) return;
        if (surfW <= 0 || surfH <= 0) return;
        ensureGame(surfW, surfH);
        running = true;
        loop = new Thread(new Runnable() {
            public void run() {
                gameLoop();
            }
        }, "game-loop");
        loop.start();
        audioThread = new Thread(new Runnable() {
            public void run() {
                audioLoop();
            }
        }, "game-audio");
        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.start();
    }

    private void stopThreads() {
        Thread l, a;
        synchronized (this) {
            running = false;
            l = loop;
            a = audioThread;
            loop = null;
            audioThread = null;
        }
        try {
            if (l != null) l.join(1500);
            if (a != null) a.join(1500);
        } catch (InterruptedException ignored) {
        }
    }

    // ------------------------------------------------------------- game loop
    private void gameLoop() {
        final long step = 1_000_000_000L / 60;
        long last = System.nanoTime(), acc = 0;
        SurfaceHolder holder = getHolder();
        while (running) {
            long now = System.nanoTime();
            acc += now - last;
            last = now;
            if (acc > step * 5) acc = step * 5;
            Game g = game;
            boolean ticked = false;
            synchronized (this) {
                while (acc >= step) {
                    g.tick();
                    acc -= step;
                    ticked = true;
                }
                if (ticked || frame == null) {
                    if (frame == null || frame.getWidth() != g.W || frame.getHeight() != g.H) layout(g);
                    g.render();
                    frame.setPixels(g.gfx.px, 0, g.W, 0, 0, g.W, g.H);
                }
            }
            if (g.wantsExit) {
                g.wantsExit = false;
                g.save.store();
                activity.exitGame();
            }
            if (!ticked) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException ignored) {
                }
                continue;
            }
            Canvas c = null;
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    try {
                        c = holder.lockHardwareCanvas();
                    } catch (Throwable t) {
                        c = null;
                    }
                }
                if (c == null) c = holder.lockCanvas();
                if (c == null) continue;
                c.drawColor(Color.BLACK);
                c.drawBitmap(frame, src, dst, paint);
            } catch (Throwable t) {
                // surface went away mid-frame; try again next loop
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- audio
    @SuppressWarnings("deprecation")
    private void audioLoop() {
        int rate = Audio.RATE;
        int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufBytes = Math.max(min, 2048 * 2);
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufBytes, AudioTrack.MODE_STREAM);
            track.play();
        } catch (Throwable t) {
            track = null;
            return;
        }
        short[] buf = new short[512];
        while (running) {
            Game g = game;
            if (g == null) break;
            g.audio.render(buf, buf.length);
            int w = track.write(buf, 0, buf.length);
            if (w < 0) break;
        }
        try {
            track.pause();
            track.flush();
            track.release();
        } catch (Throwable ignored) {
        }
        track = null;
    }

    // ------------------------------------------------------------- touch
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        Game g = game;
        if (g == null) return true;
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        float s = scale;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                g.touch.down(e.getPointerId(idx), (e.getX(idx) - offX) / s, (e.getY(idx) - offY) / s);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++)
                    g.touch.move(e.getPointerId(i), (e.getX(i) - offX) / s, (e.getY(i) - offY) / s);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                g.touch.up(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                g.touch.cancelAll();
                break;
        }
        return true;
    }
}
