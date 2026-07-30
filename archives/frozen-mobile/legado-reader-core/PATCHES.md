# Adaptation log

## Initial extraction

- Moved selected layout primitives into the isolated namespace `local.creationReadingAssistant.reader.legado`.
- Removed dependencies on Legado application globals, resources, database entities and `ReadBook`.
- Added explicit bridge-facing models for files, settings, locators and reader results.
- Kept text measurement and Chinese punctuation-aware line breaking behavior attributable to the pinned upstream commit.
- Added independent tests and Android library build gates so the module can compile without the complete Legado application.

## EPUB parser import

- Vendored the pinned upstream `me.ag2s.epublib` Java package and its required
  XHTML/NCX/OPF DTD resources without namespace changes.
- Excluded the adjacent UMD implementation and all Legado application-layer
  models, caches, databases and source/network features.
- EPUB-to-reader document conversion lives in the assistant-owned
  `local.creationReadingAssistant.reader.legado.epub` adapter package.

Future changes to adapted upstream behavior must be recorded here together with the source commit used for comparison.

## 2026-07-19 encoded EPUB navigation paths

- Kept the pinned upstream comparison point at
  `21855a7bf901becfd1caba5cf30a3c84fd1533e1`.
- Made `Resources.getByHref` compare both raw and percent-decoded href forms so
  NCX/navigation entries with encoded Chinese paths resolve to OPF resources.
- Made the assistant-owned TOC adapter skip a genuinely unresolved navigation
  target without dereferencing `TOCReference.completeHref`; child entries and
  readable spine resources remain available.
