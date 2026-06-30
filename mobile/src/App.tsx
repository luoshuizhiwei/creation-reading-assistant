import { useEffect, useMemo, useState } from "react";
import { loadMobileSnapshot, saveMobileSnapshot, type MobileSnapshot } from "./services/mobile-storage";
import { createSyncClient, parsePairingPayload, type PairingInput } from "./services/sync-client";

type MainTab = "inspirations" | "library" | "sync";

const emptySnapshot: MobileSnapshot = {
  inspirations: [],
  books: [],
  progress: [],
  sessions: [],
  updatedAt: new Date().toISOString()
};

export function App() {
  const [tab, setTab] = useState<MainTab>("inspirations");
  const [snapshot, setSnapshot] = useState<MobileSnapshot>(emptySnapshot);
  const [pairingText, setPairingText] = useState("");
  const [paired, setPaired] = useState<PairingInput>();
  const [message, setMessage] = useState("离线模式可用：你可以先记录灵感，回到同一 Wi‑Fi 后再同步。");

  useEffect(() => {
    void loadMobileSnapshot().then(setSnapshot);
  }, []);

  const client = useMemo(() => (paired ? createSyncClient(paired) : undefined), [paired]);

  const addInspiration = async () => {
    const next: MobileSnapshot = {
      ...snapshot,
      inspirations: [
        {
          id: `mobile-insp-${Date.now()}`,
          title: "新的手机灵感",
          body: "在手机端快速记下的灵感，可稍后同步到电脑端继续润色。",
          type: "note",
          status: "inbox",
          tags: ["手机端"],
          platformTags: [],
          variants: [],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          revision: 1,
          deviceId: "mobile-local"
        },
        ...snapshot.inspirations
      ],
      updatedAt: new Date().toISOString()
    };
    setSnapshot(next);
    await saveMobileSnapshot(next);
    setMessage("已在手机本地保存灵感。");
  };

  const connect = async () => {
    try {
      const input = parsePairingPayload(pairingText);
      const nextClient = createSyncClient(input);
      const result = await nextClient.pair();
      setPaired(input);
      setMessage(`已连接电脑端：${result.device.name}`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setMessage(`连接失败：${detail}。请确认电脑端“手机同步”服务仍在运行、手机和电脑在同一 Wi‑Fi；如果电脑端显示多个备用配对 URL，请换一个地址再试。`);
    }
  };

  const syncNow = async () => {
    if (!client) {
      setMessage("请先扫码连接电脑。");
      return;
    }
    try {
      const pull = await client.pull();
      const next: MobileSnapshot = {
        inspirations: pull.inspirations.map((item) => item.payload),
        books: pull.books.map((item) => item.payload),
        progress: pull.progress.map((item) => item.payload),
        sessions: pull.sessions.map((item) => item.payload),
        updatedAt: new Date().toISOString()
      };
      await saveMobileSnapshot(next);
      setSnapshot(next);
      setMessage(`同步完成：${pull.inspirations.length} 条灵感，${pull.books.length} 本书。`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    }
  };

  return (
    <main className="app-shell">
      <header className="hero">
        <p className="eyebrow">Android companion</p>
        <h1>创作阅读助手</h1>
        <p>手机端保留两个主入口：灵感中心、本地书库。电脑端开启同步后，手机可在同一 Wi‑Fi 下同步数据和书籍文件。</p>
      </header>

      <nav className="tabs">
        <button className={tab === "inspirations" ? "active" : ""} onClick={() => setTab("inspirations")}>
          灵感中心
        </button>
        <button className={tab === "library" ? "active" : ""} onClick={() => setTab("library")}>
          本地书库
        </button>
        <button className={tab === "sync" ? "active" : ""} onClick={() => setTab("sync")}>
          扫码连接电脑
        </button>
      </nav>

      <section className="notice">{message}</section>

      {tab === "inspirations" && (
        <section className="card">
          <div className="section-title">
            <h2>灵感中心</h2>
            <button onClick={() => void addInspiration()}>新建灵感</button>
          </div>
          {snapshot.inspirations.length === 0 ? (
            <p className="muted">还没有手机灵感。你可以先离线记录，稍后同步回电脑。</p>
          ) : (
            <div className="list">
              {snapshot.inspirations.map((item) => (
                <article key={item.id} className="list-item">
                  <strong>{item.title}</strong>
                  <p>{item.body || "暂无正文"}</p>
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      {tab === "library" && (
        <section className="card">
          <div className="section-title">
            <h2>本地书库</h2>
            <button disabled={!client} onClick={() => void syncNow()}>
              同步书库
            </button>
          </div>
          {snapshot.books.length === 0 ? (
            <p className="muted">本地书库为空。连接电脑后会同步 TXT、Markdown、EPUB 和阅读进度。</p>
          ) : (
            <div className="list">
              {snapshot.books.map((book) => (
                <article key={book.id} className="list-item">
                  <strong>{book.title}</strong>
                  <p>
                    {book.format.toUpperCase()} · {book.author || "作者未知"} · {book.importLabel || "本地导入"}
                  </p>
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      {tab === "sync" && (
        <section className="card">
          <h2>扫码连接电脑</h2>
          <p className="muted">在电脑端“设置 / 手机同步”生成配对码，然后把二维码载荷或配对 URL 粘贴到这里。后续版本会接入系统相机扫码。</p>
          <textarea value={pairingText} onChange={(event) => setPairingText(event.target.value)} placeholder="粘贴电脑端二维码载荷或配对 URL" />
          <div className="actions">
            <button onClick={() => void connect()}>连接电脑</button>
            <button disabled={!client} onClick={() => void syncNow()}>
              立即同步
            </button>
          </div>
        </section>
      )}
    </main>
  );
}
