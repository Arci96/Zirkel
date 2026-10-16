package de.zirkel;

/**
 * Ausrichtung der Kreisebene.
 * u/v sind die beiden Achsen der Ebene, in der der Kreis liegt.
 */
enum Orientation {
    FLOOR("Boden", "Ost", "West", "Süd", "Nord"),
    WALL_X("Wand (X-Achse)", "Ost", "West", "Oben", "Unten"),
    WALL_Z("Wand (Z-Achse)", "Süd", "Nord", "Oben", "Unten");

    final String label;
    final String uPos, uNeg, vPos, vNeg;

    Orientation(String label, String uPos, String uNeg, String vPos, String vNeg) {
        this.label = label;
        this.uPos = uPos;
        this.uNeg = uNeg;
        this.vPos = vPos;
        this.vNeg = vNeg;
    }

    Orientation next() {
        Orientation[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
