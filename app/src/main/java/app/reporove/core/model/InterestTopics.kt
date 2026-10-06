package app.reporove.core.model

/** The Explore catalog suggests names; any valid GitHub topic slug may be saved. */
object InterestTopics {
    fun normalize(text: String): String? = text.trim().lowercase().takeIf { it.matches(Regex("[a-z0-9][a-z0-9-]{0,49}")) }
}
