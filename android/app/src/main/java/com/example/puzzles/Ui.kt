package com.example.puzzles

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import java.io.File

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

object Ui {
    val BLUE = Color.rgb(0, 122, 255)
    val BAR = Color.rgb(247, 247, 247)
    val BAR_LINE = Color.rgb(178, 178, 178)
    val GROUP_BG = Color.rgb(239, 239, 244)
    val SEP = Color.rgb(200, 199, 204)

    fun rowBackground(): GradientDrawable = GradientDrawable().apply { setColor(Color.WHITE) }
    fun solid(c: Int) = ColorDrawable(c)
    fun View.click(f: () -> Unit) = setOnClickListener { f() }
}

/** Persistence equivalent to the iOS port's .save/.new files and "lastgame" default. */
object GameStore {
    val inProgress = HashSet<String>()
    private lateinit var dir: File
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        dir = ctx.filesDir
        prefs = ctx.getSharedPreferences("puzzles", Context.MODE_PRIVATE)
        inProgress.clear()
        dir.listFiles()?.forEach {
            if (it.name.endsWith(".save")) inProgress.add(it.name.removeSuffix(".save"))
        }
    }

    /** Returns (saved text or null, inprogress) */
    fun load(name: String): Pair<String?, Boolean> {
        val s = File(dir, "$name.save")
        if (s.exists()) return Pair(s.readText(), true)
        val n = File(dir, "$name.new")
        if (n.exists()) return Pair(n.readText(), false)
        return Pair(null, false)
    }

    fun save(name: String, state: String, inprogress: Boolean) {
        if (inprogress) inProgress.add(name) else inProgress.remove(name)
        val target = File(dir, "$name.${if (inprogress) "save" else "new"}")
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(state)
        tmp.renameTo(target)
        if (!inprogress) File(dir, "$name.save").delete()
    }

    var lastGame: String?
        get() = prefs.getString("lastgame", null)
        set(v) { prefs.edit().apply { if (v == null) remove("lastgame") else putString("lastgame", v) }.apply() }
}

object UiExt {
    fun selectableBg(ctx: Context): android.graphics.drawable.Drawable? {
        val tv = android.util.TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return if (tv.resourceId != 0) ctx.getDrawable(tv.resourceId) else null
    }

    fun barButton(ctx: Context, text: String, sizeSp: Float = 17f, onClick: () -> Unit): android.widget.TextView =
        android.widget.TextView(ctx).apply {
            this.text = text
            setTextColor(Ui.BLUE)
            textSize = sizeSp
            gravity = android.view.Gravity.CENTER
            setPadding(ctx.dp(12), 0, ctx.dp(12), 0)
            background = selectableBg(ctx)
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun separator(ctx: Context): View = View(ctx).apply { setBackgroundColor(Ui.BAR_LINE) }
}

fun androidx.fragment.app.Fragment.alert(msg: String, button: String = "OK") {
    androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle("Puzzles")
        .setMessage(msg)
        .setPositiveButton(button, null)
        .show()
}
