import type { ID, ISODateString } from "./common";

export type InspirationType = "plot" | "character" | "world" | "scene" | "line" | "trope" | "note";
export type InspirationStatus = "inbox" | "usable" | "polished" | "used" | "archived";
export type InspirationVariantKind = "polish" | "expand" | "platform-style" | "conflict" | "humanize";

export interface InspirationSourceLocation {
  format?: "txt" | "md" | "epub";
  progressPercent?: number;
  excerpt?: string;
  href?: string;
  cfi?: string;
  scrollTop?: number;
  createdFrom?: "reader-selection" | "reader-note" | "manual";
}

export interface InspirationSourceSnapshot {
  bookId?: ID;
  bookTitle?: string;
  bookAuthor?: string;
  format?: "txt" | "md" | "epub";
  chapterTitle?: string;
  locationLabel?: string;
  progressPercent?: number;
  excerpt?: string;
  href?: string;
  cfi?: string;
  scrollTop?: number;
  createdFrom?: "reader-selection" | "reader-note" | "manual";
  createdAt: ISODateString;
}

export interface InspirationVariant {
  id: ID;
  kind: InspirationVariantKind;
  content: string;
  prompt: string;
  model: string;
  createdAt: ISODateString;
}

export interface InspirationItem {
  id: ID;
  title: string;
  body: string;
  type: InspirationType;
  status: InspirationStatus;
  tags: string[];
  platformTags: string[];
  source?: InspirationSourceSnapshot;
  sourceBookId?: ID;
  sourceLocation?: InspirationSourceLocation;
  variants: InspirationVariant[];
  revision: number;
  deviceId: ID;
  deletedAt?: ISODateString;
  createdAt: ISODateString;
  updatedAt: ISODateString;
}

export interface CreateInspirationInput {
  title: string;
  body?: string;
  type?: InspirationType;
  status?: InspirationStatus;
  tags?: string[];
  platformTags?: string[];
  source?: Partial<InspirationSourceSnapshot>;
  sourceBookId?: ID;
  sourceLocation?: InspirationSourceLocation;
}

export type UpdateInspirationInput = Partial<
  Pick<InspirationItem, "title" | "body" | "type" | "status" | "tags" | "platformTags" | "source" | "sourceBookId" | "sourceLocation" | "variants">
>;

export interface AddInspirationVariantInput {
  kind: InspirationVariantKind;
  content: string;
  prompt: string;
  model: string;
}
