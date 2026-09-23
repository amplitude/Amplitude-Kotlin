// Release config for com.amplitude:unified-android, run from this directory by release-unified.yml.
// Only `type(unified): ...` commits count; core releases ignore them (see ../release.config.js).
module.exports = {
  "branches": ["main"],
  "tagFormat": "unified-v${version}",
  "plugins": [
    ["../tools/semantic-release/scoped-commits.mjs", {
      "scope": "unified",
      "preset": "angular",
      "parserOpts": {
        "noteKeywords": ["BREAKING CHANGE", "BREAKING CHANGES", "BREAKING"]
      }
    }],
    ["@semantic-release/changelog", {
      "changelogFile": "CHANGELOG.md"
    }],
    "@semantic-release/github",
    [
      "@google/semantic-release-replace-plugin",
      {
        "replacements": [
          {
            "files": ["gradle.properties"],
            "from": "VERSION_NAME=.*",
            "to": "VERSION_NAME=${nextRelease.version}",
            "results": [
              {
                "file": "gradle.properties",
                "hasChanged": true,
                "numMatches": 1,
                "numReplacements": 1
              }
            ],
            "countMatches": true
          },
        ]
      }
    ],
    ["@semantic-release/git", {
      "assets": ["gradle.properties", "CHANGELOG.md"],
      "message": "chore(release): unified ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}"
    }],
    ["@semantic-release/exec", {
      "publishCmd": "../gradlew :unified:publishAllPublicationsToMavenCentral -Pamplitude.unified.release=true --no-configuration-cache",
    }],
  ],
}
