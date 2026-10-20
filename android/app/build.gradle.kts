plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The C sources, icons, help pages and descriptions are used straight from the
// repository root (the parent of this android/ directory). Everything the app
// needs from there is copied into build/generated/puzzles by the task below, so
// no binary files are duplicated in the repository.
val repoRoot: File = rootProject.projectDir.parentFile
val generatedDir: File = layout.buildDirectory.dir("generated/puzzles").get().asFile

val preparePuzzleResources by tasks.registering {
    val root = repoRoot
    val out = generatedDir
    inputs.files(fileTree(root) {
        include("*.html", "style.css", "descriptions.h", "icons/*-96d24@2x.png",
                "help-back*.png", "help-forward*.png", "inprogress*.png", "iTunesArtwork.png")
    })
    outputs.dir(out)
    doLast {
        out.deleteRecursively()
        val res = File(out, "res")
        val assets = File(out, "assets")
        fun put(src: String, dir: File, name: String) {
            dir.mkdirs()
            File(root, src).copyTo(File(dir, name), overwrite = true)
        }
        val mdpi = File(res, "drawable-mdpi")
        val xhdpi = File(res, "drawable-xhdpi")
        val nodpi = File(res, "drawable-nodpi")
        put("help-back.png", mdpi, "help_back.png")
        put("help-back@2x.png", xhdpi, "help_back.png")
        put("help-forward.png", mdpi, "help_forward.png")
        put("help-forward@2x.png", xhdpi, "help_forward.png")
        put("inprogress.png", mdpi, "inprogress.png")
        put("inprogress@2x.png", xhdpi, "inprogress.png")
        put("iTunesArtwork.png", nodpi, "app_icon.png")
        File(root, "icons").listFiles { f -> f.name.endsWith("-96d24@2x.png") }!!.forEach {
            put("icons/" + it.name, nodpi, "icon_" + it.name.removeSuffix("-96d24@2x.png") + ".png")
        }
        val help = File(assets, "help")
        help.mkdirs()
        root.listFiles { f -> f.name.endsWith(".html") || f.name == "style.css" }!!.forEach {
            it.copyTo(File(help, it.name), overwrite = true)
        }
        // descriptions.h holds lines like {@"Black Box", @"Deduce the positions ..."},
        val rx = Regex("""\{@"((?:[^"\\]|\\.)*)",\s*@"((?:[^"\\]|\\.)*)"\}""")
        val text = File(root, "descriptions.h").readText()
        File(assets, "descriptions.tsv").writeText(
            rx.findAll(text).joinToString("") {
                it.groupValues[1] + "\t" + it.groupValues[2].replace("\\\"", "\"") + "\n"
            })
    }
}

android {
    namespace = "com.example.puzzles"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.puzzles"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    sourceSets["main"].res.srcDir(File(generatedDir, "res"))
    sourceSets["main"].assets.srcDir(File(generatedDir, "assets"))

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.named("preBuild") { dependsOn(preparePuzzleResources) }

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
