# [1.21.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.20.7...v1.21.0) (2025-05-21)


### Bug Fixes

* event type name for network tracking ([#275](https://github.com/amplitude/Amplitude-Kotlin/issues/275)) ([0bfc126](https://github.com/amplitude/Amplitude-Kotlin/commit/0bfc1266a868296305e05a17281ce0e089e55070))
* filter unfinished files that don't exist ([#273](https://github.com/amplitude/Amplitude-Kotlin/issues/273)) ([c1f7ab5](https://github.com/amplitude/Amplitude-Kotlin/commit/c1f7ab5900fea18f11a1184de55e92ba2a4d55c6))
* make sure that amplitude is built before writing on storage ([#274](https://github.com/amplitude/Amplitude-Kotlin/issues/274)) ([aab8e29](https://github.com/amplitude/Amplitude-Kotlin/commit/aab8e2982392d1b0f9641f4bca057d7826758b2c))
* send URL without fragment/query ([#271](https://github.com/amplitude/Amplitude-Kotlin/issues/271)) ([1ad5ddb](https://github.com/amplitude/Amplitude-Kotlin/commit/1ad5ddb9a1a8bbe1d687f89e0859d7307958e51f))


### Features

* add network tracking plugin ([#265](https://github.com/amplitude/Amplitude-Kotlin/issues/265)) ([d19a941](https://github.com/amplitude/Amplitude-Kotlin/commit/d19a941a1977e87c7e0ffdcedb1b286134b124cb))

## [1.20.7](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.20.6...v1.20.7) (2025-05-02)


### Bug Fixes

* default location listening to false ([#270](https://github.com/amplitude/Amplitude-Kotlin/issues/270)) ([3946f35](https://github.com/amplitude/Amplitude-Kotlin/commit/3946f3578089288d86acc9bb2fece8cf0a5aedf8))
* make DefaultEventUtils internal ([#264](https://github.com/amplitude/Amplitude-Kotlin/issues/264)) ([6f17917](https://github.com/amplitude/Amplitude-Kotlin/commit/6f179174c1dc9cc354b968b77887427ce2ccdf61))
* mark event utils as deprecated ([#268](https://github.com/amplitude/Amplitude-Kotlin/issues/268)) ([ff00a73](https://github.com/amplitude/Amplitude-Kotlin/commit/ff00a737aebc6a5cbb53a9d00e23b7fcb2a9e23c))
* update network listener to cover cases where network is blocked ([#267](https://github.com/amplitude/Amplitude-Kotlin/issues/267)) ([f347bb1](https://github.com/amplitude/Amplitude-Kotlin/commit/f347bb12dcbaa4fae9c710db4ec9e8d80a111240))

## [1.20.6](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.20.5...v1.20.6) (2025-04-09)


### Bug Fixes

* 4xx-handling-when-on-proxy ([#262](https://github.com/amplitude/Amplitude-Kotlin/issues/262)) ([7561305](https://github.com/amplitude/Amplitude-Kotlin/commit/7561305276893d88866ec4651e9412a6a6ae8295))

## [1.20.5](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.20.4...v1.20.5) (2025-04-02)


### Bug Fixes

* compose autocapture properties update ([#261](https://github.com/amplitude/Amplitude-Kotlin/issues/261)) ([a49b6a7](https://github.com/amplitude/Amplitude-Kotlin/commit/a49b6a7bab6f899ccb8652f53d8b5b7dfbffb346))
* reformatting and warning fixes for upload ordering ticket  ([#257](https://github.com/amplitude/Amplitude-Kotlin/issues/257)) ([61eb7ee](https://github.com/amplitude/Amplitude-Kotlin/commit/61eb7eecc1ce58d6f95cb4862a6a359ff106cece))
* retry upload order ([#259](https://github.com/amplitude/Amplitude-Kotlin/issues/259)) ([59a339d](https://github.com/amplitude/Amplitude-Kotlin/commit/59a339deac99b29b1257293162fb2f5f0d14f01b))

## [1.20.4](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.20.3...v1.20.4) (2025-03-17)


### Bug Fixes

* Mediator flaky test  ([#256](https://github.com/amplitude/Amplitude-Kotlin/issues/256)) ([7dacdfa](https://github.com/amplitude/Amplitude-Kotlin/commit/7dacdfa20c8fc5a9ad751c09fdee977bf1db3af0))
* remove synchronized on mediator ([#254](https://github.com/amplitude/Amplitude-Kotlin/issues/254)) ([d71d59b](https://github.com/amplitude/Amplitude-Kotlin/commit/d71d59be1c0f2bdc518d6839937b963b4b88ff76))

## [1.20.3](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.20.2...v1.20.3) (2025-03-10)


### Bug Fixes

* add consumer-rules for useAppSetIdForDeviceId = true ([#249](https://github.com/amplitude/Amplitude-Kotlin/issues/249)) ([83cc361](https://github.com/amplitude/Amplitude-Kotlin/commit/83cc361deaef445bb75af49e64ebd9cffca80535))
* ignore illegal state exception on addShutdownHook w/c happens when shutdown sequence has begun ([#248](https://github.com/amplitude/Amplitude-Kotlin/issues/248)) ([864cbc4](https://github.com/amplitude/Amplitude-Kotlin/commit/864cbc4f6fd098547383df865ea9dc7c6f7c3771))
* initialize custom http client with configuration ([#240](https://github.com/amplitude/Amplitude-Kotlin/issues/240)) ([1173728](https://github.com/amplitude/Amplitude-Kotlin/commit/1173728efb84722d6f74825b628cfe83e7376df9))
* respect play services flags inside context provider ([#252](https://github.com/amplitude/Amplitude-Kotlin/issues/252)) ([e7f9112](https://github.com/amplitude/Amplitude-Kotlin/commit/e7f9112e394398d4e8f56b1b45e23a01ba022fa2))

## [1.20.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.20.1...v1.20.2) (2025-02-28)


### Bug Fixes

* adding ability to track revenue currency ([#244](https://github.com/amplitude/Amplitude-Kotlin/issues/244)) ([f33552b](https://github.com/amplitude/Amplitude-Kotlin/commit/f33552b6b7b8b4bb4cfc2011592d61284e83d79b))

## [1.20.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.20.0...v1.20.1) (2025-02-25)


### Bug Fixes

* autocapture observer null pointer exception ([#243](https://github.com/amplitude/Amplitude-Kotlin/issues/243)) ([d4f80ae](https://github.com/amplitude/Amplitude-Kotlin/commit/d4f80ae72dfe4717a0b386696638d158d73664da))

# [1.20.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.19.4...v1.20.0) (2025-02-11)


### Features

* cleaning up http client interface and adding the ability to use custom http clients ([#237](https://github.com/amplitude/Amplitude-Kotlin/issues/237)) ([bddd9d7](https://github.com/amplitude/Amplitude-Kotlin/commit/bddd9d744f2e17d512ec4fd693fb4dcd28d0238e))

## [1.19.4](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.19.3...v1.19.4) (2025-01-23)


### Bug Fixes

* autocapture not main thread crash ([#235](https://github.com/amplitude/Amplitude-Kotlin/issues/235)) ([3582b73](https://github.com/amplitude/Amplitude-Kotlin/commit/3582b7368e5a642442d28dd3a8bf9e0b9ed82328))

## [1.19.3](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.19.2...v1.19.3) (2025-01-14)


### Bug Fixes

* autocapture missing events fix ([#232](https://github.com/amplitude/Amplitude-Kotlin/issues/232)) ([7476456](https://github.com/amplitude/Amplitude-Kotlin/commit/74764561d2c87c5eb6f59198822f93ca249178ad))

## [1.19.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.19.1...v1.19.2) (2024-11-14)


### Bug Fixes

* type mismatch on `queryDb` ([#228](https://github.com/amplitude/Amplitude-Kotlin/issues/228)) ([1a708c6](https://github.com/amplitude/Amplitude-Kotlin/commit/1a708c60d5889ae6787c3a0842ae981c7e5becb0))

## [1.19.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.19.0...v1.19.1) (2024-10-04)


### Bug Fixes

* don't delete old profile file for backwards compatibility ([#227](https://github.com/amplitude/Amplitude-Kotlin/issues/227)) ([4e6957c](https://github.com/amplitude/Amplitude-Kotlin/commit/4e6957c2c7a1449469fb8b4a833a8f6f76ff7200))

# [1.19.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.18.0...v1.19.0) (2024-10-02)


### Features

* migrating storage to version v3 ([#226](https://github.com/amplitude/Amplitude-Kotlin/issues/226)) ([3be476b](https://github.com/amplitude/Amplitude-Kotlin/commit/3be476b6d9dcaa328334d8f9b82a9ca0727c232e))

# [1.18.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.17.4...v1.18.0) (2024-08-22)


### Features

* Capture top-level Fragments with Autocapture ([#216](https://github.com/amplitude/Amplitude-Kotlin/issues/216)) ([e16b97a](https://github.com/amplitude/Amplitude-Kotlin/commit/e16b97ad25c1d865947856c20e2e475a5733f161))

## [1.17.4](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.17.3...v1.17.4) (2024-08-21)


### Bug Fixes

* Catch network connectivity exceptions ([#221](https://github.com/amplitude/Amplitude-Kotlin/issues/221)) ([1ed787b](https://github.com/amplitude/Amplitude-Kotlin/commit/1ed787bcdd02f701b694afb8e4e688a0e2dd9af4))

## [1.17.3](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.17.2...v1.17.3) (2024-08-13)


### Bug Fixes

* only track button text for the elementInteraction option of autocapture ([#219](https://github.com/amplitude/Amplitude-Kotlin/issues/219)) ([d566c74](https://github.com/amplitude/Amplitude-Kotlin/commit/d566c7421c648e52efecd976204bb30d8e2e61f3))

## [1.17.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.17.1...v1.17.2) (2024-08-13)


### Bug Fixes

* Add target text capture for Android Views in Autocapture ([#218](https://github.com/amplitude/Amplitude-Kotlin/issues/218)) ([7617f88](https://github.com/amplitude/Amplitude-Kotlin/commit/7617f884a93cfd74847f209714a7fcdc7ce16697))

## [1.17.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.17.0...v1.17.1) (2024-08-09)


### Bug Fixes

* Fix OOM error when a bad api key is passed ([#210](https://github.com/amplitude/Amplitude-Kotlin/issues/210)) ([8f1c047](https://github.com/amplitude/Amplitude-Kotlin/commit/8f1c047cfe698d996e73e86a226127a14e503c9a))

# [1.17.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.8...v1.17.0) (2024-08-08)


### Bug Fixes

* extract ComposeUtils to share with plugins ([#202](https://github.com/amplitude/Amplitude-Kotlin/issues/202)) ([c239bba](https://github.com/amplitude/Amplitude-Kotlin/commit/c239bbab9e67c7cd5eea942c379de852fbc2a290))
* fix release pipeline ([#211](https://github.com/amplitude/Amplitude-Kotlin/issues/211)) ([816599f](https://github.com/amplitude/Amplitude-Kotlin/commit/816599faee3cf7f098fe95c2236da8d799723e8f))
* revert autocapture scroll and swipe ([#206](https://github.com/amplitude/Amplitude-Kotlin/issues/206)) ([4b834bd](https://github.com/amplitude/Amplitude-Kotlin/commit/4b834bd366b6cef05d362973cc03075a48e7b1aa))


### Features

* add autocapture for element clicks ([#199](https://github.com/amplitude/Amplitude-Kotlin/issues/199)) ([7f0a500](https://github.com/amplitude/Amplitude-Kotlin/commit/7f0a50043f824b625b0273f4c69b4ab792353a56))
* add autocapture in configs ([#207](https://github.com/amplitude/Amplitude-Kotlin/issues/207)) ([dcb9393](https://github.com/amplitude/Amplitude-Kotlin/commit/dcb93933d2da464dd7eec1bc530b251c3ffd7e34))
* add screen name to elementInteractions properties for autocapture ([#209](https://github.com/amplitude/Amplitude-Kotlin/issues/209)) ([8044122](https://github.com/amplitude/Amplitude-Kotlin/commit/8044122fafbb055a116d4c2fb42e0e4383229c26))
* add scroll and swipe autocapture ([#201](https://github.com/amplitude/Amplitude-Kotlin/issues/201)) ([1a5a392](https://github.com/amplitude/Amplitude-Kotlin/commit/1a5a39256a16307fba0316dba2c6a4c28b4b97fd))

## [1.16.8](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.7...v1.16.8) (2024-05-30)


### Bug Fixes

* fix race condition where an event can be processed before a session gets extended ([#198](https://github.com/amplitude/Amplitude-Kotlin/issues/198)) ([c3627d0](https://github.com/amplitude/Amplitude-Kotlin/commit/c3627d0391a31db2d9383f66b56ffc62c795c958))

## [1.16.7](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.6...v1.16.7) (2024-03-28)


### Bug Fixes

* revert recent session changes from 1.16.3 to 1.16.6 ([#191](https://github.com/amplitude/Amplitude-Kotlin/issues/191)) ([8d7271f](https://github.com/amplitude/Amplitude-Kotlin/commit/8d7271feb93a007d4a416e01f137d0dd2761afcc))

## [1.16.6](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.5...v1.16.6) (2024-03-27)


### Bug Fixes

* fix storage timing issue from last commit, set sessionId before plugin.setup ([#190](https://github.com/amplitude/Amplitude-Kotlin/issues/190)) ([84b4b9d](https://github.com/amplitude/Amplitude-Kotlin/commit/84b4b9d5686612971be9b057ac901af20bc54fe9))

## [1.16.5](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.4...v1.16.5) (2024-03-27)


### Bug Fixes

* session id not set before plugin setup ([#189](https://github.com/amplitude/Amplitude-Kotlin/issues/189)) ([42e4fef](https://github.com/amplitude/Amplitude-Kotlin/commit/42e4fef44d8d3b9b49f2e895c37d63f6b9805f03))

## [1.16.4](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.3...v1.16.4) (2024-03-26)


### Bug Fixes

* only take unique error logs and control limit ([#188](https://github.com/amplitude/Amplitude-Kotlin/issues/188)) ([cca7d79](https://github.com/amplitude/Amplitude-Kotlin/commit/cca7d797ddb8a91cde38d507ad23675015632287))

## [1.16.3](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.2...v1.16.3) (2024-03-25)


### Bug Fixes

* start tracking sessions at init for session replay ([#186](https://github.com/amplitude/Amplitude-Kotlin/issues/186)) ([7b69897](https://github.com/amplitude/Amplitude-Kotlin/commit/7b69897b6c670f9bf5a9a2112459620245b99b5e))

## [1.16.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.1...v1.16.2) (2024-03-18)


### Bug Fixes

* Only parse response body when a body is expected ([#185](https://github.com/amplitude/Amplitude-Kotlin/issues/185)) ([cfd3bb8](https://github.com/amplitude/Amplitude-Kotlin/commit/cfd3bb8eeaa06aac3adf14570ad3ec322e63b18d))

## [1.16.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.16.0...v1.16.1) (2024-03-04)


### Bug Fixes

* catch potential internal AssertionError from Properties.save() in Android 8 ([#183](https://github.com/amplitude/Amplitude-Kotlin/issues/183)) ([78e93a9](https://github.com/amplitude/Amplitude-Kotlin/commit/78e93a994886559ac03203759685dfd4c0e9cf35))

# [1.16.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.15.0...v1.16.0) (2024-03-01)


### Features

* switch storage for better thread safety ([#181](https://github.com/amplitude/Amplitude-Kotlin/issues/181)) ([918d2d5](https://github.com/amplitude/Amplitude-Kotlin/commit/918d2d5de1a76f344a328c9f09abf86c4f6671b7))

# [1.15.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.14.0...v1.15.0) (2024-03-01)


### Features

* allow setting sessionId in initial configuration ([#182](https://github.com/amplitude/Amplitude-Kotlin/issues/182)) ([81c5866](https://github.com/amplitude/Amplitude-Kotlin/commit/81c5866e57427f81c8deb327f336440c2b0ff157))

# [1.14.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.13.0...v1.14.0) (2024-01-30)


### Bug Fixes

* race condition when setting device id in android ([#177](https://github.com/amplitude/Amplitude-Kotlin/issues/177)) ([671edb9](https://github.com/amplitude/Amplitude-Kotlin/commit/671edb9f7647667c2ef6ca0fd7dde8159f689eb6))


### Features

* added deviceId to Configuration options ([#178](https://github.com/amplitude/Amplitude-Kotlin/issues/178)) ([45b6181](https://github.com/amplitude/Amplitude-Kotlin/commit/45b61813c46e1495b152a52be2d97b170837b7f8))

# [1.13.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.12.2...v1.13.0) (2024-01-24)


### Features

* offline support ([#171](https://github.com/amplitude/Amplitude-Kotlin/issues/171)) ([669eead](https://github.com/amplitude/Amplitude-Kotlin/commit/669eead95c7cd745f63481663fad5ea473feb99d))

## [1.12.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.12.1...v1.12.2) (2023-11-13)


### Bug Fixes

* correctly format client_upload_time in HTTP request when minIdLength is set ([#160](https://github.com/amplitude/Amplitude-Kotlin/issues/160)) ([f5203a5](https://github.com/amplitude/Amplitude-Kotlin/commit/f5203a53f84bbea9a6443ed7602b8cc0ab653439))

## [1.12.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.12.0...v1.12.1) (2023-11-02)


### Bug Fixes

* updated README ([d74562d](https://github.com/amplitude/Amplitude-Kotlin/commit/d74562dec43451cde3d88c08c5fcf3dc81483d80))

# [1.12.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.11.2...v1.12.0) (2023-10-27)


### Features

* add client upload time ([#153](https://github.com/amplitude/Amplitude-Kotlin/issues/153)) ([9863b53](https://github.com/amplitude/Amplitude-Kotlin/commit/9863b532f0e2db05b937da35557aa04fa0594cfe))

## [1.11.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.11.1...v1.11.2) (2023-10-16)


### Bug Fixes

* skip deep link event when url is missing ([#150](https://github.com/amplitude/Amplitude-Kotlin/issues/150)) ([b8a4a87](https://github.com/amplitude/Amplitude-Kotlin/commit/b8a4a875cede9d8c78e61f36d314e90c541042f6))

## [1.11.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.11.0...v1.11.1) (2023-09-13)


### Bug Fixes

* do not throw NotImplemented error for existing "legacy database"file if the file is not a sqlite file ([#147](https://github.com/amplitude/Amplitude-Kotlin/issues/147)) ([3e0b851](https://github.com/amplitude/Amplitude-Kotlin/commit/3e0b85132a3d0c4c78d64c8980a166c978bd558a))

# [1.11.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.10.4...v1.11.0) (2023-08-23)


### Features

* migrate 'api key' storage data to 'instance name' storage ([#143](https://github.com/amplitude/Amplitude-Kotlin/issues/143)) ([67af8e3](https://github.com/amplitude/Amplitude-Kotlin/commit/67af8e3f536f23cac78bfb7906718717aebd901f))

## [1.10.4](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.10.3...v1.10.4) (2023-08-09)


### Bug Fixes

* revert "fix: wait until build (including possible legacy data migration) is finished ([#138](https://github.com/amplitude/Amplitude-Kotlin/issues/138))" ([#140](https://github.com/amplitude/Amplitude-Kotlin/issues/140)) ([c1acc71](https://github.com/amplitude/Amplitude-Kotlin/commit/c1acc7105b9b3950e9d79ff588662595c79fa104))
* wait until build (including possible legacy data migration) is finished ([#142](https://github.com/amplitude/Amplitude-Kotlin/issues/142)) ([78110e4](https://github.com/amplitude/Amplitude-Kotlin/commit/78110e449edc1bab107543c0defd3b95b11d7587)), closes [#138](https://github.com/amplitude/Amplitude-Kotlin/issues/138)

## [1.10.3](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.10.2...v1.10.3) (2023-08-09)


### Bug Fixes

* wait until build (including possible legacy data migration) is finished ([#138](https://github.com/amplitude/Amplitude-Kotlin/issues/138)) ([c3075a9](https://github.com/amplitude/Amplitude-Kotlin/commit/c3075a98d22d445588630d14ad50fdf8707bd724))

## [1.10.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.10.1...v1.10.2) (2023-06-23)


### Bug Fixes

* enable legacy data migration by default ([#130](https://github.com/amplitude/Amplitude-Kotlin/issues/130)) ([24430ec](https://github.com/amplitude/Amplitude-Kotlin/commit/24430ecf1b08d5179aeb8c9795da3e1b4dc4a679))

## [1.10.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.10.0...v1.10.1) (2023-06-22)


### Bug Fixes

* make default tracking options java compatible ([#129](https://github.com/amplitude/Amplitude-Kotlin/issues/129)) ([290bf31](https://github.com/amplitude/Amplitude-Kotlin/commit/290bf316685c406e3fbf216707e737ab1ef1d283))

# [1.10.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.9.2...v1.10.0) (2023-06-21)


### Features

* add default events ([#126](https://github.com/amplitude/Amplitude-Kotlin/issues/126)) ([12a743b](https://github.com/amplitude/Amplitude-Kotlin/commit/12a743bb8b03d87c474ab20719601d704c2e8bff))

## [1.9.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.9.1...v1.9.2) (2023-06-13)


### Bug Fixes

* do not fetch advertising Id if adid tracking is disabled ([#125](https://github.com/amplitude/Amplitude-Kotlin/issues/125)) ([7e570a2](https://github.com/amplitude/Amplitude-Kotlin/commit/7e570a291288c62a89d1ef6a1839f01465f40801))

## [1.9.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.9.0...v1.9.1) (2023-06-03)


### Bug Fixes

* thread-safe Identify object ([#124](https://github.com/amplitude/Amplitude-Kotlin/issues/124)) ([60120f2](https://github.com/amplitude/Amplitude-Kotlin/commit/60120f281a02c9abf7c90176135aff9f95c0e801))

# [1.9.0](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.8.2...v1.9.0) (2023-05-26)


### Bug Fixes

* event options should override event properties, refresh last event time on exit foreground ([#122](https://github.com/amplitude/Amplitude-Kotlin/issues/122)) ([652ea42](https://github.com/amplitude/Amplitude-Kotlin/commit/652ea42bac124e079865c3ffffcf02015f9b6765))


### Features

* add remnant events migration plugin ([#40](https://github.com/amplitude/Amplitude-Kotlin/issues/40)) ([6225f5d](https://github.com/amplitude/Amplitude-Kotlin/commit/6225f5d855ed439f154b4c297461b763337f316b))

## [1.8.2](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.8.1...v1.8.2) (2023-04-24)


### Bug Fixes

* update identify transfer to merge identify only ([#118](https://github.com/amplitude/Amplitude-Kotlin/issues/118)) ([fbf11ef](https://github.com/amplitude/Amplitude-Kotlin/commit/fbf11efd0a7a40176e89232074c75da916c4d26c))

## [1.8.1](https://github.com/amplitude/Amplitude-Kotlin/compare/v1.8.0...v1.8.1) (2023-04-13)


### Bug Fixes

* fix log session event when optOut is true ([#117](https://github.com/amplitude/Amplitude-Kotlin/issues/117)) ([155b3f5](https://github.com/amplitude/Amplitude-Kotlin/commit/155b3f56ad12a693f1213833f73d15f35213636c))

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
