/**
 * Word-level diff used to highlight replacement positions in the bilingual view.
 * A small LCS over whitespace-preserving tokens is enough for review UI purposes.
 */
export interface DiffSegment {
  text: string;
  kind: 'same' | 'removed' | 'added';
}

export function diffTokens(source: string, target: string): { before: DiffSegment[]; after: DiffSegment[] } {
  const a = tokenize(source);
  const b = tokenize(target);
  const dp = lcsTable(a, b);
  const before: DiffSegment[] = [];
  const after: DiffSegment[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      before.push({ text: a[i], kind: 'same' });
      after.push({ text: b[j], kind: 'same' });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      before.push({ text: a[i], kind: 'removed' });
      i++;
    } else {
      after.push({ text: b[j], kind: 'added' });
      j++;
    }
  }
  while (i < a.length) {
    before.push({ text: a[i], kind: 'removed' });
    i++;
  }
  while (j < b.length) {
    after.push({ text: b[j], kind: 'added' });
    j++;
  }
  return { before, after };
}

function tokenize(text: string): string[] {
  return text.split(/(\s+)/).filter(t => t.length > 0);
}

function lcsTable(a: string[], b: string[]): number[][] {
  const m = a.length;
  const n = b.length;
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array<number>(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  return dp;
}

/** Placeholder tokens, mirroring the backend extractor. */
export function placeholdersOf(text: string): string[] {
  const re = /\{\{[A-Za-z_][A-Za-z0-9_]*}}|\{[A-Za-z0-9_]+}|%\d+\$[+#0-]*\d*[a-zA-Z]|%[+#0-]*\d*[sdif]|<(x|ph)\b[^>]*\/>/g;
  return text.match(re) ?? [];
}
