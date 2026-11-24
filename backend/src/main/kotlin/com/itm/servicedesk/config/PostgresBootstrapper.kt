package com.itm.servicedesk.config

import com.itm.servicedesk.domain.builder.DatabaseConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

enum class PostgresEnsureStatus {
    DISABLED,
    NON_LOCAL_HOST,
    ALREADY_RUNNING,
    DOCKER_NOT_FOUND,
    STARTED,
    CREATED_AND_STARTED,
    START_FAILED,
}

enum class DockerContainerState {
    NOT_FOUND,
    RUNNING,
    STOPPED,
}

data class DockerCommandResult(
    val exitCode: Int,
    val output: String,
)

data class ParsedPostgresTarget(
    val host: String,
    val port: Int,
    val databaseName: String,
)

fun interface DockerCommandRunner {
    fun run(
        executable: Path,
        args: List<String>,
        timeoutSeconds: Long,
    ): Result<DockerCommandResult>
}

class PostgresBootstrapper(
    private val portProbe: PortProbe = PortProbe(CaddyBootstrapper::defaultIsLocalPortOpen),
    private val dockerRunner: DockerCommandRunner = DockerCommandRunner(::defaultRunDockerCommand),
    private val sleepFn: (Long) -> Unit = { Thread.sleep(it) },
    private val nowMillisFn: () -> Long = System::currentTimeMillis,
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val readyTimeoutMs: Long = DEFAULT_READY_TIMEOUT_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {
    private val logger = LoggerFactory.getLogger(PostgresBootstrapper::class.java)

    fun ensureRunning(
        env: Map<String, String> = EnvConfig.load(),
        databaseConfig: DatabaseConfig,
    ): PostgresEnsureStatus {
        val autoStartRaw = env["POSTGRES_AUTO_START"]?.trim()?.lowercase()
        if (autoStartRaw in DISABLED_VALUES) {
            logger.info("Auto-inicio de PostgreSQL deshabilitado (POSTGRES_AUTO_START={}).", autoStartRaw)
            return PostgresEnsureStatus.DISABLED
        }

        val target = parseJdbcUrl(databaseConfig.jdbcUrl)
        if (target == null || !isLocalHost(target.host)) {
            logger.info(
                "DB_URL no apunta a un host local; se omite la gestión automática del contenedor PostgreSQL.",
            )
            return PostgresEnsureStatus.NON_LOCAL_HOST
        }

        if (portProbe.isLocalPortOpen(target.port)) {
            logger.info(
                "PostgreSQL ya está en ejecución en el puerto {}; se omite el inicio automático.",
                target.port,
            )
            return PostgresEnsureStatus.ALREADY_RUNNING
        }

        val dockerExe = findDockerExecutable(env)
        if (dockerExe == null) {
            logger.warn(
                "No se encontró el ejecutable de Docker en PATH ni en rutas estándar; " +
                    "instala Docker o configura DOCKER_BIN para gestionar PostgreSQL automáticamente.",
            )
            return PostgresEnsureStatus.DOCKER_NOT_FOUND
        }

        val containerName =
            env["POSTGRES_CONTAINER_NAME"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_CONTAINER_NAME
        val image =
            env["POSTGRES_IMAGE"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_IMAGE

        val containerState =
            inspectContainer(dockerExe, containerName).getOrElse { error ->
                logger.warn(
                    "No se pudo consultar el estado del contenedor Docker '{}' ({}); " +
                        "verifica que Docker Desktop esté en ejecución.",
                    containerName,
                    error.message ?: error.javaClass.simpleName,
                )
                return PostgresEnsureStatus.START_FAILED
            }

        return when (containerState) {
            DockerContainerState.RUNNING -> {
                handleRunningContainer(dockerExe, containerName, target, databaseConfig.user)
            }

            DockerContainerState.STOPPED -> {
                handleStoppedContainer(dockerExe, containerName, target, databaseConfig.user)
            }

            DockerContainerState.NOT_FOUND -> {
                handleMissingContainer(dockerExe, containerName, image, target, databaseConfig)
            }
        }
    }

    private fun handleRunningContainer(
        dockerExe: Path,
        containerName: String,
        target: ParsedPostgresTarget,
        dbUser: String,
    ): PostgresEnsureStatus {
        logger.info(
            "El contenedor '{}' ya está activo; esperando a que PostgreSQL acepte conexiones en el puerto {}...",
            containerName,
            target.port,
        )
        return if (waitUntilReady(dockerExe, containerName, target, dbUser)) {
            PostgresEnsureStatus.ALREADY_RUNNING
        } else {
            logger.warn(
                "El contenedor '{}' está activo, pero PostgreSQL no respondió a tiempo en el puerto {}.",
                containerName,
                target.port,
            )
            PostgresEnsureStatus.START_FAILED
        }
    }

    private fun handleStoppedContainer(
        dockerExe: Path,
        containerName: String,
        target: ParsedPostgresTarget,
        dbUser: String,
    ): PostgresEnsureStatus {
        logger.info("Contenedor PostgreSQL '{}' detenido; iniciándolo automáticamente...", containerName)
        val startResult = dockerRunner.run(dockerExe, listOf("start", containerName), START_TIMEOUT_SECONDS)
        val cmdResult =
            startResult.getOrElse { error ->
                logger.warn(
                    "Error al ejecutar 'docker start {}': {}",
                    containerName,
                    error.message ?: error.javaClass.simpleName,
                )
                return PostgresEnsureStatus.START_FAILED
            }
        if (cmdResult.exitCode != 0) {
            logger.warn(
                "No se pudo iniciar el contenedor '{}' (código {}): {}",
                containerName,
                cmdResult.exitCode,
                summarizeOutput(cmdResult.output),
            )
            return PostgresEnsureStatus.START_FAILED
        }
        return if (waitUntilReady(dockerExe, containerName, target, dbUser)) {
            logger.info(
                "Contenedor PostgreSQL '{}' iniciado y listo en localhost:{}/{}.",
                containerName,
                target.port,
                target.databaseName,
            )
            PostgresEnsureStatus.STARTED
        } else {
            logger.warn(
                "Contenedor '{}' iniciado, pero PostgreSQL no estuvo listo en {}ms.",
                containerName,
                readyTimeoutMs,
            )
            PostgresEnsureStatus.START_FAILED
        }
    }

    private fun handleMissingContainer(
        dockerExe: Path,
        containerName: String,
        image: String,
        target: ParsedPostgresTarget,
        databaseConfig: DatabaseConfig,
    ): PostgresEnsureStatus {
        logger.info(
            "Contenedor PostgreSQL '{}' no existe; creándolo con imagen '{}' en el puerto {} (BD '{}')...",
            containerName,
            image,
            target.port,
            target.databaseName,
        )
        val runArgs =
            listOf(
                "run",
                "-d",
                "--name",
                containerName,
                "-e",
                "POSTGRES_DB=${target.databaseName}",
                "-e",
                "POSTGRES_USER=${databaseConfig.user}",
                "-e",
                "POSTGRES_PASSWORD=${databaseConfig.password}",
                "-p",
                "${target.port}:$DEFAULT_POSTGRES_PORT",
                image,
            )
        val createResult = dockerRunner.run(dockerExe, runArgs, CREATE_TIMEOUT_SECONDS)
        val cmdResult =
            createResult.getOrElse { error ->
                logger.warn(
                    "Error al crear el contenedor PostgreSQL '{}': {}",
                    containerName,
                    error.message ?: error.javaClass.simpleName,
                )
                return PostgresEnsureStatus.START_FAILED
            }
        if (cmdResult.exitCode != 0) {
            logger.warn(
                "No se pudo crear el contenedor '{}' (código {}): {}",
                containerName,
                cmdResult.exitCode,
                summarizeOutput(cmdResult.output),
            )
            return PostgresEnsureStatus.START_FAILED
        }
        return if (waitUntilReady(dockerExe, containerName, target, databaseConfig.user)) {
            logger.info(
                "Contenedor PostgreSQL '{}' creado y listo en localhost:{}/{}.",
                containerName,
                target.port,
                target.databaseName,
            )
            PostgresEnsureStatus.CREATED_AND_STARTED
        } else {
            logger.warn(
                "Contenedor '{}' creado, pero PostgreSQL no estuvo listo en {}ms.",
                containerName,
                readyTimeoutMs,
            )
            PostgresEnsureStatus.START_FAILED
        }
    }

    internal fun inspectContainer(
        dockerExe: Path,
        containerName: String,
    ): Result<DockerContainerState> {
        val result =
            dockerRunner.run(
                dockerExe,
                listOf("inspect", "-f", "{{.State.Running}}", containerName),
                INSPECT_TIMEOUT_SECONDS,
            )
        return result.mapCatching { cmdResult ->
            if (cmdResult.exitCode == 0) {
                if (cmdResult.output.trim().equals("true", ignoreCase = true)) {
                    DockerContainerState.RUNNING
                } else {
                    DockerContainerState.STOPPED
                }
            } else if (isContainerMissingOutput(cmdResult.output)) {
                DockerContainerState.NOT_FOUND
            } else {
                val detail = summarizeOutput(cmdResult.output)
                val suffix = if (detail.isNotBlank()) " Detalle: $detail" else ""
                error("docker inspect finalizó con código ${cmdResult.exitCode}.$suffix")
            }
        }
    }

    internal fun waitUntilReady(
        dockerExe: Path,
        containerName: String,
        target: ParsedPostgresTarget,
        dbUser: String,
    ): Boolean {
        val deadline = nowMillisFn() + readyTimeoutMs
        while (true) {
            if (portProbe.isLocalPortOpen(target.port) &&
                isPostgresReadyInContainer(dockerExe, containerName, dbUser, target.databaseName)
            ) {
                return true
            }
            if (nowMillisFn() >= deadline) {
                return false
            }
            sleepFn(pollIntervalMs)
        }
    }

    internal fun isPostgresReadyInContainer(
        dockerExe: Path,
        containerName: String,
        dbUser: String,
        dbName: String,
    ): Boolean {
        val args =
            listOf(
                "exec",
                containerName,
                "pg_isready",
                "-h",
                "127.0.0.1",
                "-p",
                DEFAULT_POSTGRES_PORT.toString(),
                "-U",
                dbUser,
                "-d",
                dbName,
            )
        return dockerRunner
            .run(dockerExe, args, READY_CHECK_TIMEOUT_SECONDS)
            .map { it.exitCode == 0 }
            .getOrDefault(false)
    }

    internal fun findDockerExecutable(env: Map<String, String>): Path? {
        val explicitBin = env["DOCKER_BIN"]?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitBin != null) {
            val explicitPath = Path.of(explicitBin).normalize()
            return explicitPath.takeIf { isValidExecutable(it) }
        }

        val isWindows = osName.contains("win", ignoreCase = true)
        val binaryNames =
            if (isWindows) {
                listOf("docker.exe", "docker.cmd", "docker.bat")
            } else {
                listOf("docker")
            }

        val pathValue = lookupEnvCaseInsensitive(env, "PATH")
        if (!pathValue.isNullOrBlank()) {
            for (dir in pathValue.split(File.pathSeparatorChar)) {
                val trimmedDir = dir.trim()
                if (trimmedDir.isEmpty()) continue
                val baseDir = runCatching { Path.of(trimmedDir) }.getOrNull() ?: continue
                for (binaryName in binaryNames) {
                    val candidate = baseDir.resolve(binaryName).normalize()
                    if (isValidExecutable(candidate)) {
                        return candidate
                    }
                }
            }
        }

        val fallbackCandidates =
            if (isWindows) {
                buildWindowsFallbackCandidates(env)
            } else {
                listOf(
                    Path.of("/usr/local/bin/docker"),
                    Path.of("/opt/homebrew/bin/docker"),
                    Path.of("/usr/bin/docker"),
                )
            }

        return fallbackCandidates.firstOrNull { isValidExecutable(it) }
    }

    private fun buildWindowsFallbackCandidates(env: Map<String, String>): List<Path> {
        val candidates = mutableListOf<Path>()
        val programFiles = lookupEnvCaseInsensitive(env, "ProgramFiles")
        if (!programFiles.isNullOrBlank()) {
            candidates.add(Path.of(programFiles, "Docker", "Docker", "resources", "bin", "docker.exe").normalize())
        }
        val localAppData = lookupEnvCaseInsensitive(env, "LOCALAPPDATA")
        if (!localAppData.isNullOrBlank()) {
            candidates.add(Path.of(localAppData, "Docker", "resources", "bin", "docker.exe").normalize())
        }
        candidates.add(Path.of("C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe"))
        return candidates
    }

    private fun isValidExecutable(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false
        val isWindows = osName.contains("win", ignoreCase = true)
        return isWindows || Files.isExecutable(path)
    }

    private fun lookupEnvCaseInsensitive(
        env: Map<String, String>,
        key: String,
    ): String? = env.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    companion object {
        private const val DEFAULT_CONTAINER_NAME = "itm-postgres18"
        private const val DEFAULT_IMAGE = "postgres:18-alpine"
        private const val DEFAULT_POSTGRES_PORT = 5432
        private const val DEFAULT_DATABASE_NAME = "itm_service_desk"
        private const val MAX_PORT = 65535
        private const val INSPECT_TIMEOUT_SECONDS = 10L
        private const val START_TIMEOUT_SECONDS = 30L
        private const val CREATE_TIMEOUT_SECONDS = 180L
        private const val READY_CHECK_TIMEOUT_SECONDS = 5L
        private const val DEFAULT_READY_TIMEOUT_MS = 30_000L
        private const val DEFAULT_POLL_INTERVAL_MS = 500L
        private const val LOG_TAIL_LINES = 5
        private val DISABLED_VALUES = setOf("false", "0", "no", "off")
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")

        internal fun parseJdbcUrl(jdbcUrl: String): ParsedPostgresTarget? {
            val trimmed = jdbcUrl.trim()
            if (!trimmed.startsWith("jdbc:postgresql://", ignoreCase = true)) {
                return null
            }
            val uri = runCatching { URI(trimmed.substring("jdbc:".length)) }.getOrNull() ?: return null
            val rawHost =
                uri.host
                    ?.trim()
                    ?.removePrefix("[")
                    ?.removeSuffix("]")
            if (rawHost.isNullOrBlank()) {
                return null
            }
            val port = uri.port.takeIf { it in 1..MAX_PORT } ?: DEFAULT_POSTGRES_PORT
            val databaseName =
                uri.path
                    ?.trim()
                    ?.removePrefix("/")
                    ?.substringBefore('/')
                    ?.ifBlank { DEFAULT_DATABASE_NAME }
                    ?: DEFAULT_DATABASE_NAME
            return ParsedPostgresTarget(host = rawHost, port = port, databaseName = databaseName)
        }

        internal fun isLocalHost(host: String): Boolean {
            val normalized =
                host
                    .trim()
                    .lowercase()
                    .removePrefix("[")
                    .removeSuffix("]")
            return normalized in LOCAL_HOSTS
        }

        internal fun isContainerMissingOutput(output: String): Boolean =
            output.contains("No such object", ignoreCase = true) ||
                output.contains("No such container", ignoreCase = true)

        internal fun summarizeOutput(output: String): String =
            output
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .takeLast(LOG_TAIL_LINES)
                .joinToString(" | ")

        internal fun defaultRunDockerCommand(
            executable: Path,
            args: List<String>,
            timeoutSeconds: Long,
        ): Result<DockerCommandResult> =
            runCatching {
                val tempOutput = Files.createTempFile("itm-servicedesk-docker-", ".log")
                try {
                    val nullDevice =
                        if (System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)) {
                            File("NUL")
                        } else {
                            File("/dev/null")
                        }

                    val process =
                        ProcessBuilder(listOf(executable.toAbsolutePath().toString()) + args)
                            .redirectInput(ProcessBuilder.Redirect.from(nullDevice))
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.to(tempOutput.toFile()))
                            .start()

                    val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                    if (!finished) {
                        process.destroyForcibly()
                        error("Tiempo de espera agotado al ejecutar Docker (${timeoutSeconds}s).")
                    }

                    val output = Files.readString(tempOutput, Charsets.UTF_8).trim()
                    DockerCommandResult(exitCode = process.exitValue(), output = output)
                } finally {
                    runCatching { Files.deleteIfExists(tempOutput) }
                }
            }
    }
}
