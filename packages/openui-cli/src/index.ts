#!/usr/bin/env node

import { Command } from "commander";

import { runCreateChatApp } from "./commands/create-chat-app";
import { runGenerate } from "./commands/generate";
import { runGenerateExtension } from "./commands/generate-extension";
import { resolveArgs } from "./lib/resolve-args";

const program = new Command();

// Use `-V, --cli-version` for the tool version so subcommands are free to take a
// `--version` value option (e.g. `generate-extension --version <ver>`).
program.name("openui").description("CLI for OpenUI").version("0.0.6", "-V, --cli-version");

program
  .command("create")
  .description("Scaffold a new Next.js app with OpenUI Chat")
  .option("-n, --name <string>", "Project name")
  .option("--skill", "Install the OpenUI agent skill for AI coding assistants")
  .option("--no-skill", "Skip installing the OpenUI agent skill")
  .option("--no-interactive", "Fail with error if required args are missing")
  .action(async (options: { name?: string; skill?: boolean; interactive: boolean }) => {
    await runCreateChatApp({
      name: options.name,
      skill: options.skill,
      noInteractive: !options.interactive,
    });
  });

program
  .command("generate")
  .description("Generate system prompt or JSON schema from a library definition")
  .argument("[entry]", "Path to a file that exports a createLibrary() result")
  .option("-o, --out <file>", "Write output to a file instead of stdout")
  .option(
    "--json-schema",
    "Output JSON schema with component signatures for standalone prompt generation",
  )
  .option("--export <name>", "Name of the export to use (auto-detected by default)")
  .option(
    "--prompt-options <name>",
    "Name of the PromptOptions export to use (auto-detected by default)",
  )
  .option("--no-interactive", "Fail with error if required args are missing")
  .action(
    async (
      entry: string | undefined,
      options: {
        out?: string;
        jsonSchema?: boolean;
        export?: string;
        promptOptions?: string;
        interactive: boolean;
      },
    ) => {
      const args = await resolveArgs(
        {
          entry: entry
            ? { value: entry }
            : {
                prompt: { type: "input", message: "Entry file path?" },
                required: true,
              },
        },
        options.interactive,
      );

      await runGenerate((args as { entry: string }).entry, options);
    },
  );

program
  .command("generate-extension")
  .description("Generate a registerable Extension JSON from an extension object")
  .argument("[entry]", "Path to a file that exports an extension object")
  .option("-o, --out <file>", "Write output to a file instead of stdout")
  .option("--extension-id <id>", "Extension id (overrides the value in the extension object)")
  .option("--version <version>", "Extension version (overrides the value in the extension object)")
  .option("--export <name>", "Name of the export to use (auto-detected by default)")
  .option("--no-interactive", "Fail with error if required args are missing")
  .action(
    async (
      entry: string | undefined,
      options: {
        out?: string;
        extensionId?: string;
        version?: string;
        export?: string;
        interactive: boolean;
      },
    ) => {
      const args = await resolveArgs(
        {
          entry: entry
            ? { value: entry }
            : {
                prompt: { type: "input", message: "Entry file path?" },
                required: true,
              },
        },
        options.interactive,
      );

      await runGenerateExtension((args as { entry: string }).entry, options);
    },
  );

program.parse();
