import { workspace, window, ExtensionContext } from 'vscode';
import {
	LanguageClient,
	LanguageClientOptions,
	ServerOptions,
	Executable
} from 'vscode-languageclient/node';
import * as path from 'path';

let client: LanguageClient;

export function activate(_context: ExtensionContext) {

	const config = workspace.getConfiguration('oberonLsp.languageServer');

	if (!config.get('activate')) {
		return;
	}

	const launchType = config.get('launchType');
	let diskImage: string = config.get('modifiedDiskImage') ?? '';
	if (diskImage === '') {
		diskImage = "lsph-" + launchType + ".dsk";
	}
	let arglist: string[] = [
		"-jar", "LSPServer.jar",
		"-emulator", diskImage,
		"-autocopy"
	];

	if (launchType === "a2") {
		let switches: string = config.get('launchTypeA2.switches') ?? "";
		let symbolPath: string[] = config.get('launchTypeA2.symbolpath') ?? [];
		arglist = [
			"-jar", "LSPServer.jar",
			"-exec",
	        "a2-lsph.exe",
    	    "LSPhServer.Run",
			switches,
        	"%WORKPATH%",
			...symbolPath
		];
	}

	if (launchType === "other1" || launchType === "other2" || launchType === "other3") {
		arglist = config.get(launchType+"Arguments") || [];
	} else if (config.get('cache.activate')) {
		let cachePath : string | undefined = config.get('cache.directory');
		if (cachePath !== undefined) {
			const wsf = window.activeTextEditor !== undefined ? workspace.getWorkspaceFolder(window.activeTextEditor.document.uri) : undefined;
			if (wsf !== undefined) {
				cachePath = path.join(wsf.uri.fsPath, cachePath);
			} else if (workspace.workspaceFolders !== undefined && workspace.workspaceFolders.length > 0) {
				cachePath = path.join(workspace.workspaceFolders[0].uri.fsPath, cachePath);
			} else {
				cachePath = undefined;
			}
		}
		if (cachePath !== undefined) {
			arglist.splice(2, 0, "-cache", cachePath);
		}
	}

	const executable: Executable =  {
		command: config.get("javaLocation") || "java",
		args: arglist,
		options: { cwd: config.get("location") }
	 };

	const serverOptions: ServerOptions = {
		run: executable,
		debug: executable
	};

	const globs = ['*.ob','*.ob07','*.Mod','*.Mod.txt'];

	const clientOptions: LanguageClientOptions = {
		documentSelector: [{ language: 'oberon' }],
		initializationOptions: {
			globs: globs
		}
	};

	client = new LanguageClient(
		'OberonLanguageServer',
		'Oberon Language Server',
		serverOptions,
		clientOptions
	);

	client.start();
}

export function deactivate(): Thenable<void> | undefined {
	if (!client) {
		return undefined;
	}
	return client.stop();
}
