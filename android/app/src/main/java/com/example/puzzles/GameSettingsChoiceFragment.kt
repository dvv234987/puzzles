package com.example.puzzles

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Port of GameSettingsChoiceController: pick one option, then pop. */
class GameSettingsChoiceFragment : ScreenFragment() {
    companion object {
        fun newInstance(title: String, index: Int, choices: ArrayList<String>, value: Int) =
            GameSettingsChoiceFragment().apply {
                arguments = Bundle().apply {
                    putString("title", title); putInt("index", index)
                    putStringArrayList("choices", choices); putInt("value", value)
                }
            }
    }

    override val screenTitle: String get() = requireArguments().getString("title") ?: ""
    override val helpFile: String? get() = GameFragment.current?.helpFile

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val a = requireArguments()
        val choices = a.getStringArrayList("choices") ?: arrayListOf()
        val value = a.getInt("value")
        val index = a.getInt("index")
        val (sv, col) = Rows.group(ctx)
        col.addView(Rows.separator(ctx), LinearLayout.LayoutParams(-1, 1))
        choices.forEachIndexed { i, text ->
            val row = Rows.row(ctx)
            row.addView(Rows.label(ctx, text), LinearLayout.LayoutParams(0, -2, 1f))
            if (i == value) row.addView(Rows.accessory(ctx, "\u2713", Ui.BLUE))
            row.setOnClickListener {
                parentFragmentManager.setFragmentResult("choice", Bundle().apply {
                    putInt("index", index); putInt("value", i)
                })
                parentFragmentManager.popBackStack()
            }
            col.addView(row, LinearLayout.LayoutParams(-1, ctx.dp(44)))
            col.addView(Rows.separator(ctx), LinearLayout.LayoutParams(-1, 1))
        }
        return sv
    }
}
