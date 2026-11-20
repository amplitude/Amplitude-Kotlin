import assert from 'node:assert/strict';
import { test } from 'node:test';
import { finalizeRelease, getMavenState, preflight, prepareReleasePR, reserveMavenUpload } from '../publisher/remote.mjs';

const repo = { full_name: 'amplitude/Amplitude-Kotlin' };
const plan = {
  schemaVersion: 1,
  sourceSha: '1'.repeat(40),
  mergeSha: '2'.repeat(40),
  releasePr: 43,
  lane: 'unified',
  version: '1.2.3',
  tag: 'unified-v1.2.3',
  notes: 'Release notes.',
  paths: { 'unified/CHANGELOG.md': '3'.repeat(64), 'unified/gradle.properties': '4'.repeat(64) },
};
const branch = `sdk-publisher/kotlin/${plan.lane}/${plan.version}/${plan.sourceSha}`;
const title = 'chore(release): unified 1.2.3';
const closedPr = {
  number: plan.releasePr,
  state: 'closed',
  merged: true,
  title,
  merge_commit_sha: plan.mergeSha,
  base: { ref: 'main', repo },
  head: { ref: branch, repo },
};

function releaseFixture({ tag = null, release = null } = {}) {
  const writes = [];
  const api = async (path, { method = 'GET', body } = {}) => {
    if (method !== 'GET') writes.push({ method, path, body });
    if (path === `/pulls/${plan.releasePr}`) return closedPr;
    if (path === `/git/ref/tags/${plan.tag}`) return tag;
    if (path === `/releases/tags/${plan.tag}`) return release;
    if (method === 'POST' && path === '/git/refs') {
      assert.equal(tag, null);
      tag = { ref: body.ref, object: { type: 'commit', sha: body.sha } };
      return tag;
    }
    if (method === 'POST' && path === '/releases') {
      assert.equal(release, null);
      release = body;
      return release;
    }
    assert.fail(`Unexpected call: ${method} ${path}`);
  };
  return { api, writes };
}

test('preparation creates a separate branch and PR and reuses an identical existing PR', async () => {
  const tree = '5'.repeat(40);
  const commit = '6'.repeat(40);
  const staged = { tree, files: Object.keys(plan.paths).map(path => ({ path, data: Buffer.from('staged') })) };
  const writes = [];
  let branchRef = null;
  let pr = null;
  const api = async (path, { method = 'GET', body } = {}) => {
    if (method !== 'GET') writes.push({ method, path, body });
    if (path === '/git/ref/heads/main') return { ref: 'refs/heads/main', object: { type: 'commit', sha: plan.sourceSha } };
    if (path === `/git/ref/heads/${branch}`) return branchRef;
    if (path === `/git/commits/${plan.sourceSha}`) return { tree: { sha: '7'.repeat(40) } };
    if (path === `/git/commits/${commit}`) return { tree: { sha: tree }, parents: [{ sha: plan.sourceSha }] };
    if (path === '/git/blobs') return { sha: '8'.repeat(40) };
    if (path === '/git/trees') return { sha: tree };
    if (path === '/git/commits') return { sha: commit };
    if (path === '/git/refs') {
      branchRef = { ref: body.ref, object: { type: 'commit', sha: body.sha } };
      return branchRef;
    }
    if (path.startsWith('/pulls?')) return pr ? [pr] : [];
    if (path === '/pulls') {
      pr = { ...closedPr, state: 'open', head: { ...closedPr.head, sha: commit } };
      return pr;
    }
    assert.fail(`Unexpected call: ${method} ${path}`);
  };

  assert.equal(await prepareReleasePR(api, plan, staged), plan.releasePr);
  const initialWrites = writes.length;
  assert.equal(writes.find(write => write.path === '/git/refs').body.ref, `refs/heads/${branch}`);
  assert.equal(writes.find(write => write.path === '/pulls').body.base, 'main');
  assert.equal(await prepareReleasePR(api, plan, staged), plan.releasePr);
  assert.equal(writes.length, initialWrites);
});

test('preparation stops on a stale source before any mutation', async () => {
  const paths = [];
  const api = async (path, { method = 'GET' } = {}) => {
    paths.push({ path, method });
    return { ref: 'refs/heads/main', object: { type: 'commit', sha: '0'.repeat(40) } };
  };
  await assert.rejects(prepareReleasePR(api, plan, {}), /main has moved/);
  assert.deepEqual(paths, [{ path: '/git/ref/heads/main', method: 'GET' }]);
});

test('a matching reserved tag never permits an automatic second upload', async () => {
  const { api, writes } = releaseFixture();
  assert.equal(await reserveMavenUpload(api, plan, async () => 'absent'), true);
  assert.equal(await reserveMavenUpload(api, plan, async () => 'absent'), false);
  assert.deepEqual(writes, [
    { method: 'POST', path: '/git/refs', body: { ref: `refs/tags/${plan.tag}`, sha: plan.mergeSha } },
  ]);
});

test('existing coordinates without the release tag, partial publication and conflicting tags block writes', async () => {
  let fixture = releaseFixture();
  await assert.rejects(preflight(fixture.api, plan, async () => 'complete'), /without a matching release tag/);
  await assert.rejects(preflight(fixture.api, plan, async () => 'partial'), /partial release/);
  assert.equal(fixture.writes.length, 0);

  fixture = releaseFixture({ tag: { ref: `refs/tags/${plan.tag}`, object: { type: 'commit', sha: plan.sourceSha } } });
  await assert.rejects(reserveMavenUpload(fixture.api, plan, async () => 'absent'), /different commit/);
  assert.equal(fixture.writes.length, 0);
});

test('release finalization waits for Maven and is idempotent after completion', async () => {
  const { api, writes } = releaseFixture({ tag: { ref: `refs/tags/${plan.tag}`, object: { type: 'commit', sha: plan.mergeSha } } });
  await assert.rejects(finalizeRelease(api, plan, { maven: async () => 'partial', attempts: 1 }), /not fully visible/);
  assert.equal(writes.length, 0);
  await finalizeRelease(api, plan, { maven: async () => 'complete' });
  await finalizeRelease(api, plan, { maven: async () => 'complete' });
  assert.equal(writes.length, 1);
  assert.equal(writes[0].path, '/releases');
  assert.equal(writes[0].body.target_commitish, plan.mergeSha);
});

test('Maven probes the lane package and signatures, with partial and unknown outcomes distinct', async () => {
  const urls = [];
  const complete = await getMavenState(plan, async (url, options) => {
    assert.equal(options.method, 'HEAD');
    urls.push(url);
    return { status: 200 };
  });
  assert.equal(complete, 'complete');
  assert.deepEqual(urls.map(url => url.split('/').at(-1)), [
    'unified-android-1.2.3.pom', 'unified-android-1.2.3.pom.asc',
    'unified-android-1.2.3.aar', 'unified-android-1.2.3.aar.asc',
  ]);
  assert.ok(urls.every(url => url.startsWith('https://repo.maven.apache.org/maven2/com/amplitude/unified-android/1.2.3/')));
  assert.equal(await getMavenState(plan, async () => ({ status: 404 })), 'absent');
  assert.equal(await getMavenState(plan, async url => ({ status: url.endsWith('.aar') ? 404 : 200 })), 'partial');
  await assert.rejects(getMavenState(plan, async () => ({ status: 429 })), /Could not verify Maven Central state/);
});
