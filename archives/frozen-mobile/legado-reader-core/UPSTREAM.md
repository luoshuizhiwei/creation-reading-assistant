# Upstream provenance

The native reader module adapts selected local-reading algorithms from:

- Repository: https://github.com/Luoyacheng/legado-E
- Commit: `21855a7bf901becfd1caba5cf30a3c84fd1533e1`
- License: GNU General Public License v3.0

Source paths currently used as adaptation references:

- `app/src/main/java/io/legado/app/ui/book/read/page/provider/TextMeasure.kt`
- `app/src/main/java/io/legado/app/ui/book/read/page/provider/ZhLayout.kt`
- `app/src/main/java/io/legado/app/ui/book/read/page/entities/TextPos.kt`
- `app/src/main/java/io/legado/app/ui/book/read/page/entities/PageDirection.kt`

Source paths vendored for EPUB parsing:

- `modules/book/src/main/java/me/ag2s/epublib/**`
- `modules/book/src/main/java/me/ag2s/base/PfdHelper.java`
- `modules/book/src/main/java/me/ag2s/base/ThrowableUtils.java`
- `modules/book/src/main/resources/dtd/**`

The EPUB parser package is kept in its upstream `me.ag2s.epublib` namespace so
future source comparisons remain mechanical and auditable.

The module intentionally does not include Legado's book-source engine, crawler rules, networking, subscriptions, database, media playback, UMD reader, or application-global state.
