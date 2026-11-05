import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendRoot = process.env.CARELOG_FE_ROOT;
const backendTarget = process.env.CARELOG_RPL64_BE_TARGET?.trim();

if (!frontendRoot || !backendTarget) {
  throw new Error('The RPL-64 HTTP/PostgreSQL test requires frontend and backend loopback targets.');
}

const expectedFrontendSha = '190c70564df5261b9a711b8e23cd7b457bbccbc9';
let actualFrontendSha;
try {
  actualFrontendSha = execFileSync('git', ['-C', frontendRoot, 'rev-parse', 'HEAD'], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
} catch (error) {
  actualFrontendSha = process.env.CARELOG_RPL64_FE_SOURCE_SHA;
  if (!actualFrontendSha) {
    throw new Error(`The RPL-64 test could not read the FE source SHA from CARELOG_FE_ROOT: ${error.message}`);
  }
}
if (actualFrontendSha !== expectedFrontendSha) {
  throw new Error(`The RPL-64 HTTP/PostgreSQL test requires FE source ${expectedFrontendSha}; got ${actualFrontendSha}.`);
}

const backendUrl = new URL(backendTarget);
const backendAuthority = backendTarget.slice('http://'.length).split(/[/?#]/, 1)[0] ?? '';
if (
  backendUrl.protocol !== 'http:' ||
  !['127.0.0.1', 'localhost'].includes(backendUrl.hostname) ||
  !backendUrl.port ||
  backendAuthority.includes('@') ||
  backendUrl.username ||
  backendUrl.password ||
  backendUrl.pathname !== '/' ||
  backendUrl.search ||
  backendUrl.hash
) {
  throw new Error('The RPL-64 backend target must be loopback-only HTTP.');
}

const requireFromFrontend = createRequire(resolve(frontendRoot, 'package.json'));
const { defineConfig } = requireFromFrontend('@playwright/test');
const frontendPort = 4175;

export default defineConfig({
  testDir: dirname(fileURLToPath(import.meta.url)),
  testMatch: 'carelog-customer-http-postgres.real.spec.mjs',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'line',
  timeout: 45_000,
  use: {
    baseURL: 'http://127.0.0.1:' + frontendPort,
    browserName: 'chromium',
    trace: 'off',
  },
  webServer: {
    command: 'pnpm exec vite --mode real-api-e2e --host 127.0.0.1 --port ' + frontendPort + ' --strictPort',
    cwd: frontendRoot,
    url: 'http://127.0.0.1:' + frontendPort,
    reuseExistingServer: false,
    timeout: 120_000,
    env: {
      VITE_CARELOG_API_BASE_URL: 'http://127.0.0.1:' + frontendPort,
      CARELOG_E2E_GATEWAY_TARGET: backendUrl.origin,
    },
  },
});
