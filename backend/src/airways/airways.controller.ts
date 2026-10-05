import { Controller, Get } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { CaasClient } from '../caas/caas.client';
import { parseGeoPoints } from '../geo/geopoint';

export interface Airway {
  name: string;
  points: { lat: number; lon: number }[];
}

@ApiTags('airways')
@Controller('airways')
export class AirwaysController {
  constructor(private readonly caas: CaasClient) {}

  @Get()
  @ApiOperation({ summary: 'All airways (air routes) with their points in published order' })
  async list(): Promise<Airway[]> {
    const byName = new Map<string, Airway>();
    for (const p of parseGeoPoints(await this.caas.listGeo('airways'))) {
      const airway = byName.get(p.name) ?? { name: p.name, points: [] };
      airway.points.push({ lat: p.lat, lon: p.lon });
      byName.set(p.name, airway);
    }
    return [...byName.values()].sort((a, b) => a.name.localeCompare(b.name));
  }
}
