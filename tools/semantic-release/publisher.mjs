import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { lstat, mkdtemp, readFile, realpath, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parseArgs, promisify } from "node:util";

import semanticRelease from "semantic-release";

const exec = promisify(execFile);
const require = createRequire(import.meta.url);
const repositoryUrl = "https://github.com/amplitude/Amplitude-Kotlin.git";
const analyzer = fileURLToPath(new URL("./path-commits.mjs", import.meta.url));
const lanes = {
  analytics: {
    config: "../../release.config.js",
    prefix: "v",
    paths: ["CHANGELOG.md", "gradle.properties"],
  },
  unified: {
    config: "../../unified/release.config.js",
    prefix: "unified-v",
    paths: ["unified/CHANGELOG.md", "unified/gradle.properties"],
  },
};
const releaseTypes = new Set(["patch", "minor", "major"]);
const shaPattern = /^[0-9a-f]{40}$/u;
const hashPattern = /^[0-9a-f]{64}$/u;
const versionPattern = /^(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)$/u;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function getLane(lane) {
  assert(Object.hasOwn(lanes, lane), "Release lane must be analytics or unified.");
  return lanes[lane];
}

function assertReleaseType(lane, releaseType) {
  assert(
    lane === "unified" ? releaseTypes.has(releaseType) : releaseType === null,
    "Unified requires an explicit patch, minor, or major release type. Analytics derives its release type from commits.",
  );
}

async function git(cwd, args, options = {}) {
  const { stdout } = await exec("git", args, { cwd, maxBuffer: 16 * 1024 * 1024, ...options });
  return stdout;
}

async function assertHead(cwd, sha) {
  assert(typeof sha === "string" && shaPattern.test(sha), "Expected a full lowercase commit SHA.");
  assert((await git(cwd, ["rev-parse", "--show-prefix"])).trim() === "", "Run from the source checkout root.");
  assert((await git(cwd, ["rev-parse", "HEAD"])).trim() === sha, "Checked-out HEAD differs from the supplied commit.");
}

function hash(data) {
  return createHash("sha256").update(data).digest("hex");
}

async function readCheckedOutFile(cwd, sha, path) {
  const worktreePath = join(cwd, path);
  const entry = await git(cwd, ["ls-tree", sha, "--", path]);
  assert(entry.startsWith("100644 blob "), `${path} must be a tracked, regular, non-executable file.`);
  assert(
    (await lstat(worktreePath)).isFile() && (await realpath(worktreePath)) === worktreePath,
    `${path} must be a regular file in the source checkout.`,
  );
  const bytes = await readFile(worktreePath);
  const blob = await git(cwd, ["show", `${sha}:${path}`], { encoding: "buffer" });
  assert(bytes.equals(blob), `${path} in the source checkout differs from the commit.`);
  return bytes;
}

async function preview(cwd, lane, releaseType, sourceSha) {
  assert((await git(cwd, ["rev-parse", "--is-shallow-repository"])).trim() === "false", "Release planning needs full Git history and tags.");
  const { config, prefix } = getLane(lane);
  const settings = require(config);
  const pathPlugin = settings.plugins.find((entry) => Array.isArray(entry) && /(?:^|\/)path-commits\.mjs$/u.test(entry[0]));
  assert(pathPlugin, "The release configuration must include the Kotlin path analyzer.");

  const scratch = await mkdtemp(join(tmpdir(), "kotlin-publisher-"));
  try {
    const mirror = join(scratch, "history.git");
    const worktree = join(scratch, "checkout");
    const emptyGitConfig = join(scratch, "gitconfig");
    await writeFile(emptyGitConfig, "");
    // Keep the real URL in release notes while redirecting all Git operations to
    // the scratch mirror. Fail closed if Git tries any non-file transport.
    const env = {
      PATH: process.env.PATH,
      GIT_CONFIG_GLOBAL: emptyGitConfig,
      GIT_CONFIG_NOSYSTEM: "1",
      GIT_TERMINAL_PROMPT: "0",
      GIT_ALLOW_PROTOCOL: "file",
      ...(releaseType && { UNIFIED_RELEASE_TYPE: releaseType }),
    };
    await git(cwd, ["clone", "--quiet", "--mirror", "--no-hardlinks", "--", cwd, mirror], { env });
    await git(mirror, ["update-ref", "refs/heads/main", sourceSha], { env });
    await git(scratch, ["clone", "--quiet", "--no-checkout", "--branch", "main", "--", mirror, worktree], { env });
    await git(worktree, ["config", "--local", "protocol.allow", "never"], { env });
    await git(worktree, ["config", "--local", "protocol.file.allow", "always"], { env });
    await git(worktree, ["config", "--local", `url.${pathToFileURL(mirror).href}.insteadOf`, repositoryUrl], { env });
    await git(worktree, ["read-tree", "HEAD"], { env });
    // No source files or release configuration are loaded from this checkout.
    await writeFile(join(worktree, ".releaserc.json"), "{}\n");

    return await semanticRelease(
      {
        dryRun: true,
        ci: false,
        branches: ["main"],
        repositoryUrl,
        tagFormat: prefix + "${version}",
        plugins: [[analyzer, pathPlugin[1]]],
      },
      { cwd: worktree, env },
    );
  } finally {
    await rm(scratch, { recursive: true, force: true });
  }
}

function readVersion(properties) {
  const lines = [...properties.matchAll(/^VERSION_NAME=([^\r\n]*)$/gmu)];
  assert(lines.length === 1, "Expected exactly one VERSION_NAME property.");
  return lines[0][1];
}

export async function prepareRelease({ cwd, lane, sourceSha, releaseType = null }) {
  cwd = await realpath(cwd);
  const { paths: [changelogPath, versionPath], prefix } = getLane(lane);
  assertReleaseType(lane, releaseType);
  await assertHead(cwd, sourceSha);

  const changelog = (await readCheckedOutFile(cwd, sourceSha, changelogPath)).toString("utf8");
  const properties = (await readCheckedOutFile(cwd, sourceSha, versionPath)).toString("utf8");
  const currentVersion = readVersion(properties);
  const result = await preview(cwd, lane, releaseType, sourceSha);
  const release = result?.nextRelease;
  const plan = { schemaVersion: 1, sourceSha, lane, releaseType, previousTag: null, version: null, tag: null, notes: "", paths: {} };
  if (!release) return plan;

  const { version, gitTag: tag } = release;
  assert(versionPattern.test(version) && tag === prefix + version, "Unexpected release version or tag.");
  const previousTag = result.lastRelease.gitTag ?? null;
  assert(
    previousTag ? previousTag === prefix + currentVersion : currentVersion !== version,
    "The checked-in version does not match the previous release. Resolve any incomplete release before preparing another.",
  );
  const notes = release.notes?.trim() || `## ${version}\n\nAmplitude-Kotlin ${lane} release.`;
  const files = {
    [changelogPath]: `${notes}\n${changelog.trim() ? `\n${changelog.trim()}\n` : ""}`,
    [versionPath]: properties.replace(/^VERSION_NAME=[^\r\n]*$/mu, `VERSION_NAME=${version}`),
  };

  for (const [path, content] of Object.entries(files)) {
    await writeFile(join(cwd, path), content);
  }
  return {
    ...plan,
    previousTag,
    version,
    tag,
    notes,
    paths: Object.fromEntries(Object.entries(files).map(([path, content]) => [path, hash(content)])),
  };
}

export async function verifyRelease({ cwd, plan, mergeSha }) {
  cwd = await realpath(cwd);
  assert(plan?.schemaVersion === 1 && typeof plan.sourceSha === "string" && shaPattern.test(plan.sourceSha), "Invalid release plan.");
  const { paths, prefix } = getLane(plan.lane);
  assertReleaseType(plan.lane, plan.releaseType);
  assert(typeof plan.version === "string" && versionPattern.test(plan.version) && plan.tag === prefix + plan.version, "Invalid version or tag in release plan.");
  assert(
    plan.previousTag === null
      || (typeof plan.previousTag === "string"
        && plan.previousTag !== plan.tag
        && plan.previousTag.startsWith(prefix)
        && versionPattern.test(plan.previousTag.slice(prefix.length))),
    "Release plan has an invalid previous tag.",
  );
  assert(typeof plan.notes === "string" && plan.notes.trim(), "Release plan has no notes.");
  assert(
    plan.paths && typeof plan.paths === "object"
      && Object.keys(plan.paths).length === paths.length
      && paths.every((path) => hashPattern.test(plan.paths[path])),
    "Release plan has invalid metadata paths or hashes.",
  );

  await assertHead(cwd, mergeSha);
  const parents = (await git(cwd, ["rev-list", "--parents", "-n", "1", mergeSha])).trim();
  assert(parents === `${mergeSha} ${plan.sourceSha}`, "Release commit must have exactly one parent: the tested source commit.");

  const changes = (await git(cwd, ["diff", "--no-ext-diff", "--no-renames", "--name-status", "-z", plan.sourceSha, mergeSha, "--"])).split("\0");
  assert(
    changes.length === paths.length * 2 + 1
      && paths.every((path, index) => changes[index * 2] === "M" && changes[index * 2 + 1] === path),
    "Release commit must modify exactly the expected version and changelog files.",
  );
  for (const path of paths) {
    const bytes = await readCheckedOutFile(cwd, mergeSha, path);
    assert(hash(bytes) === plan.paths[path], `${path} does not match the approved release plan.`);
    if (path.endsWith("gradle.properties")) {
      assert(readVersion(bytes.toString("utf8")) === plan.version, `${path} does not contain the planned version.`);
    }
  }
  return plan;
}

async function recordOutputs(plan) {
  const { version, tag, sourceSha, lane } = plan;
  const outputs = `has_release=${Boolean(version)}\nversion=${version ?? ""}\ntag=${tag ?? ""}\nsource_sha=${sourceSha}\nlane=${lane}\n`;
  if (process.env.GITHUB_OUTPUT) await writeFile(process.env.GITHUB_OUTPUT, outputs, { flag: "a" });
}

async function main([command, ...args]) {
  assert(command === "prepare" || command === "verify", "Expected 'prepare' or 'verify'.");
  const { values } = parseArgs({
    args,
    options: {
      manifest: { type: "string" },
      ...(command === "prepare" ? {
        lane: { type: "string" },
        "source-sha": { type: "string" },
        "release-type": { type: "string" },
      } : {
        "merge-sha": { type: "string" },
      }),
    },
  });
  assert(values.manifest, "A manifest path is required.");
  const cwd = process.cwd();
  const manifest = resolve(values.manifest);
  if (command === "prepare") {
    const plan = await prepareRelease({ cwd, lane: values.lane, sourceSha: values["source-sha"], releaseType: values["release-type"] });
    await writeFile(manifest, JSON.stringify(plan, null, 2) + "\n", { flag: "wx" });
    await recordOutputs(plan);
  } else {
    const plan = JSON.parse(await readFile(manifest, "utf8"));
    await verifyRelease({ cwd, plan, mergeSha: values["merge-sha"] });
    await recordOutputs(plan);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main(process.argv.slice(2)).catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
