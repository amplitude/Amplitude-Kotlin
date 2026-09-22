// Wraps the commit analyzer and release notes generator so each release line only sees its own commits.
//   scope: "unified"        -> keeps only `type(unified): ...` commits
//   excludeScope: "unified" -> keeps everything except `type(unified): ...` commits
import { analyzeCommits as analyze } from "@semantic-release/commit-analyzer";
import { generateNotes as notes } from "@semantic-release/release-notes-generator";

const scopeOf = (message) => message.match(/^\w+\(([^)]+)\)!?:/)?.[1];

function withScopedCommits({ scope, excludeScope, ...config }, context) {
  const commits = context.commits.filter((commit) => {
    const commitScope = scopeOf(commit.message);
    return scope ? commitScope === scope : commitScope !== excludeScope;
  });
  return [config, { ...context, commits }];
}

export const analyzeCommits = (config, context) => analyze(...withScopedCommits(config, context));
export const generateNotes = (config, context) => notes(...withScopedCommits(config, context));
