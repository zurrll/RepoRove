package app.reporove

import app.reporove.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PreferencesTest {
    @Test fun hiddenHomeFallsBackAndNavigationNeverBecomesEmpty() {
        val prefs = Preferences(tabs = listOf(MainTab.Library, MainTab.Library), home = MainTab.Feed).normalized()
        assertEquals(listOf(MainTab.Library), prefs.tabs)
        assertEquals(MainTab.Library, prefs.home)
        assertEquals(listOf(MainTab.Discover), Preferences(tabs = emptyList()).normalized().tabs)
        assertEquals(5, Preferences(tabs = MainTab.entries).normalized().tabs.size)
    }
    @Test fun allRepositorySectionsCanMoveToMoreAndEmptyOverviewIsPreserved() {
        val prefs = Preferences(repoTabs = emptyList(), modules = emptyList()).normalized()
        assertTrue(prefs.repoTabs.isEmpty())
        assertTrue(prefs.modules.isEmpty())
    }
    @Test fun malformedReadingAndInterestSettingsAreNormalized() {
        val prefs = Preferences(textScale = Float.NaN, interests = listOf(" Android ", "android", "a b", "../x", "kotlin")).normalized()
        assertEquals(1f, prefs.textScale)
        assertEquals(listOf("android", "kotlin"), prefs.interests)
        assertEquals(1.3f, Preferences(textScale = 8f).normalized().textScale)
    }
    @Test fun searchFiltersRespectSelectedContentType() {
        assertEquals("bug is:issue is:open", SearchQueries.build(SearchKind.Issues, " bug ", "Java", "open"))
        assertEquals("fix is:pr is:merged", SearchQueries.build(SearchKind.Pulls, "fix", null, "merged"))
        assertEquals("bug is:issue", SearchQueries.build(SearchKind.Issues, "bug", null, "merged"))
        assertEquals("flow language:Kotlin", SearchQueries.build(SearchKind.Code, "flow", "Kotlin", "closed"))
        assertEquals("alice", SearchQueries.build(SearchKind.Users, "alice", "Java", "open"))
        assertFalse(SearchQueries.discovery("bad topic").contains("topic:"))
    }
}
