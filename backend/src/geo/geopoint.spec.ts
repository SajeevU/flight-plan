import { distanceNm, parseGeoPoint, parseGeoPoints } from './geopoint';

describe('parseGeoPoint', () => {
  it('parses the CAAS "NAME (lat,lon)" format', () => {
    expect(parseGeoPoint('WSSL (1.42,103.87)')).toEqual({ name: 'WSSL', lat: 1.42, lon: 103.87 });
  });

  it('handles negative coordinates and extra spaces', () => {
    expect(parseGeoPoint(' LAMOB ( -4.2 , -106.4 ) ')).toEqual({ name: 'LAMOB', lat: -4.2, lon: -106.4 });
  });

  it.each([null, 42, 'NOCOORDS', 'BAD (91,0)', 'BAD (0,181)', 'BAD (a,b)'])('rejects %p', (input) => {
    expect(parseGeoPoint(input)).toBeNull();
  });

  it('drops invalid entries from a list', () => {
    expect(parseGeoPoints(['A (1,2)', 'junk', 'B (3,4)'])).toHaveLength(2);
    expect(parseGeoPoints('not an array')).toEqual([]);
  });
});

describe('distanceNm', () => {
  it('is ~60 NM per degree of latitude', () => {
    expect(distanceNm({ lat: 0, lon: 0 }, { lat: 1, lon: 0 })).toBeCloseTo(60, 0);
  });
});
