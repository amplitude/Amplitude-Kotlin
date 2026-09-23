module.exports = {
  "branches": [
    {name: 'beta', prerelease: true},
    "main"
  ],
  "tagFormat": ["v${version}"],
  "plugins": [
    ["./tools/semantic-release/path-commits.mjs", {
      "releasePaths": [
        "analytics-core/",
        "android/",
        "build.gradle.kts",
        "buildSrc/",
        "gradle/",
        "gradle.properties",
        "streaming-analytics-android/"
      ],
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
      "message": "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}"
    }],
    ["@semantic-release/exec", {
      "publishCmd": "./gradlew publishAllPublicationsToMavenCentral --no-configuration-cache",
    }],
  ],
}
