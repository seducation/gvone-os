package com.example

import com.example.data.environment.DEFAULT_PERSONAL_START_PAGE
import com.example.data.model.BrowserTab
import com.example.data.model.Environment
import com.example.data.model.EnvironmentBackground
import com.example.data.model.EnvironmentLayoutMode
import com.example.data.model.TabGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EnvironmentTabsUnitTest {

    @Test
    fun personalEnvironmentDefaultStartPage_isCorrect() {
        assertEquals("https://rssgroupfeed-jaelvwfd.manus.space", DEFAULT_PERSONAL_START_PAGE)
    }

    @Test
    fun environmentWithCustomStartPage_storesStartPageUrl() {
        val customUrl = "https://example.com/custom"
        val env = Environment(
            id = UUID.randomUUID().toString(),
            name = "Work Project",
            iconName = "Work",
            themeMode = "dark",
            background = EnvironmentBackground(),
            layoutMode = EnvironmentLayoutMode.GRID,
            objects = emptyList(),
            startPageUrl = customUrl
        )
        assertEquals(customUrl, env.startPageUrl)
    }

    @Test
    fun tabsAndGroups_filterCorrectlyByEnvironmentId() {
        val personalEnvId = "personal"
        val workEnvId = "work"

        val personalTab = BrowserTab(
            id = "tab1",
            title = "RSS Feed",
            url = DEFAULT_PERSONAL_START_PAGE,
            isPrivate = false,
            tabGroupId = null,
            environmentId = personalEnvId
        )

        val workTab = BrowserTab(
            id = "tab2",
            title = "Work Docs",
            url = "https://docs.google.com",
            isPrivate = false,
            tabGroupId = "work_group_1",
            environmentId = workEnvId
        )

        val allTabs = listOf(personalTab, workTab)

        val personalTabs = allTabs.filter { it.environmentId == personalEnvId }
        val workTabs = allTabs.filter { it.environmentId == workEnvId }

        assertEquals(1, personalTabs.size)
        assertEquals("tab1", personalTabs.first().id)
        assertEquals(DEFAULT_PERSONAL_START_PAGE, personalTabs.first().url)

        assertEquals(1, workTabs.size)
        assertEquals("tab2", workTabs.first().id)
        assertEquals(workEnvId, workTabs.first().environmentId)

        val personalGroup = TabGroup(id = "g1", name = "News", order = 0, environmentId = personalEnvId)
        val workGroup = TabGroup(id = "g2", name = "Sprint", order = 0, environmentId = workEnvId)
        val allGroups = listOf(personalGroup, workGroup)

        val filteredWorkGroups = allGroups.filter { it.environmentId == workEnvId }
        assertEquals(1, filteredWorkGroups.size)
        assertEquals("Sprint", filteredWorkGroups.first().name)
    }
}
