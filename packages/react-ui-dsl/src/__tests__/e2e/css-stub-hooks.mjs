// Hook module registered by css-stub-loader.mjs
const CSS_RE = /\.(css|scss|sass|less)(\?.*)?$/;
const CSS_STUB = "export default {};";

export async function resolve(specifier, context, nextResolve) {
  if (CSS_RE.test(specifier)) {
    return { shortCircuit: true, url: "data:text/javascript," + encodeURIComponent(CSS_STUB) };
  }
  return nextResolve(specifier, context);
}

export async function load(url, context, nextLoad) {
  if (url.startsWith("data:text/javascript,")) {
    return { format: "module", shortCircuit: true, source: decodeURIComponent(url.slice("data:text/javascript,".length)) };
  }
  return nextLoad(url, context);
}
