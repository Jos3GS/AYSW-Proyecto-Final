package com.itm.servicedesk

import com.itm.servicedesk.config.DockerCommandResult
import com.itm.servicedesk.config.PostgresBootstrapper
import com.itm.servicedesk.config.PostgresEnsureStatus
import com.itm.servicedesk.domain.builder.DatabaseConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresBootstrapperTest {
    @TempDir
    lateinit var tempDir: Path

    private fun buildDbConfig(
        jdbcUrl: String = "jdbc:postgresql://localhost:5432/itm_service_desk",
        user: String = "itm_user",
        password: String = "local_secret",
    ): DatabaseConfig =
        DatabaseConfig(
            jdbcUrl = jdbcUrl,
            user = user,
            password = password,
            maxPoolSize = 10,
            connectionTimeoutMs = 2000L,
            validationTimeoutMs = 1500L,
        )

    private fun createFakeDockerExe(): Path {
        val fakeExe = tempDir.resolve("bin/docker.exe")
        Files.createDirectories(fakeExe.parent)
        Files.writeString(fakeExe, "fake")
        return fakeExe
    }

    @Test
    fun `ensureRunning returns DISABLED when POSTGRES_AUTO_START is disabled`() {
        var probed = false
        val bootstrapper =
            PostgresBootstrapper(
                portProbe = {
                    probed = true
                    false
                },
                dockerRunner = { _, _, _ -> Result.success(DockerCommandResult(0, "")) },
            )

        assertEquals(
            PostgresEnsureStatus.DISABLED,
            bootstrapper.ensureRunning(mapOf("POSTGRES_AUTO_START" to "false"), buildDbConfig()),
        )
        assertEquals(
            PostgresEnsureStatus.DISABLED,
            bootstrapper.ensureRunning(mapOf("POSTGRES_AUTO_START" to "0"), buildDbConfig()),
        )
        assertEquals(
            PostgresEnsureStatus.DISABLED,
            bootstrapper.ensureRunning(mapOf("POSTGRES_AUTO_START" to "NO"), buildDbConfig()),
        )
        assertFalse(probed)
    }

    @Test
    fun `ensureRunning returns NON_LOCAL_HOST for remote or invalid JDBC URLs`() {
        val bootstrapper =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, _, _ -> Result.success(DockerCommandResult(0, "")) },
            )

        assertEquals(
            PostgresEnsureStatus.NON_LOCAL_HOST,
            bootstrapper.ensureRunning(
                emptyMap(),
                buildDbConfig(jdbcUrl = "jdbc:postgresql://db.production.internal:5432/itm_service_desk"),
            ),
        )
        assertEquals(
            PostgresEnsureStatus.NON_LOCAL_HOST,
            bootstrapper.ensureRunning(
                emptyMap(),
                buildDbConfig(jdbcUrl = "jdbc:mysql://localhost:3306/other"),
            ),
        )
    }

    @Test
    fun `ensureRunning returns ALREADY_RUNNING when local port is already open`() {
        var dockerCalled = false
        val bootstrapper =
            PostgresBootstrapper(
                portProbe = { port -> port == 5432 },
                dockerRunner = { _, _, _ ->
                    dockerCalled = true
                    Result.success(DockerCommandResult(0, ""))
                },
            )

        val status = bootstrapper.ensureRunning(emptyMap(), buildDbConfig())
        assertEquals(PostgresEnsureStatus.ALREADY_RUNNING, status)
        assertFalse(dockerCalled)
    }

    @Test
    fun `ensureRunning returns DOCKER_NOT_FOUND when docker binary cannot be located`() {
        val bootstrapper =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, _, _ -> Result.success(DockerCommandResult(0, "")) },
                osName = "Linux",
            )

        val status =
            bootstrapper.ensureRunning(
                env =
                    mapOf(
                        "PATH" to tempDir.resolve("empty-bin").toString(),
                        "DOCKER_BIN" to tempDir.resolve("missing-docker").toString(),
                    ),
                databaseConfig = buildDbConfig(),
            )
        assertEquals(PostgresEnsureStatus.DOCKER_NOT_FOUND, status)
    }

    @Test
    fun `ensureRunning starts stopped container and waits until ready`() {
        val dockerExe = createFakeDockerExe()
        val calls = mutableListOf<List<String>>()
        var portCheckCount = 0
        var now = 0L

        val bootstrapper =
            PostgresBootstrapper(
                portProbe = {
                    portCheckCount++
                    // Initial probe false; first readiness poll false; second readiness poll true
                    portCheckCount >= 3
                },
                dockerRunner = { _, args, _ ->
                    calls.add(args)
                    when (args.firstOrNull()) {
                        "inspect" -> Result.success(DockerCommandResult(0, "false"))
                        "start" -> Result.success(DockerCommandResult(0, "itm-postgres18"))
                        "exec" -> Result.success(DockerCommandResult(0, "127.0.0.1:5432 - accepting connections"))
                        else -> Result.failure(IllegalStateException("Unexpected command: $args"))
                    }
                },
                sleepFn = { ms -> now += ms },
                nowMillisFn = { now },
                osName = "Windows 11",
                readyTimeoutMs = 5_000L,
                pollIntervalMs = 200L,
            )

        val status =
            bootstrapper.ensureRunning(
                env = mapOf("DOCKER_BIN" to dockerExe.toString()),
                databaseConfig = buildDbConfig(),
            )

        assertEquals(PostgresEnsureStatus.STARTED, status)
        assertEquals(listOf("inspect", "-f", "{{.State.Running}}", "itm-postgres18"), calls[0])
        assertEquals(listOf("start", "itm-postgres18"), calls[1])
        assertEquals("exec", calls[2].first())
        assertTrue(calls[2].contains("pg_isready"))
    }

    @Test
    fun `ensureRunning creates container when missing and passes configured credentials and port`() {
        val dockerExe = createFakeDockerExe()
        val calls = mutableListOf<List<String>>()
        var containerCreated = false
        var now = 0L

        val bootstrapper =
            PostgresBootstrapper(
                portProbe = { containerCreated },
                dockerRunner = { _, args, _ ->
                    calls.add(args)
                    when (args.firstOrNull()) {
                        "inspect" -> {
                            Result.success(DockerCommandResult(1, "Error: No such object: custom-pg"))
                        }

                        "run" -> {
                            containerCreated = true
                            Result.success(DockerCommandResult(0, "container-id-123"))
                        }

                        "exec" -> {
                            Result.success(DockerCommandResult(0, "accepting connections"))
                        }

                        else -> {
                            Result.failure(IllegalStateException("Unexpected command: $args"))
                        }
                    }
                },
                sleepFn = { ms -> now += ms },
                nowMillisFn = { now },
                osName = "Windows 11",
                readyTimeoutMs = 5_000L,
                pollIntervalMs = 200L,
            )

        val status =
            bootstrapper.ensureRunning(
                env =
                    mapOf(
                        "PATH" to dockerExe.parent.toString(),
                        "POSTGRES_CONTAINER_NAME" to "custom-pg",
                        "POSTGRES_IMAGE" to "postgres:18.1-alpine",
                    ),
                databaseConfig =
                    buildDbConfig(
                        jdbcUrl = "jdbc:postgresql://127.0.0.1:5433/custom_db?sslmode=disable",
                        user = "custom_user",
                        password = "custom_password",
                    ),
            )

        assertEquals(PostgresEnsureStatus.CREATED_AND_STARTED, status)
        val runArgs = calls[1]
        assertEquals(
            listOf(
                "run",
                "-d",
                "--name",
                "custom-pg",
                "-e",
                "POSTGRES_DB=custom_db",
                "-e",
                "POSTGRES_USER=custom_user",
                "-e",
                "POSTGRES_PASSWORD=custom_password",
                "-p",
                "5433:5432",
                "postgres:18.1-alpine",
            ),
            runArgs,
        )
    }

    @Test
    fun `ensureRunning handles running container waiting for readiness and timeout`() {
        val dockerExe = createFakeDockerExe()
        var now = 0L
        var portOpen = false

        val readyBootstrapper =
            PostgresBootstrapper(
                portProbe = {
                    val result = portOpen
                    portOpen = true
                    result
                },
                dockerRunner = { _, args, _ ->
                    when (args.firstOrNull()) {
                        "inspect" -> Result.success(DockerCommandResult(0, "true"))
                        "exec" -> Result.success(DockerCommandResult(0, "accepting connections"))
                        else -> Result.failure(IllegalStateException("Unexpected command: $args"))
                    }
                },
                sleepFn = { ms -> now += ms },
                nowMillisFn = { now },
                osName = "Windows 11",
                readyTimeoutMs = 1_000L,
                pollIntervalMs = 250L,
            )
        assertEquals(
            PostgresEnsureStatus.ALREADY_RUNNING,
            readyBootstrapper.ensureRunning(mapOf("DOCKER_BIN" to dockerExe.toString()), buildDbConfig()),
        )

        var timeoutNow = 0L
        val timeoutBootstrapper =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, args, _ ->
                    when (args.firstOrNull()) {
                        "inspect" -> Result.success(DockerCommandResult(0, "true"))
                        else -> Result.success(DockerCommandResult(1, "not ready"))
                    }
                },
                sleepFn = { ms -> timeoutNow += ms },
                nowMillisFn = { timeoutNow },
                osName = "Windows 11",
                readyTimeoutMs = 400L,
                pollIntervalMs = 200L,
            )
        assertEquals(
            PostgresEnsureStatus.START_FAILED,
            timeoutBootstrapper.ensureRunning(mapOf("DOCKER_BIN" to dockerExe.toString()), buildDbConfig()),
        )
    }

    @Test
    fun `ensureRunning returns START_FAILED on docker errors and readiness timeouts`() {
        val dockerExe = createFakeDockerExe()
        val env = mapOf("DOCKER_BIN" to dockerExe.toString())

        // 1. Daemon error during inspect
        val inspectDaemonError =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, _, _ ->
                    Result.success(DockerCommandResult(1, "Cannot connect to the Docker daemon"))
                },
                osName = "Windows 11",
            )
        assertEquals(PostgresEnsureStatus.START_FAILED, inspectDaemonError.ensureRunning(env, buildDbConfig()))

        // 2. Start exits non-zero
        val startNonZero =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, args, _ ->
                    if (args.first() == "inspect") {
                        Result.success(DockerCommandResult(0, "false"))
                    } else {
                        Result.success(DockerCommandResult(1, "port is already allocated"))
                    }
                },
                osName = "Windows 11",
            )
        assertEquals(PostgresEnsureStatus.START_FAILED, startNonZero.ensureRunning(env, buildDbConfig()))

        // 3. Start throws exception
        val startException =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, args, _ ->
                    if (args.first() == "inspect") {
                        Result.success(DockerCommandResult(0, "false"))
                    } else {
                        Result.failure(IllegalStateException("timeout"))
                    }
                },
                osName = "Windows 11",
            )
        assertEquals(PostgresEnsureStatus.START_FAILED, startException.ensureRunning(env, buildDbConfig()))

        // 4. Start succeeds but readiness times out
        var startTimeoutNow = 0L
        val startTimeout =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, args, _ ->
                    when (args.first()) {
                        "inspect" -> Result.success(DockerCommandResult(0, "false"))
                        "start" -> Result.success(DockerCommandResult(0, "itm-postgres18"))
                        else -> Result.success(DockerCommandResult(1, "starting up"))
                    }
                },
                sleepFn = { ms -> startTimeoutNow += ms },
                nowMillisFn = { startTimeoutNow },
                osName = "Windows 11",
                readyTimeoutMs = 200L,
                pollIntervalMs = 100L,
            )
        assertEquals(PostgresEnsureStatus.START_FAILED, startTimeout.ensureRunning(env, buildDbConfig()))

        // 5. Create exits non-zero
        val createNonZero =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, args, _ ->
                    if (args.first() == "inspect") {
                        Result.success(DockerCommandResult(1, "Error: No such container: itm-postgres18"))
                    } else {
                        Result.success(DockerCommandResult(125, "pull access denied"))
                    }
                },
                osName = "Windows 11",
            )
        assertEquals(PostgresEnsureStatus.START_FAILED, createNonZero.ensureRunning(env, buildDbConfig()))

        // 6. Create throws exception
        val createException =
            PostgresBootstrapper(
                portProbe = { false },
                dockerRunner = { _, args, _ ->
                    if (args.first() == "inspect") {
                        Result.success(DockerCommandResult(1, "No such object"))
                    } else {
                        Result.failure(IllegalStateException("create boom"))
                    }
                },
                osName = "Windows 11",
            )
        assertEquals(PostgresEnsureStatus.START_FAILED, createException.ensureRunning(env, buildDbConfig()))

        // 7. Create succeeds but readiness times out
        var createTimeoutNow = 0L
        var created = false
        val createTimeout =
            PostgresBootstrapper(
                portProbe = { created },
                dockerRunner = { _, args, _ ->
                    when (args.first()) {
                        "inspect" -> {
                            Result.success(DockerCommandResult(1, "No such object"))
                        }

                        "run" -> {
                            created = true
                            Result.success(DockerCommandResult(0, "id"))
                        }

                        else -> {
                            Result.success(DockerCommandResult(2, "no response"))
                        }
                    }
                },
                sleepFn = { ms -> createTimeoutNow += ms },
                nowMillisFn = { createTimeoutNow },
                osName = "Windows 11",
                readyTimeoutMs = 200L,
                pollIntervalMs = 100L,
            )
        assertEquals(PostgresEnsureStatus.START_FAILED, createTimeout.ensureRunning(env, buildDbConfig()))
    }

    @Test
    fun `findDockerExecutable resolves Windows fallback paths`() {
        val programFiles = tempDir.resolve("ProgramFiles")
        val dockerExe = programFiles.resolve("Docker/Docker/resources/bin/docker.exe")
        Files.createDirectories(dockerExe.parent)
        Files.writeString(dockerExe, "fake")

        val bootstrapper = PostgresBootstrapper(osName = "Windows 11")
        val resolved =
            bootstrapper.findDockerExecutable(
                mapOf(
                    "Path" to "",
                    "ProgramFiles" to programFiles.toString(),
                    "LOCALAPPDATA" to tempDir.resolve("LocalAppData").toString(),
                ),
            )
        assertEquals(dockerExe.normalize(), resolved)
    }

    @Test
    fun `helper functions parse JDBC URLs and default runner handles missing binary`() {
        val parsedDefault = PostgresBootstrapper.parseJdbcUrl("jdbc:postgresql://localhost/my_db")
        assertNotNull(parsedDefault)
        assertEquals("localhost", parsedDefault.host)
        assertEquals(5432, parsedDefault.port)
        assertEquals("my_db", parsedDefault.databaseName)

        val parsedIpv6 = PostgresBootstrapper.parseJdbcUrl("jdbc:postgresql://[::1]:5439/itm_service_desk")
        assertNotNull(parsedIpv6)
        assertEquals("::1", parsedIpv6.host)
        assertEquals(5439, parsedIpv6.port)
        assertTrue(PostgresBootstrapper.isLocalHost(parsedIpv6.host))

        val parsedNoDb = PostgresBootstrapper.parseJdbcUrl("jdbc:postgresql://127.0.0.1:5432/")
        assertNotNull(parsedNoDb)
        assertEquals("itm_service_desk", parsedNoDb.databaseName)

        assertNull(PostgresBootstrapper.parseJdbcUrl("jdbc:h2:mem:test"))
        assertNull(PostgresBootstrapper.parseJdbcUrl("jdbc:postgresql://:5432/db"))

        assertTrue(PostgresBootstrapper.isContainerMissingOutput("Error: No such object: itm-postgres18"))
        assertTrue(PostgresBootstrapper.isContainerMissingOutput("No such container"))
        assertFalse(PostgresBootstrapper.isContainerMissingOutput("Cannot connect to the Docker daemon"))

        assertEquals(
            "line 2 | line 3",
            PostgresBootstrapper.summarizeOutput("\n  line 2 \n\n  line 3 \n"),
        )

        val runFailure =
            PostgresBootstrapper.defaultRunDockerCommand(
                executable = tempDir.resolve("missing${File.separator}docker"),
                args = listOf("version"),
                timeoutSeconds = 5L,
            )
        assertTrue(runFailure.isFailure)
    }
}
