package com.example.puzzles

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Port of GameSettingsController. Used for the custom game type (CFG_SETTINGS),
 * "Specific game" (CFG_DESC) and "Specific Random Seed" (CFG_SEED).
 */
class GameSettingsFragment : ScreenFragment() {
    companion object {
        fun newInstance(type: Int, cfg: Long) = GameSettingsFragment().apply {
            arguments = Bundle().apply { putInt("type", type); putLong("cfg", cfg) }
        }
    }

    private var type = 0
    private var cfg = 0L
    private var title = ""
    private lateinit var content: LinearLayout

    override val screenTitle: String get() = title
    override val helpFile: String? get() = GameFragment.current?.helpFile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = requireArguments().getInt("type")
        cfg = requireArguments().getLong("cfg")
        title = Native.cfgTitle(cfg)
        parentFragmentManager.setFragmentResultListener("choice", this) { _, b ->
            Native.cfgSetI(cfg, b.getInt("index"), b.getInt("value"))
            rebuild()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val (sv, col) = Rows.group(ctx)
        content = col
        rebuild()
        return sv
    }

    private fun choicesFor(i: Int): List<String> {
        val s = Native.cfgSval(cfg, i) ?: return emptyList()
        if (s.isEmpty()) return emptyList()
        return s.substring(1).split(s[0])
    }

    private fun rebuild() {
        val ctx = requireContext()
        content.removeAllViews()
        val n = Native.cfgCount(cfg)
        if (type != Native.CFG_SETTINGS && n > 0) {
            content.addView(Rows.header(ctx, Native.cfgName(cfg, 0)), LinearLayout.LayoutParams(-1, -2))
        }
        content.addView(Rows.separator(ctx), LinearLayout.LayoutParams(-1, 1))
        for (i in 0 until n) {
            val row = Rows.row(ctx)
            if (type == Native.CFG_SETTINGS) {
                row.addView(Rows.label(ctx, Native.cfgName(cfg, i)), LinearLayout.LayoutParams(0, -2, 1f))
                when (Native.cfgType(cfg, i)) {
                    Native.C_STRING -> row.addView(edit(i, ctx.dp(80), Gravity.END), LinearLayout.LayoutParams(ctx.dp(80), -2))
                    Native.C_BOOLEAN -> row.addView(Switch(ctx).apply {
                        isChecked = Native.cfgIval(cfg, i) != 0
                        setOnCheckedChangeListener { _, on -> Native.cfgSetI(cfg, i, if (on) 1 else 0) }
                    })
                    Native.C_CHOICES -> {
                        val choices = choicesFor(i)
                        val v = Native.cfgIval(cfg, i)
                        row.addView(TextView(ctx).apply {
                            text = choices.getOrElse(v) { "" }; textSize = 17f
                            setTextColor(Color.rgb(142, 142, 147)); gravity = Gravity.END
                        })
                        row.addView(Rows.accessory(ctx, "\u203A", Color.rgb(199, 199, 204)).apply {
                            setPadding(ctx.dp(8), 0, 0, 0)
                        })
                        row.setOnClickListener {
                            main.push(GameSettingsChoiceFragment.newInstance(
                                Native.cfgName(cfg, i), i, ArrayList(choices), v), null)
                        }
                    }
                }
            } else {
                row.addView(edit(i, 0, Gravity.START), LinearLayout.LayoutParams(0, -2, 1f))
            }
            content.addView(row, LinearLayout.LayoutParams(-1, ctx.dp(44)))
            content.addView(Rows.separator(ctx), LinearLayout.LayoutParams(-1, 1))
        }

        // second section: Apply
        content.addView(View(ctx), LinearLayout.LayoutParams(-1, ctx.dp(35)))
        content.addView(Rows.separator(ctx), LinearLayout.LayoutParams(-1, 1))
        val apply = Rows.row(ctx).apply {
            gravity = Gravity.CENTER
            addView(Rows.label(ctx, "Apply"))
            setOnClickListener { doApply() }
        }
        content.addView(apply, LinearLayout.LayoutParams(-1, ctx.dp(44)))
        content.addView(Rows.separator(ctx), LinearLayout.LayoutParams(-1, 1))
    }

    private fun edit(i: Int, width: Int, gravity: Int): EditText {
        val ctx = requireContext()
        return EditText(ctx).apply {
            setText(Native.cfgSval(cfg, i) ?: "")
            textSize = 17f
            this.gravity = gravity or Gravity.CENTER_VERTICAL
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = null
            setPadding(0, 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { Native.cfgSetS(cfg, i, s.toString()) }
            })
        }
    }

    private fun doApply() {
        val game = GameFragment.current ?: return
        if (type == Native.CFG_SETTINGS) {
            val msg = Native.setConfig(game.midend, Native.CFG_SETTINGS, cfg)
            if (msg != null) {
                alert(msg, "Close")
            } else {
                game.startNewGame()
                main.popToGame()
            }
        } else {
            val id = Native.cfgSval(cfg, 0) ?: ""
            if (game.applyGameId(id)) parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cfg != 0L) { Native.cfgFree(cfg); cfg = 0L }
    }
}
