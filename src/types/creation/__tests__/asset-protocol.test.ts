import { describe, expect, it } from "vitest";
import {
  buildCardResourceUrl,
  CREATION_ASSET_HOST,
  CREATION_ASSET_SCHEME,
  parseCardResourceUrl
} from "@/types/creation";

describe("creation-asset 资源 URL 契约", () => {
  it("构造出的 URL 能被解析回原始 ID", () => {
    const url = buildCardResourceUrl("card-1", "resource-2");
    expect(url).toBe("creation-asset://card/card-1/resource-2");
    expect(parseCardResourceUrl(url)).toEqual({ cardId: "card-1", resourceId: "resource-2" });
  });

  it("需要编码的字符会被编码，解码后含分隔符的 ID 一律拒绝", () => {
    // 空格可以安全往返。
    expect(parseCardResourceUrl(buildCardResourceUrl("card 1", "res 2"))).toEqual({
      cardId: "card 1",
      resourceId: "res 2"
    });
    // 斜杠会被编码成 %2F，解码后仍含 "/"，必须拒绝而不是当成路径分隔符。
    expect(parseCardResourceUrl(buildCardResourceUrl("card-1", "resource/2"))).toBeNull();
    expect(parseCardResourceUrl(buildCardResourceUrl("card-1", "resource\\2"))).toBeNull();
    expect(parseCardResourceUrl(buildCardResourceUrl("card-1", ".."))).toBeNull();
  });

  it("协议与主机名必须精确匹配", () => {
    expect(CREATION_ASSET_SCHEME).toBe("creation-asset");
    expect(CREATION_ASSET_HOST).toBe("card");
    expect(parseCardResourceUrl("creation-asset://other/card-1/resource-2")).toBeNull();
    expect(parseCardResourceUrl("novel-workbench-epub://card/card-1/resource-2")).toBeNull();
    expect(parseCardResourceUrl("file://card/card-1/resource-2")).toBeNull();
  });

  it("段数不为 2 或存在空段时拒绝", () => {
    expect(parseCardResourceUrl("creation-asset://card/only-one")).toBeNull();
    expect(parseCardResourceUrl("creation-asset://card/a/b/c")).toBeNull();
    expect(parseCardResourceUrl("creation-asset://card/")).toBeNull();
    expect(parseCardResourceUrl("creation-asset://card")).toBeNull();
    // 百分号转义非法时解码失败，同样拒绝。
    expect(parseCardResourceUrl("creation-asset://card/a%ZZ/b")).toBeNull();
  });

  it("非 URL 或非字符串输入拒绝", () => {
    expect(parseCardResourceUrl("")).toBeNull();
    expect(parseCardResourceUrl("not a url")).toBeNull();
    expect(parseCardResourceUrl("creation-asset://card/../b")).toBeNull();
    expect(parseCardResourceUrl(undefined as unknown as string)).toBeNull();
  });
});
