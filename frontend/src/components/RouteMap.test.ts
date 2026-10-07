import { unwrap } from './RouteMap';

describe('unwrap', () => {
  it('keeps a route crossing the dateline continuous', () => {
    expect(unwrap([{ lat: 0, lon: 170 }, { lat: 0, lon: -170 }, { lat: 0, lon: -150 }])).toEqual([
      [0, 170],
      [0, 190],
      [0, 210],
    ]);
  });

  it('leaves ordinary routes alone', () => {
    expect(unwrap([{ lat: 1, lon: 103 }, { lat: 2, lon: 101 }])).toEqual([
      [1, 103],
      [2, 101],
    ]);
  });
});
