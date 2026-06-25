declare module "../../../mock/febs/prel-mock.mjs" {
  type Piu = {
    setup(stateMap: Record<string, { value: unknown; publicWritable?: boolean }>): Piu;
  };

  const Prel: {
    __reset(): typeof Prel;
    start(name: string, version: string, deps: string[], cb: (...args: unknown[]) => void): Piu;
    define(defs: Record<string, { version?: string; js?: string[]; css?: string[] }>): typeof Prel;
    autoLoad(piuNames: string | string[], opts?: { fresh?: boolean; baseUrl?: string }): Promise<unknown>;
  };

  export default Prel;
}
