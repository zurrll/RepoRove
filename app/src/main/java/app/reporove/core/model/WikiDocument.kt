package app.reporove.core.model

import kotlinx.serialization.Serializable

@Serializable data class WikiPage(val title: String, val slug: String)
@Serializable data class WikiDocument(val title: String, val html: String, val pages: List<WikiPage>)
