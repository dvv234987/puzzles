package com.example.puzzles

import androidx.fragment.app.Fragment

/** One "view controller" in the navigation stack. */
abstract class ScreenFragment : Fragment() {
    abstract val screenTitle: String
    /** Help page shown by the "Help" button in the navigation bar, if any. */
    open val helpFile: String? get() = null
    protected val main: MainActivity get() = requireActivity() as MainActivity
}
