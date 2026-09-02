package dev.devpanda.bamboocef.web.screen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Eine Seite, auf der sich alles ausprobieren lässt, was Schritt D können muss.
 *
 * <p>Kein Monaco, keine Bibliothek, nichts Geladenes — nur gewöhnliches HTML.
 * Genau das ist der Punkt: Was hier nicht funktioniert, funktioniert auch in
 * einem Editor nicht, und ein Fehler ist hier in einer Zeile zu finden statt
 * in einer fremden Codebasis.
 *
 * <p>Was sie enthält und warum:
 *
 * <ul>
 *   <li><b>Ein Textfeld</b> — für Umlaute, ß, Klammern, Backspace, Home und
 *       Ende, Strg+A und Strg+Z.</li>
 *   <li><b>Ein Auswahlfeld</b> — der einzige einfache Weg, ein echtes
 *       Chromium-Popup auszulösen.</li>
 *   <li><b>Eine lange Liste</b> — zum Scrollen, und um zu sehen, wie groß die
 *       geänderten Bereiche dabei werden.</li>
 *   <li><b>Vier Ecken-Marker</b> — damit sich beim Klicken sofort zeigt, ob
 *       Zeigen und Treffen dieselbe Stelle meinen.</li>
 *   <li><b>Eine halbdurchsichtige Fläche</b> — die Mischung, die beim
 *       Zeichnen entschieden wird, hier im Betrieb.</li>
 * </ul>
 */
public final class ProbePage {

    private ProbePage() {
    }

    private static final String HTML = """
            <!doctype html><html lang="de"><head><meta charset="utf-8"><style>
              :root{--grund:#12131a;--flaeche:#1e2029;--rand:#3a3d4d;--schrift:#e6e8f0;
                    --hell:#7dd3a0}
              *{box-sizing:border-box}
              html,body{margin:0;height:100%;background:var(--grund);color:var(--schrift);
                        font:15px/1.5 "Segoe UI",system-ui,sans-serif;overflow:hidden}
              .ecke{position:fixed;width:80px;height:80px;display:flex;
                    align-items:center;justify-content:center;font-weight:600;
                    font-size:13px;cursor:pointer;user-select:none}
              .ecke:active{outline:4px solid #fff}
              #tl{left:0;top:0;background:#e05252}
              #tr{right:0;top:0;background:#4caf6a}
              #bl{left:0;bottom:0;background:#4a7fd0}
              #br{right:0;bottom:0;background:#d8c04a;color:#222}
              .mitte{max-width:640px;margin:0 auto;padding:96px 24px 24px;
                     height:100%;overflow-y:auto}
              h1{font-size:22px;margin:0 0 4px}
              p.hinweis{color:#9aa0b4;margin:0 0 20px;font-size:13px}
              .feld{background:var(--flaeche);border:1px solid var(--rand);
                    border-radius:8px;padding:16px;margin-bottom:16px}
              label{display:block;font-size:13px;color:#9aa0b4;margin-bottom:6px}
              textarea{width:100%;height:110px;background:#0d0e14;color:var(--schrift);
                       border:1px solid var(--rand);border-radius:6px;padding:10px;
                       font:14px/1.5 Consolas,monospace;resize:none}
              input,select{width:100%;background:#0d0e14;color:var(--schrift);
                           border:1px solid var(--rand);border-radius:6px;padding:9px}
              .glas{background:rgba(125,211,160,0.35);border:1px solid var(--hell);
                    border-radius:8px;padding:14px;margin-bottom:16px}
              .liste div{padding:7px 10px;border-bottom:1px solid var(--rand);
                         font-size:13px}
              #zeiger{color:var(--hell);font-family:Consolas,monospace;font-size:13px}
              .zeigt{cursor:pointer}.tippt{cursor:text}
            </style></head><body>

            <div class="ecke" id="tl">oben links</div>
            <div class="ecke" id="tr">oben rechts</div>
            <div class="ecke" id="bl">unten links</div>
            <div class="ecke" id="br">unten rechts</div>

            <div class="mitte">
              <h1>Prüffläche</h1>
              <p class="hinweis">Vier Ecken zum Zielen, ein Textfeld zum Tippen,
                 ein Auswahlfeld für das Popup. F10 gibt die Tastatur zurück.</p>

              <div class="feld">
                <label for="text">Textfeld — Umlaute, ß, €, Klammern, Strg+A/C/V/Z</label>
                <textarea id="text" spellcheck="false">äöü ÄÖÜ ß € {} [] &lt;&gt;</textarea>
              </div>

              <div class="feld">
                <label for="wahl">Auswahlfeld — klappt ein echtes Popup auf</label>
                <select id="wahl">
                  <option>Kupferdraht</option><option>Siliziumwafer</option>
                  <option>Prozessorkern</option><option>Speicherzelle</option>
                  <option>Signalverstärker</option><option>Netzwerkkarte</option>
                </select>
              </div>

              <div class="feld">
                <label for="zeile">Einzeiliges Feld — Doppelklick markiert ein Wort</label>
                <input id="zeile" value="Doppelklick markiert dieses Wort hier">
              </div>

              <div class="glas">Halbdurchsichtig — sieht nur richtig aus, wenn
                 vormultipliziertes Alpha richtig gemischt wird.</div>

              <div class="feld">
                <span class="zeigt">Zeigefinger</span> ·
                <span class="tippt">Schreibmarke</span> ·
                <span id="zeiger">—</span>
              </div>

              <div class="feld liste" id="liste"></div>
            </div>

            <script>
              var liste = document.getElementById('liste');
              for (var i = 1; i <= 60; i++) {
                var d = document.createElement('div');
                d.textContent = 'Zeile ' + i + ' — zum Scrollen';
                liste.appendChild(d);
              }
              var zeiger = document.getElementById('zeiger');
              document.addEventListener('mousemove', function (e) {
                zeiger.textContent = e.clientX + ', ' + e.clientY;
              });
              ['tl','tr','bl','br'].forEach(function (id) {
                document.getElementById(id).addEventListener('click', function () {
                  zeiger.textContent = 'getroffen: ' + id;
                });
              });
            </script>
            </body></html>
            """;

    /**
     * Legt die Seite ab und gibt ihre Adresse zurück.
     *
     * <p>Als Datei und nicht als {@code data:}-Adresse: Chromium verweigert
     * die seit Version 60 als Hauptdokument, und der Browser lädt dann gar
     * nichts.
     */
    public static String url() throws Exception {
        Path file = Files.createTempFile("fn-probe", ".html");
        Files.writeString(file, HTML, StandardCharsets.UTF_8);
        file.toFile().deleteOnExit();
        return file.toUri().toString();
    }
}
