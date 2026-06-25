type PiuSocket = {
  attach?: (thisObj: unknown, handlers: Record<string, unknown>) => unknown;
  emit?: (eventName: string, ...params: unknown[]) => unknown;
};

type PrelLike = {
  start: (
    name: string,
    version: string,
    dependencies: string[],
    cb: (socket: PiuSocket) => void,
  ) => PiuSocket;
};

const PIU_NAME = 'dsl-engine';
const PIU_VERSION = '1.0.0';
const PIU_DEPENDENCIES = ['session', 'locale'];

type PiuCallback = (socket: PiuSocket) => void;

declare global {
  interface Window {
    DSL_ENGINE_PIU?: PiuSocket;
    Prel: PrelLike;
  }
}

let isInitializing = false;
let pendingCallbacks: PiuCallback[] = [];

// 鎵ц鎵€鏈夌瓑寰呯殑鍥炶皟
function flushCallbacks(socket: PiuSocket): void {
  const callbacks = pendingCallbacks;
  pendingCallbacks = [];
  callbacks.forEach(cb => cb(socket));
}

// 寮€濮嬪垵濮嬪寲
function initializePiu(): void {
  isInitializing = true;
  window.Prel.start(PIU_NAME, PIU_VERSION, PIU_DEPENDENCIES, (socket: PiuSocket) => {
    // 灏?PIU 瀹炰緥瀛樺偍鍦?window 涓?
    window.DSL_ENGINE_PIU = socket;
    isInitializing = false;
    flushCallbacks(socket);
  });
}

export function createPiu(cb: PiuCallback): void {
  // 妫€鏌?window 涓槸鍚﹀凡鏈?PIU 瀹炰緥
  const piu = window.DSL_ENGINE_PIU;

  if (piu) {
    cb(piu);
    return;
  }

  // 濡傛灉娌℃湁瀹炰緥锛屽皢鍥炶皟鍔犲叆闃熷垪
  pendingCallbacks.push(cb);

  // 閬垮厤閲嶅鍒濆鍖朠IU
  if (!isInitializing) {
    initializePiu();
  }
}