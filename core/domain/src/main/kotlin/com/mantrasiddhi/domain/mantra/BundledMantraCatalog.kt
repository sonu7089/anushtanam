/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

/**
 * The [MantraCatalog] shipped inside the app.
 *
 * Definitions live as JSON on this module's classpath, under
 * `core/domain/src/main/resources/mantras`, and are read once on first use. Java
 * resources are packaged into the APK exactly as they are into the JAR, so the same
 * files serve unit tests and the device.
 *
 * Adding a mantra means adding `mantras/<id>.json` and listing it in `mantras/catalog.json`.
 * Nothing in this class, the evaluation engine or the use cases changes. See
 * `docs/adding-a-mantra.md`.
 *
 * Failures are fatal rather than silently absorbed: the catalog is part of the build, so
 * an unreadable or inconsistent definition is a packaging bug that must surface in
 * testing rather than turn into a mantra that scores every recitation as empty.
 */
object BundledMantraCatalog : MantraCatalog {

    /** Classpath directory the catalog is read from. */
    const val RESOURCE_DIRECTORY: String = "/mantras"

    /** Classpath location of the catalog index. */
    const val INDEX_RESOURCE: String = "$RESOURCE_DIRECTORY/catalog.json"

    private val delegate: MantraCatalog by lazy(::load)

    override val mantras: List<Mantra> get() = delegate.mantras

    override val default: Mantra get() = delegate.default

    override fun find(id: String): Mantra? = delegate.find(id)

    private fun load(): MantraCatalog {
        val index = MantraCatalogReader.readIndex(readResource(INDEX_RESOURCE), INDEX_RESOURCE)
        val mantras = index.mantraFiles.map { file ->
            val resource = "$RESOURCE_DIRECTORY/$file"
            MantraCatalogReader.readMantra(readResource(resource), resource)
        }
        return MantraCatalog.of(mantras, index.defaultMantraId)
    }

    private fun readResource(path: String): String =
        javaClass.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw MantraCatalogException(
                "Bundled mantra resource '$path' is missing. Definition files belong in " +
                    "core/domain/src/main/resources${RESOURCE_DIRECTORY} and must be listed " +
                    "in ${INDEX_RESOURCE}."
            )
}
