package com.itm.servicedesk

import com.itm.servicedesk.config.CaddyBootstrapper
import com.itm.servicedesk.config.CaddyEnsureStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaddyBootstrapperTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `ensureRunning returns DISABLED when CADDY_AUTO_START is disabled`() {
        var probed = false
        var started = false
        val bootstrapper =
            CaddyBootstrapper(
                portProbe = {
                    probed = true
                    false
                },
                processProbe = {
                    probed = true
                    false
                },
                caddyStarter = { _, _ ->
                    started = true
                    Result.success(Unit)
                },
                workingDir = tempDir,
            )

        assertEquals(CaddyEnsureStatus.DISABLED, bootstrapper.ensureRunning(mapOf("CADDY_AUTO_START" to "false")))
        assertEquals(CaddyEnsureStatus.DISABLED, bootstrapper.ensureRunning(mapOf("CADDY_AUTO_START" to "0")))
        assertEquals(CaddyEnsureStatus.DISABLED, bootstrapper.ensureRunning(mapOf("CADDY_AUTO_START" to "NO")))
        assertFalse(probed)
        assertFalse(started)
    }

    @Test
    fun `ensureRunning returns ALREADY_RUNNING when caddy port admin port or process is active`() {
        var starterCalled = false

        val byCaddyPort =
            CaddyBootstrapper(
                portProbe = { port -> port == 8443 },
                processProbe = { false },
                caddyStarter = { _, _ ->
                    starterCalled = true
                    Result.success(Unit)
                },
                workingDir = tempDir,
            )
        assertEquals(CaddyEnsureStatus.ALREADY_RUNNING, byCaddyPort.ensureRunning(emptyMap()))

        val byAdminPort =
            CaddyBootstrapper(
                portProbe = { port -> port == 2019 },
                processProbe = { false },
                caddyStarter = { _, _ ->
                    starterCalled = true
                    Result.success(Unit)
                },
                workingDir = tempDir,
            )
        assertEquals(CaddyEnsureStatus.ALREADY_RUNNING, byAdminPort.ensureRunning(emptyMap()))

        val byProcess =
            CaddyBootstrapper(
                portProbe = { false },
                processProbe = { true },
                caddyStarter = { _, _ ->
                    starterCalled = true
                    Result.success(Unit)
                },
                workingDir = tempDir,
            )
        assertEquals(CaddyEnsureStatus.ALREADY_RUNNING, byProcess.ensureRunning(emptyMap()))
        assertFalse(starterCalled)
    }

    @Test
    fun `ensureRunning returns CADDYFILE_NOT_FOUND when Caddyfile is missing`() {
        val bootstrapper =
            CaddyBootstrapper(
                portProbe = { false },
                processProbe = { false },
                caddyStarter = { _, _ -> Result.success(Unit) },
                workingDir = tempDir,
            )

        val status = bootstrapper.ensureRunning(mapOf("CADDY_PORT" to "9443"))
        assertEquals(CaddyEnsureStatus.CADDYFILE_NOT_FOUND, status)
    }

    @Test
    fun `ensureRunning returns EXECUTABLE_NOT_FOUND when caddy binary cannot be located`() {
        val caddyfile = tempDir.resolve("infra/Caddyfile")
        Files.createDirectories(caddyfile.parent)
        Files.writeString(caddyfile, "localhost:8443 {\n}\n")

        val bootstrapper =
            CaddyBootstrapper(
                portProbe = { false },
                processProbe = { false },
                caddyStarter = { _, _ -> Result.success(Unit) },
                osName = "Linux",
                workingDir = tempDir,
            )

        val status =
            bootstrapper.ensureRunning(
                mapOf(
                    "PATH" to tempDir.resolve("empty-bin").toString(),
                    "CADDY_BIN" to tempDir.resolve("missing-caddy").toString(),
                ),
            )
        assertEquals(CaddyEnsureStatus.EXECUTABLE_NOT_FOUND, status)
    }

    @Test
    fun `ensureRunning starts caddy and handles success and failure`() {
        val caddyfile = tempDir.resolve("backend/infra/Caddyfile")
        Files.createDirectories(caddyfile.parent)
        Files.writeString(caddyfile, "localhost:8443 {\n}\n")

        val fakeExe = tempDir.resolve("bin/caddy.exe")
        Files.createDirectories(fakeExe.parent)
        Files.writeString(fakeExe, "fake")

        var capturedExe: Path? = null
        var capturedConfig: Path? = null

        val successBootstrapper =
            CaddyBootstrapper(
                portProbe = { false },
                processProbe = { false },
                caddyStarter = { exe, config ->
                    capturedExe = exe
                    capturedConfig = config
                    Result.success(Unit)
                },
                osName = "Windows 11",
                workingDir = tempDir,
            )

        val successStatus =
            successBootstrapper.ensureRunning(
                mapOf("PATH" to fakeExe.parent.toString()),
            )
        assertEquals(CaddyEnsureStatus.STARTED, successStatus)
        assertEquals(fakeExe.normalize(), capturedExe)
        assertEquals(caddyfile.normalize(), capturedConfig)

        val failingBootstrapper =
            CaddyBootstrapper(
                portProbe = { false },
                processProbe = { false },
                caddyStarter = { _, _ -> Result.failure(IllegalStateException("boom")) },
                osName = "Windows 11",
                workingDir = tempDir,
            )

        val failedStatus =
            failingBootstrapper.ensureRunning(
                mapOf("CADDY_BIN" to fakeExe.toString()),
            )
        assertEquals(CaddyEnsureStatus.START_FAILED, failedStatus)
    }

    @Test
    fun `findCaddyExecutable resolves WinGet fallback paths on Windows`() {
        val localAppData = tempDir.resolve("LocalAppData")
        val wingetLinksExe = localAppData.resolve("Microsoft/WinGet/Links/caddy.exe")
        Files.createDirectories(wingetLinksExe.parent)
        Files.writeString(wingetLinksExe, "fake")

        val bootstrapper =
            CaddyBootstrapper(
                osName = "Windows 11",
                workingDir = tempDir,
            )

        val resolvedFromLinks =
            bootstrapper.findCaddyExecutable(
                mapOf(
                    "Path" to "",
                    "LOCALAPPDATA" to localAppData.toString(),
                ),
            )
        assertEquals(wingetLinksExe.normalize(), resolvedFromLinks)

        Files.deleteIfExists(wingetLinksExe)
        val packageExe = localAppData.resolve("Microsoft/WinGet/Packages/CaddyServer.Caddy_123/caddy.exe")
        Files.createDirectories(packageExe.parent)
        Files.writeString(packageExe, "fake")

        val resolvedFromPackage =
            bootstrapper.findCaddyExecutable(
                mapOf(
                    "LOCALAPPDATA" to localAppData.toString(),
                    "USERPROFILE" to tempDir.resolve("user").toString(),
                    "ProgramData" to tempDir.resolve("programdata").toString(),
                    "ProgramFiles" to tempDir.resolve("programfiles").toString(),
                ),
            )
        assertEquals(packageExe.normalize(), resolvedFromPackage)
    }

    @Test
    fun `resolveCaddyfile supports absolute and relative paths`() {
        val customConfig = tempDir.resolve("custom/MyCaddyfile")
        Files.createDirectories(customConfig.parent)
        Files.writeString(customConfig, "localhost:8443 {}")

        val bootstrapper = CaddyBootstrapper(workingDir = tempDir)

        val resolvedAbsolute =
            bootstrapper.resolveCaddyfile(mapOf("CADDYFILE_PATH" to customConfig.toAbsolutePath().toString()))
        assertNotNull(resolvedAbsolute)
        assertEquals(customConfig.normalize(), resolvedAbsolute)

        val missing = bootstrapper.resolveCaddyfile(mapOf("CADDYFILE_PATH" to "does/not/exist"))
        assertNull(missing)
    }

    @Test
    fun `default probes and helpers work deterministically`() {
        val boundPort: Int
        ServerSocket(0).use { server ->
            boundPort = server.localPort
            assertTrue(CaddyBootstrapper.defaultIsLocalPortOpen(boundPort))
        }
        assertFalse(CaddyBootstrapper.defaultIsLocalPortOpen(boundPort))

        assertTrue(CaddyBootstrapper.isCaddyCommand("caddy"))
        assertTrue(CaddyBootstrapper.isCaddyCommand("C:\\WinGet\\Links\\caddy.exe"))
        assertTrue(CaddyBootstrapper.isCaddyCommand("/usr/local/bin/caddy"))
        assertFalse(CaddyBootstrapper.isCaddyCommand("java.exe"))

        // Verify process probe runs without throwing
        CaddyBootstrapper.defaultIsCaddyProcessRunning()

        val logFile = tempDir.resolve("caddy.log")
        Files.writeString(logFile, "old line\n")
        val initialOffset = Files.size(logFile)
        Files.writeString(logFile, "old line\nnew error line 1\nnew error line 2\n")
        assertEquals(
            "new error line 1 | new error line 2",
            CaddyBootstrapper.readNewLogTail(logFile, initialOffset),
        )
        assertEquals("", CaddyBootstrapper.readNewLogTail(tempDir.resolve("missing.log"), 0L))

        val startFailure =
            CaddyBootstrapper.defaultStartCaddy(
                executable = tempDir.resolve("nonexistent-caddy${File.separator}caddy"),
                caddyfile = tempDir.resolve("infra/Caddyfile"),
            )
        assertTrue(startFailure.isFailure)
    }
}
