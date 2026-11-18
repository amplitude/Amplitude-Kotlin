import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { promisify } from "node:util";

import { prepareRelease, verifyRelease } from "./publisher.mjs";

const exec = promisify(execFile);

async function git(cwd, ...args) {
  return (await exec("git", args, { cwd })).stdout.trim();
}

async function commit(cwd, message, amend = false) {
  await git(cwd, "add", ".");
  await git(cwd, "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgsign=false", "commit", ...(amend ? ["--amend"] : []), "-m", message);
  return git(cwd, "rev-parse", "HEAD");
}

async function fixture(t) {
  const cwd = await mkdtemp(join(tmpdir(), "kotlin-publisher-test-"));
  t.after(() => rm(cwd, { recursive: true, force: true }));
  await git(cwd, "init", "--initial-branch=main");
  await git(cwd, "config", "user.email", "test@example.com");
  await git(cwd, "config", "user.name", "Test");
  await mkdir(join(cwd, "android"));
  await mkdir(join(cwd, "unified"));
  await writeFile(join(cwd, "CHANGELOG.md"), "# Analytics history\n");
  await writeFile(join(cwd, "gradle.properties"), "VERSION_NAME=1.3.0\n");
  await writeFile(join(cwd, "unified/CHANGELOG.md"), "# Unified history\n");
  await writeFile(join(cwd, "unified/gradle.properties"), "VERSION_NAME=1.1.0\n");
  await writeFile(join(cwd, "android/source.kt"), "analytics\n");
  await writeFile(join(cwd, "unified/source.kt"), "unified\n");
  const base = await commit(cwd, "feat: initial");
  await git(cwd, "tag", "v1.3.0");
  await git(cwd, "tag", "unified-v1.1.0");
  return { cwd, base };
}

test("plans Analytics using only its release paths and verifies the squash commit", async (t) => {
  const { cwd, base } = await fixture(t);
  await writeFile(join(cwd, "unified/source.kt"), "unified feature\n");
  await commit(cwd, "feat: Unified feature");
  await writeFile(join(cwd, "android/source.kt"), "analytics fix\n");
  const sourceSha = await commit(cwd, "fix: Analytics fix");
  await git(cwd, "checkout", "--detach", sourceSha);

  const plan = await prepareRelease({ cwd, lane: "analytics", sourceSha });
  assert.equal(plan.previousTag, "v1.3.0");
  assert.equal(plan.version, "1.3.1");
  assert.equal(plan.tag, "v1.3.1");
  assert.match(plan.notes, /https:\/\/github\.com\/amplitude\/Amplitude-Kotlin\/compare\//u);
  assert.match(plan.notes, /Analytics fix/u);
  assert.doesNotMatch(plan.notes, /Unified feature/u);
  assert.equal(await git(cwd, "rev-parse", "refs/tags/v1.3.0"), base);
  assert.equal(await git(cwd, "tag", "--list", "v1.3.1"), "");
  assert.equal(await git(cwd, "diff", "--name-only"), "CHANGELOG.md\ngradle.properties");
  const mergeSha = await commit(cwd, "chore(release): 1.3.1");
  await verifyRelease({ cwd, plan, mergeSha });

  await assert.rejects(
    () => verifyRelease({ cwd, plan: { ...plan, sourceSha: base }, mergeSha }),
    /exactly one parent/u,
  );
  await writeFile(join(cwd, "android/source.kt"), "unexpected change\n");
  const expandedMerge = await commit(cwd, "chore(release): 1.3.1", true);
  await assert.rejects(() => verifyRelease({ cwd, plan, mergeSha: expandedMerge }), /modify exactly/u);
});

test("plans Unified independently using the selected bump and rejects modified metadata", async (t) => {
  const { cwd } = await fixture(t);
  await writeFile(join(cwd, "unified/source.kt"), "unified fix\n");
  const sourceSha = await commit(cwd, "fix: Unified fix");
  const plan = await prepareRelease({ cwd, lane: "unified", releaseType: "minor", sourceSha });
  assert.equal(plan.tag, "unified-v1.2.0");
  assert.equal(plan.previousTag, "unified-v1.1.0");
  assert.equal(await readFile(join(cwd, "gradle.properties"), "utf8"), "VERSION_NAME=1.3.0\n");
  await writeFile(join(cwd, "unified/CHANGELOG.md"), "unapproved notes\n");
  const mergeSha = await commit(cwd, "chore(release): Unified 1.2.0");
  await assert.rejects(() => verifyRelease({ cwd, plan, mergeSha }), /does not match the approved release plan/u);
});

test("leaves the checkout unchanged when the lane has no releasable changes", async (t) => {
  const { cwd } = await fixture(t);
  await writeFile(join(cwd, "unified/source.kt"), "unified feature\n");
  const sourceSha = await commit(cwd, "feat: Unified feature");
  const plan = await prepareRelease({ cwd, lane: "analytics", sourceSha });
  assert.equal(plan.version, null);
  assert.deepEqual(plan.paths, {});
  assert.equal(await git(cwd, "status", "--porcelain"), "");
});
