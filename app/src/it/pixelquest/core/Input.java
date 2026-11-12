package it.pixelquest.core;

/** Logical controller state for one frame (merged from touch controls and keyboard/gamepad). */
public final class Input {
    public float ax, ay;
    public boolean a, b, c, s, pause;
    private boolean pa, pb, pc, ps, ppause, pl, pr, pu, pd;
    public boolean aP, bP, cP, sP, pauseP, leftP, rightP, upP, downP;

    // keyboard / gamepad state written by the platform layer (any thread)
    public volatile float keyAx, keyAy;
    public volatile boolean keyA, keyB, keyC, keyS, keyPause;
    public boolean backP;

    public boolean left() {
        return ax < -0.3f;
    }

    public boolean right() {
        return ax > 0.3f;
    }

    public boolean up() {
        return ay < -0.5f;
    }

    public boolean down() {
        return ay > 0.5f;
    }

    public int dir() {
        return ax < -0.2f ? -1 : ax > 0.2f ? 1 : 0;
    }

    public boolean runHeld() {
        return Math.abs(ax) >= TouchControls.RUN_AT;
    }

    /** Merge sources and compute edge-triggered flags. */
    public void latch(TouchControls tc) {
        float tax = tc != null ? tc.ax : 0, tay = tc != null ? tc.ay : 0;
        ax = keyAx != 0 ? keyAx : tax;
        ay = keyAy != 0 ? keyAy : tay;
        a = keyA || (tc != null && tc.a);
        b = keyB || (tc != null && tc.b);
        c = keyC || (tc != null && tc.c);
        s = keyS || (tc != null && tc.s);
        pause = keyPause || (tc != null && tc.pause);
        // a new finger on a button is always a press, even if another touch was already holding it
        aP = a && !pa || tc != null && tc.aNew;
        bP = b && !pb || tc != null && tc.bNew;
        cP = c && !pc || tc != null && tc.cNew;
        sP = s && !ps || tc != null && tc.sNew;
        pauseP = pause && !ppause;
        leftP = left() && !pl;
        rightP = right() && !pr;
        upP = up() && !pu;
        downP = down() && !pd;
        pa = a;
        pb = b;
        pc = c;
        ps = s;
        ppause = pause;
        pl = left();
        pr = right();
        pu = up();
        pd = down();
    }

    /** Swallow currently held buttons so they don't trigger again on the next screen. */
    public void consume() {
        aP = bP = cP = sP = pauseP = leftP = rightP = upP = downP = backP = false;
    }
}
