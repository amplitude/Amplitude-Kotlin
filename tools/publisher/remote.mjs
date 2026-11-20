import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { appendFileSync, readFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const repository = 'amplitude/Amplitude-Kotlin';
const sourceBranch = 'main';
const sourceRef = `refs/heads/${sourceBranch}`;
const apiRoot = `https://api.github.com/repos/${repository}`;
const mavenRoot = 'https://repo.maven.apache.org/maven2/com/amplitude';
const shaPattern = /^[a-f0-9]{40}$/;
const digestPattern = /^[a-f0-9]{64}$/;
const versionPattern = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

const lanes = {
  analytics: {
    tagPrefix: 'v',
    paths: ['CHANGELOG.md', 'gradle.properties'],
    packages: [['analytics-core', 'jar'], ['analytics-android', 'aar'], ['streaming-analytics-android', 'aar']],
  },
  unified: {
    tagPrefix: 'unified-v',
    paths: ['unified/CHANGELOG.md', 'unified/gradle.properties'],
    packages: [['unified-android', 'aar']],
  },
};

export function validatePlan(plan, merged = false) {
  const lane = plan && Object.hasOwn(lanes, plan.lane) && lanes[plan.lane];
  if (!lane || plan.schemaVersion !== 1 || typeof plan.sourceSha !== 'string' || !shaPattern.test(plan.sourceSha) ||
      typeof plan.version !== 'string' || !versionPattern.test(plan.version) || plan.tag !== `${lane.tagPrefix}${plan.version}` ||
      typeof plan.notes !== 'string' || plan.notes.length > 60_000 || !plan.paths ||
      Object.keys(plan.paths).length !== lane.paths.length ||
      !lane.paths.every(path => Object.hasOwn(plan.paths, path) && typeof plan.paths[path] === 'string' && digestPattern.test(plan.paths[path]))) {
    throw new Error('Expected a valid Kotlin release plan with exact version and changelog paths.');
  }
  if (merged && (typeof plan.mergeSha !== 'string' || !shaPattern.test(plan.mergeSha) || plan.mergeSha === plan.sourceSha ||
      !Number.isSafeInteger(plan.releasePr) || plan.releasePr < 1)) {
    throw new Error('Expected a merged release plan with the verified merge SHA and pull request number.');
  }
  return plan;
}

function releaseBranch(plan) {
  return `sdk-publisher/kotlin/${plan.lane}/${plan.version}/${plan.sourceSha}`;
}

function releaseTitle(plan) {
  return `chore(release): ${plan.lane === 'unified' ? 'unified ' : ''}${plan.version}`;
}

function encodedRef(path) {
  return path.split('/').map(encodeURIComponent).join('/');
}

class ApiError extends Error {
  constructor(method, path, status) {
    // Never log the request headers, request body, or credentials from an error.
    super(`GitHub ${method} ${path} returned HTTP ${status}.`);
    this.status = status;
  }
}

function githubApi(token) {
  return async (path, { method = 'GET', body, missing = false } = {}) => {
    const response = await fetch(`${apiRoot}${path}`, {
      method,
      redirect: 'error',
      headers: {
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30_000),
    });
    if (response.status === 404 && missing) return null;
    if (!response.ok) throw new ApiError(method, path, response.status);
    return response.json();
  };
}

function git(args) {
  const env = { ...process.env };
  delete env.GH_TOKEN;
  return execFileSync('git', args, { env, maxBuffer: 16 * 1024 * 1024 });
}

function applyReleasePatch(plan, patch) {
  const paths = lanes[plan.lane].paths;
  if (git(['rev-parse', 'HEAD']).toString().trim() !== plan.sourceSha ||
      git(['status', '--porcelain=v1', '--untracked-files=no']).length) {
    throw new Error('The release PR must start from a clean checkout of the planned source SHA.');
  }
  git(['apply', '--index', '--', patch]);
  const nameStatus = git(['diff', '--cached', '--name-status', '--no-renames', '-z']).toString().split('\0');
  const expected = paths.flatMap(path => ['M', path]).concat('');
  if (JSON.stringify(nameStatus) !== JSON.stringify(expected)) {
    throw new Error('Patch must modify exactly the lane version and changelog files.');
  }
  const files = paths.map(path => {
    if (!git(['ls-files', '--stage', '--', path]).toString().startsWith('100644 ')) {
      throw new Error(`Unexpected Git file mode for ${path}.`);
    }
    const data = git(['show', `:${path}`]);
    if (createHash('sha256').update(data).digest('hex') !== plan.paths[path]) {
      throw new Error(`Staged content differs from the release plan for ${path}.`);
    }
    return { path, data };
  });
  return { tree: git(['write-tree']).toString().trim(), files };
}

function pullRequestBody(plan) {
  return `### Describe what this PR is addressing
Prepare the Amplitude Kotlin ${plan.lane} ${plan.version} release.

### Describe the solution
Update the lane's version and changelog from source commit \`${plan.sourceSha}\`.

### Steps to verify the change
Review the version and release notes. Merge by squash once required checks pass and the planned source is still the tip of \`main\`.

### Useful links and documentation (optional)
The private SDK Publisher workflow publishes the approved release.

### Checklist
* [x] Does your PR title have the correct [title format](https://github.com/amplitude/Amplitude-Kotlin/blob/main/CONTRIBUTING.md#pr-commit-title-conventions)?
* [ ] Does your PR have a breaking change?
`;
}

async function readRef(api, namespace, name) {
  return api(`/git/ref/${namespace}/${encodedRef(name)}`, { missing: true });
}

async function checkBranch(api, plan, expectedTree, branch) {
  const ref = await readRef(api, 'heads', branch);
  if (ref === null) return null;
  const commitSha = ref.object?.sha;
  if (ref.ref !== `refs/heads/${branch}` || ref.object?.type !== 'commit' || !shaPattern.test(commitSha)) {
    throw new Error(`Unexpected Git reference for release branch ${branch}.`);
  }
  const commit = await api(`/git/commits/${commitSha}`);
  if (commit.tree?.sha !== expectedTree || commit.parents?.length !== 1 ||
      commit.parents[0].sha !== plan.sourceSha) {
    throw new Error(`Existing release branch ${branch} differs from the planned version change.`);
  }
  return commitSha;
}

function checkPR(pr, plan, branch, headSha) {
  if (pr.base?.ref !== sourceBranch || pr.base.repo?.full_name !== repository ||
      pr.head?.ref !== branch || pr.head.repo?.full_name !== repository ||
      pr.head.sha !== headSha || pr.state !== 'open' || pr.draft ||
      pr.title !== releaseTitle(plan) || !Number.isSafeInteger(pr.number) || pr.number < 1) {
    throw new Error(`Existing pull request for ${branch} does not match the planned release.`);
  }
  return pr.number;
}

async function checkSourceTip(api, plan) {
  const main = await api('/git/ref/heads/main');
  if (main.ref !== sourceRef || main.object?.type !== 'commit' || main.object.sha !== plan.sourceSha) {
    throw new Error('Kotlin main has moved. Run a fresh prepare at its new tip.');
  }
}

export async function prepareReleasePR(api, plan, staged) {
  validatePlan(plan);
  await checkSourceTip(api, plan);

  const branch = releaseBranch(plan);
  let headSha = await checkBranch(api, plan, staged.tree, branch);
  if (headSha === null) {
    const source = await api(`/git/commits/${plan.sourceSha}`);
    const entries = [];
    for (const { path, data } of staged.files) {
      const blob = await api('/git/blobs', { method: 'POST', body: { content: data.toString('base64'), encoding: 'base64' } });
      entries.push({ path, mode: '100644', type: 'blob', sha: blob.sha });
    }
    const tree = await api('/git/trees', { method: 'POST', body: { base_tree: source.tree.sha, tree: entries } });
    if (tree.sha !== staged.tree) throw new Error('GitHub constructed a tree different from the release plan.');
    const commit = await api('/git/commits', {
      method: 'POST', body: { message: releaseTitle(plan), tree: tree.sha, parents: [plan.sourceSha] },
    });
    await checkSourceTip(api, plan);
    try {
      await api('/git/refs', { method: 'POST', body: { ref: `refs/heads/${branch}`, sha: commit.sha } });
    } catch (error) {
      if (![409, 422].includes(error.status)) throw error;
      // A concurrent prepare may have created the same immutable branch content.
    }
    headSha = await checkBranch(api, plan, staged.tree, branch);
    if (!headSha) throw new Error(`Could not create or verify release branch ${branch}.`);
  }

  const prQuery = new URLSearchParams({ state: 'all', head: `amplitude:${branch}`, per_page: '100' });
  const findExisting = async () => {
    const candidates = await api(`/pulls?${prQuery}`);
    if (candidates.length > 1) throw new Error(`Multiple pull requests exist for ${branch}.`);
    return candidates.length ? checkPR(candidates[0], plan, branch, headSha) : null;
  };
  const existing = await findExisting();
  if (existing !== null) return existing;

  await checkSourceTip(api, plan);
  try {
    const pr = await api('/pulls', {
      method: 'POST', body: { head: branch, base: sourceBranch, title: releaseTitle(plan), body: pullRequestBody(plan) },
    });
    return checkPR(pr, plan, branch, headSha);
  } catch (error) {
    if (![409, 422].includes(error.status)) throw error;
    const concurrent = await findExisting();
    if (concurrent !== null) return concurrent;
    throw error;
  }
}

export async function getMavenState(plan, request = fetch) {
  validatePlan(plan);
  const files = lanes[plan.lane].packages.flatMap(([artifact, extension]) => {
    const base = `${mavenRoot}/${artifact}/${plan.version}/${artifact}-${plan.version}`;
    return ['pom', 'pom.asc', extension, `${extension}.asc`].map(suffix => `${base}.${suffix}`);
  });
  const statuses = await Promise.all(files.map(async url => {
    const response = await request(url, {
      method: 'HEAD', redirect: 'error', signal: AbortSignal.timeout(20_000),
    });
    if (response.status !== 200 && response.status !== 404) {
      throw new Error(`Could not verify Maven Central state (HTTP ${response.status}): ${url}`);
    }
    return response.status;
  }));
  if (statuses.every(status => status === 200)) return 'complete';
  if (statuses.every(status => status === 404)) return 'absent';
  return 'partial';
}

async function checkMergedPR(api, plan) {
  const pr = await api(`/pulls/${plan.releasePr}`);
  if (pr.number !== plan.releasePr || pr.state !== 'closed' || !pr.merged ||
      pr.merge_commit_sha !== plan.mergeSha || pr.base?.ref !== sourceBranch ||
      pr.base.repo?.full_name !== repository || pr.head?.ref !== releaseBranch(plan) ||
      pr.head.repo?.full_name !== repository || pr.title !== releaseTitle(plan)) {
    throw new Error('The release pull request does not match the verified Kotlin merge.');
  }
}

async function tagState(api, plan) {
  const ref = await readRef(api, 'tags', plan.tag);
  if (ref === null) return 'missing';
  if (ref.ref !== `refs/tags/${plan.tag}`) throw new Error('Unexpected release tag reference.');
  let target = ref.object;
  for (let depth = 0; target?.type === 'tag' && depth < 8; depth++) {
    if (!shaPattern.test(target.sha)) throw new Error('Malformed annotated release tag.');
    target = (await api(`/git/tags/${target.sha}`)).object;
  }
  if (target?.type !== 'commit' || target.sha !== plan.mergeSha) {
    throw new Error(`Tag ${plan.tag} already exists at a different commit.`);
  }
  return 'matching';
}

async function releaseState(api, plan) {
  const release = await api(`/releases/tags/${encodeURIComponent(plan.tag)}`, { missing: true });
  if (release === null) return 'missing';
  if (release.tag_name !== plan.tag || release.name !== plan.tag || release.draft || release.prerelease ||
      typeof release.body !== 'string' || release.body.trimEnd() !== plan.notes.trimEnd()) {
    throw new Error(`GitHub Release ${plan.tag} exists with different metadata. Review it manually.`);
  }
  return 'matching';
}

export async function preflight(api, plan, maven = getMavenState) {
  validatePlan(plan, true);
  // Recheck the public merge before using the plan for any external write.
  await checkMergedPR(api, plan);
  const [central, tag, release] = await Promise.all([maven(plan), tagState(api, plan), releaseState(api, plan)]);
  if (central === 'partial') throw new Error('Maven Central has a partial release. Reconcile the deployment in Central Portal.');
  if ((central !== 'absent' || release === 'matching') && tag === 'missing') {
    throw new Error('Publication exists without a matching release tag. Review its provenance manually.');
  }
  if (release === 'matching' && central !== 'complete') {
    throw new Error('GitHub Release exists before a complete Maven Central publication. Review it manually.');
  }
  return { central, tag };
}

export async function reserveMavenUpload(api, plan, maven = getMavenState) {
  const { central, tag } = await preflight(api, plan, maven);
  if (tag === 'matching') return false;
  if (central !== 'absent') throw new Error('Maven Central is not empty for the planned version.');
  try {
    await api('/git/refs', { method: 'POST', body: { ref: `refs/tags/${plan.tag}`, sha: plan.mergeSha } });
  } catch (error) {
    if ([409, 422].includes(error.status) && await tagState(api, plan) === 'matching') return false;
    throw error;
  }
  if (await tagState(api, plan) !== 'matching') throw new Error('Could not verify the reserved release tag.');
  return true;
}

export async function finalizeRelease(api, plan, { maven = getMavenState, attempts = 20, wait = () => delay(15_000) } = {}) {
  validatePlan(plan, true);
  await checkMergedPR(api, plan);
  if (await tagState(api, plan) !== 'matching') throw new Error('The release tag must exist before finalization.');
  const release = await releaseState(api, plan);
  for (let attempt = 1; attempt <= attempts; attempt++) {
    if (await maven(plan) === 'complete') {
      if (release === 'matching') return;
      try {
        await api('/releases', {
          method: 'POST',
          body: { tag_name: plan.tag, target_commitish: plan.mergeSha, name: plan.tag, body: plan.notes, draft: false, prerelease: false },
        });
      } catch (error) {
        if (![409, 422].includes(error.status)) throw error;
      }
      if (await releaseState(api, plan) !== 'matching' || await tagState(api, plan) !== 'matching') {
        throw new Error('Could not verify the GitHub Release and its source tag.');
      }
      return;
    }
    if (attempt < attempts) await wait();
  }
  throw new Error('Maven Central is not fully visible. Check Central Portal before retrying finalization.');
}

function writeOutput(name, value) {
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, `${name}=${value}\n`);
  console.log(`${name}=${value}`);
}

async function main() {
  const { positionals, values } = parseArgs({
    allowPositionals: true,
    options: { plan: { type: 'string' }, patch: { type: 'string' } },
  });
  const [command] = positionals;
  if (positionals.length !== 1 || !['prepare-pr', 'preflight', 'tag', 'finalize'].includes(command) || !values.plan ||
      (command === 'prepare-pr') !== Boolean(values.patch)) {
    throw new Error('Usage: remote.mjs prepare-pr|preflight|tag|finalize --plan PATH [--patch PATH]');
  }
  const plan = validatePlan(JSON.parse(readFileSync(values.plan, 'utf8')), command !== 'prepare-pr');
  const token = command === 'preflight' ? undefined : process.env.GH_TOKEN;
  if (command !== 'preflight' && !token) throw new Error('A target-scoped Octo STS token is required as GH_TOKEN.');
  const api = githubApi(token);

  if (command === 'prepare-pr') {
    writeOutput('pr_number', await prepareReleasePR(api, plan, applyReleasePatch(plan, values.patch)));
  } else if (command === 'preflight') {
    const { central, tag } = await preflight(api, plan);
    writeOutput('central_state', central);
    writeOutput('tag_state', tag);
  } else if (command === 'tag') {
    writeOutput('tag_created', await reserveMavenUpload(api, plan));
  } else {
    await finalizeRelease(api, plan);
    writeOutput('release_tag', plan.tag);
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
