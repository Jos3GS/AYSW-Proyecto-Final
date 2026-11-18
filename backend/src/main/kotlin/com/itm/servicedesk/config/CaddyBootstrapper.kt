package com.itm.servicedesk.config

import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

enum class CaddyEnsureStatus {
    DISABLED,
    ALREADY_RUNNING,
    CADDYFILE_NOT_FOUND,
    EXECUTABLE_NOT_FOUND,
    STARTED,
    START_FAILED,
}

fun interface PortProbe {
    fun isLocalPortOpen(port: Int): Boolean
}

fun interface ProcessProbe {
    fun isCaddyProcessRunning(): Boolean
}

fun interface CaddyStarter {
    fun start(
        executable: Path,
        caddyfile: Path,
    ): Result<Unit>
}

class CaddyBootstrapper(
    private val portProbe: PortProbe = PortProbe(::defaultIsLocalPortOpen),
    private val processProbe: ProcessProbe = ProcessProbe(::defaultIsCaddyProcessRunning),
    private val caddyStarter: CaddyStarter = CaddyStarter(::defaultStartCaddy),
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val workingDir: Path = Path.of(".").toAbsolutePath().normalize(),
) {
    private val logger = LoggerFactory.getLogger(CaddyBootstrapper::class.java)

    fun ensureRunning(env: Map<String, String> = EnvConfig.load()): CaddyEnsureStatus {
        val autoStartRaw = env["CADDY_AUTO_START"]?.trim()?.lowercase()
        if (autoStartRaw in DISABLED_VALUES) {
            logger.info("Auto-inicio de Caddy deshabilitado (CADDY_AUTO_START={}).", autoStartRaw)
            return CaddyEnsureStatus.DISABLED
        }

        val caddyPort =
            env["CADDY_PORT"]
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it in 1..MAX_PORT }
                ?: DEFAULT_CADDY_PORT

        if (isCaddyAlreadyRunning(caddyPort)) {
            logger.info("Caddy ya está en ejecución (puerto {}); se omite el inicio automático.", caddyPort)
            return CaddyEnsureStatus.ALREADY_RUNNING
        }

        val caddyfile = resolveCaddyfile(env)
        if (caddyfile == null) {
            val configuredPath = env["CADDYFILE_PATH"]?.trim().orEmpty().ifEmpty { DEFAULT_CADDYFILE_PATH }
            logger.warn(
                "No se encontró Caddyfile en '{}'; la aplicación continuará sin iniciar Caddy.",
                configuredPath,
            )
            return CaddyEnsureStatus.CADDYFILE_NOT_FOUND
        }

        val executable = findCaddyExecutable(env)
        if (executable == null) {
            logger.warn(
                "No se encontró el ejecutable de Caddy en PATH ni en rutas estándar; " +
                    "instala Caddy o configura CADDY_BIN para habilitar el inicio automático.",
            )
            return CaddyEnsureStatus.EXECUTABLE_NOT_FOUND
        }

        val result = caddyStarter.start(executable, caddyfile)
        return result.fold(
            onSuccess = {
                logger.info(
                    "Caddy iniciado correctamente con '{}' (https://localhost:{}).",
                    caddyfile,
                    caddyPort,
                )
                CaddyEnsureStatus.STARTED
            },
            onFailure = { error ->
                logger.warn(
                    "No se pudo iniciar Caddy automáticamente ({}); la aplicación continuará en HTTP local.",
                    error.message ?: error.javaClass.simpleName,
                )
                CaddyEnsureStatus.START_FAILED
            },
        )
    }

    internal fun isCaddyAlreadyRunning(caddyPort: Int): Boolean =
        portProbe.isLocalPortOpen(caddyPort) ||
            portProbe.isLocalPortOpen(DEFAULT_CADDY_ADMIN_PORT) ||
            processProbe.isCaddyProcessRunning()

    internal fun resolveCaddyfile(env: Map<String, String>): Path? {
        val configured =
            env["CADDYFILE_PATH"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_CADDYFILE_PATH
        val rawPath = Path.of(configured)
        val candidates =
            if (rawPath.isAbsolute) {
                listOf(rawPath.normalize())
            } else {
                listOf(
                    workingDir.resolve(rawPath).normalize(),
                    workingDir.resolve("backend").resolve(rawPath).normalize(),
                )
            }
        return candidates.firstOrNull { Files.isRegularFile(it) }
    }

    internal fun findCaddyExecutable(env: Map<String, String>): Path? {
        val explicitBin = env["CADDY_BIN"]?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitBin != null) {
            val explicitPath = Path.of(explicitBin).normalize()
            return explicitPath.takeIf { isValidExecutable(it) }
        }

        val isWindows = osName.contains("win", ignoreCase = true)
        val binaryNames =
            if (isWindows) {
                listOf("caddy.exe", "caddy.cmd", "caddy.bat")
            } else {
                listOf("caddy")
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
                    Path.of("/opt/homebrew/bin/caddy"),
                    Path.of("/usr/local/bin/caddy"),
                    Path.of("/usr/bin/caddy"),
                )
            }

        return fallbackCandidates.firstOrNull { isValidExecutable(it) }
    }

    private fun buildWindowsFallbackCandidates(env: Map<String, String>): List<Path> {
        val candidates = mutableListOf<Path>()
        val localAppData = lookupEnvCaseInsensitive(env, "LOCALAPPDATA")
        if (!localAppData.isNullOrBlank()) {
            val localAppDir = Path.of(localAppData)
            candidates.add(localAppDir.resolve("Microsoft/WinGet/Links/caddy.exe").normalize())
            val packagesDir = localAppDir.resolve("Microsoft/WinGet/Packages")
            if (Files.isDirectory(packagesDir)) {
                runCatching {
                    Files.list(packagesDir).use { stream ->
                        stream
                            .filter {
                                Files.isDirectory(it) &&
                                    it.fileName.toString().startsWith("CaddyServer.Caddy", ignoreCase = true)
                            }.forEach { pkgDir ->
                                candidates.add(pkgDir.resolve("caddy.exe").normalize())
                            }
                    }
                }
            }
        }
        val userProfile = lookupEnvCaseInsensitive(env, "USERPROFILE")
        if (!userProfile.isNullOrBlank()) {
            candidates.add(Path.of(userProfile, "scoop", "shims", "caddy.exe").normalize())
        }
        val programData = lookupEnvCaseInsensitive(env, "ProgramData")
        if (!programData.isNullOrBlank()) {
            candidates.add(Path.of(programData, "chocolatey", "bin", "caddy.exe").normalize())
        }
        val programFiles = lookupEnvCaseInsensitive(env, "ProgramFiles")
        if (!programFiles.isNullOrBlank()) {
            candidates.add(Path.of(programFiles, "Caddy", "caddy.exe").normalize())
        }
        candidates.add(Path.of("C:\\caddy\\caddy.exe"))
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
        private const val DEFAULT_CADDY_PORT = 8443
        private const val DEFAULT_CADDY_ADMIN_PORT = 2019
        private const val MAX_PORT = 65535
        private const val DEFAULT_CADDYFILE_PATH = "infra/Caddyfile"
        private const val PORT_PROBE_TIMEOUT_MS = 250
        private const val START_TIMEOUT_SECONDS = 15L
        private const val LOG_TAIL_LINES = 5
        private val DISABLED_VALUES = setOf("false", "0", "no", "off")

        internal fun isCaddyCommand(command: String): Boolean {
            val fileName = runCatching { Path.of(command).fileName?.toString() }.getOrNull() ?: command
            val normalized = fileName.trim().lowercase()
            return normalized == "caddy" || normalized == "caddy.exe"
        }

        internal fun defaultIsLocalPortOpen(port: Int): Boolean {
            val addresses =
                runCatching { InetAddress.getAllByName("localhost").toList() }
                    .getOrDefault(emptyList())
                    .ifEmpty {
                        listOf(
                            InetAddress.getByName("127.0.0.1"),
                            InetAddress.getByName("::1"),
                        )
                    }
            return addresses.any { address ->
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(address, port), PORT_PROBE_TIMEOUT_MS)
                        true
                    }
                }.getOrDefault(false)
            }
        }

        internal fun defaultIsCaddyProcessRunning(): Boolean =
            ProcessHandle
                .allProcesses()
                .filter { it.isAlive }
                .anyMatch { handle ->
                    val command = handle.info().command().orElse("")
                    command.isNotBlank() && isCaddyCommand(command)
                }

        internal fun defaultStartCaddy(
            executable: Path,
            caddyfile: Path,
        ): Result<Unit> =
            runCatching {
                val normalizedCaddyfile = caddyfile.toAbsolutePath().normalize()
                val processDir =
                    normalizedCaddyfile.parent
                        ?.let { parent ->
                            if (parent.fileName?.toString() == "infra") parent.parent else parent
                        }
                        ?: Path.of(".").toAbsolutePath().normalize()
                val logFile =
                    Path.of(System.getProperty("java.io.tmpdir"), "itm-servicedesk-caddy.log")
                logFile.parent?.let { Files.createDirectories(it) }
                val initialSize = if (Files.exists(logFile)) Files.size(logFile) else 0L
                val nullDevice =
                    if (System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)) {
                        File("NUL")
                    } else {
                        File("/dev/null")
                    }

                val process =
                    ProcessBuilder(
                        executable.toAbsolutePath().toString(),
                        "start",
                        "--config",
                        normalizedCaddyfile.toString(),
                    ).directory(processDir.toFile())
                        .redirectInput(ProcessBuilder.Redirect.from(nullDevice))
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                        .start()

                val finished = process.waitFor(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    error("Tiempo de espera agotado al iniciar Caddy (log: '$logFile').")
                }

                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    val detail = readNewLogTail(logFile, initialSize)
                    val suffix = if (detail.isNotBlank()) " Detalle: $detail" else ""
                    error("Caddy finalizó con código $exitCode.$suffix")
                }
            }

        internal fun readNewLogTail(
            logFile: Path,
            startOffset: Long,
        ): String {
            if (!Files.isRegularFile(logFile)) return ""
            return runCatching {
                val allBytes = Files.readAllBytes(logFile)
                val safeOffset = startOffset.coerceIn(0L, allBytes.size.toLong()).toInt()
                val newText = String(allBytes, safeOffset, allBytes.size - safeOffset, Charsets.UTF_8)
                newText
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                    .takeLast(LOG_TAIL_LINES)
                    .joinToString(" | ")
            }.getOrDefault("")
        }
    }
}
