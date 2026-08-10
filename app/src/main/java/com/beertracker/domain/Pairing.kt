package com.beertracker.domain

/**
 * The food pairing vocabulary Systembolaget publishes per product, in the
 * `tasteSymbols` field of the product search API.
 *
 * [symbol] is the Swedish key used only to match API responses. [label] is
 * what the app stores in `TriedBeer.goesWellWith` and shows to the user.
 * Storing the label rather than the enum name keeps `goesWellWith` a plain
 * list of strings, so a pairing the user types themselves lives in the same
 * list without a separate column.
 *
 * Declaration order is the display order everywhere: meats, then seafood,
 * then the rest of the plate, then the occasion values.
 */
enum class Pairing(val symbol: String, val label: String) {
    PORK("Fläsk", "Pork"),
    POULTRY("Fågel", "Poultry"),
    LAMB("Lamm", "Lamb"),
    BEEF("Nöt", "Beef"),
    GAME("Vilt", "Game"),
    FISH("Fisk", "Fish"),
    SHELLFISH("Skaldjur", "Shellfish"),
    VEGETABLES("Grönsaker", "Vegetables"),
    CHEESE("Ost", "Cheese"),
    DESSERT("Dessert", "Dessert"),
    SPICY("Kryddstarkt", "Spicy food"),
    ASIAN("Asiatiskt", "Asian food"),
    BUFFET("Buffémat", "Buffet"),
    APERITIF("Aperitif", "Aperitif"),
    SOCIAL("Sällskapsdryck", "Social drink"),
    ;

    companion object {
        private val bySymbol = entries.associateBy { it.symbol }
        private val byLabel = entries.associateBy { it.label }

        /**
         * Exact match, deliberately not fuzzy. If Systembolaget renames a
         * symbol, that pairing goes missing rather than silently becoming
         * the wrong one.
         */
        fun fromSymbol(symbol: String): Pairing? = bySymbol[symbol]

        fun fromLabel(label: String): Pairing? = byLabel[label]
    }
}
