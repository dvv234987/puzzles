package com.example.puzzles

/** JNI entry points implemented in android_frontend.c */
object Native {
    init { System.loadLibrary("puzzles") }

    // game list
    @JvmStatic external fun gameCount(): Int
    @JvmStatic external fun gameName(i: Int): String
    @JvmStatic external fun gameCanSolve(i: Int): Boolean
    @JvmStatic external fun gameHelpTopic(i: Int): String
    @JvmStatic external fun version(): String
    /** LEFT, MIDDLE, RIGHT, LEFT_DRAG, MIDDLE_DRAG, RIGHT_DRAG, LEFT_RELEASE,
     *  MIDDLE_RELEASE, RIGHT_RELEASE, CURSOR_UP, CURSOR_DOWN, CURSOR_LEFT,
     *  CURSOR_RIGHT, MOD_SHFT */
    @JvmStatic external fun buttonCodes(): IntArray

    // midend
    @JvmStatic external fun create(gameIndex: Int, view: Any, tileSizeMul: Int): Long
    @JvmStatic external fun destroy(h: Long)
    @JvmStatic external fun colours(h: Long): IntArray
    @JvmStatic external fun newGame(h: Long)
    @JvmStatic external fun restart(h: Long)
    @JvmStatic external fun forceRedraw(h: Long)
    @JvmStatic external fun size(h: Long, w: Int, hgt: Int): IntArray
    @JvmStatic external fun processKey(h: Long, x: Int, y: Int, button: Int): Int
    @JvmStatic external fun timer(h: Long, t: Float)
    @JvmStatic external fun canUndo(h: Long): Boolean
    @JvmStatic external fun status(h: Long): Int
    @JvmStatic external fun wantsStatusbar(h: Long): Boolean
    @JvmStatic external fun tileSize(h: Long): Int
    @JvmStatic external fun gameId(h: Long): String
    @JvmStatic external fun solve(h: Long): String?

    // presets / ids
    @JvmStatic external fun numPresets(h: Long): Int
    @JvmStatic external fun whichPreset(h: Long): Int
    @JvmStatic external fun presetName(h: Long, i: Int): String
    @JvmStatic external fun setPreset(h: Long, i: Int)
    @JvmStatic external fun applyGameId(h: Long, id: String): String?

    // save / load
    @JvmStatic external fun serialise(h: Long): String
    @JvmStatic external fun deserialise(h: Long, data: String): String?

    // config dialogs
    @JvmStatic external fun cfgOpen(h: Long, which: Int): Long
    @JvmStatic external fun cfgFree(p: Long)
    @JvmStatic external fun cfgTitle(p: Long): String
    @JvmStatic external fun cfgCount(p: Long): Int
    @JvmStatic external fun cfgName(p: Long, i: Int): String
    @JvmStatic external fun cfgType(p: Long, i: Int): Int
    @JvmStatic external fun cfgSval(p: Long, i: Int): String?
    @JvmStatic external fun cfgIval(p: Long, i: Int): Int
    @JvmStatic external fun cfgSetI(p: Long, i: Int, v: Int)
    @JvmStatic external fun cfgSetS(p: Long, i: Int, s: String)
    @JvmStatic external fun setConfig(h: Long, which: Int, p: Long): String?

    const val CFG_SETTINGS = 0
    const val CFG_SEED = 1
    const val CFG_DESC = 2
    const val C_STRING = 0
    const val C_CHOICES = 1
    const val C_BOOLEAN = 2
    const val C_END = 3
}
