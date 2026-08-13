import type { CreationProjectOutline } from "@/types/creation";

/** 一份可复用的双卷大纲：卷一含第一/二章，卷二含第三章。displayNumber 留空以让章节键=标题。 */
export function makeOutline(): CreationProjectOutline {
  return {
    project: { id: "p1", title: "P1" },
    looseChapters: [],
    volumes: [
      {
        projectId: "p1",
        id: "v1",
        title: "卷一",
        sortOrder: 0,
        createdAt: "",
        updatedAt: "",
        revision: 1,
        chapters: [
          {
            id: "c1",
            volumeId: "v1",
            title: "第一章",
            sortOrder: 0,
            status: "草稿",
            numbering: "auto",
            customNumber: null,
            displayNumber: null,
            createdAt: "",
            updatedAt: "",
            revision: 1,
            scenes: [
              { id: "s1", chapterId: "c1", title: "场景A", sortOrder: 0, createdAt: "", updatedAt: "", revision: 1, wordCount: 100, planning: {} },
              { id: "s2", chapterId: "c1", title: "场景B", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1, wordCount: 200, planning: {} }
            ]
          },
          {
            id: "c2",
            volumeId: "v1",
            title: "第二章",
            sortOrder: 1,
            status: "草稿",
            numbering: "auto",
            customNumber: null,
            displayNumber: null,
            createdAt: "",
            updatedAt: "",
            revision: 1,
            scenes: [
              { id: "s3", chapterId: "c2", title: "场景C", sortOrder: 0, createdAt: "", updatedAt: "", revision: 1, wordCount: 50, planning: {} }
            ]
          }
        ]
      },
      {
        projectId: "p1",
        id: "v2",
        title: "卷二",
        sortOrder: 1,
        createdAt: "",
        updatedAt: "",
        revision: 1,
        chapters: [
          {
            id: "c3",
            volumeId: "v2",
            title: "第三章",
            sortOrder: 0,
            status: "已完成",
            numbering: "auto",
            customNumber: null,
            displayNumber: null,
            createdAt: "",
            updatedAt: "",
            revision: 1,
            scenes: [
              { id: "s4", chapterId: "c3", title: "场景D", sortOrder: 0, createdAt: "", updatedAt: "", revision: 1, wordCount: 80, planning: {} }
            ]
          }
        ]
      }
    ]
  } as unknown as CreationProjectOutline;
}
