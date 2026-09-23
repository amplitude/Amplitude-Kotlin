import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, mkdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { promisify } from "node:util";
import test from "node:test";

import {
  analyzeCommits,
  hasReleaseChanges,
  releaseCommits,
} from "./path-commits.mjs";

const execFileAsync = promisify(execFile);

async function git(cwd, ...args) {
  const { stdout } = await execFileAsync("git", args, { cwd });
  return stdout.trim();
}

async function commit(cwd, message) {
  await git(cwd, "add", ".");
  await git(cwd, "-c", "core.hooksPath=/dev/null", "commit", "-m", message);
  return git(cwd, "rev-parse", "HEAD");
}

async function repository() {
  const cwd = await mkdtemp(join(tmpdir(), "path-commits-"));
  await git(cwd, "init");
  await git(cwd, "config", "user.email", "test@example.com");
  await git(cwd, "config", "user.name", "Test");
  await mkdir(join(cwd, "android"));
  await mkdir(join(cwd, "unified"));
  await writeFile(join(cwd, "android", "source.kt"), "core\n");
  await writeFile(join(cwd, "unified", "source.kt"), "unified\n");
  const initialCommit = await commit(cwd, "feat: initial");
  return { cwd, initialCommit };
}

const context = (cwd, lastRelease, commits, releaseType = "patch") => ({
  cwd,
  commits,
  env: { ...process.env, UNIFIED_RELEASE_TYPE: releaseType },
  lastRelease: { gitHead: lastRelease },
  logger: { log() {} },
});

test("filters commits by owned paths", async () => {
  const { cwd, initialCommit } = await repository();
  await writeFile(join(cwd, "android", "source.kt"), "core changed\n");
  const coreCommit = await commit(cwd, "fix: core");
  await writeFile(join(cwd, "unified", "source.kt"), "unified changed\n");
  const unifiedCommit = await commit(cwd, "feat: unified");
  const commits = [
    { hash: coreCommit, message: "fix: core" },
    { hash: unifiedCommit, message: "feat: unified" },
  ];

  assert.deepEqual(
    await releaseCommits(["unified/"], context(cwd, initialCommit, commits)),
    [commits[1]],
  );
});

test("does not release when changes are reverted before release", async () => {
  const { cwd, initialCommit } = await repository();
  await writeFile(join(cwd, "unified", "source.kt"), "changed\n");
  await commit(cwd, "feat: change unified");
  await writeFile(join(cwd, "unified", "source.kt"), "unified\n");
  const revertCommit = await commit(cwd, "revert: restore unified");
  const releaseContext = context(cwd, initialCommit, [
    { hash: revertCommit, message: "revert: restore unified" },
  ]);

  assert.equal(await hasReleaseChanges(["unified/"], releaseContext), false);
  assert.equal(
    await analyzeCommits(
      { releasePaths: ["unified/"], releaseTypeEnv: "UNIFIED_RELEASE_TYPE" },
      releaseContext,
    ),
    null,
  );
});

test("uses the explicit release type when the release trigger changes", async () => {
  const { cwd, initialCommit } = await repository();
  await writeFile(join(cwd, "unified", "release-trigger"), "1\n");
  const hash = await commit(cwd, "build: trigger unified release");
  const unifiedDirectory = join(cwd, "unified");

  assert.equal(
    await analyzeCommits(
      { releasePaths: ["unified/"], releaseTypeEnv: "UNIFIED_RELEASE_TYPE" },
      context(unifiedDirectory, initialCommit, [{ hash, message: "build: trigger unified release" }], "minor"),
    ),
    "minor",
  );
});
