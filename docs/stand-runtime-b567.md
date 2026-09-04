# B5, B6, B7: Verwalter, Wächter, Reihenfolge

Stand: 1. September 2026. Die drei Blöcke stehen und greifen ineinander: Der
Verwalter weiß, welche Browser offen sind; die Reihenfolge beim Beenden nutzt
dieses Wissen; und der Wächter fängt den Fall auf, für den keine Reihenfolge
mehr hilft — den harten Abbruch.

---

## In einem Satz

Ein normaler Ausgang lässt **null** Hilfsprozesse zurück, drei harte Abbrüche
mit Wächter ebenfalls, der Verwalter zählt über einen ganzen Lebenslauf sauber
auf null zurück — und die Meldung ohne Stapel ist gefunden und behoben.

---

## B5 — BrowserManager

**Zwei Listen, nicht eine.** Eine Sitzung ist nicht zu, wenn jemand
`close()` gerufen hat: `CefBrowser.close(true)` ist eine Bitte, und die
Bestätigung — `onBeforeClose` — kommt später aus der Nachrichtenschleife. Wer
nur eine Liste führt, hält beim Herunterfahren entweder zu früh für fertig
oder wartet auf etwas, das er gerade selbst weggeworfen hat.

```text
register  →  offen
closing   →  offen − 1, wartet + 1      (close(true) ist raus)
closed    →  wartet − 1                 (onBeforeClose ist da)
```

### Gemessen im Spiel

Ein Lebenslauf mit vier Sitzungen (Selbsttest plus drei Zyklen):

```text
Browser registriert: Sitzung 1 (256x256)    — offen: 1
Browser schließt:    Sitzung 1              — offen: 0, warten: 1
Browser entfernt:    Sitzung 1              — offen: 0, warten: 0
… dasselbe für Sitzung 2, 3 und 4 (1920x1062)
```

**Die Bestätigung kam jedes Mal in derselben Sekunde** wie das Schließen. Der
Zähler stand nach jedem Zyklus wieder auf null.

### Und was das Spiel nicht zeigen konnte

`closeAll()` sah im Spiel **immer eine leere Liste**: Die Bildschirme schließen
ihre Browser selbst, bevor das Herunterfahren beginnt. Der interessante Fall
ist der andere, und der steht in sieben Prüfläufen
(`BrowserManagerTest`, alle grün):

| Prüfung | wogegen sie schützt |
|---|---|
| `closeAll` mit fünf offenen Sitzungen | jede meldet sich mitten in der Schleife ab — ohne Kopie eine `ConcurrentModificationException` |
| `closeAll` dreimal hintereinander | ein zweiter Aufruf darf nichts kaputtmachen |
| aus dem leeren Zustand | beim Beenden darf nichts werfen |
| Warten auf eine Bestätigung, die erst beim Pumpen kommt | das ist Chromiums echtes Verhalten |
| Frist, wenn die Bestätigung ausbleibt | sonst hinge das Spiel für immer |
| eine Sitzung, die beim Schließen wirft | die anderen müssen trotzdem zugehen |

---

## B6 — ProcessGuard

**Was hilft, ist nichts, was der Prozess tut, sondern etwas, was das
Betriebssystem für ihn tut.** Windows kennt Job Objects: eine Klammer um eine
Gruppe von Prozessen. Verschwindet die letzte Handhabe darauf, beendet Windows
alles darin. Beim Sterben eines Prozesses verschwindet sie immer — auch beim
härtesten Abbruch, denn Handhaben schließt das Betriebssystem, nicht das
Programm.

```text
CreateJobObject
SetInformationJobObject(JobObjectExtendedLimitInformation,
                        LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE)
AssignProcessToJobObject(eigener Prozess)
```

**Vor `CefApp.startup()`**, denn Kindprozesse erben die Zugehörigkeit — wer
später klammert, klammert die schon gestarteten Helfer nicht mehr ein.

### Warum JNA und ein eigenes Interface

Die Fremdfunktionsschnittstelle ist unter Java 21 eine Vorschau und verlangt
`--enable-preview`; Spieler setzen keine JVM-Schalter, und eine Mod kann sie
nicht erzwingen. JNA liegt ohnehin im Laufzeitpfad, weil Minecraft es über
OSHI mitbringt.

JNAs eigenes `Kernel32` kennt die drei Job-Funktionen nicht und `WinNT` die
Konstanten auch nicht — nachgesehen im Jar, nicht vermutet. Also ein eigenes
Interface mit drei Funktionen und einer Struktur.

**Ein falsches Bild von der Struktur schaltet den Wächter ab, statt den Start
zu kosten.** `JOBOBJECT_EXTENDED_LIMIT_INFORMATION` misst auf 64-Bit-Windows
144 Bytes; stimmt das nicht, gibt es eine Zeile im Protokoll und keinen Job.
Ein Wächter, der den Start verhindert, wäre schlimmer als keiner.

### Gemessen

```text
ProcessGuard: aktiv — Job Object mit KILL_ON_JOB_CLOSE (schon im Job: nein)
```

**„Schon im Job: nein" ist die wichtigere Hälfte der Zeile.** Sie sagt, dass
Gradle den Client nicht bereits in einer eigenen Klammer hält — die früheren
Null-Messungen waren also nicht Gradles Verdienst, und die Klammer ist jetzt
unsere. Unter einem echten Launcher kann das anders aussehen; verschachtelte
Jobs gehen seit Windows 8, ein Fehlschlag käme als Fehler 5 mit
entsprechendem Text ins Protokoll.

| Messung | Helfer vorher | danach |
|---|---|---|
| normaler Ausgang | 4 | **0** |

Die harten Abbrüche stehen weiter unten unter „Die Nachprüfungen" — dreimal
gemessen, dreimal null. Zum Vergleich ohne Wächter: ebenfalls dreimal
gemessen, einmal eine Waise.

---

## B7 — Geordnetes Herunterfahren

**Die Reihenfolge ist der ganze Inhalt.**

```text
1. BrowserManager.closeAll()          alle bitten, zuzugehen
2. awaitClosed(2000 ms, WebPump)      pumpen, bis jede bestätigt hat
3. backend.close()                    erst dann abräumen
```

Wer bei 3 anfängt, räumt CEF ab, während es noch Browser schließen wollte —
und genau daraus entstehen die Hilfsprozesse, die stehenbleiben.

**Das läuft im Renderthread, und das muss es:** Gepumpt wird dort, und nur wer
pumpt, bekommt die Bestätigungen zu sehen. Der Threadname steht deshalb in der
Protokollzeile — ein späterer Aufrufer vom falschen Thread soll auffallen,
statt still zu hängen.

Die Frist von zwei Sekunden ist großzügig für den Normalfall (im Spiel kam die
Bestätigung in derselben Sekunde) und kurz genug, dass niemand denkt, das
Spiel hänge. Läuft sie ab, schreibt sie auf, wer gefehlt hat.

### Gemessen

```text
Web-Runtime fährt herunter — im Thread Render thread, offen: 0
Chromium ist unten: TERMINATED
Web-Runtime ist unten — offen: 0, ohne Bestätigung: 0
jcef_helper nach normalem Ausgang: 0
```

Der Ausgang wurde über ein Schließen des Fensters ausgelöst, nicht über einen
Abbruch — der Client beendete sich mit Rückgabewert null.

---

## Die Meldung ohne Stapel — gefunden und behoben

**Sie war echt: ein `StackOverflowError` in einer Endlosschleife über den
Fokus.** Gefunden hat sie die Spur aus diesem Schritt — erst mit einer
Protokollzeile in `setFocus` kam auch der Stapel mit:

```text
FnBrowser.setFocus(true)
  → CefBrowser_N.setFocus → N_SetFocus (nativ)
      → CefClient.onGotFocus            (upstream, CefClient.java:461)
          → browser.setFocus(true)      zurück auf Anfang
```

Die Schleife läuft, bis der Stapel überläuft; der native Teil schluckt den
Fehler und macht weiter. Sichtbar war davon nur die Kopfzeile — ein bis sieben
Mal je Browsererzeugung, je nachdem wie oft der Bildschirm den Fokus setzt.

**Warum MCEF sie nie zeigte** — der fünfte Unterschied zwischen Fork und
upstream, und der erste, der ein Fehler ist:

```java
// upstream                          // CinemaMod-Fork
focusedBrowser_ = browser;           if (focusHandler_ != null)
browser.setFocus(true);   ← Rückschlag    focusHandler_.onGotFocus(browser);
if (focusHandler_ != null) …
```

Gebrochen wird der Kreis bei uns: eine Bremse in `FnBrowser.setFocus`, die
Chromiums Rückmeldung auf den gerade gesetzten Fokus nicht ein zweites Mal
weiterreicht. Die andere Hälfte gehört upstream, und ein Patch dagegen wäre
einer gegen fremdes Fokusverhalten statt gegen eine fehlende Möglichkeit.

**Gegenprobe im Spiel:** null Meldungen, `setFocus` dreimal statt dutzendfach,
und die Spur liest sich sauber:

```text
Spur Browser 1: createImmediately
Spur Browser 1: createBrowser zurück
Spur Browser 1: resize auf 1920x1080
Spur Browser 1: setFocus true
Spur Browser 1: erstes onPaint 1920x1080
```

---

## Die Nachprüfungen

### Harter Abbruch mit Wächter — dreimal

| Lauf | Helfer vorher | danach |
|---|---|---|
| 1 | 5 | **0** |
| 2 | 5 | **0** |
| 3 | 5 | **0** |

### Start ohne Gradle

Über das erzeugte `runClient.cmd` — das, was eine Entwicklungsumgebung im
Kern auch tut:

```text
ProcessGuard: aktiv — Job Object mit KILL_ON_JOB_CLOSE (schon im Job: nein)
```

**Ungeprüft bleibt der echte Launcher.** Dort kann der Prozess bereits in
einem Job liegen; `AssignProcessToJobObject` scheiterte dann mit Fehler 5, und
der Protokolltext dafür steht bereit.

### Messwerte nach dem Fix — und warum sie sich verschoben haben

Der Rechner hat inzwischen **vier Monitore statt einem**. Absolute Zahlen sind
deshalb nicht mit denen aus B4 vergleichbar. Was vergleichbar ist, ist
dieselbe Messung auf beiden Wegen, unmittelbar nacheinander:

| Größe | MCEF (CEF 116) | eigene Laufzeit (CEF 146) |
|---|---|---|
| Takt A p50 | 33,40 ms = **29,9/s** | **16,58 ms = 60,3/s** |
| Eingabe→Bild p50 | 32,1 ms | **27,1 ms** |
| Eingabe→Bild p95 | **55,4 ms** | 59,6 ms |
| Upload p50 | **9,7 ms** | 13,9 ms |
| Minecrafts Bildzeit p50 | 9,0 ms | 9,2 ms |

**Der Takt ist der Punkt:** doppelt so viele Bilder, und MCEFs 29,9 zeigen die
Deckelung, gegen die der ganze Umbau angetreten ist. Beim p95 liegt der neue
Weg heute vier Millisekunden hinter MCEF — bei doppelter Bildzahl in Stufe A
und auf einem Rechner, der zwischen den Messungen drei Monitore dazubekommen
hat. Ein Rückschritt durch den Fokus-Fix ist ausgeschlossen: Er nimmt Arbeit
weg, er fügt keine hinzu.

---

## Was noch offen ist

```text
Handprüfung in Monaco     Liste steht, nicht gefahren — braucht einen Menschen
echter Launcher           ungeprüft, Protokolltext steht bereit
p95 in Stufe B            heute vier Millisekunden hinter MCEF, bei anderer
                          Monitorlage als in B4 gemessen — nachmessen, wenn
                          der Rechner wieder in einem festen Zustand ist
```

Die Liste für die Handprüfung liegt als `handpruefung-monaco.md` bereit —
zwanzig Zeilen, je eine Spalte für beide Wege.

---

## Vorschlag für den nächsten Schritt

Nicht ausgeführt, nur vorgeschlagen:

```text
1. Handprüfung in Monaco fahren — nach docs/handpruefung-monaco.md
danach erst
B8  MCEF-Importe raus
B9  MCEF entfernen
```
