const heightClasses = [
  'h-[18%]', 'h-[28%]', 'h-[38%]', 'h-[48%]', 'h-[58%]',
  'h-[68%]', 'h-[78%]', 'h-[88%]', 'h-full',
];

// Tailwind needs literal class names, so bar heights snap to ~10% steps.
export function barHeight(value, maximum) {
  if (!value || !maximum) return heightClasses[0];
  return heightClasses[Math.min(heightClasses.length - 1, Math.ceil((value / maximum) * heightClasses.length) - 1)];
}
