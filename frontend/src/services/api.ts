/** @author Nikunj Malik */
import axios from 'axios';

const client = axios.create({ baseURL: '/api', timeout: 60000 });

export interface EndToEndRequest {
  nl: string;
  strategy: 'rule' | 'claude';
  schema?: Record<string, string>;
  payload?: Record<string, any>;
  persist?: boolean;
  generateScenarios?: boolean;
}

export interface FromDslRequest {
  dsl: any;
  schema?: Record<string, string>;
  payload?: Record<string, any>;
  persist?: boolean;
  generateScenarios?: boolean;
}

/** Loose stage-keyed pipeline result. */
export interface PipelineResponse {
  status: string;
  stage1_sl?: any;
  stage1_slRendered?: string;
  stage2_lint?: any;
  stage3_dsl?: any;
  stage4_dslValidation?: any;
  stage5_ast?: any;
  stage6_typeCheck?: any;
  stage7_optimization?: any;
  stage8_ir?: any;
  stage9_artifactId?: number;
  stage10_execution?: any;
  stage11_scenarios?: any[];
}

export interface ExtensionFn {
  name: string;
  pack: string;
  signature: string;
  returnType: string;
  description: string;
}

export async function health(): Promise<{ status: string; claudeAvailable: boolean; extensionFunctions: number }> {
  return (await client.get('/pipeline/health')).data;
}

export async function listExtensions(): Promise<ExtensionFn[]> {
  return (await client.get('/pipeline/extensions')).data;
}

export async function runEndToEnd(req: EndToEndRequest): Promise<PipelineResponse> {
  return (await client.post('/pipeline/end-to-end', req)).data;
}

export async function runFromDsl(req: FromDslRequest): Promise<PipelineResponse> {
  return (await client.post('/pipeline/from-dsl', req)).data;
}

export async function listArtifacts(): Promise<any[]> {
  return (await client.get('/pipeline/artifacts')).data;
}
