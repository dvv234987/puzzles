package com.example.puzzles

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient

/** Port of GameHelpController: a web view over the bundled HTML help. */
class HelpFragment : ScreenFragment() {
    companion object {
        fun newInstance(file: String) = HelpFragment().apply {
            arguments = Bundle().apply { putString("file", file) }
        }
    }

    override val screenTitle: String get() = ""
    private var web: WebView? = null

    fun canGoBack() = web?.canGoBack() == true
    fun canGoForward() = web?.canGoForward() == true
    fun goBack() { web?.goBack() }
    fun goForward() { web?.goForward() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val w = WebView(requireContext())
        web = w
        w.setBackgroundColor(android.graphics.Color.WHITE)
        w.settings.javaScriptEnabled = true
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                main.refreshBar()
                if (url.endsWith("/help.html")) {
                    val ver = requireContext().packageManager
                        .getPackageInfo(requireContext().packageName, 0).versionName ?: ""
                    view.evaluateJavascript("document.getElementById('version').innerHTML = '$ver';", null)
                    view.evaluateJavascript(
                        "document.getElementById('orig_version').innerHTML = '${Native.version()}';", null)
                }
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { main.refreshBar() }
        }
        w.loadUrl("file:///android_asset/help/" + requireArguments().getString("file"))
        return w
    }

    override fun onDestroyView() {
        web?.destroy(); web = null
        super.onDestroyView()
    }
}
