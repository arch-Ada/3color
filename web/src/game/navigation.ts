export type Direction = 'arrowleft' | 'arrowright' | 'arrowup' | 'arrowdown';
export interface Position {
  x: number;
  y: number;
}

/** Choose a nearby vertex in a 90-degree directional cone, favoring alignment.
 * Coordinates must match the displayed layout, including portrait rotation.
 * Outside the cone (or at an edge), stay put rather than wrap.
 */
export function spatialNeighbor(
  points: readonly Position[],
  selected: number,
  direction: Direction,
): number {
  const origin = points[selected];
  if (!origin) return selected;
  const [dx, dy] = {
    arrowleft: [-1, 0],
    arrowright: [1, 0],
    arrowup: [0, -1],
    arrowdown: [0, 1],
  }[direction];
  let best = selected,
    bestScore = Infinity;
  points.forEach((point, index) => {
    if (index === selected) return;
    const x = point.x - origin.x,
      y = point.y - origin.y;
    const forward = x * dx + y * dy;
    const sideways = Math.abs(x * dy - y * dx);
    if (forward <= 1e-6 || sideways > forward) return;
    const score = Math.hypot(x, y) * (1 + 2 * (sideways / forward) ** 2);
    if (score < bestScore) {
      best = index;
      bestScore = score;
    }
  });
  return best;
}
