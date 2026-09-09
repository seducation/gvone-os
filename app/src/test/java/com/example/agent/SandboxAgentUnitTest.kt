package com.example.agent

import com.example.agent.sandbox.AgentPersona
import com.example.agent.sandbox.AgentPlanStep
import com.example.data.command.CommandEngine
import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import org.junit.Assert.*
import org.junit.Test

class SandboxAgentUnitTest {

    @Test
    fun testAgentPersonaMatching() {
        assertEquals(AgentPersona.ATLAS, AgentPersona.fromString("atlas"))
        assertEquals(AgentPersona.COMET, AgentPersona.fromString("comet"))
        assertEquals(AgentPersona.DIA, AgentPersona.fromString("dia"))
        assertEquals(AgentPersona.AUTO, AgentPersona.fromString("auto"))
        assertEquals(AgentPersona.AUTO, AgentPersona.fromString("unknown_persona"))
    }

    @Test
    fun testPersonaAttributes() {
        val atlas = AgentPersona.ATLAS
        assertTrue(atlas.description.contains("Autonomous Browser Operator"))
        assertEquals("ATLAS", atlas.badge)

        val comet = AgentPersona.COMET
        assertTrue(comet.description.contains("Multi-Source Research"))
        assertEquals("COMET", comet.badge)

        val dia = AgentPersona.DIA
        assertTrue(dia.description.contains("Live File & Code Sandbox"))
        assertEquals("DIA", dia.badge)
    }

    @Test
    fun testCommandEngineHasAgentAndGroupCommands() {
        val commands = CommandEngine.BUILT_IN_COMMANDS
        val triggers = commands.flatMap { it.getAllTriggers() }

        assertTrue("Command list must contain /agent", triggers.contains("/agent"))
        assertTrue("Command list must contain /group", triggers.contains("/group"))
        assertTrue("Command list must contain /sandbox", triggers.contains("/sandbox"))
        assertTrue("Command list must contain /organize", triggers.contains("/organize"))
    }

    @Test
    fun testTerminalLineAgentTypes() {
        val planLine = TerminalLine("[PLAN] Step 1 -> Step 2", TerminalLineType.AGENT_PLAN)
        val stepLine = TerminalLine("[STEP 1/3] Navigating...", TerminalLineType.AGENT_STEP)
        val thoughtLine = TerminalLine("  Thought: extracting data", TerminalLineType.AGENT_THOUGHT)
        val toolLine = TerminalLine("  [TOOL] web_search", TerminalLineType.AGENT_TOOL)

        assertEquals(TerminalLineType.AGENT_PLAN, planLine.type)
        assertEquals(TerminalLineType.AGENT_STEP, stepLine.type)
        assertEquals(TerminalLineType.AGENT_THOUGHT, thoughtLine.type)
        assertEquals(TerminalLineType.AGENT_TOOL, toolLine.type)
    }

    @Test
    fun testAgentPlanStepStructure() {
        val step = AgentPlanStep(
            stepNumber = 1,
            description = "Perform search query",
            tool = "search_and_browse",
            expectedOutcome = "Search results extracted"
        )

        assertEquals(1, step.stepNumber)
        assertEquals("Perform search query", step.description)
        assertEquals("search_and_browse", step.tool)
        assertEquals("Search results extracted", step.expectedOutcome)
    }
}
