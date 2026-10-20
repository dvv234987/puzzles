package com.example.puzzles

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Port of GameListViewController: the grid of puzzles on a black background. */
class GameListFragment : ScreenFragment() {

    override val screenTitle: String get() = "Puzzles"
    override val helpFile: String get() = "help.html"

    private val descriptions = HashMap<String, String>()
    private var recycler: RecyclerView? = null
    private var tablet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireContext().assets.open("descriptions.tsv").bufferedReader().useLines { lines ->
            lines.forEach { l ->
                val p = l.split('\t')
                if (p.size == 2) descriptions[p[0]] = p[1]
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        tablet = resources.configuration.smallestScreenWidthDp >= 600
        val rv = RecyclerView(ctx)
        recycler = rv
        rv.setBackgroundColor(Color.BLACK)
        val lm = GridLayoutManager(ctx, 1)
        rv.layoutManager = lm
        rv.adapter = Adapter()
        val cellDp = if (tablet) 246 else 320
        rv.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
            val widthDp = (r - l) / resources.displayMetrics.density
            val span = maxOf(1, (widthDp / cellDp).toInt())
            if (lm.spanCount != span) lm.spanCount = span
        }
        rv.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.bottom = ctx.dp(10)
                if (lm.spanCount > 1) { outRect.left = ctx.dp(5); outRect.right = ctx.dp(5) }
            }
        })
        return rv
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // viewWillAppear
            GameStore.lastGame = null
            recycler?.adapter?.notifyDataSetChanged()
        }
    }

    private inner class Cell(val root: FrameLayout) : RecyclerView.ViewHolder(root) {
        val title = TextView(root.context)
        val icon = ImageView(root.context)
        val detail = TextView(root.context)
        val progress = ImageView(root.context).apply { setImageResource(R.drawable.inprogress) }

        init {
            val c = root.context
            root.setBackgroundColor(Color.WHITE)
            title.setTextColor(Color.BLACK)
            title.typeface = android.graphics.Typeface.DEFAULT_BOLD
            detail.setTextColor(Color.BLACK)
            detail.textSize = 14f
            if (tablet) {
                title.textSize = 16f; title.gravity = Gravity.CENTER
                root.addView(title, FrameLayout.LayoutParams(-1, c.dp(31)))
                root.addView(icon, FrameLayout.LayoutParams(c.dp(96), c.dp(96), Gravity.CENTER_HORIZONTAL).apply { topMargin = c.dp(31) })
                root.addView(detail, FrameLayout.LayoutParams(-1, c.dp(50)).apply {
                    topMargin = c.dp(31 + 96); leftMargin = c.dp(5); rightMargin = c.dp(5)
                })
                root.addView(progress, FrameLayout.LayoutParams(c.dp(40), c.dp(40), Gravity.END).apply {
                    topMargin = c.dp(50); rightMargin = c.dp(10)
                })
                root.layoutParams = RecyclerView.LayoutParams(-1, c.dp(31 + 96 + 50))
            } else {
                title.textSize = 20f
                root.addView(icon, FrameLayout.LayoutParams(c.dp(96), c.dp(96)).apply { leftMargin = c.dp(2); topMargin = c.dp(2) })
                root.addView(title, FrameLayout.LayoutParams(-1, c.dp(26)).apply { leftMargin = c.dp(100) })
                root.addView(detail, FrameLayout.LayoutParams(-1, c.dp(70)).apply { leftMargin = c.dp(100); topMargin = c.dp(30) })
                root.addView(progress, FrameLayout.LayoutParams(c.dp(40), c.dp(40), Gravity.END).apply { topMargin = c.dp(5) })
                root.layoutParams = RecyclerView.LayoutParams(-1, c.dp(100))
            }
            detail.maxLines = if (tablet) 3 else 4
            detail.ellipsize = TextUtils.TruncateAt.END
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Cell>() {
        override fun getItemCount() = Native.gameCount()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Cell(FrameLayout(parent.context))

        override fun onBindViewHolder(h: Cell, position: Int) {
            val name = Native.gameName(position)
            h.title.text = name
            h.detail.text = descriptions[name] ?: ""
            var iconName = name.replace(" ", "").lowercase()
            if (iconName == "rectangles") iconName = "rect"
            val res = resources.getIdentifier("icon_$iconName", "drawable", requireContext().packageName)
            if (res != 0) h.icon.setImageResource(res) else h.icon.setImageDrawable(null)
            h.progress.visibility = if (GameStore.inProgress.contains(name)) View.VISIBLE else View.GONE
            h.root.setOnClickListener {
                main.push(GameFragment.newInstance(position), "game")
                GameStore.lastGame = name
            }
        }
    }
}
