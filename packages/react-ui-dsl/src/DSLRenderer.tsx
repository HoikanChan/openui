import { Renderer, RendererProps } from '@openuidev/react-lang';
import { useLibrary, useToolProvider } from './context/useDslRuntime';

interface DSLRendererProps extends Omit<RendererProps, 'toolProvider'> {

}

export default function DSLRenderer({library, ...props}: DSLRendererProps) {
  const mergedLib = useLibrary(library);
  const toolProviderFromPiu = useToolProvider();

  return <Renderer library={mergedLib} toolProvider={toolProviderFromPiu} {...props}/>
}