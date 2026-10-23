/*
 * Runs inside Strange Eons via `--run`. Renders every requested component
 * and records one line per component in the results file:
 *
 *   OK<TAB>name<TAB>sheetCount
 *   FAIL<TAB>name<TAB>message
 *   SKIP<TAB>name<TAB>reason
 *   DONE
 *
 * The harness treats a missing DONE line as a crash. Configuration comes
 * from system properties set by the Gradle task (rt.*).
 */
importClass(java.io.File);
importClass(java.io.PrintWriter);
importClass(java.io.OutputStreamWriter);
importClass(java.io.FileOutputStream);
importClass(javax.imageio.ImageIO);
importClass(Packages.ca.cgjennings.apps.arkham.sheet.RenderTarget);
importClass(Packages.ca.cgjennings.apps.arkham.diy.DIY);
importClass(Packages.ca.cgjennings.apps.arkham.plugins.BundleInstaller);
importClass(Packages.gamedata.ClassMap);
importClass(Packages.gamedata.Game);

function prop(k) {
    var v = java.lang.System.getProperty(k);
    return v == null ? "" : String(v);
}

var outDir = new File(prop("rt.out"));
var componentsDir = prop("rt.components");
var games = prop("rt.games").split(",").filter(function (g) { return g.length > 0; });
var dpi = Number(prop("rt.dpi"));
var results = new PrintWriter(new OutputStreamWriter(new FileOutputStream(prop("rt.results")), "UTF-8"));

function record(status, name, detail) {
    results.println(status + "\t" + name + "\t" + String(detail).replace(/[\t\r\n]+/g, " "));
    results.flush();
}

function render(gc, name) {
    var sheets = gc.createDefaultSheets();
    for (var i = 0; i < sheets.length; ++i) {
        var img = sheets[i].paint(RenderTarget.EXPORT, dpi);
        var f = new File(outDir, name + "-" + i + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
    return sheets.length;
}

function attempt(name, factory) {
    try {
        var gc = factory();
        record("OK", name, render(gc, name));
    } catch (e) {
        var detail = e.javaException ? e.javaException.toString() : String(e);
        record("FAIL", name, detail);
    }
}

// Saved components, named by their path relative to the components folder.
function walk(dir, prefix) {
    var kids = dir.listFiles();
    if (kids == null) return;
    java.util.Arrays.sort(kids);
    for (var k = 0; k < kids.length; ++k) {
        var f = kids[k];
        var n = String(f.getName());
        if (f.isDirectory()) {
            walk(f, prefix + n + "/");
        } else if (n.toLowerCase().endsWith(".eon")) {
            var name = "components/" + prefix + n.substring(0, n.length - 4);
            attempt(name, function () {
                var gc = ResourceKit.getGameComponentFromFile(f, false);
                if (gc == null) throw new Error("could not read " + f);
                return gc;
            });
        }
    }
}

try {
    if (componentsDir.length > 0) {
        walk(new File(componentsDir), "");
    }

    // One default instance of each class-map entry for the requested games.
    if (games.length > 0) {
        var map = new ClassMap();
        var seen = {};
        var cats = map.getCategories().toArray();
        for (var c = 0; c < cats.length; ++c) {
            var entries = map.getCategoryEntries(cats[c]).toArray();
            for (var x = 0; x < entries.length; ++x) {
                var entry = entries[x];
                var game = entry.getGame();
                var code = game == null ? "" : String(game.getCode());
                if (games.indexOf(code) < 0) continue;
                var key = String(entry.getKey());
                if (seen[key]) continue;
                seen[key] = true;
                var name = "classmap/" + code + "/" + key.replace(/^@/, "").replace(/[^A-Za-z0-9._-]+/g, "_");
                var mapping = String(entry.getMapping());
                var type = String(entry.getType());
                if (type == "EDITOR_DIY") {
                    attempt(name, function () { return new DIY(mapping.substring(4), code, false); });
                } else if (type == "EDITOR_COMPILED") {
                    attempt(name, function () {
                        var gc = BundleInstaller.findClassAcrossBundles(mapping).getConstructor().newInstance();
                        gc.getSettings().set(Game.GAME_SETTING_KEY, code);
                        return gc;
                    });
                } else {
                    record("SKIP", name, "scripted class-map entries open an editor; save an .eon instead");
                }
            }
        }
    }
    results.println("DONE");
} finally {
    results.close();
}
