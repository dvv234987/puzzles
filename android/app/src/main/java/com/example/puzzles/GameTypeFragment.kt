package com.example.puzzles

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Row helpers shared by the grouped-table style screens. */
object Rows {
    fun row(ctx: android.content.Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(ctx.dp(16), 0, ctx.dp(12), 0)
        background = UiExt.selectableBg(ctx)?.let { sel ->
            android.graphics.drawable.LayerDrawable(arrayOf(Ui.solid(Color.WHITE), sel))
        } ?: Ui.solid(Color.WHITE)
    }

    fun label(ctx: android.content.Context, text: String): TextView = TextView(ctx).apply {
        this.text = text; textSize = 17f; setTextColor(Color.BLACK)
    }

    fun accessory(ctx: android.content.Context, text: String, color: Int): TextView = TextView(ctx).apply {
        this.text = text; textSize = 20f; setTextColor(color)
    }

    fun separator(ctx: android.content.Context): View = View(ctx).apply { setBackgroundColor(Ui.SEP) }

    fun group(ctx: android.content.Context): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, ctx.dp(20), 0, ctx.dp(20)) }
        val sv = ScrollView(ctx).apply { setBackgroundColor(Ui.GROUP_BG); addView(col, ViewGroup.LayoutParams(-1, -2)) }
        return Pair(sv, col)
    }

    fun header(ctx: android.content.Context, text: String): TextView = TextView(ctx).apply {
        this.text = text.uppercase(); textSize = 13f; setTextColor(Color.rgb(109, 109, 114))
        setPadding(ctx.dp(16), 0, ctx.dp(16), ctx.dp(6))
    }
}

/** Port of GameTypeController: list of presets plus "Custom". */
class GameTypeFragment : ScreenFragment() {
    companion object { fun newInstance() = GameTypeFragment() }

    override val screenTitle: String get() = "Game Type"
    override val helpFile: String? get() = GameFragment.current?.helpFile

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val game = GameFragment.current ?: return View(ctx)
        val h = game.midend
        val (sv, col) = Rows.group(ctx)
        val n = Native.numPresets(h)
        val which = Native.whichPreset(h)

        col.addView(Rows.separator(ctx), LinearLayout.LayoutParams(-1, 1))
        for (i in 0..n) {
            val row = Rows.row(ctx)
            val custom = i == n
            row.addView(Rows.label(ctx, if (custom) "Custom" else Native.presetName(h, i)),
                LinearLayout.LayoutParams(0, -2, 1f))
            if (custom) {
                if (which < 0) row.addView(Rows.accessory(ctx, "\u2713", Ui.BLUE))
                else row.addView(Rows.accessory(ctx, "\u203A", Color.rgb(199, 199, 204)))
            } else if (i == which) {
                row.addView(Rows.accessory(ctx, "\u2713", Ui.BLUE))
            }
            row.setOnClickListener {
                if (!custom) {
                    Native.setPreset(h, i)
                    game.startNewGame()
                    main.popToGame()
                } else {
                    val cfg = Native.cfgOpen(h, Native.CFG_SETTINGS)
                    main.push(GameSettingsFragment.newInstance(Native.CFG_SETTINGS, cfg), null)
                }
            }
            col.addView(row, LinearLayout.LayoutParams(-1, ctx.dp(44)))
            col.addView(Rows.separator(ctx), LinearLayout.LayoutParams(-1, 1))
        }
        return sv
    }
}
