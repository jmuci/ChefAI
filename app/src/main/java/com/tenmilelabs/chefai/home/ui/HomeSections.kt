package com.tenmilelabs.chefai.home.ui

import com.tenmilelabs.chefai.home.data.model.ComponentModel

/**
 * One Home panel: a [ComponentModel.SectionHeader] and everything after it up to the next one.
 *
 * The SDUI payload is a flat list (`section_header`, `carousel`, `section_header`, …), but each
 * header and its carousel render together on one panel surface, so they are grouped client-side
 * rather than asking the server for a nesting it does not otherwise need.
 *
 * @property key stable across refreshes — the id of the section's first component.
 */
data class HomeSection(
    val key: String,
    val components: List<ComponentModel>,
)

/**
 * Groups [components] into [HomeSection]s: every [ComponentModel.SectionHeader] opens a new
 * section, and anything else joins the section currently open. Components that arrive before the
 * first header form a header-less section of their own. Order is preserved throughout.
 */
fun groupIntoSections(components: List<ComponentModel>): List<HomeSection> {
    val sections = mutableListOf<MutableList<ComponentModel>>()
    for (component in components) {
        if (component is ComponentModel.SectionHeader || sections.isEmpty()) {
            sections += mutableListOf(component)
        } else {
            sections.last() += component
        }
    }
    return sections.map { HomeSection(key = it.first().id, components = it) }
}
