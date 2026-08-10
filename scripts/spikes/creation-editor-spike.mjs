#!/usr/bin/env node
/**
 * Editor headless spike — strict novel-body schema via @tiptap/core + @tiptap/pm.
 *
 * Scope:
 *   - Only node types: doc, paragraph, quoteLetter, centeredText, authorNote,
 *     sceneBreak, text.
 *   - Only marks: bold, italic.
 *   - No DOM, no UI: exercises schema construction and JSON round-trip only.
 *
 * Verifies:
 *   - Known JSON survives schema.nodeFromJSON -> toJSON exactly (round-trip).
 *   - Chinese text is preserved byte-for-byte.
 *   - JSON containing an unsupported `table` node is rejected.
 *   - sceneBreak is an atom leaf node.
 *
 * Output: a single-line JSON document (stdout). Non-zero exit on failure.
 */

import { Mark, Node, getSchema } from '@tiptap/core';
import { Schema } from '@tiptap/pm/model';

const nodeExtensions = [
  Node.create({
    name: 'doc',
    topNode: true,
    content: 'block+',
  }),
  Node.create({
    name: 'paragraph',
    group: 'block',
    content: 'inline*',
    renderHTML: () => ['p', 0],
  }),
  Node.create({
    name: 'quoteLetter',
    group: 'block',
    content: 'inline*',
    renderHTML: () => ['blockquote', 0],
  }),
  Node.create({
    name: 'centeredText',
    group: 'block',
    content: 'inline*',
    renderHTML: () => ['p', { style: 'text-align: center' }, 0],
  }),
  Node.create({
    name: 'authorNote',
    group: 'block',
    content: 'inline*',
    renderHTML: () => ['div', { 'data-author-note': 'true' }, 0],
  }),
  Node.create({
    name: 'sceneBreak',
    group: 'block',
    atom: true,
    renderHTML: () => ['hr'],
  }),
  Node.create({
    name: 'text',
    group: 'inline',
  }),
];

const markExtensions = [
  Mark.create({
    name: 'bold',
    renderHTML: () => ['strong', 0],
  }),
  Mark.create({
    name: 'italic',
    renderHTML: () => ['em', 0],
  }),
];

const schema = getSchema([...nodeExtensions, ...markExtensions]);

if (!(schema instanceof Schema)) {
  throw new Error('getSchema did not return a @tiptap/pm Schema instance');
}

/** Structural deep equality (plain JSON objects/arrays/primitives). */
function deepEqual(a, b) {
  if (Object.is(a, b)) return true;
  if (typeof a !== 'object' || typeof b !== 'object' || a === null || b === null) {
    return false;
  }
  const aKeys = Object.keys(a);
  const bKeys = Object.keys(b);
  if (aKeys.length !== bKeys.length) return false;
  for (const key of aKeys) {
    if (!Object.prototype.hasOwnProperty.call(b, key)) return false;
    if (!deepEqual(a[key], b[key])) return false;
  }
  return true;
}

/** Collect every text node's string content in document order. */
function collectText(node) {
  const out = [];
  node.descendants((child) => {
    if (child.isText) out.push(child.text);
    return true;
  });
  return out;
}

const knownJson = {
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: [{ type: 'text', text: '夜风穿过长街，雪落在肩头。她想起七年前那个雨夜。' }],
    },
    {
      type: 'quoteLetter',
      content: [
        { type: 'text', text: '君子一言，驷马难追。', marks: [{ type: 'bold' }] },
        { type: 'text', text: '——第三卷，第九章' },
      ],
    },
    {
      type: 'centeredText',
      content: [{ type: 'text', text: '—— 翌日 ——', marks: [{ type: 'italic' }] }],
    },
    {
      type: 'authorNote',
      content: [{ type: 'text', text: '作者注：此处伏笔对应第二卷的玉佩。', marks: [{ type: 'bold' }, { type: 'italic' }] }],
    },
    { type: 'sceneBreak' },
    {
      type: 'paragraph',
      content: [{ type: 'text', text: '他推开门，纸鸢在檐角摇晃。' }],
    },
  ],
};

const node = schema.nodeFromJSON(knownJson);
const roundTripped = node.toJSON();
const roundTrip = deepEqual(knownJson, roundTripped);

const originalTexts = knownJson.content
  .flatMap((block) => block.content ?? [])
  .filter((child) => child.type === 'text')
  .map((child) => child.text);
const roundTrippedTexts = collectText(node);
const chineseTextPreserved =
  JSON.stringify(originalTexts) === JSON.stringify(roundTrippedTexts) &&
  roundTrippedTexts.includes('夜风穿过长街，雪落在肩头。她想起七年前那个雨夜。') &&
  roundTrippedTexts.includes('作者注：此处伏笔对应第二卷的玉佩。');

const unsupportedJson = {
  type: 'doc',
  content: [
    { type: 'paragraph', content: [{ type: 'text', text: '正文段落' }] },
    {
      type: 'table',
      content: [
        {
          type: 'tableRow',
          content: [
            {
              type: 'tableCell',
              content: [{ type: 'paragraph', content: [{ type: 'text', text: '单元格' }] }],
            },
          ],
        },
      ],
    },
  ],
};

let unsupportedContentRejected = false;
let rejectionMessage = '';
try {
  schema.nodeFromJSON(unsupportedJson);
} catch (err) {
  unsupportedContentRejected = true;
  rejectionMessage = err instanceof Error ? err.message : String(err);
}

// Direct proof that `table` itself is not part of the strict schema.
let tableTypeRejected = false;
try {
  schema.nodeFromJSON({ type: 'doc', content: [{ type: 'table' }] });
} catch (err) {
  tableTypeRejected =
    err instanceof Error && err.message.includes('Unknown node type: table');
}

const allowedNodeTypes = Object.keys(schema.nodes).sort();
const allowedMarks = Object.keys(schema.marks).sort();
const sceneBreakType = schema.nodes.sceneBreak;
const sceneBreakAtom = sceneBreakType.isAtom === true && sceneBreakType.isLeaf === true;

const result = {
  roundTrip,
  unsupportedContentRejected,
  chineseTextPreserved,
  allowedNodeTypes,
  allowedMarks,
  sceneBreakAtom,
  tableTypeRejected,
  unsupportedNodeRejectedWith: unsupportedContentRejected ? rejectionMessage : null,
};

const expectedNodes = ['authorNote', 'centeredText', 'doc', 'paragraph', 'quoteLetter', 'sceneBreak', 'text'];
const expectedMarks = ['bold', 'italic'];
const schemaIsStrict =
  JSON.stringify(allowedNodeTypes) === JSON.stringify(expectedNodes) &&
  JSON.stringify(allowedMarks) === JSON.stringify(expectedMarks);

const allPass =
  roundTrip &&
  unsupportedContentRejected &&
  chineseTextPreserved &&
  sceneBreakAtom &&
  tableTypeRejected &&
  schemaIsStrict;

if (!allPass) {
  console.error(JSON.stringify({ ...result, schemaIsStrict, allPass: false }, null, 2));
  process.exitCode = 1;
} else {
  console.log(JSON.stringify({ ...result, schemaIsStrict, allPass: true }));
}
