package lspserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileCreate;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import lspserver.OberonFile.AnalysisResult;
import lspserver.OberonFile.IdentifierReference;

public class CachingServer extends Server {

	private final List<String> pendingUris = new ArrayList<>();

	private final Map<String, OberonFile> cachedFiles = new ConcurrentHashMap<>();
	private final Map<String, List<OberonFile>> cachedFilesByModule = new ConcurrentHashMap<>();
	private final Map<String, List<OberonFile>> cachedFilesByDepModule = new ConcurrentHashMap<>();
	private final File cacheDir;
	private final List<String> suffixes = new ArrayList<>();
	private int progressCounter = -1;

	public CachingServer(File cacheDir, Bridge bridge, boolean debug) {
		super(bridge, debug);
		this.cacheDir = cacheDir;
	}

	private RegistrationParams capabilities = null;

	protected void fillCapabilities(ServerCapabilities cap, InitializeParams params) {
		super.fillCapabilities(cap, params);

		if (params.getCapabilities().getWindow().getWorkDoneProgress() == Boolean.TRUE) {
			progressCounter = 0;
		}

		JsonObject obj = (JsonObject) params.getInitializationOptions();
		JsonArray globs = obj == null ? null : (JsonArray) obj.get("globs");
		if (globs == null)
			return;
		for (int i = 0; i < globs.size(); i++) {
			String glob = globs.get(i).getAsString();
			if (glob.startsWith("*") && !glob.substring(1).contains("*")) {
				suffixes.add(glob.substring(1));
			}
		}
		if (suffixes.isEmpty())
			return;
		for (WorkspaceFolder wf : params.getWorkspaceFolders()) {
			if (wf.getUri().startsWith("file:")) {
				new WorkspaceFolderScanner(wf).scan();
			}
		}
		WorkspaceFoldersOptions wfo = new WorkspaceFoldersOptions();
		wfo.setSupported(true);
		wfo.setChangeNotifications(true);
		WorkspaceServerCapabilities wsc = new WorkspaceServerCapabilities(wfo);
		FileOperationsServerCapabilities fo = new FileOperationsServerCapabilities();
		wsc.setFileOperations(fo);
		List<FileOperationFilter> filter = new ArrayList<>();
		for (String suffix : suffixes) {
			FileOperationPattern fop = new FileOperationPattern("**/*" + suffix);
			filter.add(new FileOperationFilter(fop, "file"));
		}
		fo.setDidCreate(new FileOperationOptions(filter));
		fo.setDidDelete(new FileOperationOptions(filter));
		fo.setDidRename(new FileOperationOptions(filter));
		cap.setWorkspace(wsc);
		List<Registration> registrations = new ArrayList<>();
		if (params.getCapabilities().getWorkspace().getDidChangeWatchedFiles() != null && params.getCapabilities().getWorkspace().getDidChangeWatchedFiles().getDynamicRegistration() == Boolean.TRUE) {
			DidChangeWatchedFilesRegistrationOptions cwf = new DidChangeWatchedFilesRegistrationOptions();
			List<FileSystemWatcher> watchers = new ArrayList<>();
			for (String suffix : suffixes) {
				watchers.add(new FileSystemWatcher("**/*" + suffix, 7));
			}
			cwf.setWatchers(watchers);
			registrations.add(new Registration(UUID.randomUUID().toString(), "workspace/didChangeWatchedFiles", cwf));
		}
		if (!registrations.isEmpty()) {
			capabilities = new RegistrationParams(registrations);
		}
	}

	@Override
	public void initialized(InitializedParams params) {
		super.initialized(params);
		backgroundExecutor.submit(this::handlePendingQueue);
		if (capabilities != null) {
			client.registerCapability(capabilities);
		}
	}

	@Override
	protected List<Diagnostic> getErrors(OberonFile of) throws InterruptedException, ExecutionException {

		class ExportInfo {
			private final int exportedPos, endPos, symbolIndex;
			private boolean used;

			private ExportInfo(int exportedPos, int endPos, int symbolIndex) {
				this.exportedPos = exportedPos;
				this.endPos = endPos;
				this.symbolIndex = symbolIndex;
			}
		}

		List<Diagnostic> errors = super.getErrors(of);
		of.setUnusedExportsFound(false);
		if (of.getCachedModuleName() == null)
			return errors;

		List<Diagnostic> exports = of.waitWhenDirty(backgroundExecutor, ar -> {
			Map<Integer, Integer> symRefs = new HashMap<>();
			for (Map.Entry<Integer, IdentifierReference> entry : ar.getExportedSymbolRefs().entrySet()) {
				if (entry.getValue().getModule().equals(ar.getModuleName())) {
					symRefs.put(entry.getValue().getEndPos(), entry.getKey());
				}
			}
			return ar.getIdDefinitions().values().stream()
					.filter(id -> id.isExported())
					.map(id -> new ExportInfo(id.getExportedPos(), id.getEndPos(), symRefs.getOrDefault(id.getEndPos(), id.getEndPos())))
					.collect(Collectors.toList());
		}).thenApply(eilist -> {
			List<Diagnostic> diags = new ArrayList<>();
			for (OberonFile of2 : allFiles(of.getCachedModuleName(), true)) {
				of2.waitToAddWhenDirty(diags, backgroundExecutor, ar2 -> {
					Map<Integer, List<Integer>> deps = ar2.getModuleDeps().get(of.getCachedModuleName());
					if (deps == null)
						return Collections.emptyList();
					for (ExportInfo ei : eilist) {
						if (deps.containsKey(ei.endPos) || deps.containsKey(ei.symbolIndex))
							ei.used = true;
					}
					return Collections.emptyList();
				});
			}
			for (ExportInfo ei : eilist) {
				if (!ei.used) {
					Diagnostic diag = new Diagnostic(new Range(of.getPos(ei.exportedPos - 1), of.getPos(ei.exportedPos)), OberonFile.UNUSED_EXPORT);
					diag.setSeverity(DiagnosticSeverity.Hint);
					diag.setTags(Arrays.asList(DiagnosticTag.Unnecessary));
					diags.add(diag);
				}
			}
			return diags;
		}).get();
		if (!exports.isEmpty()) {
			of.setUnusedExportsFound(true);
		}
		errors.addAll(exports);
		return errors;
	}

	@Override
	protected List<Either<Command, CodeAction>> fillCodeActions(CodeActionParams params, OberonFile of, AnalysisResult ar) {
		List<Either<Command, CodeAction>> actions = super.fillCodeActions(params, of, ar);
		JsonArray data = new JsonArray();
		data.add("REMOVE_EXPORT");
		data.add(of.getUri());
		addCodeAction(actions, params, ar, data, OberonFile.UNUSED_EXPORT, "export");
		return actions;
	}

	@Override
	protected CompletableFuture<CodeAction> fillResolvedCodeAction(CodeAction unresolved) {
		JsonArray data = (JsonArray) unresolved.getData();
		if (data.get(0).getAsString().equals("REMOVE_EXPORT")) {
			OberonFile of = fileForURI(data.get(1).getAsString());
			return of.waitWhenDirty(backgroundExecutor, ar -> {
				CodeAction resolved = new CodeAction(unresolved.getTitle());
				resolved.setIsPreferred(unresolved.getIsPreferred());
				resolved.setData(data);
				resolved.setDiagnostics(unresolved.getDiagnostics());
				resolved.setKind(unresolved.getKind());
				List<TextEdit> edits = new ArrayList<>();
				for (Diagnostic diag : unresolved.getDiagnostics()) {
					edits.add(new TextEdit(diag.getRange(), ""));
				}
				Map<String, List<TextEdit>> changes = new HashMap<>();
				changes.put(of.getUri(), edits);
				resolved.setEdit(new WorkspaceEdit(changes));
				return resolved;
			});
		}
		return super.fillResolvedCodeAction(unresolved);
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		CompletableFuture<Object> result = super.shutdown();
		for (OberonFile of : cachedFiles.values()) {
			of.waitWhenDirty(backgroundExecutor, ar -> {
				serialize(of, ar);
				return null;
			});
		}
		return result;
	}

	private void enqueuePendingUri(String uri) {
		synchronized (pendingUris) {
			pendingUris.add(uri);
			pendingUris.notifyAll();
		}
	}

	protected OberonFile fileForURI(String uri) {
		OberonFile result = super.fileForURI(uri);
		return result == null ? cachedFiles.get(uri) : result;
	}

	protected Collection<OberonFile> allFiles(String moduleNameHint, boolean includeDependencies) {
		Collection<OberonFile> result = super.allFiles(moduleNameHint, includeDependencies);
		Collection<OberonFile> cachedResult;
		if (moduleNameHint == null) {
			cachedResult = cachedFiles.values();
		} else if (includeDependencies) {
			cachedResult = cachedFilesByDepModule.get(moduleNameHint);
		} else {
			cachedResult = cachedFilesByModule.get(moduleNameHint);
		}
		if (cachedResult != null && !cachedResult.isEmpty()) {
			Set<String> resultURIs = result.stream().map(of -> of.getNormalizedUri()).collect(Collectors.toSet());
			result = new ArrayList<>(result);
			for (OberonFile of : cachedResult) {
				if (resultURIs.add(of.getNormalizedUri())) {
					result.add(of);
				}
			}
		}
		return result;
	}

	private void addToCache(String uri, OberonFile file) {
		cachedFiles.put(uri, file);
		synchronized (cachedFilesByModule) {
			List<OberonFile> oldList = cachedFilesByModule.get(file.getCachedModuleName());
			List<OberonFile> newList = oldList == null ? new ArrayList<>() : new ArrayList<>(oldList);
			newList.add(file);
			cachedFilesByModule.put(file.getCachedModuleName(), newList);
		}
		synchronized (cachedFilesByDepModule) {
			for (String dep : file.getCachedDependencies()) {
				List<OberonFile> oldList = cachedFilesByDepModule.get(dep);
				List<OberonFile> newList = oldList == null ? new ArrayList<>() : new ArrayList<>(oldList);
				newList.add(file);
				cachedFilesByDepModule.put(dep, newList);
			}
		}
	}

	private void removeFromCache(String uri) {
		OberonFile file = cachedFiles.remove(uri);
		if (file == null)
			return;
		synchronized (cachedFilesByModule) {
			for (String key : cachedFilesByModule.keySet()) {
				List<OberonFile> oldList = cachedFilesByModule.get(key);
				if (oldList.contains(file)) {
					List<OberonFile> newList = new ArrayList<>(oldList);
					newList.removeIf(f -> f.getNormalizedUri().equals(file.getNormalizedUri()));
					cachedFilesByModule.put(key, newList);
				}
			}
		}
		synchronized (cachedFilesByDepModule) {
			for (String key : cachedFilesByDepModule.keySet()) {
				List<OberonFile> oldList = cachedFilesByDepModule.get(key);
				if (oldList.contains(file)) {
					List<OberonFile> newList = new ArrayList<>(oldList);
					newList.removeIf(f -> f.getNormalizedUri().equals(file.getNormalizedUri()));
					cachedFilesByDepModule.put(key, newList);
				}
			}
		}
	}

	private void serialize(OberonFile file, AnalysisResult ar) {
		if (ar.isCached())
			return;
		File cacheFile = new File(cacheDir, OberonFile.hashText(file.getUri()) + ".cache");
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
			oos.writeObject(file.getUri());
			ar.prepareSerialize();
			oos.writeObject(ar);
			ar.afterDeserialize();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	protected void fileClosed(OberonFile file) {
		if (file != null) {
			if (!file.getUri().startsWith("file:")) {
				super.fileClosed(file);
				return;
			}
			file.waitWhenDirty(backgroundExecutor, ar -> {
				if (file.getCachedModuleName() != null) {
					serialize(file, ar);
					addToCache(file.getUri(), file);
				}
				return null;
			});
		}
	}

	@Override
	protected void updateDiagnostics(OberonFile of) throws ExecutionException, InterruptedException {
		super.updateDiagnostics(of);

		if (of.getCachedDependencies() != null) {
			for (String impMod : of.getCachedDependencies()) {
				if (!impMod.equals(of.getCachedModuleName())) {
					for (OberonFile dof : allFiles(impMod, false)) {
						if (impMod.equals(dof.getCachedModuleName()) && dof.isUnusedExportsFound()) {
							super.updateDiagnostics(dof);
						}
					}
				}
			}
		}
	}

	@Override
	public WorkspaceService getWorkspaceService() {
		return new OberonWorkspaceService() {

			@Override
			public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
				for (FileEvent evt : params.getChanges()) {
					if (!evt.getUri().startsWith("file:"))
						continue;
					enqueuePendingUri((evt.getType() == FileChangeType.Deleted ? "-" : "") + evt.getUri());
				}
			}

			@Override
			public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
				System.err.println("Change of workspace folders not supported, please reload!");
			}

			@Override
			public void didCreateFiles(CreateFilesParams params) {
				for (FileCreate evt : params.getFiles()) {
					if (!evt.getUri().startsWith("file:"))
						continue;
					enqueuePendingUri(evt.getUri());
				}
			}

			@Override
			public void didDeleteFiles(DeleteFilesParams params) {
				for (FileDelete evt : params.getFiles()) {
					if (!evt.getUri().startsWith("file:"))
						continue;
					enqueuePendingUri("-" + evt.getUri());
				}
			}

			@Override
			public void didRenameFiles(RenameFilesParams params) {
				for (FileRename evt : params.getFiles()) {
					if (evt.getOldUri().startsWith("file:"))
						enqueuePendingUri("-" + evt.getOldUri());
					if (evt.getNewUri().startsWith("file:"))
						enqueuePendingUri(evt.getNewUri());
				}
			}
		};
	}

	private void handlePendingQueue() {
		try {
			Thread.sleep(2000); // give some time for initialization
			int progressPos = 0, progressCount = 0;
			while (true) {
				String nextUri;
				synchronized (pendingUris) {
					while (pendingUris.isEmpty()) {
						pendingUris.wait();
					}
					if (progressPos == progressCount) {
						progressPos = 0;
						progressCount = pendingUris.size();
					}
					nextUri = pendingUris.remove(0);
				}
				sendProgress(progressPos, progressCount);
				if (nextUri.startsWith("-file:")) {
					String deleteUri = nextUri.substring(1);
					File deleteCacheFile = new File(cacheDir, OberonFile.hashText(deleteUri) + ".cache");
					if (deleteCacheFile.exists()) {
						deleteCacheFile.delete();
					}
					removeFromCache(deleteUri);
					PublishDiagnosticsParams diags = new PublishDiagnosticsParams(deleteUri, new ArrayList<>());
					client.publishDiagnostics(diags);
				} else if (nextUri.startsWith("file:")) {
					File file = new File(new URI(nextUri));
					if (file.exists()) {
						String content = Files.asCharSource(file, StandardCharsets.UTF_8).read();
						File cacheFile = new File(cacheDir, OberonFile.hashText(nextUri) + ".cache");
						OberonFile of = cachedFiles.get(nextUri);
						if (of != null) {
							of.setContent(content);
						} else if (cacheFile.exists()) {
							try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile))) {
								String uri = (String) ois.readObject();
								if (uri.equals(nextUri)) {
									AnalysisResult ar = (AnalysisResult) ois.readObject();
									ar.afterDeserialize();
									of = new OberonFile(uri, ar, content);
									if (!of.isDirty()) {
										updateDiagnostics(of);
									}
								}
							} catch (IOException ex) {
								ex.printStackTrace();
							}
						}
						if (of == null) {
							of = new OberonFile(nextUri, content);
						}
						if (of.isDirty()) {
							analyzeFile(of, of.getContentVersion());
						}
						if (of.getCachedModuleName() != null) {
							removeFromCache(nextUri);
							addToCache(nextUri, of);
						}
					}
				}
				progressPos++;
				sendProgress(progressPos, progressCount);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void sendProgress(int progressPos, int progressCount) throws InterruptedException, ExecutionException {
		if (progressCounter == -1)
			return;
		WorkDoneProgressNotification wdpn;
		if (progressPos == 0) {
			progressCounter++;
			client.createProgress(new WorkDoneProgressCreateParams(Either.forLeft("Indexing-" + progressCounter))).get();
			WorkDoneProgressBegin wdpb = new WorkDoneProgressBegin();
			wdpb.setPercentage(0);
			wdpb.setMessage(progressPos + "/" + progressCount);
			wdpb.setTitle("Indexing Oberon files");
			wdpb.setCancellable(false);
			wdpn = wdpb;
		} else if (progressPos == progressCount) {
			WorkDoneProgressEnd wdpe = new WorkDoneProgressEnd();
			wdpe.setMessage(progressPos + "/" + progressCount);
			wdpn = wdpe;
		} else {
			WorkDoneProgressReport wdpr = new WorkDoneProgressReport();
			wdpr.setCancellable(false);
			wdpr.setPercentage(progressPos * 100 / progressCount);
			wdpr.setMessage(progressPos + "/" + progressCount);
			wdpn = wdpr;
		}
		client.notifyProgress(new ProgressParams(Either.forLeft("Indexing-" + progressCounter), Either.forLeft(wdpn)));
	}

	private class WorkspaceFolderScanner {

		private final WorkspaceFolder wf;
		private final List<String> uris = new ArrayList<>();
		private File buildOrderFile = null;

		public WorkspaceFolderScanner(WorkspaceFolder wf) {
			this.wf = wf;
		}

		public void scan() {
			try {
				scanFiles(new File(new URI(wf.getUri())));
			} catch (URISyntaxException ex) {
				// ignore
			}
			if (buildOrderFile != null) {
				try {
					List<String> fileList = Files.readLines(buildOrderFile, StandardCharsets.UTF_8);
					Map<String, Integer> fileIndexMap = new HashMap<String, Integer>(), uriIndexMap = new HashMap<String, Integer>();
					for (int i = 0; i < fileList.size(); i++) {
						fileIndexMap.put(fileList.get(i).toLowerCase(), i);
					}
					for (String uri : uris) {
						uriIndexMap.put(uri, fileIndexMap.getOrDefault(uri.toLowerCase().replaceFirst("^.*/", ""), Integer.MAX_VALUE));
					}
					uris.sort(Comparator.comparing(uri -> uriIndexMap.get(uri)));
				} catch (IOException ex) {
					// continue without ordering the files
					ex.printStackTrace();
				}
			}
			for (String uri : uris) {
				enqueuePendingUri(uri);
			}
		}

		private boolean isRelevantSuffix(File file) {
			for (String suffix : suffixes) {
				if (file.getName().endsWith(suffix)) {
					return true;
				}
			}
			return false;
		}

		private void scanFiles(File dir) {
			if (dir != null && dir.isDirectory()) {
				for (File file : dir.listFiles()) {
					if (file.isDirectory()) {
						scanFiles(file);
					} else if (isRelevantSuffix(file)) {
						uris.add(file.toURI().toString());
					} else if (file.getName().equalsIgnoreCase("OberonBuildOrder.Tool")) {
						buildOrderFile = file;
					}
				}
			}
		}
	}
}