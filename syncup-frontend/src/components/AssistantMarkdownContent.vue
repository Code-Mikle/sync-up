<template>
  <div class="assistant-markdown">
    <template v-for="(block, blockIndex) in blocks" :key="blockIndex">
      <p v-if="block.type === 'paragraph'" class="assistant-markdown__paragraph">
        <template v-for="(line, lineIndex) in block.lines" :key="lineIndex">
          <template v-for="(segment, segmentIndex) in parseInline(line)" :key="segmentIndex">
            <strong v-if="segment.bold">{{ segment.text }}</strong>
            <template v-else>{{ segment.text }}</template>
          </template>
          <br v-if="lineIndex < block.lines.length - 1" />
        </template>
      </p>

      <ol v-else-if="block.type === 'ordered-list'" class="assistant-markdown__list">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <template v-for="(segment, segmentIndex) in parseInline(item)" :key="segmentIndex">
            <strong v-if="segment.bold">{{ segment.text }}</strong>
            <template v-else>{{ segment.text }}</template>
          </template>
        </li>
      </ol>

      <ul v-else class="assistant-markdown__list">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <template v-for="(segment, segmentIndex) in parseInline(item)" :key="segmentIndex">
            <strong v-if="segment.bold">{{ segment.text }}</strong>
            <template v-else>{{ segment.text }}</template>
          </template>
        </li>
      </ul>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';

type MarkdownBlock =
  | { type: 'paragraph'; lines: string[] }
  | { type: 'ordered-list' | 'unordered-list'; items: string[] };

type InlineSegment = {
  text: string;
  bold: boolean;
};

const props = defineProps<{
  content: string;
}>();

const ORDERED_ITEM_PATTERN = /^\s*\d+[.．、)]\s+(.+)$/;
const UNORDERED_ITEM_PATTERN = /^\s*[-*+]\s+(.+)$/;
const BOLD_PATTERN = /\*\*(.+?)\*\*/g;

const blocks = computed(() => parseBlocks(props.content));

const parseBlocks = (content: string): MarkdownBlock[] => {
  const lines = content.replace(/\r\n?/g, '\n').split('\n');
  const result: MarkdownBlock[] = [];
  let paragraphLines: string[] = [];

  const flushParagraph = () => {
    if (paragraphLines.length > 0) {
      result.push({type: 'paragraph', lines: paragraphLines});
      paragraphLines = [];
    }
  };

  for (let index = 0; index < lines.length;) {
    const line = lines[index];
    if (!line.trim()) {
      flushParagraph();
      index += 1;
      continue;
    }

    const orderedMatch = line.match(ORDERED_ITEM_PATTERN);
    if (orderedMatch) {
      flushParagraph();
      const items: string[] = [];
      while (index < lines.length) {
        const itemMatch = lines[index].match(ORDERED_ITEM_PATTERN);
        if (!itemMatch) {
          break;
        }
        items.push(itemMatch[1]);
        index += 1;
      }
      result.push({type: 'ordered-list', items});
      continue;
    }

    const unorderedMatch = line.match(UNORDERED_ITEM_PATTERN);
    if (unorderedMatch) {
      flushParagraph();
      const items: string[] = [];
      while (index < lines.length) {
        const itemMatch = lines[index].match(UNORDERED_ITEM_PATTERN);
        if (!itemMatch) {
          break;
        }
        items.push(itemMatch[1]);
        index += 1;
      }
      result.push({type: 'unordered-list', items});
      continue;
    }

    paragraphLines.push(line);
    index += 1;
  }

  flushParagraph();
  return result;
};

const parseInline = (text: string): InlineSegment[] => {
  const result: InlineSegment[] = [];
  let lastIndex = 0;
  BOLD_PATTERN.lastIndex = 0;

  for (const match of text.matchAll(BOLD_PATTERN)) {
    const matchIndex = match.index ?? 0;
    if (matchIndex > lastIndex) {
      result.push({text: text.slice(lastIndex, matchIndex), bold: false});
    }
    result.push({text: match[1], bold: true});
    lastIndex = matchIndex + match[0].length;
  }
  if (lastIndex < text.length || result.length === 0) {
    result.push({text: text.slice(lastIndex), bold: false});
  }
  return result;
};
</script>

<style scoped>
.assistant-markdown {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.assistant-markdown__paragraph {
  margin: 0;
  line-height: 1.65;
  white-space: pre-wrap;
}

.assistant-markdown__list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 1.35rem;
  line-height: 1.6;
}

.assistant-markdown__list li {
  padding-left: 2px;
}

strong {
  color: var(--app-primary-deep);
  font-weight: 800;
}
</style>
