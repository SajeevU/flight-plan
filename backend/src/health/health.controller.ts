import { Controller, Get } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';
import { CaasClient } from '../caas/caas.client';

@ApiTags('health')
@Controller('health')
export class HealthController {
  constructor(private readonly caas: CaasClient) {}

  @Get()
  health() {
    return { status: 'ok', dataSource: this.caas.mock ? 'fixtures' : 'caas', version: process.env.APP_VERSION ?? 'dev' };
  }
}
