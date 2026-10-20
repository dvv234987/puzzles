package com.example.puzzles

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** Port of GameViewController + the UIKit parts of GameView.m. */
class GameFragment : ScreenFragment(), GameCanvasView.Host {

    companion object {
        fun newInstance(index: Int) = GameFragment().apply {
            arguments = Bundle().apply { putInt("index", index) }
        }
        /** The game currently on screen (used by the settings/type screens). */
        var current: GameFragment? = null
    }

    private var index = 0
    private var name = ""
    private var handle = 0L
    private var ready = false
    private var destroyed = false
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var canvas: GameCanvasView
    private lateinit var root: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var keyBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var genOverlay: FrameLayout
    private lateinit var genBox: LinearLayout
    private val keyButtons = HashMap<String, TextView>()
    private var netCentre = false
    private var netShift = false

    override val screenTitle: String get() = name
    override val helpFile: String get() = Native.gameHelpTopic(index) + ".html"

    private fun d(v: Int) = requireContext().dp(v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = requireArguments().getInt("index")
        name = Native.gameName(index)
        current = this
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        canvas = GameCanvasView(ctx).apply { host = this@GameFragment; gameName = name }

        statusView = TextView(ctx).apply {
            setTextColor(Color.BLACK); textSize = 14f; gravity = Gravity.CENTER_VERTICAL
            setPadding(d(4), 0, d(4), 0); visibility = View.GONE
        }
        keyBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.BAR); visibility = View.GONE
        }
        bottomBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.BAR)
            addView(UiExt.barButton(ctx, "Game") { doGameMenu() }, lpButton())
            addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            addView(UiExt.barButton(ctx, "Undo") { if (ready) Native.processKey(handle, -1, -1, 'u'.code) }, lpButton())
            addView(UiExt.barButton(ctx, "Redo") { if (ready) Native.processKey(handle, -1, -1, 'r'.code and 0x1F) }, lpButton())
            addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            addView(UiExt.barButton(ctx, "Type") { doType() }, lpButton())
        }
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(statusView, LinearLayout.LayoutParams(-1, d(20)))
            addView(UiExt.separator(ctx), LinearLayout.LayoutParams(-1, 1))
            addView(keyBar, LinearLayout.LayoutParams(-1, toolbarHeight()))
            addView(UiExt.separator(ctx), LinearLayout.LayoutParams(-1, 1))
            addView(bottomBar, LinearLayout.LayoutParams(-1, toolbarHeight()))
        }

        // "Generating puzzle" genOverlay: black box, shown after 250 ms
        val spinner = ProgressBar(ctx).apply { isIndeterminate = true }
        val label = TextView(ctx).apply { text = "Generating puzzle"; setTextColor(Color.WHITE); textSize = 16f }
        genBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK); setPadding(d(8), 0, d(8), 0); visibility = View.GONE
            addView(spinner, LinearLayout.LayoutParams(d(32), d(32)))
            addView(label, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = d(8) })
        }
        genOverlay = FrameLayout(ctx).apply {
            isClickable = true; visibility = View.GONE
            addView(genBox, FrameLayout.LayoutParams(d(200), d(50), Gravity.CENTER))
        }
        root = FrameLayout(ctx).apply {
            addView(column, FrameLayout.LayoutParams(-1, -1))
            addView(genOverlay, FrameLayout.LayoutParams(-1, -1))
        }

        if (handle == 0L) startMidend()
        return root
    }

    private fun lpButton() = LinearLayout.LayoutParams(-2, -1)

    private fun toolbarHeight(): Int {
        val c = resources.configuration
        val phone = c.smallestScreenWidthDp < 600
        return d(if (phone && c.orientation == Configuration.ORIENTATION_LANDSCAPE) 32 else 44)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::root.isInitialized) return
        (keyBar.layoutParams as LinearLayout.LayoutParams).height = toolbarHeight()
        (bottomBar.layoutParams as LinearLayout.LayoutParams).height = toolbarHeight()
        canvas.post { canvas.relayout() }
    }

    // ------------------------------------------------------------ midend setup

    private fun startMidend() {
        handle = Native.create(index, canvas, 4)
        canvas.handle = handle
        canvas.loadColours()
        root.setBackgroundColor(canvas.colours[0])
        val (saved, inprogress) = GameStore.load(name)
        if (saved != null) {
            val msg = Native.deserialise(handle, saved)
            if (msg != null) {
                alert(msg)
                startNewGame()
            } else if (!inprogress) {
                startNewGame()
            } else {
                finishNewGame()
            }
        } else {
            if (name == "Pattern" && resources.configuration.smallestScreenWidthDp < 600) {
                Native.applyGameId(handle, "5")
            }
            startNewGame()
        }
    }

    fun startNewGame() {
        ready = false
        canvas.ready = false
        genOverlay.visibility = View.VISIBLE
        genBox.visibility = View.GONE
        ui.postDelayed({ if (!ready && !destroyed) genBox.visibility = View.VISIBLE }, 250)
        val h = handle
        Thread {
            Native.newGame(h)
            ui.post {
                if (destroyed) {
                    Native.destroy(h)
                } else {
                    finishNewGame()
                }
            }
        }.start()
    }

    private fun finishNewGame() {
        ready = true
        canvas.ready = true
        genBox.visibility = View.GONE
        genOverlay.visibility = View.GONE
        layoutGame()
    }

    /** The "layoutSubviews" part that depends on the current game id. */
    private fun layoutGame() {
        statusView.visibility = if (Native.wantsStatusbar(handle)) View.VISIBLE else View.GONE
        if (statusView.visibility == View.GONE) statusView.text = ""
        buildKeyBar()
        canvas.post { canvas.relayout() }
    }

    private fun buildKeyBar() {
        keyBar.removeAllViews()
        keyButtons.clear()
        netCentre = false; netShift = false
        var mainCount = 9
        var extraCount = 0
        var labels: Array<String>? = null
        var extraLabels: Array<String>? = null
        val id = Native.gameId(handle)
        when (name) {
            "Filling" -> { extraLabels = arrayOf("0"); extraCount = 1 }
            "Keen" -> { mainCount = atoi(id); extraLabels = arrayOf("Marks"); extraCount = 1 }
            "Map" -> { mainCount = 1; labels = arrayOf("Labels") }
            "Net" -> {
                mainCount = 0
                extraLabels = arrayOf("Jumble", "Centre", "Shift")
                extraCount = if (id.contains("w:")) 3 else 2
            }
            "Solo" -> {
                val m = Regex("^(\\d+)x(\\d+)").find(id)
                if (m != null) mainCount = m.groupValues[1].toInt() * m.groupValues[2].toInt()
            }
            "Towers" -> { mainCount = atoi(id); extraLabels = arrayOf("Marks"); extraCount = 1 }
            "Undead" -> { mainCount = 4; labels = arrayOf("Ghost", "Vampire", "Zombie", "Erase") }
            "Unequal" -> { mainCount = atoi(id); extraLabels = arrayOf("Marks", "Hints"); extraCount = 2 }
            else -> {
                keyBar.visibility = View.GONE
                keyBarSeparatorVisible(false)
                return
            }
        }
        keyBar.visibility = View.VISIBLE
        keyBarSeparatorVisible(true)
        val total = mainCount + extraCount
        val phone = resources.configuration.smallestScreenWidthDp < 600
        val size = if (phone && total > 9) 15f else 17f
        val ctx = requireContext()
        keyBar.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        for (i in 0 until total) {
            val title = when {
                i < mainCount -> when {
                    labels != null -> labels[i]
                    i < 9 -> (1 + i).toString()
                    else -> ('a' + (i - 9)).toString()
                }
                else -> extraLabels!![i - mainCount]
            }
            val b = UiExt.barButton(ctx, title, size) { keyButton(title) }
            b.setPadding(d(if (total > 9) 4 else 8), 0, d(if (total > 9) 4 else 8), 0)
            keyButtons[title] = b
            keyBar.addView(b, LinearLayout.LayoutParams(-2, -1))
            keyBar.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        }
        keyBar.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 1f))
    }

    private fun keyBarSeparatorVisible(v: Boolean) {
        val col = keyBar.parent as LinearLayout
        val i = col.indexOfChild(keyBar)
        col.getChildAt(i - 1).visibility = if (v) View.VISIBLE else View.GONE
    }

    private fun atoi(s: String): Int {
        val m = Regex("^\\s*(\\d+)").find(s)
        return m?.groupValues?.get(1)?.toInt() ?: 0
    }

    private fun setToggle(title: String, on: Boolean) {
        keyButtons[title]?.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        if (title == "Centre") netCentre = on
        if (title == "Shift") netShift = on
    }

    private fun keyButton(title: String) {
        if (!ready) return
        if (name == "Net") {
            if (title == "Centre") {
                if (netCentre) setToggle("Centre", false) else { setToggle("Centre", true); setToggle("Shift", false) }
                return
            }
            if (title == "Shift") {
                if (netShift) setToggle("Shift", false) else { setToggle("Shift", true); setToggle("Centre", false) }
                return
            }
        }
        Native.processKey(handle, -1, -1, title[0].code)
    }

    // GameCanvasView.Host
    override fun statusText(s: String) { if (::statusView.isInitialized) statusView.text = s }
    override fun netCentreMode() = name == "Net" && netCentre
    override fun netShiftMode() = name == "Net" && netShift

    // ------------------------------------------------------------------ menu

    private fun doGameMenu() {
        if (!ready) return
        val items = mutableListOf("New game", "Specific game", "Specific Random Seed", "Restart")
        if (Native.gameCanSolve(index)) items.add("Solve")
        AlertDialog.Builder(requireContext())
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> startNewGame()
                    1 -> openIdConfig(Native.CFG_DESC)
                    2 -> openIdConfig(Native.CFG_SEED)
                    3 -> Native.restart(handle)
                    4 -> Native.solve(handle)?.let { alert(it, "Close") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openIdConfig(which: Int) {
        val cfg = Native.cfgOpen(handle, which)
        main.push(GameSettingsFragment.newInstance(which, cfg), null)
    }

    private fun doType() {
        if (!ready) return
        main.push(GameTypeFragment.newInstance(), null)
    }

    /** Called by the settings screen for "Specific game" / "Specific Random Seed". */
    fun applyGameId(id: String): Boolean {
        val msg = Native.applyGameId(handle, id)
        if (msg != null) { alert(msg, "Close"); return false }
        startNewGame()
        return true
    }

    val midend: Long get() = handle
    val gameIndex: Int get() = index

    // ------------------------------------------------------------- lifecycle

    fun saveGame() {
        if (!ready || handle == 0L) return
        val inprogress = Native.canUndo(handle) && Native.status(handle) == 0
        GameStore.save(name, Native.serialise(handle), inprogress)
    }

    override fun onPause() { super.onPause(); saveGame() }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) saveGame() else current = this
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyed = true
        saveGame()
        if (current === this) current = null
        if (::canvas.isInitialized) { canvas.ready = false; canvas.handle = 0L; canvas.release() }
        if (ready && handle != 0L) Native.destroy(handle)   // else freed when generation ends
        handle = 0L
    }
}
