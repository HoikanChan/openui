// TODO: replace with the real dslLibrary once @openuidev/react-ui-dsl is published
// import { dslLibrary } from "@openuidev/react-ui-dsl";
// export { dslLibrary };

import { openuiLibrary } from "@openuidev/react-ui";
import type { Library } from "@openuidev/react-lang";

export const dslLibrary: Library = openuiLibrary as unknown as Library;
