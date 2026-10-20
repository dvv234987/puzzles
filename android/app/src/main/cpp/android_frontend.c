/*
 * android_frontend.c: JNI bridge between Simon Tatham's puzzle midend and
 * the Kotlin UI. Plays the role that the C drawing callbacks in GameView.m
 * (ios_drawing) play in the iOS port.
 */
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <stdarg.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "puzzles.h"

#ifndef PUZZLES_VER
#define PUZZLES_VER "Revision: unknown"
#endif
char ver[] = PUZZLES_VER;

#ifdef __ANDROID__
#define ATTACH_ARG(p) ((JNIEnv **)(p))
#else
#define ATTACH_ARG(p) ((void **)(p))
#endif

struct frontend {
    JavaVM *vm;
    jobject view;            /* global ref to Kotlin GameCanvasView */
    int ncolours;
};

struct blitter {
    int id;
};

typedef struct {
    midend *me;
    frontend fe;
    const game *g;
} Handle;

typedef struct {
    config_item *items;
    int n;
    char *title;
} Cfg;

static struct {
    int ready;
    jmethodID drawText, fillRect, drawLine, drawPolygon, drawCircle, update,
        clip, unclip, status, blitNew, blitFree, blitSave, blitLoad,
        timerOn, timerOff;
} M;

static JNIEnv *env_for(frontend *fe)
{
    JNIEnv *env = NULL;
    if ((*fe->vm)->GetEnv(fe->vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        (*fe->vm)->AttachCurrentThread(fe->vm, ATTACH_ARG(&env), NULL);
    }
    return env;
}

/* ---------- platform routines required by the puzzle code ---------- */

void fatal(char *fmt, ...)
{
    va_list ap;
    char buf[512];
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_FATAL, "puzzles", "%s", buf);
#else
    fprintf(stderr, "fatal: %s\n", buf);
#endif
    abort();
}

void frontend_default_colour(frontend *fe, float *output)
{
    /* same as the iOS port: grey (0.8) background */
    (void)fe;
    output[0] = output[1] = output[2] = 0.8f;
}

void get_random_seed(void **randseed, int *randseedsize)
{
    /* The iOS port seeds with time(); include nanoseconds so two games
     * generated within the same second still differ. */
    struct timespec *ts = snew(struct timespec);
    clock_gettime(CLOCK_REALTIME, ts);
    *randseed = ts;
    *randseedsize = sizeof(struct timespec);
}

void activate_timer(frontend *fe)
{
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.timerOn);
}

void deactivate_timer(frontend *fe)
{
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.timerOff);
}

/* ---------- drawing_api ---------- */

static void a_draw_text(void *h, int x, int y, int fonttype, int fontsize,
                        int align, int colour, char *text)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    jstring s = (*env)->NewStringUTF(env, text);
    (*env)->CallVoidMethod(env, fe->view, M.drawText, x, y, fonttype,
                           fontsize, align, colour, s);
    (*env)->DeleteLocalRef(env, s);
}

static void a_draw_rect(void *h, int x, int y, int w, int hh, int colour)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.fillRect, x, y, w, hh, colour);
}

static void a_draw_line(void *h, int x1, int y1, int x2, int y2, int colour)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.drawLine, x1, y1, x2, y2, colour);
}

static void a_draw_polygon(void *h, int *coords, int npoints,
                           int fillcolour, int outlinecolour)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    jintArray arr = (*env)->NewIntArray(env, npoints * 2);
    (*env)->SetIntArrayRegion(env, arr, 0, npoints * 2, (const jint *)coords);
    (*env)->CallVoidMethod(env, fe->view, M.drawPolygon, arr, fillcolour,
                           outlinecolour);
    (*env)->DeleteLocalRef(env, arr);
}

static void a_draw_circle(void *h, int cx, int cy, int radius,
                          int fillcolour, int outlinecolour)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.drawCircle, cx, cy, radius,
                           fillcolour, outlinecolour);
}

static void a_draw_update(void *h, int x, int y, int w, int hh)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.update, x, y, w, hh);
}

static void a_clip(void *h, int x, int y, int w, int hh)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.clip, x, y, w, hh);
}

static void a_unclip(void *h)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.unclip);
}

static void a_start_draw(void *h) { (void)h; }
static void a_end_draw(void *h) { (void)h; }

static void a_status_bar(void *h, char *text)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    jstring s = (*env)->NewStringUTF(env, text);
    (*env)->CallVoidMethod(env, fe->view, M.status, s);
    (*env)->DeleteLocalRef(env, s);
}

static blitter *a_blitter_new(void *h, int w, int hh)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    blitter *bl = snew(blitter);
    bl->id = (*env)->CallIntMethod(env, fe->view, M.blitNew, w, hh);
    return bl;
}

static void a_blitter_free(void *h, blitter *bl)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.blitFree, bl->id);
    sfree(bl);
}

static void a_blitter_save(void *h, blitter *bl, int x, int y)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.blitSave, bl->id, x, y);
}

static void a_blitter_load(void *h, blitter *bl, int x, int y)
{
    frontend *fe = h;
    JNIEnv *env = env_for(fe);
    (*env)->CallVoidMethod(env, fe->view, M.blitLoad, bl->id, x, y);
}

static char *a_text_fallback(void *h, const char *const *strings, int n)
{
    (void)h; (void)n;
    return dupstr(strings[0]);
}

static const struct drawing_api android_drawing = {
    a_draw_text, a_draw_rect, a_draw_line, a_draw_polygon, a_draw_circle,
    a_draw_update, a_clip, a_unclip, a_start_draw, a_end_draw, a_status_bar,
    a_blitter_new, a_blitter_free, a_blitter_save, a_blitter_load,
    NULL, NULL, NULL, NULL, NULL, NULL, /* begin_doc .. end_doc (printing) */
    NULL, NULL,                         /* line_width, line_dotted */
    a_text_fallback,
    NULL,                               /* draw_thick_line: drawing.c emulates it */
};

/* ---------- JNI helpers ---------- */

#define FN(ret, name) JNIEXPORT ret JNICALL Java_com_example_puzzles_Native_##name
#define H(h) ((Handle *)(intptr_t)(h))

static jstring jstr(JNIEnv *env, const char *s)
{
    return s ? (*env)->NewStringUTF(env, s) : NULL;
}

static int cache_methods(JNIEnv *env, jobject view)
{
    jclass c = (*env)->GetObjectClass(env, view);
#define GM(f, n, sig) M.f = (*env)->GetMethodID(env, c, n, sig); if (!M.f) return 0
    GM(drawText, "nDrawText", "(IIIIIILjava/lang/String;)V");
    GM(fillRect, "nFillRect", "(IIIII)V");
    GM(drawLine, "nDrawLine", "(IIIII)V");
    GM(drawPolygon, "nDrawPolygon", "([III)V");
    GM(drawCircle, "nDrawCircle", "(IIIII)V");
    GM(update, "nUpdate", "(IIII)V");
    GM(clip, "nClip", "(IIII)V");
    GM(unclip, "nUnclip", "()V");
    GM(status, "nStatus", "(Ljava/lang/String;)V");
    GM(blitNew, "nBlitterNew", "(II)I");
    GM(blitFree, "nBlitterFree", "(I)V");
    GM(blitSave, "nBlitterSave", "(III)V");
    GM(blitLoad, "nBlitterLoad", "(III)V");
    GM(timerOn, "nActivateTimer", "()V");
    GM(timerOff, "nDeactivateTimer", "()V");
#undef GM
    M.ready = 1;
    return 1;
}

/* ---------- game list ---------- */

FN(jint, gameCount)(JNIEnv *env, jclass c) { (void)env; (void)c; return gamecount; }
FN(jstring, gameName)(JNIEnv *env, jclass c, jint i) { (void)c; return jstr(env, gamelist[i]->name); }
FN(jboolean, gameCanSolve)(JNIEnv *env, jclass c, jint i) { (void)env; (void)c; return gamelist[i]->can_solve != 0; }
FN(jstring, gameHelpTopic)(JNIEnv *env, jclass c, jint i) { (void)c; return jstr(env, gamelist[i]->htmlhelp_topic); }
FN(jstring, version)(JNIEnv *env, jclass c) { (void)c; return jstr(env, ver); }

FN(jintArray, buttonCodes)(JNIEnv *env, jclass c)
{
    (void)c;
    jint v[] = { LEFT_BUTTON, MIDDLE_BUTTON, RIGHT_BUTTON, LEFT_DRAG,
                 MIDDLE_DRAG, RIGHT_DRAG, LEFT_RELEASE, MIDDLE_RELEASE,
                 RIGHT_RELEASE, CURSOR_UP, CURSOR_DOWN, CURSOR_LEFT,
                 CURSOR_RIGHT, MOD_SHFT };
    jintArray a = (*env)->NewIntArray(env, 14);
    (*env)->SetIntArrayRegion(env, a, 0, 14, v);
    return a;
}

/* ---------- midend lifecycle ---------- */

FN(jlong, create)(JNIEnv *env, jclass c, jint index, jobject view, jint tilemul)
{
    (void)c;
    if (!M.ready && !cache_methods(env, view)) return 0;
    Handle *hd = snew(Handle);
    memset(hd, 0, sizeof *hd);
    (*env)->GetJavaVM(env, &hd->fe.vm);
    hd->fe.view = (*env)->NewGlobalRef(env, view);
    hd->g = gamelist[index];

    /* as in the iOS port: bias tile size upwards via <GAME>_TILESIZE */
    char envname[64];
    snprintf(envname, sizeof envname, "%s_TILESIZE", hd->g->name);
    for (char *p = envname; *p; p++) {
        if (*p == ' ') *p = '_';
        else if (*p >= 'a' && *p <= 'z') *p -= 32;
    }
    char val[16];
    snprintf(val, sizeof val, "%d", hd->g->preferred_tilesize * tilemul);
    setenv(envname, val, 1);

    hd->me = midend_new(&hd->fe, hd->g, &android_drawing, &hd->fe);
    return (jlong)(intptr_t)hd;
}

FN(void, destroy)(JNIEnv *env, jclass c, jlong h)
{
    (void)c;
    Handle *hd = H(h);
    midend_free(hd->me);
    (*env)->DeleteGlobalRef(env, hd->fe.view);
    sfree(hd);
}

FN(jintArray, colours)(JNIEnv *env, jclass c, jlong h)
{
    (void)c;
    Handle *hd = H(h);
    float *col = midend_colours(hd->me, &hd->fe.ncolours);
    jintArray a = (*env)->NewIntArray(env, hd->fe.ncolours);
    jint *tmp = snewn(hd->fe.ncolours, jint);
    for (int i = 0; i < hd->fe.ncolours; i++) {
        int r = (int)(col[i * 3 + 0] * 255 + 0.5f);
        int g = (int)(col[i * 3 + 1] * 255 + 0.5f);
        int b = (int)(col[i * 3 + 2] * 255 + 0.5f);
        tmp[i] = (jint)(0xFF000000u | (r << 16) | (g << 8) | b);
    }
    (*env)->SetIntArrayRegion(env, a, 0, hd->fe.ncolours, tmp);
    sfree(tmp);
    sfree(col);
    return a;
}

FN(void, newGame)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; midend_new_game(H(h)->me); }
FN(void, restart)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; midend_restart_game(H(h)->me); }
FN(void, forceRedraw)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; midend_force_redraw(H(h)->me); }

FN(jintArray, size)(JNIEnv *env, jclass c, jlong h, jint w, jint hh)
{
    (void)c;
    int x = w, y = hh;
    midend_size(H(h)->me, &x, &y, 0);
    jint v[2] = { x, y };
    jintArray a = (*env)->NewIntArray(env, 2);
    (*env)->SetIntArrayRegion(env, a, 0, 2, v);
    return a;
}

FN(jint, processKey)(JNIEnv *env, jclass c, jlong h, jint x, jint y, jint button)
{
    (void)env; (void)c;
    return midend_process_key(H(h)->me, x, y, button);
}

FN(void, timer)(JNIEnv *env, jclass c, jlong h, jfloat t) { (void)env; (void)c; midend_timer(H(h)->me, t); }
FN(jboolean, canUndo)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; return midend_can_undo(H(h)->me) != 0; }
FN(jint, status)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; return midend_status(H(h)->me); }
FN(jboolean, wantsStatusbar)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; return midend_wants_statusbar(H(h)->me) != 0; }
FN(jint, tileSize)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; return midend_tilesize(H(h)->me); }

FN(jstring, gameId)(JNIEnv *env, jclass c, jlong h)
{
    (void)c;
    char *id = midend_get_game_id(H(h)->me);
    jstring s = jstr(env, id);
    sfree(id);
    return s;
}

FN(jstring, solve)(JNIEnv *env, jclass c, jlong h)
{
    (void)c;
    return jstr(env, midend_solve(H(h)->me));
}

/* ---------- presets ---------- */

FN(jint, numPresets)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; return midend_num_presets(H(h)->me); }
FN(jint, whichPreset)(JNIEnv *env, jclass c, jlong h) { (void)env; (void)c; return midend_which_preset(H(h)->me); }

FN(jstring, presetName)(JNIEnv *env, jclass c, jlong h, jint i)
{
    (void)c;
    char *name; game_params *params;
    midend_fetch_preset(H(h)->me, i, &name, &params);
    return jstr(env, name);
}

FN(void, setPreset)(JNIEnv *env, jclass c, jlong h, jint i)
{
    (void)env; (void)c;
    char *name; game_params *params;
    midend_fetch_preset(H(h)->me, i, &name, &params);
    midend_set_params(H(h)->me, params);
}

FN(jstring, applyGameId)(JNIEnv *env, jclass c, jlong h, jstring id)
{
    (void)c;
    const char *u = (*env)->GetStringUTFChars(env, id, NULL);
    char *copy = dupstr(u);
    (*env)->ReleaseStringUTFChars(env, id, u);
    const char *msg = midend_game_id(H(h)->me, copy);
    jstring r = jstr(env, msg);
    sfree(copy);
    return r;
}

/* ---------- save / load ---------- */

typedef struct { char *buf; size_t len, cap; } Buf;

static void buf_write(void *ctx, void *data, int len)
{
    Buf *b = ctx;
    if (b->len + len + 1 > b->cap) {
        b->cap = (b->len + len + 1) * 2;
        b->buf = srealloc(b->buf, b->cap);
    }
    memcpy(b->buf + b->len, data, len);
    b->len += len;
    b->buf[b->len] = 0;
}

FN(jstring, serialise)(JNIEnv *env, jclass c, jlong h)
{
    (void)c;
    Buf b = { NULL, 0, 0 };
    buf_write(&b, "", 0);
    midend_serialise(H(h)->me, buf_write, &b);
    jstring s = jstr(env, b.buf);
    sfree(b.buf);
    return s;
}

typedef struct { const char *p; size_t left; } Src;

static int buf_read(void *ctx, void *data, int len)
{
    Src *s = ctx;
    if ((size_t)len > s->left) len = (int)s->left;
    memcpy(data, s->p, len);
    s->p += len;
    s->left -= len;
    return len;
}

FN(jstring, deserialise)(JNIEnv *env, jclass c, jlong h, jstring data)
{
    (void)c;
    const char *u = (*env)->GetStringUTFChars(env, data, NULL);
    Src s = { u, strlen(u) };
    const char *msg = midend_deserialise(H(h)->me, buf_read, &s);
    jstring r = jstr(env, msg);
    (*env)->ReleaseStringUTFChars(env, data, u);
    return r;
}

/* ---------- config dialogs ---------- */

#define CFG(p) ((Cfg *)(intptr_t)(p))

FN(jlong, cfgOpen)(JNIEnv *env, jclass c, jlong h, jint which)
{
    (void)env; (void)c;
    Cfg *cfg = snew(Cfg);
    cfg->items = midend_get_config(H(h)->me, which, &cfg->title);
    cfg->n = 0;
    while (cfg->items[cfg->n].type != C_END) cfg->n++;
    return (jlong)(intptr_t)cfg;
}

FN(void, cfgFree)(JNIEnv *env, jclass c, jlong p)
{
    (void)env; (void)c;
    free_cfg(CFG(p)->items);
    sfree(CFG(p)->title);
    sfree(CFG(p));
}

FN(jstring, cfgTitle)(JNIEnv *env, jclass c, jlong p) { (void)c; return jstr(env, CFG(p)->title); }
FN(jint, cfgCount)(JNIEnv *env, jclass c, jlong p) { (void)env; (void)c; return CFG(p)->n; }
FN(jstring, cfgName)(JNIEnv *env, jclass c, jlong p, jint i) { (void)c; return jstr(env, CFG(p)->items[i].name); }
FN(jint, cfgType)(JNIEnv *env, jclass c, jlong p, jint i) { (void)env; (void)c; return CFG(p)->items[i].type; }
FN(jstring, cfgSval)(JNIEnv *env, jclass c, jlong p, jint i) { (void)c; return jstr(env, CFG(p)->items[i].sval); }
FN(jint, cfgIval)(JNIEnv *env, jclass c, jlong p, jint i) { (void)env; (void)c; return CFG(p)->items[i].ival; }
FN(void, cfgSetI)(JNIEnv *env, jclass c, jlong p, jint i, jint v) { (void)env; (void)c; CFG(p)->items[i].ival = v; }

FN(void, cfgSetS)(JNIEnv *env, jclass c, jlong p, jint i, jstring s)
{
    (void)c;
    const char *u = (*env)->GetStringUTFChars(env, s, NULL);
    sfree(CFG(p)->items[i].sval);
    CFG(p)->items[i].sval = dupstr(u);
    (*env)->ReleaseStringUTFChars(env, s, u);
}

FN(jstring, setConfig)(JNIEnv *env, jclass c, jlong h, jint which, jlong p)
{
    (void)c;
    return jstr(env, midend_set_config(H(h)->me, which, CFG(p)->items));
}
