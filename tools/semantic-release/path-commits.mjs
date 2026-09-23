// Limits semantic-release to commits and net changes in the files owned by one release line.
import { execFile } from "node:child_process";
import { promisify } from "node:util";

import { analyzeCommits as analyze } from "@semantic-release/commit-analyzer";
import { generateNotes as notes } from "@semantic-release/release-notes-generator";

const execFileAsync = promisify(execFile);
const releaseTypes = new Set(["major", "minor", "patch"]);

const topLevelPathspec = (path) => `:(top)${path}`;

async function git(args, context) {
  return execFileAsync("git", args, {
    cwd: context.cwd,
    env: context.env,
  });
}

export async function releaseCommits(releasePaths, context) {
  const range = context.lastRelease.gitHead ? `${context.lastRelease.gitHead}..HEAD` : "HEAD";
  const { stdout } = await git(
    ["log", "--full-history", "--format=%H", range, "--", ...releasePaths.map(topLevelPathspec)],
    context,
  );
  const matchingHashes = new Set(stdout.split("\n").filter(Boolean));
  return context.commits.filter((commit) => matchingHashes.has(commit.hash));
}

export async function hasReleaseChanges(releasePaths, context) {
  const pathspecs = releasePaths.map(topLevelPathspec);
  if (!context.lastRelease.gitHead) {
    const { stdout } = await git(["ls-files", "--", ...pathspecs], context);
    return stdout.length > 0;
  }

  try {
    await git(["diff", "--quiet", context.lastRelease.gitHead, "HEAD", "--", ...pathspecs], context);
    return false;
  } catch (error) {
    if (error.code === 1) {
      return true;
    }
    throw error;
  }
}

async function withReleaseCommits({ releasePaths, releaseTypeEnv, ...config }, context) {
  if (!Array.isArray(releasePaths) || releasePaths.length === 0) {
    throw new Error("releasePaths must contain at least one owned path");
  }

  const commits = await releaseCommits(releasePaths, context);
  return [{ releasePaths, releaseTypeEnv, config }, { ...context, commits }];
}

export async function analyzeCommits(pluginConfig, context) {
  const [{ releasePaths, releaseTypeEnv, config }, releaseContext] =
    await withReleaseCommits(pluginConfig, context);

  if (!(await hasReleaseChanges(releasePaths, context))) {
    context.logger.log("No net changes found in this release's owned paths");
    return null;
  }

  if (releaseTypeEnv) {
    const releaseType = context.env[releaseTypeEnv];
    if (!releaseTypes.has(releaseType)) {
      throw new Error(`${releaseTypeEnv} must be one of: major, minor, patch`);
    }
    return releaseType;
  }

  return analyze(config, releaseContext);
}

export async function generateNotes(pluginConfig, context) {
  const [{ config }, releaseContext] = await withReleaseCommits(pluginConfig, context);
  return notes(config, releaseContext);
}
