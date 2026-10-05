import { Controller, Get, Param, Query } from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiQuery, ApiTags } from '@nestjs/swagger';
import { FlightsService } from './flights.service';

@ApiTags('flights')
@Controller('flights')
export class FlightsController {
  constructor(private readonly flights: FlightsService) {}

  @Get()
  @ApiOperation({ summary: 'List flight plans, optionally filtered by callsign' })
  @ApiQuery({ name: 'callsign', required: false, description: 'Case-insensitive substring of the callsign' })
  @ApiOkResponse({ description: 'Flight summaries sorted by callsign' })
  list(@Query('callsign') callsign?: string) {
    return this.flights.list(callsign);
  }

  @Get(':id/route')
  @ApiOperation({ summary: 'Filed route of a flight resolved to coordinates' })
  route(@Param('id') id: string) {
    return this.flights.route(id);
  }

  @Get(':id/alternate-route')
  @ApiOperation({ summary: 'Alternate route between the same airports that avoids the filed waypoints' })
  alternateRoute(@Param('id') id: string) {
    return this.flights.alternateRoute(id);
  }
}
