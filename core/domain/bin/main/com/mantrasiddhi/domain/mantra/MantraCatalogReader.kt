/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses and validates mantra definition files.
 *
 * Everything here is a pure function over JSON text, so the whole catalog format is
 * unit-testable without touching the classpath or Android. [BundledMantraCatalog] is the
 * only production caller; it supplies the text and this decides whether it is usable.
 *
 * The reader is strict on purpose. A mantra ships inside the APK, so a definition that is
 * wrong is a build mistake, and every rejection names the file, the field and what was
 * expected. Keys it does not recognise are ignored, which leaves room for notes such as
 * `"_source"` alongside the real fields.
 *
 * The format is documented, with a worked example, in `docs/adding-a-mantra.md`.
 */
object MantraCatalogReader {

    /** The catalog index: which definitions to load, and which of them the app teaches. */
    data class CatalogIndex(
        val defaultMantraId: String,
        /** Definition file names, resolved relative to the index file. */
        val mantraFiles: List<String>
    )

    private val json = Json

    /**
     * Parses a catalog index (`mantras/catalog.json`).
     *
     * @param text The file's contents.
     * @param source File name used in error messages.
     * @throws MantraCatalogException when the file is not valid JSON or a field is
     *   missing, empty or of the wrong type.
     */
    fun readIndex(text: String, source: String): CatalogIndex {
        val root = parse(text, source)
        val files = root.required("mantras").array().map { it.nonBlankString() }
        if (files.isEmpty()) {
            root.required("mantras").fail("must list at least one definition file")
        }
        return CatalogIndex(
            defaultMantraId = root.required("defaultMantraId").nonBlankString(),
            mantraFiles = files
        )
    }

    /**
     * Parses one mantra definition (`mantras/<id>.json`).
     *
     * Word indices are assigned from the order of the `words` array rather than read from
     * the file, so a definition cannot claim an order it does not have.
     *
     * @param text The file's contents.
     * @param source File name used in error messages.
     * @throws MantraCatalogException when the file is not valid JSON, a required field is
     *   missing or malformed, or the definition is internally inconsistent (padas out of
     *   order, duplicate transliterations, benchmark timings that run backwards).
     */
    fun readMantra(text: String, source: String): Mantra {
        val root = parse(text, source)
        val padaCount = root.required("padaCount").positiveInt()
        val words = readWords(root, padaCount)

        val mantra = Mantra(
            id = root.required("id").nonBlankString().lowercase(),
            name = root.required("name").nonBlankString(),
            words = words,
            padaCount = padaCount,
            aksharaCount = root.required("aksharaCount").positiveInt(),
            benchmark = root.optional("benchmark")?.let { readBenchmark(it) },
            textRules = root.optional("textRules")?.let { readTextRules(it) } ?: MantraTextRules.NONE
        )
        verifyBenchmarkTimings(root, mantra)
        return mantra
    }

    // ------------------------------------------------------------------ words

    private fun readWords(root: Node, padaCount: Int): List<MantraWord> {
        val entries = root.required("words").array()
        if (entries.isEmpty()) root.required("words").fail("must list at least one word")
        val words = entries.mapIndexed { index, node -> readWord(node, index, padaCount) }
        verifyUniqueTransliterations(root, words)
        verifyPadasAreContiguous(root, words, padaCount)
        return words
    }

    private fun readWord(node: Node, index: Int, padaCount: Int): MantraWord {
        val padaField = node.required("pada")
        val pada = padaField.positiveInt()
        if (pada > padaCount) {
            padaField.fail("is $pada but the mantra declares padaCount $padaCount")
        }
        val syllablesField = node.required("syllables")
        val syllables = syllablesField.array().map { it.nonBlankString() }
        if (syllables.isEmpty()) syllablesField.fail("must list at least one syllable")

        val start = node.optional("benchmarkStartMs")?.nonNegativeLong() ?: 0L
        val end = node.optional("benchmarkEndMs")?.nonNegativeLong() ?: 0L
        if (end < start) {
            node.fail("has benchmarkEndMs ($end) before benchmarkStartMs ($start)")
        }
        return MantraWord(
            index = index,
            devanagari = node.required("devanagari").nonBlankString(),
            transliteration = node.required("transliteration").nonBlankString().lowercase(),
            syllables = syllables,
            pada = pada,
            benchmarkStartMs = start,
            benchmarkEndMs = end
        )
    }

    private fun verifyUniqueTransliterations(root: Node, words: List<MantraWord>) {
        val duplicates = words.groupingBy { it.transliteration }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicates.isEmpty()) return
        root.required("words").fail(
            "repeats the transliteration(s) ${duplicates.sorted().joinToString()}; " +
                "per-word scores are keyed by transliteration, so each word needs a distinct one"
        )
    }

    /**
     * Rejects definitions whose words are not grouped by pada in recitation order.
     *
     * [Mantra.padas] exposes each pada as an index *range*, which is only meaningful when
     * a pada's words are consecutive, so this is checked once here rather than defended
     * against everywhere downstream.
     */
    private fun verifyPadasAreContiguous(root: Node, words: List<MantraWord>, padaCount: Int) {
        val order = words.map { it.pada }
        val missing = (1..padaCount).filterNot { it in order }
        val field = root.required("words")
        if (missing.isNotEmpty()) {
            field.fail(
                "has no words in pada(s) ${missing.joinToString()}; every pada must carry at least one word"
            )
        }
        if (order != order.sorted()) {
            field.fail(
                "lists padas as ${order.joinToString()}; the words of a pada must be consecutive " +
                    "and padas must run 1..$padaCount in recitation order"
            )
        }
    }

    // -------------------------------------------------------------- benchmark

    private fun readBenchmark(node: Node): MantraBenchmark {
        val unitField = node.optional("timestampsUnit")
        val unit = when (unitField) {
            null -> MantraBenchmark.TimestampUnit.MILLISECONDS
            else -> {
                val name = unitField.nonBlankString()
                MantraBenchmark.TimestampUnit.fromJson(name) ?: unitField.fail(
                    "is '$name'; expected one of ${MantraBenchmark.TimestampUnit.jsonNames.joinToString()}"
                )
            }
        }
        return MantraBenchmark(
            audioResourceName = node.required("audioResourceName").nonBlankString(),
            timestampsAsset = node.optional("timestampsAsset")?.nonBlankString(),
            timestampsUnit = unit
        )
    }

    /**
     * A benchmark is only usable if every word is timed against it, so a definition that
     * declares audio must time all of its words or none of them.
     */
    private fun verifyBenchmarkTimings(root: Node, mantra: Mantra) {
        if (mantra.benchmark == null) return
        val untimed = mantra.words.filter { it.benchmarkDurationMs <= 0L }
        if (untimed.isEmpty()) return
        val field = root.required("benchmark")
        if (untimed.size == mantra.words.size) {
            field.fail(
                "declares a recording but no word is timed against it. Give every word " +
                    "benchmarkStartMs/benchmarkEndMs, or drop 'benchmark' to score this " +
                    "mantra from text alone"
            )
        }
        field.fail(
            "is only partly timed: ${untimed.joinToString { it.transliteration }} have no " +
                "duration, and per-word audio scoring needs a window for every word"
        )
    }

    // ------------------------------------------------------------- text rules

    private fun readTextRules(node: Node): MantraTextRules {
        val replacements = node.optional("compoundReplacements")?.array().orEmpty().map { entry ->
            MantraTextRules.CompoundReplacement(
                from = entry.required("from").nonBlankString().lowercase(),
                to = entry.required("to").nonBlankString().lowercase()
            )
        }
        val overrides = node.optional("spellingOverrides")?.let { field ->
            field.obj().keys.associate { heard ->
                heard.lowercase() to field.required(heard).nonBlankString().lowercase()
            }
        }.orEmpty()
        return MantraTextRules(compoundReplacements = replacements, spellingOverrides = overrides)
    }

    // ---------------------------------------------------------------- plumbing

    private fun parse(text: String, source: String): Node {
        val element = runCatching { json.parseToJsonElement(text) }
            .getOrElse { throw MantraCatalogException("$source is not valid JSON: ${it.message}", it) }
        return Node(source, ROOT_PATH, element).also { it.obj() }
    }

    private const val ROOT_PATH = "<root>"

    /**
     * One JSON value together with where it came from, so every failure can name the file
     * and the field that is wrong.
     */
    private class Node(val source: String, private val path: String, private val element: JsonElement) {

        fun fail(problem: String): Nothing =
            throw MantraCatalogException("$source: '$path' $problem.")

        fun obj(): JsonObject = element as? JsonObject ?: fail("must be an object")

        fun array(): List<Node> = (element as? JsonArray ?: fail("must be an array"))
            .mapIndexed { i, child -> Node(source, "$path[$i]", child) }

        /** The named field, or `null` when it is absent or explicitly `null`. */
        fun optional(name: String): Node? = obj()[name]
            ?.takeUnless { it is JsonNull }
            ?.let { Node(source, childPath(name), it) }

        fun required(name: String): Node =
            optional(name) ?: fail("is missing the required field '$name'")

        fun nonBlankString(): String {
            val primitive = element as? JsonPrimitive ?: fail("must be a string")
            if (!primitive.isString) fail("must be a string")
            val content = primitive.content.trim()
            if (content.isEmpty()) fail("must not be blank")
            return content
        }

        fun positiveInt(): Int {
            val value = wholeNumber()
            if (value < 1L || value > Int.MAX_VALUE.toLong()) {
                fail("must be a positive whole number, was $value")
            }
            return value.toInt()
        }

        fun nonNegativeLong(): Long {
            val value = wholeNumber()
            if (value < 0L) fail("must not be negative, was $value")
            return value
        }

        private fun wholeNumber(): Long {
            val primitive = element as? JsonPrimitive ?: fail("must be a number")
            if (primitive.isString) fail("must be a number, not a quoted string")
            return primitive.content.toLongOrNull() ?: fail("must be a whole number, was ${primitive.content}")
        }

        private fun childPath(name: String): String = if (path == ROOT_PATH) name else "$path.$name"
    }
}
