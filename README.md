# MoeMusic Bandcamp Source

Standalone MoeMusic source for public Bandcamp track pages.

> [!NOTE]
> This project is fully vibe-coded, with limited manual review.

The source parses public `data-tralbum` metadata and resolves a fresh `mp3-128` URL at playback time. 
Private, subscriber-only, and pages without a public stream are unavailable; no login, purchase, or download flow is attempted.
Due to lack of api, no loudness normalization data can be provided. Expect lower volume for tracks from this source.

Search is intentionally omitted because Bandcamp's HTML search is protected by a JavaScript challenge and there is no supported public search API.

Public track-page parsing was checked against [yt-dlp](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/bandcamp.py), [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/main/java/org/schabi/newpipe/extractor/services/bandcamp/extractors/BandcampStreamExtractor.java), and Lavaplayer source.

Build with ./gradlew build. Install the generated full jar from build/libs into config/moemusic/plugins/.

---

# MoeMusic Bandcamp 音源

面向公开 Bandcamp 单曲页面的 MoeMusic 独立音源插件。

> [!NOTE]
> 本项目完全由 AI 生成，仅进行了基本的人工审阅。

插件解析公开的 `data-tralbum` 元数据，并在播放时解析新的 `mp3-128` 地址。
私密、仅订阅以及没有公开音频流的页面不可用；插件不会尝试登录、购买或下载。
由于 API 限制，无法提供响度均衡数据，因此该平台的曲目播放时的音量可能略低。

Bandcamp 的 HTML 搜索受 JavaScript 挑战保护，且没有受支持的公开搜索 API，因此本插件不提供搜索。

公开单曲页面的解析流程参考了 [yt-dlp](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/bandcamp.py)、[NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/main/java/org/schabi/newpipe/extractor/services/bandcamp/extractors/BandcampStreamExtractor.java) 和 Lavaplayer 实现。

使用 ./gradlew build 构建，并将 build/libs 中生成的 full jar 放入 config/moemusic/plugins/。

