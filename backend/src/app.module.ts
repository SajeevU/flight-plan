import { Module } from '@nestjs/common';
import { AirwaysController } from './airways/airways.controller';
import { CaasClient } from './caas/caas.client';
import { FlightsController } from './flights/flights.controller';
import { FlightsService } from './flights/flights.service';
import { HealthController } from './health/health.controller';

@Module({
  controllers: [FlightsController, AirwaysController, HealthController],
  providers: [CaasClient, FlightsService],
})
export class AppModule {}
