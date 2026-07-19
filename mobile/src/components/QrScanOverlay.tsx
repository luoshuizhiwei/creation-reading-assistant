import { useEffect, useRef, useState } from "react";
import jsQR from "jsqr";
import { formatQrScanError } from "../utils/mobile-helpers";
import { addMobileLog } from "../services/mobile-logger";

export function QrScanOverlay({
  onResult,
  onClose,
  onFallbackPaste,
  onMessage
}: {
  onResult: (text: string) => void;
  onClose: () => void;
  onFallbackPaste: () => void;
  onMessage: (message: string) => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const onResultRef = useRef(onResult);
  const onMessageRef = useRef(onMessage);
  const [error, setError] = useState("");

  onResultRef.current = onResult;
  onMessageRef.current = onMessage;

  useEffect(() => {
    let stream: MediaStream | undefined;
    let stopped = false;
    let timer: number | undefined;
    let timeout: number | undefined;

    const stop = () => {
      stopped = true;
      if (timer) window.clearTimeout(timer);
      if (timeout) window.clearTimeout(timeout);
      stream?.getTracks().forEach((track) => track.stop());
    };

    const scanFrame = () => {
      if (stopped) return;
      const video = videoRef.current;
      const canvas = canvasRef.current;
      const context = canvas?.getContext("2d", { willReadFrequently: true });
      if (video && canvas && context && video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && video.videoWidth && video.videoHeight) {
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        context.drawImage(video, 0, 0, canvas.width, canvas.height);
        const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
        const code = jsQR(imageData.data, imageData.width, imageData.height);
        if (code?.data) {
          stop();
          onResultRef.current(code.data);
          return;
        }
      }
      timer = window.setTimeout(scanFrame, 260);
    };

    const start = async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error("mediaDevices unavailable");
        stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: { ideal: "environment" } }
        });
        const video = videoRef.current;
        if (!video || stopped) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        video.srcObject = stream;
        if (video.readyState < HTMLMediaElement.HAVE_METADATA) {
          await new Promise<void>((resolve, reject) => {
            const ready = () => { cleanup(); resolve(); };
            const failed = () => { cleanup(); reject(new Error("camera metadata unavailable")); };
            const cleanup = () => {
              video.removeEventListener("loadedmetadata", ready);
              video.removeEventListener("error", failed);
            };
            video.addEventListener("loadedmetadata", ready, { once: true });
            video.addEventListener("error", failed, { once: true });
          });
        }
        if (stopped) return;
        await video.play();
        onMessageRef.current("摄像头已打开，请把电脑端二维码放进取景框。");
        timeout = window.setTimeout(() => {
          setError("扫码超时。请靠近二维码、提高电脑屏幕亮度，或改用粘贴配对 URL。");
        }, 45_000);
        scanFrame();
      } catch (err) {
        if (stopped) return;
        const message = formatQrScanError(err);
        setError(message);
        addMobileLog("error", "二维码扫码", message, { code: "QR_CAMERA_FAILED" });
        onMessageRef.current(message);
      }
    };

    void start();
    return stop;
  }, []);

  return (
    <section className="qr-scan-overlay" role="dialog" aria-modal="true" aria-label="扫码连接电脑">
      <div className="qr-scan-panel">
        <header className="qr-scan-header">
          <div>
            <strong>扫码连接电脑</strong>
            <p>把电脑端二维码放进取景框，识别后会自动连接。</p>
          </div>
          <button className="ghost-button" onClick={onClose}>
            关闭
          </button>
        </header>
        <div className="qr-video-frame">
          <video ref={videoRef} className="qr-video" muted playsInline autoPlay />
          <canvas ref={canvasRef} hidden />
          <div className="qr-corners" aria-hidden="true" />
        </div>
        {error && <p className="qr-scan-error">{error}</p>}
        <div className="qr-scan-actions">
          <button onClick={onFallbackPaste}>粘贴配对 URL</button>
          <button className="ghost-button" onClick={onClose}>
            稍后再扫
          </button>
        </div>
      </div>
    </section>
  );
}
