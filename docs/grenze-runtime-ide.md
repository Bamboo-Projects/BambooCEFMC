# Wo die Laufzeitumgebung aufhört

Festgelegt am 1. September 2026, unmittelbar nach der bestandenen
Handprüfung. Die Grenze ist keine Ordnerfrage, sondern eine
Zuständigkeitsfrage: **Was ein Browser können muss, gehört in die
Web-Laufzeitumgebung. Was diese Seite zeigt, gehört in FactoryNetwork.**

---

## Die Aufteilung

| CEF/Web-Runtime | FactoryNetwork |
|---|---|
| CEF starten und laden | die IDE als Web-Anwendung |
| Zeichnen ohne Fenster (OSR) | Explorer und Ordnerdarstellung |
| Textur-Übertragung | Registerkarten |
| Eingabeereignisse | Monacos Einstellungen |
| Mauszeiger | Terminal |
| Fokus | Anbindung des Sprachdienstes |
| eigene Ressourcenschemata | Oberfläche für das Dateisystem |
| Lebenslauf der Browser | Oberfläche für Maschinen und Übersichten |
| ProcessGuard | |
| Auslieferung der Laufzeitumgebung | |

---

## Was die Grenze in der Praxis entscheidet

**Ein Fehler gehört dorthin, wo seine Ursache liegt, nicht dorthin, wo er
auffällt.** Die Handprüfung hat das dreimal gezeigt, und jedes Mal sah es
zuerst nach der anderen Seite aus:

```text
Eingabetaste macht keine Zeile   sah nach Monaco aus, war die Laufzeit
Escape schließt nicht            sah nach Monaco aus, war unser Bildschirm
falscher Mauszeiger              sah nach der Seite aus, war die Laufzeit
```

Alle drei lagen in der Laufzeitumgebung. Der Umkehrschluss gilt genauso: Ein
fehlender Ordner in der Seitenleiste bleibt ein Fehler der Seite, auch wenn er
in einem Browser auffällt, den wir selbst gebaut haben.

**Für die Freigabe der Laufzeitumgebung zählt nur die linke Spalte.** Punkte
aus der rechten sind keine Sperre — sie stehen in
[`ide-offene-punkte.md`](ide-offene-punkte.md).
