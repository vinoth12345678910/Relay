import { cn } from '@/lib/cn';

/**
 * Shows server-provided text (payloads, response snippets) verbatim. It is rendered as a React
 * text node, so markup like <script> is displayed, never executed or parsed as HTML.
 */
export function CodeBlock({
  text,
  label,
  className,
}: {
  text: string;
  label: string;
  className?: string;
}) {
  return (
    <pre
      role="region"
      aria-label={label}
      tabIndex={0}
      className={cn(
        'max-h-80 overflow-auto rounded-md border border-border bg-code-bg p-3 font-mono text-[12.5px] leading-relaxed whitespace-pre-wrap break-all',
        className,
      )}
    >
      <code>{text}</code>
    </pre>
  );
}
