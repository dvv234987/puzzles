package com.example.puzzles

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Plays the role of AppDelegate + UINavigationController. */
class MainActivity : AppCompatActivity() {

    private lateinit var bar: TopBar
    val containerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always start fresh: fragments hold native pointers and are never restored.
        super.onCreate(null)
        GameStore.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        bar = TopBar(this)
        bar.onBack = { onBackPressedDispatcher.onBackPressed() }
        val container = FrameLayout(this).apply { id = containerId }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BAR)
            addView(bar, LinearLayout.LayoutParams(-1, dp(44)))
            addView(View(this@MainActivity).apply { setBackgroundColor(Ui.BAR_LINE) }, LinearLayout.LayoutParams(-1, 1))
            addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            insets
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        supportFragmentManager.addOnBackStackChangedListener { bar.post { refreshBar() } }

        supportFragmentManager.beginTransaction()
            .add(containerId, GameListFragment(), "list")
            .commitNow()

        // Like AppDelegate: reopen the game that was on screen when we were last used
        val last = GameStore.lastGame
        if (last != null) {
            val i = (0 until Native.gameCount()).firstOrNull { Native.gameName(it) == last }
            if (i != null) push(GameFragment.newInstance(i), "game")
        }
        refreshBar()
    }

    fun top(): ScreenFragment? =
        supportFragmentManager.fragments.filterIsInstance<ScreenFragment>().lastOrNull { !it.isHidden }

    fun push(f: ScreenFragment, backStackName: String?) {
        val cur = top()
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .apply { if (cur != null) hide(cur) }
            .add(containerId, f)
            .addToBackStack(backStackName)
            .commit()
    }

    fun showHelp(file: String) = push(HelpFragment.newInstance(file), null)

    /** popToViewController:gameViewController */
    fun popToGame() {
        supportFragmentManager.popBackStack("game", 0)
    }

    fun refreshBar() {
        val list = supportFragmentManager.fragments.filterIsInstance<ScreenFragment>()
        val top = list.lastOrNull { !it.isHidden } ?: return
        val idx = list.indexOf(top)
        val prev = if (idx > 0) list[idx - 1] else null
        val help = top.helpFile
        bar.update(top.screenTitle, prev?.screenTitle, help?.let { f -> { showHelp(f) } }, top as? HelpFragment)
    }
}

/** UINavigationBar look-alike (iOS 7 style, as in the screenshots). */
class TopBar(ctx: Context) : FrameLayout(ctx) {
    var onBack: () -> Unit = {}

    private val chevron = ImageView(ctx).apply { setImageResource(R.drawable.ic_chevron_back) }
    private val backText = TextView(ctx).apply { setTextColor(Ui.BLUE); textSize = 17f; maxLines = 1 }
    private val backBox = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(ctx.dp(8), 0, ctx.dp(8), 0)
        background = UiExt.selectableBg(ctx)
        addView(chevron, LinearLayout.LayoutParams(ctx.dp(14), ctx.dp(22)))
        addView(backText, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = ctx.dp(6) })
        setOnClickListener { onBack() }
    }
    private val helpBack = ImageView(ctx).apply {
        setImageResource(R.drawable.help_back); setPadding(ctx.dp(10), 0, ctx.dp(10), 0)
    }
    private val helpForward = ImageView(ctx).apply {
        setImageResource(R.drawable.help_forward); setPadding(ctx.dp(10), 0, ctx.dp(10), 0)
    }
    private val leftBox = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(backBox, LinearLayout.LayoutParams(-2, -1))
        addView(helpBack, LinearLayout.LayoutParams(-2, -1))
        addView(helpForward, LinearLayout.LayoutParams(-2, -1))
    }
    private val titleView = TextView(ctx).apply {
        setTextColor(android.graphics.Color.BLACK); textSize = 17f
        typeface = android.graphics.Typeface.DEFAULT_BOLD; maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
    }
    private var helpAction: (() -> Unit)? = null
    private val helpBtn = UiExt.barButton(ctx, "Help") { helpAction?.invoke() }

    init {
        setBackgroundColor(Ui.BAR)
        addView(titleView, LayoutParams(-2, -2, Gravity.CENTER).apply {
            leftMargin = ctx.dp(110); rightMargin = ctx.dp(110)
        })
        addView(leftBox, LayoutParams(-2, -1, Gravity.START))
        addView(helpBtn, LayoutParams(-2, -1, Gravity.END))
    }

    fun update(title: String, backTitle: String?, help: (() -> Unit)?, helpPage: HelpFragment?) {
        helpAction = help
        backBox.visibility = if (backTitle != null) View.VISIBLE else View.GONE
        backText.text = backTitle ?: ""
        helpBtn.visibility = if (help != null) View.VISIBLE else View.GONE
        if (helpPage != null) {
            titleView.text = ""
            helpBack.visibility = View.VISIBLE; helpForward.visibility = View.VISIBLE
            helpBack.alpha = if (helpPage.canGoBack()) 1f else 0.3f
            helpForward.alpha = if (helpPage.canGoForward()) 1f else 0.3f
            helpBack.setOnClickListener { helpPage.goBack() }
            helpForward.setOnClickListener { helpPage.goForward() }
        } else {
            titleView.text = title
            helpBack.visibility = View.GONE; helpForward.visibility = View.GONE
        }
    }
}
