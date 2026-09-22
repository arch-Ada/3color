import { expect, it } from 'vitest';
import { spatialNeighbor } from './navigation';
it('navigates a slightly irregular layout spatially instead of by ID', () => {
  const points = [
    { x: 100, y: 100 },
    { x: 102, y: 200 },
    { x: 0, y: 102 },
    { x: 200, y: 98 },
    { x: 98, y: 0 },
  ];
  expect(spatialNeighbor(points, 0, 'arrowdown')).toBe(1);
  expect(spatialNeighbor(points, 0, 'arrowleft')).toBe(2);
  expect(spatialNeighbor(points, 0, 'arrowright')).toBe(3);
  expect(spatialNeighbor(points, 0, 'arrowup')).toBe(4);
  expect(spatialNeighbor(points, 4, 'arrowup')).toBe(4);
});
it('favors aligned neighbors without jumping to distant vertices', () => {
  const points = [
    { x: 0, y: 0 },
    { x: 50, y: 48 },
    { x: 100, y: 2 },
    { x: 1000, y: 0 },
  ];
  expect(spatialNeighbor(points, 0, 'arrowright')).toBe(2);
});
it('does not move sideways or wrap when no candidate is in the directional cone', () => {
  const points = [
    { x: 0, y: 0 },
    { x: 1, y: 100 },
    { x: -10, y: 0 },
    { x: 0, y: 0 },
  ];
  expect(spatialNeighbor(points, 0, 'arrowright')).toBe(0);
});
it('uses rotated display coordinates and is unaffected by uniform zoom', () => {
  const portrait = [
    { x: 100, y: 100 },
    { x: 200, y: 100 },
    { x: 100, y: 0 },
  ];
  expect(spatialNeighbor(portrait, 0, 'arrowup')).toBe(2);
  expect(
    spatialNeighbor(
      portrait.map((p) => ({ x: p.x * 2, y: p.y * 2 })),
      0,
      'arrowup',
    ),
  ).toBe(2);
});
