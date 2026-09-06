/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

/**
 * Every mantra the app knows about, plus which one it currently teaches.
 *
 * The catalog is immutable and validated at load time: by the time one exists, every
 * mantra in it is internally consistent and [default] is guaranteed to resolve. Callers
 * therefore never have to defend against a half-built mantra.
 *
 * The shipped implementation is [BundledMantraCatalog], read from
 * `core/domain/src/main/resources/mantras`. Tests build small catalogs directly with
 * [of].
 */
interface MantraCatalog {

    /** All mantras, in the order the catalog index lists them. Never empty. */
    val mantras: List<Mantra>

    /**
     * The mantra the app teaches, scores and counts japa against.
     *
     * Chosen by `defaultMantraId` in the catalog index, so switching the app to another
     * bundled mantra is a one-line change to a JSON file.
     */
    val default: Mantra

    /** The mantra with this [id], or `null` when the catalog does not carry it. */
    fun find(id: String): Mantra?

    /**
     * The mantra with this [id].
     *
     * @throws MantraCatalogException when no such mantra is bundled — an unknown id is a
     *   packaging mistake, not a runtime condition to recover from.
     */
    fun require(id: String): Mantra =
        find(id) ?: throw MantraCatalogException(
            "No mantra with id '$id'. Bundled ids: ${mantras.joinToString { it.id }}."
        )

    /**
     * Looks a reference word up across the whole catalog, preferring [default].
     *
     * Used only where a caller holds a transliteration but not the mantra it came from
     * (historic sessions, the presentation layer's feedback strings). Prefer
     * [Mantra.wordFor] whenever the mantra is known.
     */
    fun wordFor(transliteration: String): MantraWord? =
        default.wordFor(transliteration)
            ?: mantras.firstNotNullOfOrNull { it.wordFor(transliteration) }

    companion object {
        /**
         * Builds a validated catalog.
         *
         * @param mantras The mantras, in index order; must be non-empty with unique ids.
         * @param defaultMantraId Which of them the app teaches.
         * @throws MantraCatalogException when the list is empty, ids collide, or
         *   [defaultMantraId] names no mantra in [mantras].
         */
        fun of(mantras: List<Mantra>, defaultMantraId: String): MantraCatalog =
            ImmutableMantraCatalog(mantras, defaultMantraId)
    }
}

/** The only [MantraCatalog] implementation: a validated, immutable snapshot. */
private class ImmutableMantraCatalog(
    override val mantras: List<Mantra>,
    defaultMantraId: String
) : MantraCatalog {

    private val byId: Map<String, Mantra>

    override val default: Mantra

    init {
        if (mantras.isEmpty()) {
            throw MantraCatalogException("The mantra catalog is empty; at least one mantra is required.")
        }
        val duplicates = mantras.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw MantraCatalogException(
                "Duplicate mantra ids: ${duplicates.sorted().joinToString()}. Each id must be unique."
            )
        }
        byId = mantras.associateBy { it.id }
        default = byId[defaultMantraId] ?: throw MantraCatalogException(
            "defaultMantraId '$defaultMantraId' names no bundled mantra. " +
                "Bundled ids: ${mantras.joinToString { it.id }}."
        )
    }

    override fun find(id: String): Mantra? = byId[id]
}
