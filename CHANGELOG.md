# [1.8.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.7.1...v1.8.0) (2023-04-10)


### Bug Fixes

* filter null values in identify intercept ([#116](https://github.com/amplitude/Amplitude-Kotlin/issues/116)) ([3689fc1](https://github.com/amplitude/Amplitude-Kotlin/commit/3689fc1088b9603c2918ff352fa8c2aa89bc63ea))


### Features

* update retry handling ([#113](https://github.com/amplitude/Amplitude-Kotlin/issues/113)) ([1020acd](https://github.com/amplitude/Amplitude-Kotlin/commit/1020acdbf080b5823088c0046f5eedcef86ded56))

## [1.7.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.7.0...v1.7.1) (2023-03-06)


### Bug Fixes

* catch and prevent index out bound exception ([#108](https://github.com/amplitude/Amplitude-Kotlin/issues/108)) ([7a4eb11](https://github.com/amplitude/Amplitude-Kotlin/commit/7a4eb11e8f7a6e5b55b3c2eafc5e61d5cae6e3c5))

# [1.7.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.6.2...v1.7.0) (2023-02-24)


### Features

* add Identify volume reduction ([#107](https://github.com/amplitude/Amplitude-Kotlin/issues/107)) ([8c3c39c](https://github.com/amplitude/Amplitude-Kotlin/commit/8c3c39ce1f79485a0ce716bbf01de464a9afe9a8))

## [1.6.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.6.1...v1.6.2) (2023-02-14)


### Bug Fixes

* return 'sessionId' property to Amplitude class ([#106](https://github.com/amplitude/Amplitude-Kotlin/issues/106)) ([7a938a1](https://github.com/amplitude/Amplitude-Kotlin/commit/7a938a1acb0ec0d07f1eab3cf27c4f7e9b751bf8))

## [1.6.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.6.0...v1.6.1) (2023-02-03)


### Bug Fixes

* catch filenotfound exception when rollover before upload ([#103](https://github.com/amplitude/Amplitude-Kotlin/issues/103)) ([80bc1f5](https://github.com/amplitude/Amplitude-Kotlin/commit/80bc1f59e1866f4a429aa84574075ac13fa67c8f))

# [1.6.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.5.2...v1.6.0) (2023-01-19)


### Features

* add log.debug to send event and handle response ([#100](https://github.com/amplitude/Amplitude-Kotlin/issues/100)) ([23c485a](https://github.com/amplitude/Amplitude-Kotlin/commit/23c485ac633263511f2a81299af1ca1d9ef12e71))

## [1.5.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.5.1...v1.5.2) (2022-11-19)


### Bug Fixes

* Add Index out of bound exception handling for PropertiesFile.save() ([#96](https://github.com/amplitude/Amplitude-Kotlin/issues/96)) ([c6578bf](https://github.com/amplitude/Amplitude-Kotlin/commit/c6578bfdfc9ee782fe01106cf607e19c979ed810))

## [1.5.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.5.0...v1.5.1) (2022-11-11)


### Bug Fixes

* add error handling and logging for file load crash issue ([#95](https://github.com/amplitude/Amplitude-Kotlin/issues/95)) ([640bc8e](https://github.com/amplitude/Amplitude-Kotlin/commit/640bc8e35210e3d9270064aaaa786c6307f66b9e))

# [1.5.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.8...v1.5.0) (2022-11-04)


### Features

* support integration with experiment android ([#92](https://github.com/amplitude/Amplitude-Kotlin/issues/92)) ([2ada09b](https://github.com/amplitude/Amplitude-Kotlin/commit/2ada09b6a7e77103391a92690d55b8fe604843da))

## [1.4.8](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.7...v1.4.8) (2022-10-28)


### Bug Fixes

* start build() lazily ([#90](https://github.com/amplitude/Amplitude-Kotlin/issues/90)) ([d829ba7](https://github.com/amplitude/Amplitude-Kotlin/commit/d829ba7a5dc6ae2524402c4f50581b817f14323a))

## [1.4.7](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.6...v1.4.7) (2022-10-28)


### Bug Fixes

* move session-specific logic to Timeline ([#89](https://github.com/amplitude/Amplitude-Kotlin/issues/89)) ([b353b8c](https://github.com/amplitude/Amplitude-Kotlin/commit/b353b8c47db387768fbfb71b26af86cdcc7dbac6))

## [1.4.6](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.5...v1.4.6) (2022-10-24)


### Bug Fixes

* restore previous session data on app opening ([#87](https://github.com/amplitude/Amplitude-Kotlin/issues/87)) ([e4f9874](https://github.com/amplitude/Amplitude-Kotlin/commit/e4f9874f60fe82ea1edbf9c8d76335c7def2581d))

## [1.4.5](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.4...v1.4.5) (2022-10-19)


### Bug Fixes

* move session-specific logic from ContextPlugin to avoid race conditions ([#86](https://github.com/amplitude/Amplitude-Kotlin/issues/86)) ([311c230](https://github.com/amplitude/Amplitude-Kotlin/commit/311c23053a9dcc2ce0b697aecf0588f189a116ca))

## [1.4.4](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.3...v1.4.4) (2022-10-14)


### Bug Fixes

* clean up permission ([#85](https://github.com/amplitude/Amplitude-Kotlin/issues/85)) ([0402c91](https://github.com/amplitude/Amplitude-Kotlin/commit/0402c913620ba528431f953a2a4db0f97ef1129d))
* make all variable in configuration mutable ([#84](https://github.com/amplitude/Amplitude-Kotlin/issues/84)) ([917796c](https://github.com/amplitude/Amplitude-Kotlin/commit/917796c558b286f6c44ed2accddca9056bf59086))
* make trackingOptions able to overwrite in configuration ([#83](https://github.com/amplitude/Amplitude-Kotlin/issues/83)) ([281f0de](https://github.com/amplitude/Amplitude-Kotlin/commit/281f0deb779961027caa65e752d4211533aeacd8))

## [1.4.3](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.2...v1.4.3) (2022-10-03)


### Bug Fixes

* DXOC-200 lateinit var not initialized ([#81](https://github.com/amplitude/Amplitude-Kotlin/issues/81)) ([546e026](https://github.com/amplitude/Amplitude-Kotlin/commit/546e02640f515cd50e1e84d696f22d0b91c3d233))

## [1.4.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.1...v1.4.2) (2022-09-29)


### Bug Fixes

* fix other location info not auto populate ([#79](https://github.com/amplitude/Amplitude-Kotlin/issues/79)) ([5810c9d](https://github.com/amplitude/Amplitude-Kotlin/commit/5810c9d795fe840c254ce050e41d82ed3b9b62af))

## [1.4.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.4.0...v1.4.1) (2022-09-27)


### Bug Fixes

* fix the ip address not catch issue ([#78](https://github.com/amplitude/Amplitude-Kotlin/issues/78)) ([bf8e2a1](https://github.com/amplitude/Amplitude-Kotlin/commit/bf8e2a17a070e41fbea1bd9511b49e6ce0155aef))

# [1.4.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.3.2...v1.4.0) (2022-09-27)


### Bug Fixes

* add GetIngestionMetadataPlugin in android.Amplitude ([#77](https://github.com/amplitude/Amplitude-Kotlin/issues/77)) ([0c6d6cb](https://github.com/amplitude/Amplitude-Kotlin/commit/0c6d6cb7b125e2856b241ddf8141a0069c723abc))


### Features

* add ampli extra plugin for attaching ingestion metadata information ([#76](https://github.com/amplitude/Amplitude-Kotlin/issues/76)) ([a8aa604](https://github.com/amplitude/Amplitude-Kotlin/commit/a8aa604a7e894df44f8c36d81a270e43adfb3e69))

## [1.3.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.3.1...v1.3.2) (2022-09-22)


### Bug Fixes

* enable the remote ip ([#75](https://github.com/amplitude/Amplitude-Kotlin/issues/75)) ([232620c](https://github.com/amplitude/Amplitude-Kotlin/commit/232620cf75e4ece7718678ce6a4dd7553ad859b3))

## [1.3.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.3.0...v1.3.1) (2022-09-20)


### Bug Fixes

* fix optOut ([#74](https://github.com/amplitude/Amplitude-Kotlin/issues/74)) ([e476e4c](https://github.com/amplitude/Amplitude-Kotlin/commit/e476e4cad568c587ee9d8766bb71073a32e49335))

# [1.3.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.2.1...v1.3.0) (2022-09-11)


### Features

* allow android configuration values to be updated ([#68](https://github.com/amplitude/Amplitude-Kotlin/issues/68)) ([f2497df](https://github.com/amplitude/Amplitude-Kotlin/commit/f2497dfb79db594caa772f57ccf395d710de3f0c))

## [1.2.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.2.0...v1.2.1) (2022-09-09)


### Bug Fixes

* fix app set id null issue ([#67](https://github.com/amplitude/Amplitude-Kotlin/issues/67)) ([0cdc238](https://github.com/amplitude/Amplitude-Kotlin/commit/0cdc23824a891981779a6d3081aa3ac091972164))

# [1.2.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.1.3...v1.2.0) (2022-09-09)


### Features

* add ingestion_metadata field ([#63](https://github.com/amplitude/Amplitude-Kotlin/issues/63)) ([354ec7b](https://github.com/amplitude/Amplitude-Kotlin/commit/354ec7bd35acd50d337ae424d324c8fb27106bdf))

## [1.1.3](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.1.2...v1.1.3) (2022-08-12)


### Bug Fixes

* Unterminated arrays caused by multi client instances with same name and api key ([#61](https://github.com/amplitude/Amplitude-Kotlin/issues/61)) ([48d5be2](https://github.com/amplitude/Amplitude-Kotlin/commit/48d5be24507cc9c895dfaa5ea61f7ee09015dadc))

## [1.1.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.1.1...v1.1.2) (2022-08-10)


### Bug Fixes

* add version_name to event json body ([#60](https://github.com/amplitude/Amplitude-Kotlin/issues/60)) ([0ff8d67](https://github.com/amplitude/Amplitude-Kotlin/commit/0ff8d67eafd019a8c75c763c201a7bc47a028b27))

## [1.1.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.1.0...v1.1.1) (2022-08-06)


### Bug Fixes

* Amplitude.build() is not main thread safe on Android ([#55](https://github.com/amplitude/Amplitude-Kotlin/issues/55)) ([42ad931](https://github.com/amplitude/Amplitude-Kotlin/commit/42ad9314eb7a1fe1be5334b256fefc9fa120518c))
* fix not call close in strict mode ([#56](https://github.com/amplitude/Amplitude-Kotlin/issues/56)) ([20bf7ef](https://github.com/amplitude/Amplitude-Kotlin/commit/20bf7efeff8dfb5223e73b655a022f6e12a912c8))

# [1.1.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.0.1...v1.1.0) (2022-07-29)


### Bug Fixes

* authorize for release action ([#47](https://github.com/amplitude/Amplitude-Kotlin/issues/47)) ([5c44d34](https://github.com/amplitude/Amplitude-Kotlin/commit/5c44d345d6bcc3ed8a3594e75e73bc6e4f82a5ad))
* invalid json array string for android file storage ([#50](https://github.com/amplitude/Amplitude-Kotlin/issues/50)) ([593050a](https://github.com/amplitude/Amplitude-Kotlin/commit/593050a4c5c92e7173efb08725379639af87f9f7))


### Features

* add reset method to reset userId and deviceId ([#48](https://github.com/amplitude/Amplitude-Kotlin/issues/48)) ([837895a](https://github.com/amplitude/Amplitude-Kotlin/commit/837895a98100d9a150192d6512b8bbc6f11825f6))

## [1.0.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.0.0...v1.0.1) (2022-07-21)


### Bug Fixes

* formatter on AndroidStorage.kt ([#46](https://github.com/amplitude/Amplitude-Kotlin/issues/46)) ([2b8a4a6](https://github.com/amplitude/Amplitude-Kotlin/commit/2b8a4a6c97c2477a0ae160d82eb5eb7a34820e26))

# 1.0.0 (2022-06-28)


### Bug Fixes

* fix android aar issue ([#32](https://github.com/amplitude/Amplitude-Kotlin/issues/32)) ([69f5fe7](https://github.com/amplitude/Amplitude-Kotlin/commit/69f5fe7ed91d05252bc4a98b89ec7f8aade72d76))
* fix group call ([#42](https://github.com/amplitude/Amplitude-Kotlin/issues/42)) ([f84d143](https://github.com/amplitude/Amplitude-Kotlin/commit/f84d1437d183d8208ae45a5b393c673a1ea8cdab))


### Features

* add amplitude destination plugin draft ([#3](https://github.com/amplitude/Amplitude-Kotlin/issues/3)) ([7ee9408](https://github.com/amplitude/Amplitude-Kotlin/commit/7ee9408d8d8ed7a0ef291dde6267b33811969565))
* add android context plugin ([#14](https://github.com/amplitude/Amplitude-Kotlin/issues/14)) ([25b6da3](https://github.com/amplitude/Amplitude-Kotlin/commit/25b6da3359c5284bc1361d1f8ca08ae8a5584aca))
* add basic test action and issue templates ([#9](https://github.com/amplitude/Amplitude-Kotlin/issues/9)) ([e72c608](https://github.com/amplitude/Amplitude-Kotlin/commit/e72c608842ea2d5864eed77960de6e53b0b485b0))
* add button to kotlin example to send custom event ([#41](https://github.com/amplitude/Amplitude-Kotlin/issues/41)) ([5c1e573](https://github.com/amplitude/Amplitude-Kotlin/commit/5c1e5735c0d96963e3468513b88a09aac149200f))
* add configuration java support ([#35](https://github.com/amplitude/Amplitude-Kotlin/issues/35)) ([a4e0801](https://github.com/amplitude/Amplitude-Kotlin/commit/a4e0801e5d83eb04c0772ab5d37e6c15495786f6))
* add error handling and retry ([#13](https://github.com/amplitude/Amplitude-Kotlin/issues/13)) ([689b114](https://github.com/amplitude/Amplitude-Kotlin/commit/689b114c4630bf4e6e451815fa5eb1a1e8b197a6))
* add eu and batch support ([#17](https://github.com/amplitude/Amplitude-Kotlin/issues/17)) ([6402b2a](https://github.com/amplitude/Amplitude-Kotlin/commit/6402b2a8958bb6fc9e739c771da171295ad3f144))
* add event bridge module ([#12](https://github.com/amplitude/Amplitude-Kotlin/issues/12)) ([49229ac](https://github.com/amplitude/Amplitude-Kotlin/commit/49229acb877e9572f16038e54120edf52af8ad9f))
* add event id support ([#25](https://github.com/amplitude/Amplitude-Kotlin/issues/25)) ([94aaa6b](https://github.com/amplitude/Amplitude-Kotlin/commit/94aaa6bf2b7f061720c06d7f4f7a7fc38a9c9436))
* add github action about docs and release ([#19](https://github.com/amplitude/Amplitude-Kotlin/issues/19)) ([3aa868e](https://github.com/amplitude/Amplitude-Kotlin/commit/3aa868ef24c0e857ef3d7824cc42d736b5343957))
* add identify support ([#18](https://github.com/amplitude/Amplitude-Kotlin/issues/18)) ([2f86adc](https://github.com/amplitude/Amplitude-Kotlin/commit/2f86adc582a7aa67a9157048ad8ce04984266dd5))
* add identity module and file storage ([#8](https://github.com/amplitude/Amplitude-Kotlin/issues/8)) ([8b7bada](https://github.com/amplitude/Amplitude-Kotlin/commit/8b7bada03342d7ad8a0c689e8d3c86271d521fba))
* add Java android example ([#24](https://github.com/amplitude/Amplitude-Kotlin/issues/24)) ([2670a2b](https://github.com/amplitude/Amplitude-Kotlin/commit/2670a2b9bbef381b9c28696499cebd1c78192249))
* add Kotlin android sample ([#23](https://github.com/amplitude/Amplitude-Kotlin/issues/23)) ([3b948c8](https://github.com/amplitude/Amplitude-Kotlin/commit/3b948c8bac688ff2896bc7e634e1519c5520ce65))
* add parner_id ([#16](https://github.com/amplitude/Amplitude-Kotlin/issues/16)) ([5e46cb9](https://github.com/amplitude/Amplitude-Kotlin/commit/5e46cb9d3d301c59e96cd56a72e7d66958615e62))
* add plan in configuration and export ([#34](https://github.com/amplitude/Amplitude-Kotlin/issues/34)) ([d84a84e](https://github.com/amplitude/Amplitude-Kotlin/commit/d84a84e3f2123249ea1d8e38f3121dd6a5b798c7))
* add plan versionId support ([#22](https://github.com/amplitude/Amplitude-Kotlin/issues/22)) ([ae84619](https://github.com/amplitude/Amplitude-Kotlin/commit/ae84619255b575ef5b895fbfa63d864daf88dcc2))
* add revenue helper class ([#6](https://github.com/amplitude/Amplitude-Kotlin/issues/6)) ([341fcf1](https://github.com/amplitude/Amplitude-Kotlin/commit/341fcf172673088cd41719bdea653e027b838c87))
* add unit tests for common jvm module ([#29](https://github.com/amplitude/Amplitude-Kotlin/issues/29)) ([dcc0e9d](https://github.com/amplitude/Amplitude-Kotlin/commit/dcc0e9dbbbf5464c07c5f1c6a607243882cccf13))
* add unit tests in core module ([#30](https://github.com/amplitude/Amplitude-Kotlin/issues/30)) ([4283148](https://github.com/amplitude/Amplitude-Kotlin/commit/428314817a594bcab8bdfc03f74f183286b806f3))
* add unit tests in event bridge module ([#28](https://github.com/amplitude/Amplitude-Kotlin/issues/28)) ([8d63c28](https://github.com/amplitude/Amplitude-Kotlin/commit/8d63c28fa79211942884ce7215e2b6e6b4c90cea))
* add unit tests in id module ([#27](https://github.com/amplitude/Amplitude-Kotlin/issues/27)) ([dc78bc6](https://github.com/amplitude/Amplitude-Kotlin/commit/dc78bc60aed5bc42239b2ff3e0f004bf99eaf59b))
* android local sync update ([#20](https://github.com/amplitude/Amplitude-Kotlin/issues/20)) ([e25ecd4](https://github.com/amplitude/Amplitude-Kotlin/commit/e25ecd49f983adefafe8652ceadcd5166c59296d))
* enable to pass the event options for all track call ([#36](https://github.com/amplitude/Amplitude-Kotlin/issues/36)) ([8dd1ab6](https://github.com/amplitude/Amplitude-Kotlin/commit/8dd1ab6eaf2542913cf15fe645197e296116f6c8))
* expose more api for support ([#33](https://github.com/amplitude/Amplitude-Kotlin/issues/33)) ([312fd85](https://github.com/amplitude/Amplitude-Kotlin/commit/312fd855527b88e702a322f50127dd257e0ba67a))
* make configuration open ([#38](https://github.com/amplitude/Amplitude-Kotlin/issues/38)) ([d0dfb15](https://github.com/amplitude/Amplitude-Kotlin/commit/d0dfb15219cda646b1733f7afef6f81ea8548ce9))
* remove json object from public interface ([#26](https://github.com/amplitude/Amplitude-Kotlin/issues/26)) ([0840b5d](https://github.com/amplitude/Amplitude-Kotlin/commit/0840b5d5fc538b2f088538365586cbf5babb819d))
* set the client userId and deviceId in identify call ([#37](https://github.com/amplitude/Amplitude-Kotlin/issues/37)) ([2c8b3f1](https://github.com/amplitude/Amplitude-Kotlin/commit/2c8b3f1160f8503d827ca7367ada9da57836e968))
* set up android module bare bone ([#7](https://github.com/amplitude/Amplitude-Kotlin/issues/7)) ([a8a8dc9](https://github.com/amplitude/Amplitude-Kotlin/commit/a8a8dc9391ce3eaeb6981b49a0f0f7d6a4b5c4a6))
* Set up jvm example ([#4](https://github.com/amplitude/Amplitude-Kotlin/issues/4)) ([ae825d6](https://github.com/amplitude/Amplitude-Kotlin/commit/ae825d61b0405d7dc9e64187a883bbc14fa600f8))
* setup basic barebone ([#2](https://github.com/amplitude/Amplitude-Kotlin/issues/2)) ([8e4b635](https://github.com/amplitude/Amplitude-Kotlin/commit/8e4b6356db7c83b70c98f33c5ecfa83ba59ba3f3))
* setup basic doc and workflow ([#1](https://github.com/amplitude/Amplitude-Kotlin/issues/1)) ([31bd223](https://github.com/amplitude/Amplitude-Kotlin/commit/31bd223bace3bb2cbb3efabf26abb0fa3ad9576d))
* setup common modules ([#11](https://github.com/amplitude/Amplitude-Kotlin/issues/11)) ([4a4ff66](https://github.com/amplitude/Amplitude-Kotlin/commit/4a4ff66b9c4c0bf3a5d3fcc78b0358ca8b9871b7))
* update android storage ([#15](https://github.com/amplitude/Amplitude-Kotlin/issues/15)) ([56c1d6a](https://github.com/amplitude/Amplitude-Kotlin/commit/56c1d6aebbd5cea058392c83bf1fe2ca07a28ef5))
* update library name ([#21](https://github.com/amplitude/Amplitude-Kotlin/issues/21)) ([309976d](https://github.com/amplitude/Amplitude-Kotlin/commit/309976d80d69f3ff5ad38d5c64459a42f860e294))
