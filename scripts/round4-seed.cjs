/**
 * 第四轮视觉验收种子：书库 JSON（3 本不同格式 + 进度 + 近 30 天会话）
 * 与 workspace.sqlite 的 inbox_items。必须以 ELECTRON_RUN_AS_NODE=1 运行
 * （better-sqlite3 的原生模块按 Electron ABI 编译）。
 *
 * 用法：electron.exe round4-seed.cjs <profileDir>
 */
const path = require("node:path");
const fs = require("node:fs");
const Database = require("better-sqlite3");

const profileDir = process.argv[2];
if (!profileDir) {
  process.stderr.write("usage: round4-seed.cjs <profileDir>\n");
  process.exitCode = 1;
  return;
}

const dataRoot = path.join(profileDir, "NovelWorkbench");
const libraryRoot = path.join(dataRoot, "AppLibrary");
const workspaceDb = path.join(dataRoot, "CreationWorkspace", "workspace.sqlite");

function iso(msAgo) {
  return new Date(Date.now() - msAgo).toISOString();
}

function dateKey(msAgo) {
  const d = new Date(Date.now() - msAgo);
  const m = `${d.getMonth() + 1}`.padStart(2, "0");
  const day = `${d.getDate()}`.padStart(2, "0");
  return `${d.getFullYear()}-${m}-${day}`;
}

const PARA = "雨下了一整夜，屋檐的水线在灯下织成一道细帘。他把信纸翻过来，背面只有一行被水洇开的小字：不要打开阁楼。多年前的那个夏天也是这样开始的，潮湿、闷热、蝉声在远处断续。";

function txtParagraphs(count) {
  const lines = [];
  for (let i = 0; i < count; i += 1) {
    lines.push(`${PARA.slice(0, 60 + ((i * 17) % 60))}（${i + 1}）`);
  }
  return lines.join("\n\n");
}

function txtContent() {
  const chapters = ["雨夜来信", "阁楼的锁", "旧运河", "巡夜人", "禁夜令", "当铺的怀表", "十年前的笔迹"];
  return chapters
    .map((title, index) => `第${["一", "二", "三", "四", "五", "六", "七"][index]}章 ${title}\n\n${txtParagraphs(14)}`)
    .join("\n\n");
}

function mdContent() {
  return [
    "# 城市观察笔记",
    "",
    "记录禁夜令之下城市的日常切片，供创作取材。",
    "",
    "## 第一节 街景速写",
    "",
    txtParagraphs(6),
    "",
    "## 第二节 摘录与批注",
    "",
    "> 「东西当出去的那天就该忘了它。人也一样。」——当铺老板",
    "",
    txtParagraphs(4),
    "",
    "## 第三节 待核事实",
    "",
    "- 巡夜队的换班时间：每晚十点与凌晨两点",
    "- 旧运河入口数量：已知三处，传说第四处",
    "- 禁夜令颁布年份：待查证",
    "",
    "## 第四节 灵感碎片",
    "",
    txtParagraphs(5)
  ].join("\n");
}

function xhtmlChapter(title, body) {
  return `<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>${title}</title></head>
<body><h1>${title}</h1>${body.split("\n\n").map((p) => `<p>${p}</p>`).join("")}</body>
</html>`;
}

async function writeBookFiles(books) {
  const JSZip = require("jszip");
  fs.writeFileSync(books[0].filePath, txtContent(), "utf8");
  fs.writeFileSync(books[2].filePath, mdContent(), "utf8");
  fs.mkdirSync(path.dirname(books[1].filePath), { recursive: true });

  const zip = new JSZip();
  zip.file("mimetype", "application/epub+zip", { compression: "STORE" });
  zip.file(
    "META-INF/container.xml",
    `<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>`
  );
  const chapterTitles = ["雨夜来信", "阁楼的锁", "旧运河"];
  const chapters = chapterTitles.map((title) => xhtmlChapter(title, txtParagraphs(10)));
  zip.file(
    "OEBPS/content.opf",
    `<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">urn:uuid:round4-test-epub</dc:identifier>
    <dc:title>测试 EPUB 资料</dc:title>
    <dc:creator>某作者</dc:creator>
    <dc:language>zh-CN</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
    <item id="c3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="c1"/><itemref idref="c2"/><itemref idref="c3"/><itemref idref="nav"/></spine>
</package>`
  );
  zip.file(
    "OEBPS/nav.xhtml",
    `<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>目录</title></head>
<body><nav epub:type="toc"><h1>目录</h1><ol>
${chapterTitles.map((title, i) => `  <li><a href="chapter${i + 1}.xhtml">${title}</a></li>`).join("\n")}
</ol></nav></body>
</html>`
  );
  chapters.forEach((content, i) => zip.file(`OEBPS/chapter${i + 1}.xhtml`, content));
  const buf = await zip.generateAsync({ type: "nodebuffer", mimeType: "application/epub+zip" });
  fs.writeFileSync(books[1].filePath, buf);
}

async function seedLibraryJson() {
  fs.mkdirSync(libraryRoot, { recursive: true });
  const books = [
    {
      id: "book-txt-001",
      title: "测试 TXT 资料",
      filePath: path.join(libraryRoot, "book-txt-001.txt"),
      format: "txt",
      author: "佚名",
      importedAt: iso(40 * 86400000),
      updatedAt: iso(2 * 86400000),
      size: 1024 * 1024,
      originalPath: "D:/Materials/测试 TXT 资料.txt"
    },
    {
      id: "book-epub-002",
      title: "测试 EPUB 资料",
      filePath: path.join(libraryRoot, "files", "book-epub-002.epub"),
      format: "epub",
      author: "某作者",
      importLabel: "精校版",
      importedAt: iso(20 * 86400000),
      updatedAt: iso(1 * 86400000),
      size: 2048 * 1024,
      originalPath: "D:/Materials/测试 EPUB 资料.epub",
      epub: {
        toc: [
          { id: "toc-1", label: "雨夜来信", href: "chapter1.xhtml", level: 0 },
          { id: "toc-2", label: "阁楼的锁", href: "chapter2.xhtml", level: 0 },
          { id: "toc-3", label: "旧运河", href: "chapter3.xhtml", level: 0 }
        ]
      }
    },
    {
      id: "book-md-003",
      title: "测试 Markdown 笔记",
      filePath: path.join(libraryRoot, "book-md-003.md"),
      format: "md",
      importedAt: iso(9 * 86400000),
      updatedAt: iso(5 * 3600000),
      size: 64 * 1024,
      originalPath: "D:/Notes/测试 Markdown 笔记.md"
    }
  ];

  writeBookFiles(books);
  fs.writeFileSync(path.join(libraryRoot, "library.json"), JSON.stringify({ books }, null, 2), "utf8");

  const items = [
    {
      bookId: "book-txt-001",
      filePath: books[0].filePath,
      format: "txt",
      progressPercent: 0.423,
      lastReadAt: iso(26 * 3600000),
      totalReadingTimeMs: 4 * 3600000 + 21 * 60000,
      completionState: "reading",
      currentLocation: { progressPercent: 0.423, updatedAt: iso(26 * 3600000) },
      revision: 3
    },
    {
      bookId: "book-epub-002",
      filePath: books[1].filePath,
      format: "epub",
      progressPercent: 0.781,
      lastReadAt: iso(20 * 3600000),
      totalReadingTimeMs: 11 * 3600000 + 5 * 60000,
      completionState: "reading",
      currentLocation: { progressPercent: 0.781, updatedAt: iso(20 * 3600000) },
      revision: 7
    },
    {
      bookId: "book-md-003",
      filePath: books[2].filePath,
      format: "md",
      progressPercent: 0.06,
      lastReadAt: iso(5 * 3600000),
      totalReadingTimeMs: 12 * 60000,
      completionState: "reading",
      currentLocation: { progressPercent: 0.06, updatedAt: iso(5 * 3600000) },
      revision: 1
    }
  ];
  fs.writeFileSync(
    path.join(libraryRoot, "reading-progress.json"),
    JSON.stringify({ version: 2, updatedAt: iso(0), items }, null, 2),
    "utf8"
  );

  // 近 30 天阅读会话：工作日密度高于周末，节律图呈自然起伏
  const sessions = [];
  let seq = 0;
  for (let daysAgo = 29; daysAgo >= 0; daysAgo -= 1) {
    const weekday = new Date(Date.now() - daysAgo * 86400000).getDay();
    if (weekday === 0 || weekday === 6) continue;
    const count = 1 + ((daysAgo * 7) % 2);
    for (let i = 0; i < count; i += 1) {
      const startAgo = daysAgo * 86400000 + (20 - i * 3) * 3600000;
      const activeMin = 25 + ((daysAgo * 13 + i * 17) % 55);
      seq += 1;
      sessions.push({
        id: `sess-seed-${String(seq).padStart(3, "0")}`,
        bookId: seq % 3 === 0 ? "book-epub-002" : "book-txt-001",
        filePath: seq % 3 === 0 ? books[1].filePath : books[0].filePath,
        format: seq % 3 === 0 ? "epub" : "txt",
        startAt: iso(startAgo),
        endAt: iso(startAgo - activeMin * 60000),
        durationMs: (activeMin + 6) * 60000,
        activeDurationMs: activeMin * 60000,
        idleDurationMs: 6 * 60000,
        wallDurationMs: (activeMin + 6) * 60000,
        startLocation: { progressPercent: 0.3 },
        endLocation: { progressPercent: 0.35 },
        dateKey: dateKey(daysAgo * 86400000),
        dailyActiveMs: { [dateKey(daysAgo * 86400000)]: activeMin * 60000 },
        status: "ended",
        source: "manualOpen",
        endReason: "leave-reader"
      });
    }
  }
  fs.writeFileSync(
    path.join(libraryRoot, "reading-sessions.json"),
    JSON.stringify({ version: 1, updatedAt: iso(0), sessions }, null, 2),
    "utf8"
  );
}

function seedInboxItems() {
  const db = new Database(workspaceDb);
  const nowIso = iso(0);
  const items = [
    {
      id: "inbox-seed-001",
      title: "开篇钩子：雨夜来信",
      body: "主角在整理旧居时收到一封没有邮戳的信，落款是自己十年前的笔迹。信里只有一句：不要打开阁楼。这个钩子可以先放进第一章第一节。",
      type: "plot",
      status: "usable",
      tags: ["悬念", "开局"],
      platformTags: ["番茄"],
      variants: [],
      agoMs: 3 * 3600000
    },
    {
      id: "inbox-seed-002",
      title: "配角语录：当铺老板",
      body: "「东西当出去的那天就该忘了它。人也一样。」——当铺老板收下怀表时随口说的这句话，可以作为他隐藏身份的第一处伏笔。",
      type: "line",
      status: "inbox",
      tags: ["台词", "伏笔"],
      platformTags: [],
      variants: [],
      agoMs: 26 * 3600000
    },
    {
      id: "inbox-seed-003",
      title: "世界观补丁：城市禁夜令",
      body: "这座城市每晚十一点后断电封锁，违者由巡夜队带走。禁夜令的官方理由是防止瘟疫，真实原因与地下的旧运河网有关。",
      type: "world",
      status: "used",
      tags: ["世界观"],
      platformTags: ["起点"],
      variants: [
        { id: "variant-seed-a", kind: "expand", content: "（扩写候选）禁夜令颁布于三十年前的旱季，当时运河水位异常……", model: "test-model", createdAt: nowIso }
      ],
      agoMs: 52 * 3600000
    }
  ];
  const insert = db.prepare(
    "INSERT INTO inbox_items (id, legacy_id, title, body, type, status, tags_json, platform_tags_json, source_json, variants_json, revision, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
  );
  for (const item of items) {
    insert.run(
      item.id,
      item.title,
      item.body,
      item.type,
      item.status,
      JSON.stringify(item.tags),
      JSON.stringify(item.platformTags),
      "null",
      JSON.stringify(item.variants),
      1,
      iso(item.agoMs),
      iso(item.agoMs)
    );
  }
  db.close();
}

(async () => {
  await seedLibraryJson();
  seedInboxItems();
  process.stdout.write(JSON.stringify({ ok: true }) + "\n");
})().catch((error) => {
  process.stderr.write(`${error && error.stack ? error.stack : String(error)}\n`);
  process.exitCode = 1;
});
