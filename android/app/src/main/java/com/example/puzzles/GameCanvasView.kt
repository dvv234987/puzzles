package com.example.puzzles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Equivalent of GameView.m: owns the off-screen bitmap the puzzle draws into,
 * implements the drawing callbacks that android_frontend.c forwards (the n*
 * methods below are called from native code), and turns touches into
 * midend key events.
 */
class GameCanvasView(context: Context) : View(context) {

    interface Host {
        fun statusText(s: String)
        fun netCentreMode(): Boolean
        fun netShiftMode(): Boolean
    }

    var host: Host? = null
    var handle = 0L
    var gameName = ""
    /** false while a new game is being generated (iOS sets me = NULL) */
    var ready = false

    private var bitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    val gameRect = Rect()
    var colours = IntArray(0)
        private set

    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
    private val fillAA = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val textPaint = Paint().apply { isAntiAlias = true; typeface = Typeface.SANS_SERIF }
    private val handler = Handler(Looper.getMainLooper())
    private var clipping = false

    private class Blitter(val w: Int, val h: Int) {
        var bmp: Bitmap? = null
        var ox = 0; var oy = 0     // origin passed to save
        var sx = 0; var sy = 0     // origin of the (clipped) saved image
    }
    private val blitters = HashMap<Int, Blitter>()
    private var nextBlitter = 1

    private val codes = Native.buttonCodes()
    private val buttonDown = intArrayOf(codes[0], codes[2], codes[1])
    private val buttonDrag = intArrayOf(codes[3], codes[5], codes[4])
    private val buttonUp = intArrayOf(codes[6], codes[8], codes[7])

    fun loadColours() { colours = Native.colours(handle) }

    // ---------------------------------------------------------------- layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    /** Equivalent of layoutSubviews: fit the puzzle in the view and redraw. */
    fun relayout() {
        if (!ready || handle == 0L || width == 0 || height == 0) return
        val sz = Native.size(handle, width, height)
        val w = max(1, sz[0]); val h = max(1, sz[1])
        val left = (width - w) / 2
        val top = (height - h) / 2
        gameRect.set(left, top, left + w, top + h)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            bmpCanvas = Canvas(it)
            if (colours.isNotEmpty()) it.eraseColor(colours[0])
        }
        clipping = false
        Native.forceRedraw(handle)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bm = bitmap ?: return
        if (!ready) return
        canvas.drawBitmap(bm, gameRect.left.toFloat(), gameRect.top.toFloat(), null)
    }

    // --------------------------------------------- drawing callbacks (native)

    fun nDrawText(x: Int, y: Int, fonttype: Int, fontsize: Int, align: Int, colour: Int, text: String) {
        val c = bmpCanvas ?: return
        textPaint.textSize = fontsize.toFloat()
        textPaint.color = colours[colour]
        val width = textPaint.measureText(text)
        var tx = x.toFloat()
        var ty = y.toFloat()
        when (align and 3) {          // ALIGN_HLEFT=0, HCENTRE=1, HRIGHT=2
            1 -> tx -= width / 2
            2 -> tx -= width
        }
        if ((align and 0x100) != 0) ty += fontsize * 0.4f   // ALIGN_VCENTRE
        c.drawText(text, tx, ty, textPaint)
    }

    fun nFillRect(x: Int, y: Int, w: Int, h: Int, colour: Int) {
        val c = bmpCanvas ?: return
        fill.color = colours[colour]
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fill)
    }

    fun nDrawLine(x1: Int, y1: Int, x2: Int, y2: Int, colour: Int) {
        val c = bmpCanvas ?: return
        stroke.color = colours[colour]
        c.drawLine(x1 + 0.5f, y1 + 0.5f, x2 + 0.5f, y2 + 0.5f, stroke)
    }

    fun nDrawPolygon(coords: IntArray, fillColour: Int, outlineColour: Int) {
        val c = bmpCanvas ?: return
        val n = coords.size / 2
        if (n == 0) return
        val path = Path()
        path.moveTo(coords[0] + 0.5f, coords[1] + 0.5f)
        for (i in 1 until n) path.lineTo(coords[i * 2] + 0.5f, coords[i * 2 + 1] + 0.5f)
        path.close()
        if (fillColour >= 0) {
            fillAA.color = colours[fillColour]
            c.drawPath(path, fillAA)
        }
        stroke.color = colours[outlineColour]
        c.drawPath(path, stroke)
    }

    fun nDrawCircle(cx: Int, cy: Int, radius: Int, fillColour: Int, outlineColour: Int) {
        val c = bmpCanvas ?: return
        // iOS: ellipse in rect (cx-r+1, cy-r+1, 2r-1, 2r-1)
        val r = max(0f, radius - 0.5f)
        val fx = cx + 0.5f
        val fy = cy + 0.5f
        if (fillColour >= 0) {
            fillAA.color = colours[fillColour]
            c.drawCircle(fx, fy, r, fillAA)
        }
        stroke.color = colours[outlineColour]
        c.drawCircle(fx, fy, r, stroke)
    }

    fun nUpdate(x: Int, y: Int, w: Int, h: Int) { invalidate() }

    fun nClip(x: Int, y: Int, w: Int, h: Int) {
        val c = bmpCanvas ?: return
        if (!clipping) c.save()
        c.clipRect(x, y, x + w, y + h)
        clipping = true
    }

    fun nUnclip() {
        val c = bmpCanvas ?: return
        if (clipping) c.restore()
        clipping = false
    }

    fun nStatus(s: String) {
        val h = host ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) h.statusText(s)
        else handler.post { h.statusText(s) }
    }

    fun nBlitterNew(w: Int, h: Int): Int {
        val id = nextBlitter++
        blitters[id] = Blitter(w, h)
        return id
    }

    fun nBlitterFree(id: Int) {
        blitters.remove(id)?.bmp?.recycle()
    }

    fun nBlitterSave(id: Int, x: Int, y: Int) {
        val b = blitters[id] ?: return
        val bm = bitmap ?: return
        b.ox = x; b.oy = y
        b.bmp?.recycle(); b.bmp = null
        val sx = max(0, x); val sy = max(0, y)
        val ex = min(bm.width, x + b.w); val ey = min(bm.height, y + b.h)
        if (ex > sx && ey > sy) {
            b.bmp = Bitmap.createBitmap(bm, sx, sy, ex - sx, ey - sy)
            b.sx = sx; b.sy = sy
        }
    }

    fun nBlitterLoad(id: Int, x: Int, y: Int) {
        val b = blitters[id] ?: return
        val c = bmpCanvas ?: return
        val img = b.bmp ?: return
        var dx = x; var dy = y
        if (x == -1 && y == -1) { dx = b.ox; dy = b.oy }     // BLITTER_FROMSAVED
        c.drawBitmap(img, (dx + b.sx - b.ox).toFloat(), (dy + b.sy - b.oy).toFloat(), null)
    }

    // ------------------------------------------------------------------ timer

    @Volatile private var timerOn = false
    private val tick = object : Runnable {
        override fun run() {
            if (ready && handle != 0L) Native.timer(handle, 0.02f)
            if (timerOn) handler.postDelayed(this, 20)
        }
    }

    fun nActivateTimer() {
        handler.post {
            if (!timerOn) { timerOn = true; handler.postDelayed(tick, 20) }
        }
    }

    fun nDeactivateTimer() {
        handler.post { timerOn = false; handler.removeCallbacks(tick) }
    }

    fun release() {
        timerOn = false
        handler.removeCallbacksAndMessages(null)
        bitmap?.recycle(); bitmap = null; bmpCanvas = null
        blitters.values.forEach { it.bmp?.recycle() }
        blitters.clear()
    }

    // ------------------------------------------------------------------ touch

    private var touchState = 0          // 0 idle, 1 down (undecided), 2 button sent
    private var touchButton = 0         // index into buttonDown: 0 left, 1 right, 2 middle
    private var touchXpixels = 0
    private var touchYpixels = 0
    private var touchXstart = 0f
    private var touchYstart = 0f
    private val touchTimer = Runnable { handleTouchTimer() }

    private fun adjustDrag(p: IntArray) {
        if (gameName == "Untangle") {
            val ts = Native.tileSize(handle)
            p[0] = max(ts / 8, p[0])
            p[0] = min(gameRect.width() - ts / 8 - 1, p[0])
            p[1] = max(ts / 8, p[1])
            p[1] = min(gameRect.height() - ts / 8 - 1, p[1])
        }
    }

    private fun handleTouchTimer() {
        val h = host ?: return
        if (h.netCentreMode() || h.netShiftMode()) return
        if (touchState == 1) {
            touchButton = if (gameName == "Net") 2 else 1
            Native.processKey(handle, touchXpixels, touchYpixels, buttonDown[touchButton])
            touchState = 2
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!ready || handle == 0L) return false
        val h = host ?: return false
        val px = e.x - gameRect.left
        val py = e.y - gameRect.top
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.postDelayed(touchTimer, 500)
                touchState = 1
                touchXstart = px; touchYstart = py
                val p = intArrayOf(px.toInt(), py.toInt())
                adjustDrag(p)
                touchXpixels = p[0]; touchYpixels = p[1]
                touchButton = 0
                if (h.netCentreMode()) Native.processKey(handle, touchXpixels, touchYpixels, 0x03)
            }
            MotionEvent.ACTION_MOVE -> {
                val p = intArrayOf(
                    min(gameRect.width() - 1, max(0, px.toInt())),
                    min(gameRect.height() - 1, max(0, py.toInt())))
                adjustDrag(p)
                val xpixels = p[0]; val ypixels = p[1]
                if (h.netCentreMode()) {
                    Native.processKey(handle, xpixels, ypixels, 0x03)
                } else if (h.netShiftMode()) {
                    val ts = Native.tileSize(handle)
                    while (touchXpixels <= xpixels - ts) {
                        Native.processKey(handle, -1, -1, codes[13] or codes[11]); touchXpixels += ts
                    }
                    while (touchXpixels >= xpixels + ts) {
                        Native.processKey(handle, -1, -1, codes[13] or codes[12]); touchXpixels -= ts
                    }
                    while (touchYpixels <= ypixels - ts) {
                        Native.processKey(handle, -1, -1, codes[13] or codes[9]); touchYpixels += ts
                    }
                    while (touchYpixels >= ypixels + ts) {
                        Native.processKey(handle, -1, -1, codes[13] or codes[10]); touchYpixels -= ts
                    }
                } else {
                    if (touchState == 1) {
                        val thr = context.dpf(10f)
                        if (abs(px - touchXstart) >= thr || abs(py - touchYstart) >= thr) {
                            handler.removeCallbacks(touchTimer)
                            Native.processKey(handle, touchXpixels, touchYpixels, buttonDown[touchButton])
                            touchState = 2
                        }
                    }
                    if (touchState == 2) {
                        Native.processKey(handle, xpixels, ypixels, buttonDrag[touchButton])
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val p = intArrayOf(
                    min(gameRect.width() - 1, max(0, px.toInt())),
                    min(gameRect.height() - 1, max(0, py.toInt())))
                adjustDrag(p)
                if (!(h.netCentreMode() || h.netShiftMode())) {
                    if (touchState == 1) {
                        Native.processKey(handle, touchXpixels, touchYpixels, buttonDown[touchButton])
                    }
                    Native.processKey(handle, p[0], p[1], buttonUp[touchButton])
                }
                touchState = 0
                handler.removeCallbacks(touchTimer)
            }
            MotionEvent.ACTION_CANCEL -> {
                touchState = 0
                handler.removeCallbacks(touchTimer)
            }
        }
        return true
    }
}
