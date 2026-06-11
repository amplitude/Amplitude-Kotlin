const limits = [
  {
    // Android Release AAR
    path: './android/build/outputs/aar/android-release.aar',
    limit: '399kb',
    brotli: false,
  },
  {
    // Core JAR file
    path: [
      './core/build/libs/core-*.jar',
      '!core/build/libs/*-sources.jar',
    ],
    limit: '550kb',
    brotli: false,
  },
]

module.exports = limits;
