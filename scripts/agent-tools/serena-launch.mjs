#!/usr/bin/env node
// Project-scoped Serena launcher. Generated settings never overwrite .serena/project.yml.
/**
 * Launch the pinned Serena navigation server against one explicit OnlyDragons clone.
 *
 * Resolves the project's Java major and reviewed tool installations, validates the
 * installed Serena version, and writes per-process generated settings under the
 * clone's ignored .serena/runtime. Tracked project.yml remains the source config.
 * --check reports resolved dependencies without launching Serena; normal mode owns
 * one stdio child and forwards termination. Generated runtime files are retained.
 *
 * Tool exposure is navigation-only; edits, shell execution and shared context use
 * the agent's native tools. Environment overrides select installation paths, not
 * permission to expand navigationTools or use a mismatched JDK.
 */
import { spawn, spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const SERENA_VERSION = '1.7.0';
const VSCODE_JAVA_VERSION = '1.56.0'; // This bundle contains Eclipse JDT LS core 1.61.0.
const navigationTools = [
  'initial_instructions', 'get_current_config', 'list_dir', 'find_file',
  'search_for_pattern', 'get_symbols_overview', 'find_symbol', 'find_referencing_symbols',
];

/** Raise a startup error for the outer diagnostic/exit handler. @param {string} message */
function fail(message) { throw new Error(message); }
/** Test an optional candidate path without opening its contents. @param {string} file */
function exists(file) { return Boolean(file) && fs.existsSync(file); }
/** Read a required UTF-8 config/release file; filesystem errors propagate. @param {string} file */
function read(file) { return fs.readFileSync(file, 'utf8'); }
/** Write JSON's YAML-compatible subset to an owned generated settings file. */
function jsonYaml(file, object) { fs.writeFileSync(file, JSON.stringify(object, null, 2) + '\n'); }

try {
  let project = process.cwd();
  let check = false;
  for (let i = 2; i < process.argv.length; i++) {
    const arg = process.argv[i];
    if (arg === '--check') check = true;
    else if (arg === '--project' && process.argv[i + 1]) project = process.argv[++i];
    else fail('Usage: node scripts/agent-tools/serena-launch.mjs [--project PATH] [--check]');
  }
  project = fs.realpathSync(project);
  for (const file of ['AGENTS.md', 'versions.properties', '.serena/project.yml']) {
    if (!exists(path.join(project, file))) fail(`Expected OnlyDragons checkout: missing ${file} in ${project}`);
  }
  const javaMajor = read(path.join(project, 'versions.properties')).match(/^javaVersion=(\d+)\s*$/m)?.[1];
  if (!javaMajor) fail('versions.properties must declare javaVersion.');
  const win = process.platform === 'win32';
  const toolsRoot = win ? path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local'), 'MinecraftAgentTools') : undefined;
  const source = path.resolve(process.env.ONLYDRAGONS_SOURCE || project);
  const runtime = path.resolve(process.env.ONLYDRAGONS_AGENT_RUNTIME || path.join(source, '.symphony', 'runtime'));
  const defaultBin = win ? path.join(toolsRoot, 'bin', 'serena.exe') : path.join(runtime, 'serena-venv', 'bin', 'serena');
  const bin = path.resolve(process.env.ONLYDRAGONS_SERENA_BIN || defaultBin);
  const defaultPython = win ? path.join(toolsRoot, 'tools', 'serena-agent', 'Scripts', 'python.exe') : path.join(runtime, 'serena-venv', 'bin', 'python');
  const python = path.resolve(process.env.ONLYDRAGONS_SERENA_PYTHON || defaultPython);
  if (!exists(bin) || !exists(python)) fail('Serena is not installed. Linux: bash scripts/agent-tools/install-serena.sh. Windows: install serena-agent==1.7.0, or configure ONLYDRAGONS_SERENA_BIN and ONLYDRAGONS_SERENA_PYTHON.');

  // Prefer explicit environment selections, then the shared runtime and Windows
  // installations. Inspect release metadata instead of guessing from folder names.
  const javaCandidates = [process.env.ONLYDRAGONS_JAVA_HOME, process.env.JAVA_HOME, path.join(runtime, 'java')];
  if (win) {
    const adoptium = path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Eclipse Adoptium');
    if (exists(adoptium)) for (const entry of fs.readdirSync(adoptium).sort().reverse()) javaCandidates.push(path.join(adoptium, entry));
  }
  const javaHome = javaCandidates.find(candidate => {
    if (!exists(candidate) || !exists(path.join(candidate, 'bin', win ? 'java.exe' : 'java')) || !exists(path.join(candidate, 'release'))) return false;
    return read(path.join(candidate, 'release')).match(/^JAVA_VERSION="(\d+)/m)?.[1] === javaMajor;
  });
  if (!javaHome) fail(`JDK ${javaMajor} is required. Set ONLYDRAGONS_JAVA_HOME to its installation directory.`);
  if (process.env.ONLYDRAGONS_JAVA_HOME && path.resolve(javaHome) !== path.resolve(process.env.ONLYDRAGONS_JAVA_HOME)) fail(`ONLYDRAGONS_JAVA_HOME does not contain JDK ${javaMajor}.`);
  const jdtlsRoot = path.resolve(process.env.ONLYDRAGONS_JDTLS_ROOT || path.join(win ? toolsRoot : runtime, `jdtls-vscode-java-${VSCODE_JAVA_VERSION}`));
  const jdtlsServer = path.join(jdtlsRoot, 'server');
  const lombokDir = path.join(jdtlsRoot, 'lombok');
  const lombok = exists(lombokDir) && fs.readdirSync(lombokDir).find(name => /^lombok-.*\.jar$/.test(name));
  if (!exists(path.join(jdtlsServer, 'plugins')) || !lombok) fail(`The vscode-java ${VSCODE_JAVA_VERSION} language server bundle is missing from ${jdtlsRoot}. Run the Linux installer or set ONLYDRAGONS_JDTLS_ROOT.`);

  // Read only public project settings; the matching Serena environment supplies safe YAML parsing.
  const inspected = spawnSync(python, ['-c', 'import importlib.metadata,json,sys,yaml; print(json.dumps({"version":importlib.metadata.version("serena-agent"),"project":yaml.safe_load(open(sys.argv[1],encoding="utf-8")) or {}}))', path.join(project, '.serena', 'project.yml')], { encoding: 'utf8', windowsHide: true });
  if (inspected.error || inspected.status !== 0) fail('Unable to inspect the installed Serena package and project configuration. Check ONLYDRAGONS_SERENA_PYTHON.');
  const installed = JSON.parse(inspected.stdout);
  if (installed.version !== SERENA_VERSION) fail(`Expected Serena ${SERENA_VERSION}, found ${installed.version}.`);
  const details = { ready: true, serenaVersion: installed.version, project, executable: bin, python, javaHome: path.resolve(javaHome), jdtlsRoot, tools: navigationTools };
  if (check) {
    process.stdout.write(JSON.stringify(details, null, 2) + '\n');
  } else {
    const state = path.join(project, '.serena', 'runtime', process.platform, `${process.pid}-${randomBytes(6).toString('hex')}`);
    const projectState = path.join(state, 'project');
    // This directory must exist first: Serena otherwise falls back to the original .serena folder.
    fs.mkdirSync(projectState, { recursive: true });
    fs.copyFileSync(path.join(project, '.serena', 'project.yml'), path.join(projectState, 'project.yml'));
    const gradleHome = win ? path.join(toolsRoot, 'gradle-home') : path.join(project, '.serena', 'runtime', 'gradle-home');
    fs.mkdirSync(gradleHome, { recursive: true });
    const existingLs = installed.project.ls_specific_settings || {};
    jsonYaml(path.join(projectState, 'project.local.yml'), {
      ls_specific_settings: { ...existingLs, java: {
        ...(existingLs.java || {}), gradle_wrapper_enabled: true, use_system_java_home: true,
        java_home: path.resolve(javaHome), gradle_java_home: path.resolve(javaHome),
        gradle_user_home: gradleHome, runtimes: [{ name: `JavaSE-${javaMajor}`, path: path.resolve(javaHome), default: true }],
        jdtls_path: jdtlsServer, lombok_path: path.join(lombokDir, lombok), jdtls_xmx: '1G', jdtls_xms: '100m',
      } },
      fixed_tools: navigationTools, excluded_tools: [], included_optional_tools: [],
    });
    const mode = path.join(state, 'navigation.yml');
    jsonYaml(mode, { name: 'onlydragons-navigation', description: 'Read-only Java navigation for this checkout.', prompt: 'Use the client native tools for edits, shell commands, and shared project context. Serena provides Java navigation only.', fixed_tools: navigationTools });
    jsonYaml(path.join(state, 'serena_config.yml'), {
      projects: [project], trusted_project_path_patterns: [project], project_serena_folder_location: projectState,
      web_dashboard: false, web_dashboard_open_on_launch: false, gui_log_window: false,
      base_modes: [mode], default_modes: [], fixed_tools: navigationTools,
      tool_timeout: 240,
    });
    // Inherit stdio for MCP framing and hide native windows. Exit/error events set
    // this wrapper's status; no global language-server/JVM search or shutdown occurs.
    const child = spawn(bin, ['start-mcp-server', '--project', project, '--context', 'codex', '--enable-web-dashboard=false', '--open-web-dashboard=false', '--enable-gui-log-window=false'], {
      cwd: project, stdio: 'inherit', windowsHide: true,
      env: { ...process.env, SERENA_HOME: state, JAVA_HOME: path.resolve(javaHome) },
    });
    for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { if (!child.killed) child.kill(signal); });
    child.on('error', error => { process.stderr.write(`Serena could not start: ${error.message}\n`); process.exitCode = 1; });
    child.on('exit', (code, signal) => { process.exitCode = code ?? (signal ? 1 : 0); });
  }
} catch (error) {
  process.stderr.write(`OnlyDragons Serena: ${error.message}\n`);
  process.exitCode = 1;
}
