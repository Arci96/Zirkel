# Zirkel (Paper 1.21.11)

Plugin zum Nachbauen von Kreisen und Halbkreisen (Boden und Wand) mit durchsichtiger Vorschau.
Java 21 erforderlich.

## Mit GitHub bauen (ohne lokale Installation)

1. Auf github.com ein neues Repository anlegen (z. B. `Zirkel`).
2. Den Inhalt dieses Ordners hochladen (Add file -> Upload files), inklusive des versteckten Ordners `.github`.
   Oder per Git:
       git init
       git add .
       git commit -m "Erste Version"
       git branch -M main
       git remote add origin https://github.com/DEIN-NAME/Zirkel.git
       git push -u origin main
3. Reiter **Actions** öffnen. Der Build startet automatisch.
4. Nach ca. 1 Minute den fertigen Lauf anklicken und unter **Artifacts** `Zirkel` herunterladen.
   Darin liegt `Zirkel.jar` -> in den `plugins`-Ordner des Servers legen.

### Release mit festem Download-Link
   git tag v1.0.0
   git push origin v1.0.0
Danach liegt die Jar im Reiter **Releases**.

## Lokal bauen
   mvn clean package   ->  target/Zirkel.jar

## Steuerung (Item: /zirkel give)
- Rechtsklick Block      Mittelpunkt setzen
- Rechtsklick Luft       Mittelpunkt auf deine Position
- Shift + Rechtsklick    Ausrichtung: Boden -> Wand (X-Achse) -> Wand (Z-Achse)
- Linksklick             Vollkreis -> Halbkreis (4 Richtungen)
- Shift + Linksklick     Zirkel entfernen
- Shift + Mausrad        Radius (1-64)
- Q / Shift + Q          hoch / runter

Befehle: /zirkel give | radius <n> | hoch [n] | runter [n] | move <dx> <dy> <dz>
         | ausrichtung <boden|wand-x|wand-z> | form [voll] | block <material> | loeschen
Permission: zirkel.use (Standard: OP)
