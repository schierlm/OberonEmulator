package lspserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolOptions;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple.Two;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.google.gson.JsonArray;

import lspserver.OberonFile.AnalysisResult;
import lspserver.OberonFile.Identifier;
import lspserver.OberonFile.IdentifierReference;
import lspserver.OberonFile.ModuleDepType;
import lspserver.OberonFile.ParamTag;
import lspserver.OberonFormatter.FormatTokenInfo;

public class Server implements LanguageServer, LanguageClientAware {
	protected LanguageClient client;
	protected final Bridge bridge;
	protected final Map<String, OberonFile> openFiles = new ConcurrentHashMap<>();
	protected final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();
	protected final Set<String> skippedFilenames = new HashSet<>();
	private final boolean debug;

	public Server(Bridge bridge, boolean debug) {
		this.bridge = bridge;
		this.debug = debug;
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		ServerCapabilities cap = new ServerCapabilities();
		fillCapabilities(cap, params);
		ServerInfo si = new ServerInfo("Oberon Language Server", "0.1");
		InitializeResult ir = new InitializeResult(cap, si);
		return CompletableFuture.completedFuture(ir);
	}

	protected void fillCapabilities(ServerCapabilities cap, InitializeParams params) {
		cap.setTextDocumentSync(TextDocumentSyncKind.Full);
		cap.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(new SemanticTokensLegend(Bridge.TOKEN_TYPES, Bridge.TOKEN_MODIFIERS), new SemanticTokensServerFull(false), false));
		cap.setDocumentSymbolProvider(new DocumentSymbolOptions("Oberon"));
		cap.setCompletionProvider(new CompletionOptions(false, null));
		cap.setDefinitionProvider(true);
		cap.setDocumentHighlightProvider(true);
		cap.setReferencesProvider(true);
		cap.setLinkedEditingRangeProvider(true);
		cap.setSignatureHelpProvider(new SignatureHelpOptions(Arrays.asList("("), Arrays.asList(",", ")")));
		cap.setFoldingRangeProvider(true);
		cap.setHoverProvider(true);
		cap.setRenameProvider(new RenameOptions(true));
		cap.setDocumentFormattingProvider(true);
		cap.setCallHierarchyProvider(true);
		CodeActionOptions cao = new CodeActionOptions(Arrays.asList(CodeActionKind.QuickFix));
		cao.setResolveProvider(true);
		cap.setCodeActionProvider(cao);
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		try {
			bridge.shutdown();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		for (OberonFile of : openFiles.values()) {
			fileClosed(of);
		}
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void exit() {
		System.exit(0);
	}

	protected OberonFile fileForURI(String uri) {
		return openFiles.get(uri);
	}

	protected Collection<OberonFile> allFiles(String moduleNameHint, boolean includeDependencies) {
		if (moduleNameHint == null) {
			return openFiles.values();
		} else if (includeDependencies) {
			return openFiles.values().stream().filter(of -> of.getCachedModuleName() == null || of.getCachedModuleName().equals(moduleNameHint) || of.getCachedDependencies() == null || of.getCachedDependencies().contains(moduleNameHint)).collect(Collectors.toList());
		} else {
			return openFiles.values().stream().filter(of -> of.getCachedModuleName() == null || of.getCachedModuleName().equals(moduleNameHint)).collect(Collectors.toList());
		}
	}

	protected void fileClosed(OberonFile file) {
		if (file != null) {
			PublishDiagnosticsParams diags = new PublishDiagnosticsParams(file.getUri(), new ArrayList<>());
			client.publishDiagnostics(diags);
		}
	}

	protected final boolean isSkipped(String uri) {
		return skippedFilenames.contains(uri.replaceFirst("^.*/", ""));
	}

	protected final IdentifierReference lookupSymbolRef(IdentifierReference definition) {
		while (definition != null && definition.getEndPos() < 0) {
			List<IdentifierReference> result = new ArrayList<>();
			IdentifierReference definition_ = definition;
			for (OberonFile dof : allFiles(definition.getModule(), false)) {
				dof.waitToAddWhenDirty(result, backgroundExecutor, dar -> {
					if (definition_.getModule().equals(dar.getModuleName())) {
						IdentifierReference lookup = dar.getExportedSymbolRefs().get(definition_.getEndPos());
						if (lookup != null)
							return Arrays.asList(lookup);
					}
					return Collections.emptyList();
				});
			}
			definition = result.size() == 1 ? result.get(0) : null;
		}
		return definition;
	}

	protected final Identifier findAt(SortedMap<Integer, Identifier> map, int pos) {
		SortedMap<Integer, Identifier> mapAfterPos = map.tailMap(pos);
		if (!mapAfterPos.isEmpty()) {
			int endPos = mapAfterPos.firstKey();
			Identifier id = map.get(endPos);
			if (id.getStartPos() <= pos && id.getEndPos() >= pos) {
				return id;
			}
		}
		return null;
	}

	protected final SortedMap<Integer, Identifier> findInRange(SortedMap<Integer, Identifier> map, int from, int to) {
		SortedMap<Integer, Identifier> result = new TreeMap<>();
		int pos = from - 1;
		while (pos <= to && !map.tailMap(pos + 1).isEmpty()) {
			pos = map.tailMap(pos + 1).firstKey();
			if (pos > to)
				break;
			Identifier id = map.get(pos);
			if (id.getStartPos() >= from && id.getEndPos() <= to)
				result.put(id.getEndPos(), id);
		}
		return result;
	}

	/**
	 * Find a function range.
	 *
	 * @param ranges
	 *            Function ranges
	 * @param pos
	 *            Position
	 * @return Array of
	 *         <li>{@code value[0]} = start,
	 *         <li>{@code value[1]} = defEndPos,
	 *         <li>{@code value[2]} = end.
	 */
	protected final int[] findFunction(SortedMap<Integer, int[]> ranges, int pos) {
		SortedMap<Integer, int[]> mapBeforePos = ranges.headMap(pos);
		int startPos = mapBeforePos.isEmpty() ? -1 : mapBeforePos.lastKey();
		while (startPos != -1) {
			int[] val = ranges.get(startPos);
			if (pos < val[1])
				return new int[] { startPos, val[0], val[1] };
			startPos = val.length == 2 ? -1 : val[2];
		}
		return null;
	}

	protected final int[] findRangeToDelete(Identifier id, AnalysisResult ar, Set<Integer> endPosOfDefinitionsToDelete) {
		if (id.getKind() == SymbolKind.Function) {
			int funcStart = ar.getFunctionRanges().headMap(id.getEndPos()).lastKey();
			int[] funcRange = ar.getFunctionRanges().get(funcStart);
			if (funcRange[0] != id.getEndPos())
				throw new NoSuchElementException();
			return new int[] { funcStart, funcRange[1] + 1 };
		} else {
			int declStart, declEnd;
			int[] defList = ar.getDefinitionLists().get(ar.getDefinitionLists().headMap(id.getEndPos()).lastKey());
			SortedMap<Integer, Integer> declMap = ar.getDeclarationBlocks().headMap(id.getEndPos());
			if (declMap.isEmpty()) {
				declStart = defList[0];
				declEnd = defList[2];
			} else {
				declStart = declMap.lastKey();
				declEnd = declMap.get(declStart);
				if (declStart > id.getStartPos() || declEnd < id.getEndPos()) {
					declStart = defList[0];
					declEnd = defList[2];
				}
			}
			SortedMap<Integer, Identifier> otherIDs = findInRange(ar.getIdDefinitions(), defList[0], defList[1]);
			if (!otherIDs.values().stream().allMatch(oid -> endPosOfDefinitionsToDelete.contains(oid.getEndPos()))) {
				// def list contains more variables, so surgically only remove
				// this one
				SortedMap<Integer, Identifier> laterIDs = otherIDs.tailMap(id.getEndPos() + 1);
				SortedMap<Integer, Identifier> earlierIDs = otherIDs.headMap(id.getStartPos() + 1);
				if (!earlierIDs.isEmpty() && !laterIDs.isEmpty() && endPosOfDefinitionsToDelete.contains(laterIDs.firstKey())) {
					laterIDs = Collections.emptySortedMap();
				}
				if (!laterIDs.isEmpty()) {
					// remove the part from start of this symbol to start of the
					// next symbol
					return new int[] { id.getStartPos(), laterIDs.get(laterIDs.firstKey()).getStartPos() };
				} else {
					// there must be a symbol before this one; so remove the
					// part from end of symbol before
					// to the end of this symbol
					return new int[] { earlierIDs.lastKey(), id.getEndPos() };
				}
			} else {
				// we need to check if other definition lists still exist
				boolean removeWholeDeclarationBlock = true;
				int pos = declStart;
				while (pos < declEnd && !ar.getDefinitionLists().tailMap(pos).isEmpty()) {
					pos = ar.getDefinitionLists().tailMap(pos).firstKey();
					if (pos >= declEnd)
						break;
					int[] otherList = ar.getDefinitionLists().get(pos);
					pos = otherList[2];
					SortedMap<Integer, Identifier> otherListIDs = findInRange(ar.getIdDefinitions(), otherList[0], otherList[1]);
					if (!otherListIDs.values().stream().allMatch(oid -> endPosOfDefinitionsToDelete.contains(oid.getEndPos()))) {
						removeWholeDeclarationBlock = false;
						break;
					}
				}
				if (removeWholeDeclarationBlock)
					return new int[] { declStart, declEnd };
				else
					return new int[] { defList[0], defList[2] };
			}
		}
	}

	protected void addCodeAction(List<Either<Command, CodeAction>> actions, CodeActionParams params, AnalysisResult ar, JsonArray data, List<Diagnostic> errors, String message, String what) {
		List<Diagnostic> relevantDiags = new ArrayList<>();
		for (Diagnostic diag : params.getContext().getDiagnostics()) {
			if (diag.getTags() != null && diag.getTags().contains(DiagnosticTag.Unnecessary) && diag.getMessage().equals(message)) {
				relevantDiags.add(diag);
			}
		}
		if (!relevantDiags.isEmpty()) {
			CodeAction ca = new CodeAction(relevantDiags.size() == 1 ? "Remove unused " + what : "Remove " + relevantDiags.size() + " unused " + what + "s");
			ca.setIsPreferred(true);
			ca.setData(data);
			ca.setDiagnostics(relevantDiags);
			ca.setKind(CodeActionKind.QuickFix);
			actions.add(Either.forRight(ca));
			List<Diagnostic> allDiags = errors.stream().filter(
					d -> d.getTags() != null && d.getTags().contains(DiagnosticTag.Unnecessary) &&
							d.getMessage().equals(message))
					.collect(Collectors.toList());
			if (allDiags.size() > 1) {
				ca = new CodeAction("Remove all unused " + what + "s");
				ca.setData(data);
				ca.setDiagnostics(allDiags);
				ca.setKind(CodeActionKind.QuickFix);
				actions.add(Either.forRight(ca));
			}
		}
	}

	protected List<Either<Command, CodeAction>> fillCodeActions(CodeActionParams params, OberonFile of, AnalysisResult ar, List<Diagnostic> errors) {
		JsonArray data = new JsonArray();
		data.add("REMOVE");
		data.add(of.getUri());
		List<Either<Command, CodeAction>> actions = new ArrayList<>();
		if (!ar.getDefinitionLists().isEmpty()) {
			addCodeAction(actions, params, ar, data, errors, OberonFile.UNUSED_DEFINITION, "definition");
			addCodeAction(actions, params, ar, data, errors, OberonFile.UNUSED_PARAM, "procedure parameter");
		}
		return actions;
	}

	protected CompletableFuture<CodeAction> fillResolvedCodeAction(CodeAction unresolved) {
		JsonArray data = (JsonArray) unresolved.getData();
		if (data.get(0).getAsString().equals("REMOVE")) {
			OberonFile of = fileForURI(data.get(1).getAsString());
			return of.waitWhenDirty(backgroundExecutor, ar -> {
				CodeAction resolved = new CodeAction(unresolved.getTitle());
				resolved.setIsPreferred(unresolved.getIsPreferred());
				resolved.setData(data);
				resolved.setDiagnostics(unresolved.getDiagnostics());
				resolved.setKind(unresolved.getKind());
				List<int[]> deletionRanges = new ArrayList<>();
				Set<Integer> deleteEndPositions = new HashSet<>();
				for (Diagnostic diag : unresolved.getDiagnostics()) {
					Identifier id = ar.getIdDefinitions().get(of.getRawPos(diag.getRange().getEnd()));
					if (id != null)
						deleteEndPositions.add(id.getEndPos());
				}
				for (Integer endPos : deleteEndPositions) {
					Identifier id = ar.getIdDefinitions().get(endPos);
					deletionRanges.add(findRangeToDelete(id, ar, deleteEndPositions));
				}
				deletionRanges.sort(Comparator.comparing((int[] e) -> e[0]));
				List<TextEdit> edits = new ArrayList<>();
				if (!deletionRanges.isEmpty()) {
					int[] currentRange = deletionRanges.get(0);
					for (int i = 1; i < deletionRanges.size(); i++) {
						int[] nextRange = deletionRanges.get(i);
						if (nextRange[0] > currentRange[1]) {
							edits.add(new TextEdit(new Range(of.getPos(currentRange[0]), of.getPos(currentRange[1])), ""));
							currentRange = nextRange;
						} else {
							currentRange[1] = Math.max(currentRange[1], nextRange[1]);
						}
					}
					edits.add(new TextEdit(new Range(of.getPos(currentRange[0]), of.getPos(currentRange[1])), ""));
				}
				Map<String, List<TextEdit>> changes = new HashMap<>();
				changes.put(of.getUri(), edits);
				resolved.setEdit(new WorkspaceEdit(changes));
				return resolved;
			});
		}
		return CompletableFuture.completedFuture(unresolved);
	}

	protected boolean allowGlobalRename() {
		return false;
	}

	@Override
	public TextDocumentService getTextDocumentService() {
		return new TextDocumentService() {

			@Override
			public void didSave(DidSaveTextDocumentParams params) {
			}

			@Override
			public void didOpen(DidOpenTextDocumentParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				if (of == null) {
					of = new OberonFile(params.getTextDocument().getUri(), params.getTextDocument().getText());
				} else {
					of.setContent(params.getTextDocument().getText());
				}
				openFiles.put(params.getTextDocument().getUri(), of);
				fileChanged(of);
			}

			@Override
			public void didClose(DidCloseTextDocumentParams params) {
				fileClosed(openFiles.remove(params.getTextDocument().getUri()));
			}

			@Override
			public void didChange(DidChangeTextDocumentParams params) {
				OberonFile of = openFiles.get(params.getTextDocument().getUri());
				if (of == null || isSkipped(of.getNormalizedUri()))
					return;
				if (params.getContentChanges().size() != 1)
					throw new IllegalArgumentException("Incremental changes not supported)");
				of.setContent(params.getContentChanges().get(0).getText());
				fileChanged(of);
			}

			@Override
			public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				if (of == null)
					return CompletableFuture.completedFuture(new ArrayList<>());
				return of.waitWhenDirty(backgroundExecutor, f -> new ArrayList<>(f.getOutline()));
			}

			@Override
			public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				if (of == null)
					return CompletableFuture.completedFuture(new SemanticTokens(Collections.emptyList()));
				return of.waitWhenDirty(backgroundExecutor, f -> new SemanticTokens(IntStream.of(f.getSemanticTokens()).mapToObj(i -> i).collect(Collectors.toList())));
			}

			@Override
			public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
				OberonFile of = fileForURI(position.getTextDocument().getUri());
				int pos = of.getRawPos(position.getPosition());
				if (of == null || pos == -1 || isSkipped(of.getNormalizedUri()))
					return CompletableFuture.completedFuture(Either.forLeft(new ArrayList<>()));
				String prefix = of.getContent().substring(0, pos);
				CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = new CompletableFuture<>();
				backgroundExecutor.submit(() -> {
					try {
						List<CompletionItem> completions = bridge.complete(prefix);
						result.complete(Either.forLeft(completions));
					} catch (Throwable ex) {
						ex.printStackTrace();
						result.completeExceptionally(ex);
					}
				});
				return result;
			}

			@Override
			public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				return of.waitWhenDirty(backgroundExecutor, ar -> {
					if (debug && params.getPosition().getLine() == 0 && params.getPosition().getCharacter() == 0) {
						// validate all references in this file!
						int[] errors = { 0 };
						for (Identifier id : ar.getIdDefinitions().values()) {
							if (id.getKind() == SymbolKind.Module) {
								continue;
							}
							try {
								findRangeToDelete(id, ar, new HashSet<Integer>(Arrays.asList(id.getEndPos())));
							} catch (RuntimeException ex) {
								System.err.println("Unable to find delete range for pos " + id.getEndPos());
								errors[0]++;
							}
						}
						for (Identifier id : ar.getIdReferences().values()) {
							String desc_ = id.getDefinition().toString();
							IdentifierReference definition = lookupSymbolRef(id.getDefinition());
							if (definition == null) {
								System.err.println(desc_ + ": symbol reference lookup failed");
								continue;
							} else if (!definition.equals(id.getDefinition())) {
								desc_ += "->" + definition;
							}
							desc_ += " '" + of.getContent().substring(id.getStartPos(), id.getEndPos()) + "'";
							String desc = desc_;
							final boolean[] found = { false };
							for (OberonFile dof : allFiles(definition.getModule(), false)) {
								try {
									dof.waitWhenDirty(backgroundExecutor, dar -> {
										if (dar.getModuleName().equals(definition.getModule())) {
											found[0] = true;
											if (!dar.getIdDefinitions().containsKey(definition.getEndPos())) {
												System.err.println(desc + ": identifier not found");
												errors[0]++;
											}
										}
										return null;
									}).get();
								} catch (InterruptedException | ExecutionException ex) {
								}
							}
							if (!found[0]) {
								System.err.println(desc + ": module not loaded");
								errors[0]++;
							}
						}
						System.err.println("Reference validation finished, " + errors[0] + " errors detected.");
					}
					int pos = of.getRawPos(params.getPosition());
					Identifier id = findAt(ar.getIdReferences(), pos);
					return id != null ? id.getDefinition() : null;
				}).thenApply((IdentifierReference sref) -> {
					List<Location> result = new ArrayList<>();
					if (sref != null) {
						IdentifierReference definition = lookupSymbolRef(sref);
						if (definition != null) {
							Location loc = buildLocation(of, of.getCachedModuleName(), definition);
							if (loc != null) {
								result.add(loc);
							}
						}
					}
					return Either.forLeft(result);
				});
			}

			private CallHierarchyItem buildCallHierarchyItem(OberonFile of, AnalysisResult ar, Identifier id) {
				CallHierarchyItem chi = new CallHierarchyItem();
				chi.setUri(of.getUri());
				chi.setName(of.getContent().substring(id.getStartPos(), id.getEndPos()));
				chi.setKind(id.getKind());
				chi.setData(new Two<>(ar.getModuleName(), id.getEndPos()));
				Range r = new Range(of.getPos(id.getStartPos()), of.getPos(id.getEndPos()));
				chi.setSelectionRange(r);
				if (id.getKind() == SymbolKind.Function) {
					int[] func = findFunction(ar.getFunctionRanges(), id.getEndPos());
					if (func != null && func[1] == id.getEndPos()) {
						r = new Range(of.getPos(func[0]), of.getPos(func[1]));
					}
				}
				chi.setRange(r);
				return chi;
			}

			private Location buildLocation(OberonFile baseFile, String baseModule, IdentifierReference definition) {
				String uri = null;
				for (OberonFile of : allFiles(definition.getModule(), false)) {
					try {
						Either<Location, String> loc = of.<Either<Location, String>> waitWhenDirty(backgroundExecutor, ar -> {
							if (ar.getModuleName().equals(definition.getModule())) {
								Identifier id = ar.getIdDefinitions().get(definition.getEndPos());
								if (id != null) {
									return Either.forLeft(new Location(of.getUri(), new Range(of.getPos(id.getStartPos()), of.getPos(id.getEndPos()))));
								}
								return Either.forRight(of.getUri());
							}
							return null;
						}).get();
						if (loc != null && loc.isLeft())
							return loc.getLeft();
						else if (loc != null && loc.isRight())
							uri = loc.getRight();
					} catch (InterruptedException | ExecutionException ex) {
					}
				}
				if (uri == null) {
					String baseUri = baseFile.getUri();
					int pos = baseUri.lastIndexOf("/" + baseModule + ".");
					if (pos != -1) {
						uri = baseUri.substring(0, pos + 1) + definition.getModule() + baseUri.substring(pos + baseModule.length() + 1);
					}
				}
				Location location = uri == null ? null : new Location(uri, new Range(new Position(0, 0), new Position(0, 0)));
				return location;
			}

			@Override
			public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
				return computeReferences(params.getTextDocument().getUri(), params.getPosition());
			}

			private CompletableFuture<List<? extends Location>> computeReferences(String uri, Position position){
				OberonFile of = fileForURI(uri);
				return of.waitWhenDirty(backgroundExecutor, ar -> {
					int pos = of.getRawPos(position);
					Identifier def = findAt(ar.getIdDefinitions(), pos);
					if (def != null) {
						return new IdentifierReference(def.isExported() ? ar.getModuleName() : "<local>", def.getEndPos());
					}
					Identifier ref = findAt(ar.getIdReferences(), pos);
					if (ref != null) {
						if (ref.getDefinition().getModule().equals(ar.getModuleName())) {
							if (!ar.getIdDefinitions().get(ref.getDefinition().getEndPos()).isExported() && ref.getDefinition().getEndPos() >= 0) {
								return new IdentifierReference("<local>", ref.getDefinition().getEndPos());
							}
						}
						return ref.getDefinition();
					}
					return (IdentifierReference) null;
				}).thenApply(ref -> {
					List<Location> result = new ArrayList<>();
					if (ref == null)
						return result;
					IdentifierReference pendingRef = lookupSymbolRef(ref);
					if (pendingRef == null)
						return result;
					List<IdentifierReference> pendingRefs = new ArrayList<>();
					pendingRefs.add(pendingRef);
					while (!pendingRefs.isEmpty()) {
						IdentifierReference reference = pendingRefs.remove(0);
						if (reference == null)
							continue;
						Collection<OberonFile> files;
						String defModuleLocal = reference.getModule();
						if (defModuleLocal.equals("<local>")) {
							defModuleLocal = of.getCachedModuleName();
							files = Arrays.asList(of);
						} else {
							files = allFiles(defModuleLocal, true);
						}
						final String defModule = defModuleLocal;
						for (OberonFile of2 : files) {
							of2.waitToAddWhenDirty(result, backgroundExecutor, ar2 -> {
								List<Location> locations = new ArrayList<>();
								if (ar2.getModuleName().equals(defModule)) {
									Identifier rid = findAt(ar2.getIdDefinitions(), reference.getEndPos());
									if (rid != null) {
										locations.add(new Location(of2.getUri(), new Range(of2.getPos(rid.getStartPos()), of2.getPos(rid.getEndPos()))));
									}
								}
								Map<ModuleDepType,Map<Integer, List<Integer>>> modRefs0 = ar2.getModuleDeps().get(defModule);
								Map<Integer, List<Integer>> modRefs = modRefs0 == null ? null : modRefs0.get(ModuleDepType.DEFINITION);
								if (modRefs != null) {
									List<Integer> refs = modRefs.get(reference.getEndPos());
									if (refs != null) {
										for (Integer rend : refs) {
											if (rend < 0) {
												pendingRefs.add(new IdentifierReference(ar2.getModuleName(), rend));
											} else {
												Identifier rid = ar2.getIdReferences().get(rend);
												if (rid != null) {
													locations.add(new Location(of2.getUri(), new Range(of2.getPos(rid.getStartPos()), of2.getPos(rid.getEndPos()))));
												}
											}
										}
									}
								}
								return locations;
							});
						}
					}
					return result;
				});
			}

			@Override
			public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				if (of == null)
					return CompletableFuture.completedFuture(new ArrayList<>());
				return of.waitWhenDirty(backgroundExecutor, ar -> {
					int pos = of.getRawPos(params.getPosition());
					IdentifierReference defReference;
					Identifier def = findAt(ar.getIdDefinitions(), pos);
					if (def != null) {
						defReference = new IdentifierReference(ar.getModuleName(), def.getEndPos());
					} else {
						Identifier ref = findAt(ar.getIdReferences(), pos);
						if (ref != null) {
							defReference = ref.getDefinition();
						} else {
							defReference = null;
						}
					}
					List<DocumentHighlight> result = new ArrayList<>();
					if (defReference == null)
						return result;

					if (ar.getModuleName().equals(defReference.getModule())) {
						Identifier rid = findAt(ar.getIdDefinitions(), defReference.getEndPos());
						if (rid != null) {
							result.add(new DocumentHighlight(new Range(of.getPos(rid.getStartPos()), of.getPos(rid.getEndPos())), DocumentHighlightKind.Read));
						}
					}
					Map<ModuleDepType,Map<Integer, List<Integer>>> modRefs0 = ar.getModuleDeps().get(defReference.getModule());
					Map<Integer, List<Integer>> modRefs = modRefs0 == null ? null : modRefs0.get(ModuleDepType.DEFINITION);

					if (modRefs != null) {
						List<Integer> refs = modRefs.get(defReference.getEndPos());
						if (refs != null) {
							for (Integer rend : refs) {
								if (rend < 0) {
									continue;
								}
								Identifier rid = ar.getIdReferences().get(rend);
								if (rid != null)
									result.add(new DocumentHighlight(new Range(of.getPos(rid.getStartPos()), of.getPos(rid.getEndPos())), rid.isWrittenTo() ? DocumentHighlightKind.Write : DocumentHighlightKind.Read));
							}
						}
					}
					return result;
				});
			}

			private CompletableFuture<List<? extends Location>> editingRanges(String uri, Position position, boolean global) {
				OberonFile of = fileForURI(uri);
				return of.waitWhenDirty(backgroundExecutor, ar -> {
					final List<Location> locations = new ArrayList<>();
					int pos = of.getRawPos(position);
					IdentifierReference defReference = null;
					Identifier definition = findAt(ar.getIdDefinitions(), pos);
					if (definition != null) {
						defReference = new IdentifierReference(ar.getModuleName(), definition.getEndPos());
					} else {
						Identifier ref = findAt(ar.getIdReferences(), pos);
						if (ref != null) {
							defReference = ref.getDefinition();
						}
					}
					if (defReference != null && defReference.getModule().equals(ar.getModuleName())) {
						if (ar.getIdReferences().containsKey(defReference.getEndPos())) {
							// do not allow to rename unaliased IMPORTs!
							return locations;
						}
						Identifier id = ar.getIdDefinitions().get(defReference.getEndPos());
						if (id.isExported()) {
							// do not allow to locally rename exported definitions
							if (global) {
								locations.add(new Location("<exported>", new Range(new Position(0, 0), new Position(0,0))));
							}
							return locations;
						}
						locations.add(new Location(of.getUri(),new Range(of.getPos(id.getStartPos()), of.getPos(id.getEndPos()))));
						Map<ModuleDepType,Map<Integer, List<Integer>>> modRefs0 = ar.getModuleDeps().get(defReference.getModule());
						Map<Integer, List<Integer>> modRefs = modRefs0 == null ? null : modRefs0.get(ModuleDepType.DEFINITION);

						if (modRefs != null) {
							List<Integer> refs = modRefs.get(defReference.getEndPos());
							if (refs != null) {
								for (Integer rend : refs) {
									if (rend < 0)
										continue;
									Identifier ref = ar.getIdReferences().get(rend);
									locations.add(new Location(of.getUri(), new Range(of.getPos(ref.getStartPos()), of.getPos(ref.getEndPos()))));
								}
							}
						}
					} else if (defReference != null && global) {
						// rename export from other module
						locations.add(new Location("<exported>", new Range(new Position(0, 0), new Position(0,0))));
					}
					return locations;
				}).thenApply(r -> {
					if (r.size() == 1 && r.get(0).getUri().equals("<exported>")) {
						try {
							return computeReferences(uri, position).get();
						} catch (InterruptedException|ExecutionException ex) {
							ex.printStackTrace();
							r.clear();
						}
					}
					return r;
				});
			}

			@Override
			public CompletableFuture<LinkedEditingRanges> linkedEditingRange(LinkedEditingRangeParams params) {
				return editingRanges(params.getTextDocument().getUri(), params.getPosition(), false)
						.thenApply(r -> new LinkedEditingRanges(r.stream().map(l -> l.getRange())
								.collect(Collectors.toList())));
			}

			private boolean positionBefore(Position p1, Position p2) {
				return p1.getLine() < p2.getLine() || (p1.getLine() == p2.getLine() && p1.getCharacter() <= p2.getCharacter());
			}


			@Override
			public CompletableFuture<Either<Range, PrepareRenameResult>> prepareRename(PrepareRenameParams params) {
				return editingRanges(params.getTextDocument().getUri(), params.getPosition(), allowGlobalRename()).thenApply(ls -> {
					for (Location l : ls) {
						if (l.getUri().equals(params.getTextDocument().getUri()) && positionBefore(l.getRange().getStart(), params.getPosition()) && positionBefore(params.getPosition(), l.getRange().getEnd()))
							return Either.forLeft(l.getRange());
					}
					return null;
				});
			}

			@Override
			public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
				return editingRanges(params.getTextDocument().getUri(), params.getPosition(), allowGlobalRename()).thenApply(ls -> {
					Map<String, List<TextEdit>> changes = new HashMap<>();
					for (Location l : ls) {
						changes.computeIfAbsent(l.getUri(), x -> new ArrayList<>()).add(new TextEdit(l.getRange(), params.getNewName()));
					}
					return new WorkspaceEdit(changes);
				});
			}

			@Override
			public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				CompletableFuture<SignatureHelp> result = new CompletableFuture<>();
				backgroundExecutor.submit(new Callable<Void>() {
					public Void call() throws Exception {
						Thread.sleep(200);
						of.waitWhenDirty(backgroundExecutor, ar -> {
							int paramIndex = 0, startPos = -1;
							{
								SortedMap<Integer, ParamTag> tagsBefore = ar.getParamTags().headMap(of.getRawPos(params.getPosition()) + 1);
								int depth = 0;
								loop: while (!tagsBefore.isEmpty()) {
									int p = tagsBefore.lastKey();
									ParamTag t = tagsBefore.get(p);
									tagsBefore = tagsBefore.headMap(p);
									switch (t) {
									case CALL_START:
										if (depth == 0) {
											startPos = p - 1;
											break loop;
										}
										depth--;
										break;
									case END:
										depth++;
										break;
									case NEXT:
										if (depth == 0)
											paramIndex++;
										break;
									case END_LAST:
									case PROC_START:
									default:
										break loop;
									}
								}
							}
							if (startPos == -1)
								return new Two<Integer, IdentifierReference>(paramIndex, null);
							while (startPos > 0 && of.getContent().charAt(startPos - 1) <= ' ')
								startPos--;
							Identifier refid = ar.getIdReferences().get(startPos);
							return new Two<>(paramIndex, refid.getDefinition());
						}).thenApply(pair -> {
							final int paramIndex_ = pair.getFirst();
							IdentifierReference ref = lookupSymbolRef(pair.getSecond());
							if (ref == null)
								return null;
							for (OberonFile of2 : allFiles(ref.getModule(), false)) {
								try {
									SignatureHelp sh = of2.waitWhenDirty(backgroundExecutor, ar2 -> {
										if (ar2.getModuleName().equals(ref.getModule())) {
											Identifier id = ar2.getIdDefinitions().get(ref.getEndPos());
											if (id != null) {
												int ssPos = id.getStartPos();
												SortedMap<Integer, ParamTag> tagsAfter = ar2.getParamTags().tailMap(ssPos);
												int p = tagsAfter.firstKey();
												ParamTag t = tagsAfter.get(p);
												if (t != ParamTag.PROC_START)
													return null;
												int psPos = p;
												tagsAfter = tagsAfter.tailMap(p + 1);
												List<ParameterInformation> pis = new ArrayList<>();
												while (!tagsAfter.isEmpty()) {
													p = tagsAfter.firstKey();
													t = tagsAfter.get(p);
													tagsAfter = tagsAfter.tailMap(p + 1);
													if (t != ParamTag.NEXT && t != ParamTag.END && t != ParamTag.END_LAST)
														return null;
													ParameterInformation pi = new ParameterInformation();
													pi.setLabel(new Two<Integer, Integer>(psPos - ssPos, p - 1 - ssPos));
													pis.add(pi);
													psPos = p;
													if (t != ParamTag.NEXT)
														break;
												}
												SignatureInformation si = new SignatureInformation(of2.getContent().substring(ssPos, psPos));
												si.setParameters(pis);
												return new SignatureHelp(Arrays.asList(si), 0, paramIndex_);
											}
										}
										return null;
									}).get();
									if (sh != null)
										return sh;
								} catch (InterruptedException | ExecutionException ex) {
								}
							}
							return null;
						}).thenApply(r -> result.complete(r));
						return null;
					}
				});
				return result;
			}

			@Override
			public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				if (of == null)
					return CompletableFuture.completedFuture(new ArrayList<>());
				return of.waitWhenDirty(backgroundExecutor, ar -> {
					List<FoldingRange> result = new ArrayList<>();
					Map<Integer, Integer> lastLineForOuterFunc = new HashMap<>();
					for (Map.Entry<Integer, int[]> func : ar.getFunctionRanges().entrySet()) {
						int outerFunc = func.getValue().length > 2 ? func.getValue()[2] : -1;
						int lastLine = lastLineForOuterFunc.getOrDefault(outerFunc, -1);
						int startLine = of.getPos(func.getKey()).getLine();
						int endLine = of.getPos(func.getValue()[1]).getLine();
						if (startLine > lastLine) {
							result.add(new FoldingRange(startLine, endLine));
							lastLineForOuterFunc.put(outerFunc, endLine);
						}
					}
					return result;
				});
			}

			@Override
			public CompletableFuture<Hover> hover(HoverParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				if (of == null)
					return CompletableFuture.completedFuture(null);
				return of.waitWhenDirty(backgroundExecutor, ar -> {
					int pos = of.getRawPos(params.getPosition());
					Identifier def = findAt(ar.getIdDefinitions(), pos);
					if (def != null) {
						return new Two<>(def, new IdentifierReference(ar.getModuleName(), def.getEndPos()));
					}
					Identifier ref = findAt(ar.getIdReferences(), pos);
					if (ref != null) {
						return new Two<>(ref, ref.getDefinition());
					}
					return new Two<>((Identifier) null, (IdentifierReference) null);
				}).thenApply(pair -> {
					Identifier id = pair.getFirst();
					IdentifierReference def = lookupSymbolRef(pair.getSecond());
					List<Hover> hovers = new ArrayList<>();
					if (def != null) {
						for (OberonFile of2 : allFiles(def.getModule(), false)) {
							of2.waitToAddWhenDirty(hovers, backgroundExecutor, ar2 -> {
								if (ar2.getModuleName() != null && ar2.getModuleName().equals(def.getModule())) {
									Identifier rid = findAt(ar2.getIdDefinitions(), def.getEndPos());
									if (rid != null) {
										String content = of2.getContent();
										int startPos = rid.getStartPos();
										while (startPos > 0 && content.charAt(startPos - 1) != '\n')
											startPos--;
										int endPos = rid.getEndPos();
										while (endPos < content.length() && content.charAt(endPos) != '\n')
											endPos++;
										return Arrays.asList(new Hover(new MarkupContent(MarkupKind.PLAINTEXT, of2.getContent().substring(startPos, endPos)), new Range(of.getPos(id.getStartPos()), of.getPos(id.getEndPos()))));
									}
								}
								return Collections.emptyList();
							});
						}
					}
					if (!hovers.isEmpty()) {
						Hover hover = hovers.get(0);
						hover.setRange(new Range(of.getPos(id.getStartPos()), of.getPos(id.getEndPos())));
						return hover;
					}
					return null;
				});
			}

			@Override
			public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				if (of == null || isSkipped(of.getNormalizedUri()))
					return CompletableFuture.completedFuture(new ArrayList<>());
				String content = of.getContent();
				Range fullRange = new Range(of.getPos(0), of.getPos(of.getContent().length()));
				CompletableFuture<List<? extends TextEdit>> result = new CompletableFuture<>();
				backgroundExecutor.submit(() -> {
					String newText;
					try {
						List<FormatTokenInfo> tokens = bridge.format(content);
						OberonFormatter ofo = new OberonFormatter();
						int pos = 0;
						for (FormatTokenInfo token : tokens) {
							if (pos < token.getStartPos()) {
								ofo.appendWhitespace(content.substring(pos, token.getStartPos()));
							}
							ofo.appendToken(content.substring(token.getStartPos(), token.getEndPos()), token);
							pos = token.getEndPos();
						}
						if (pos < content.length() && content.charAt(pos) == '.') {
							FormatTokenInfo endToken = new FormatTokenInfo(pos, pos+1, 00);
							ofo.appendToken(content.substring(pos, pos+1), endToken);
							pos++;
						}
						if (pos < content.length())
							ofo.appendWhitespace(content.substring(pos));
						newText = ofo.getResult();
					} catch (Exception ex) {
						ex.printStackTrace();
						result.complete(new ArrayList<>());
						return;
					}
					result.complete(Arrays.asList(new TextEdit(fullRange, newText)));
				});
				return result;
			}

			@Override
			public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				return of.waitWhenDirty(backgroundExecutor, (Function<AnalysisResult, Either<List<CallHierarchyItem>, IdentifierReference>>) ar -> {
					int pos = of.getRawPos(params.getPosition());
					Identifier id = findAt(ar.getIdDefinitions(), pos);
					IdentifierReference def;
					if (id != null) {
						def = new IdentifierReference(ar.getModuleName(), id.getEndPos());
					} else {
						id = findAt(ar.getIdReferences(), pos);
						if (id != null) {
							def = id.getDefinition();
						} else {
							int[] func = findFunction(ar.getFunctionRanges(), pos);
							if (func != null) {
								id = ar.getIdDefinitions().get(func[1]);
								if (id != null) {
									def = new IdentifierReference(ar.getModuleName(), func[1]);
								} else {
									return Either.forLeft(Collections.emptyList());
								}
							} else {
								return Either.forLeft(Collections.emptyList());
							}
						}
					}
					if (def == null || !def.getModule().equals(ar.getModuleName())) {
						return Either.forRight(def);
					}
					CallHierarchyItem chi = buildCallHierarchyItem(of, ar, id);
					return Either.forLeft(Arrays.asList(chi));
				}).thenApply((Either<List<CallHierarchyItem>, IdentifierReference> either) -> {
					if (either.isLeft())
						return either.getLeft();
					IdentifierReference ref = lookupSymbolRef(either.getRight());
					List<CallHierarchyItem> result = new ArrayList<>();
					if (ref == null)
						return result;
					for (OberonFile of2 : allFiles(ref.getModule(), false)) {
						of2.waitToAddWhenDirty(result, backgroundExecutor, ar2 -> {
							CallHierarchyItem chi = null;
							if (ar2.getModuleName().equals(ref.getModule())) {
								Identifier id = ar2.getIdDefinitions().get(ref.getEndPos());
								if (id != null)
									chi = buildCallHierarchyItem(of2, ar2, id);
							}
							return chi == null ? Collections.emptyList() : Arrays.asList(chi);
						});
					}
					return result;
				});
			}

			@Override
			public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams params) {
				JsonArray details = (JsonArray) params.getItem().getData();
				String defModule = details.get(0).getAsString();
				int defEndPos = details.get(1).getAsInt();
				CompletableFuture<List<CallHierarchyIncomingCall>> ret = new CompletableFuture<>();
				backgroundExecutor.submit(() -> {
					List<CallHierarchyIncomingCall> result = new ArrayList<>();
					for (OberonFile of : allFiles(defModule, true)) {
						of.waitToAddWhenDirty(result, backgroundExecutor, ar -> {
							Map<Integer, List<Range>> functionCallRanges = new TreeMap<>();
							Map<ModuleDepType,Map<Integer, List<Integer>>> modRefs0 = ar.getModuleDeps().get(defModule);
							Map<Integer, List<Integer>> modRefs = modRefs0 == null ? null : modRefs0.get(ModuleDepType.DEFINITION);
							if (modRefs != null) {
								List<Integer> rends = modRefs.get(defEndPos);
								if (rends != null) {
									for (Integer rend : rends) {
										if (rend < 0)
											continue;
										Identifier rid = ar.getIdReferences().get(rend);
										if (rid == null)
											continue;
										int[] func = findFunction(ar.getFunctionRanges(), rend);
										if (func != null) {
											functionCallRanges.computeIfAbsent(func[1], x -> new ArrayList<>()).add(new Range(of.getPos(rid.getStartPos()), of.getPos(rid.getEndPos())));
										}
									}
								}
							}
							List<CallHierarchyIncomingCall> calls = new ArrayList<>();
							for (int funcPos : functionCallRanges.keySet()) {
								Identifier id = ar.getIdDefinitions().get(funcPos);
								calls.add(new CallHierarchyIncomingCall(buildCallHierarchyItem(of, ar, id), functionCallRanges.get(funcPos)));
							}
							return calls;
						});
					}
					ret.complete(result);
				});
				return ret;
			}

			@Override
			public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams params) {
				JsonArray details = (JsonArray) params.getItem().getData();
				String defModule = details.get(0).getAsString();
				int defEndPos = details.get(1).getAsInt();
				CompletableFuture<List<CallHierarchyOutgoingCall>> ret = new CompletableFuture<>();
				backgroundExecutor.submit(() -> {
					List<Map.Entry<String, Map<Integer, List<Range>>>> tempResult = new ArrayList<>();
					for (OberonFile of : allFiles(defModule, false)) {
						of.waitToAddWhenDirty(tempResult, backgroundExecutor, ar -> {
							Map<String, Map<Integer, List<Range>>> functionRefRanges = new TreeMap<>();
							if (ar.getModuleName().equals(defModule)) {
								Identifier rid = findAt(ar.getIdDefinitions(), defEndPos);
								if (rid != null && rid.getKind() == SymbolKind.Function) {
									int[] func = findFunction(ar.getFunctionRanges(), defEndPos);
									if (func != null && func[1] == defEndPos) {
										for (Identifier ref : ar.getIdReferences().subMap(defEndPos, func[2]).values()) {
											if (ref.getKind() == SymbolKind.Function) {
												IdentifierReference def = lookupSymbolRef(ref.getDefinition());
												if (def != null) {
													functionRefRanges.computeIfAbsent(def.getModule(), x -> new HashMap<>()).computeIfAbsent(def.getEndPos(), x -> new ArrayList<>()).add(new Range(of.getPos(ref.getStartPos()), of.getPos(ref.getEndPos())));
												}
											}
										}
									}
								}
							}
							return new ArrayList<>(functionRefRanges.entrySet());
						});
					}
					List<CallHierarchyOutgoingCall> result = new ArrayList<>();
					for (Map.Entry<String, Map<Integer, List<Range>>> entry : tempResult) {
						for (OberonFile of : allFiles(entry.getKey(), false)) {
							of.waitToAddWhenDirty(result, backgroundExecutor, ar -> {
								List<CallHierarchyOutgoingCall> calls = new ArrayList<>();
								if (ar.getModuleName().equals(entry.getKey())) {
									for (Map.Entry<Integer, List<Range>> positions : entry.getValue().entrySet()) {
										Identifier id = ar.getIdDefinitions().get(positions.getKey());
										calls.add(new CallHierarchyOutgoingCall(buildCallHierarchyItem(of, ar, id), positions.getValue()));
									}
								}
								return calls;
							});
						}
					}
					ret.complete(result);
				});
				return ret;
			}

			@Override
			public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
				OberonFile of = fileForURI(params.getTextDocument().getUri());
				List<Diagnostic> errors;
				try {
					errors = getErrors(of, false);
				} catch (InterruptedException | ExecutionException ex) {
					ex.printStackTrace();
					errors = new ArrayList<>();
				}
				List<Diagnostic> errors0 = errors;
				return of.waitWhenDirty(backgroundExecutor, ar -> {
					return fillCodeActions(params, of, ar, errors0);
				});
			}

			@Override
			public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
				return fillResolvedCodeAction(unresolved);
			}
		};
	}

	@Override
	public WorkspaceService getWorkspaceService() {
		return new OberonWorkspaceService();
	}

	private void fileChanged(OberonFile oberonFile) {
		int version = oberonFile.getContentVersion();
		if (isSkipped(oberonFile.getNormalizedUri())) {
			oberonFile.analyzeSkipped();
			return;
		}
		backgroundExecutor.submit(() -> {
			try {
				Thread.sleep(150);
				analyzeFile(oberonFile, version);
			} catch (Throwable ex) {
				ex.printStackTrace();
				client.logMessage(new MessageParams(MessageType.Error, "Exception: " + ex.toString()));
			}
		});
	}

	protected void updateDiagnostics(OberonFile of) throws ExecutionException, InterruptedException {
		List<Diagnostic> errors = getErrors(of, true);
		if (errors != null) {
			PublishDiagnosticsParams diags = new PublishDiagnosticsParams(of.getUri(), errors);
			client.publishDiagnostics(diags);
		}
	}

	protected void analyzeFile(OberonFile oberonFile, int version) throws IOException, InterruptedException, ExecutionException {

		class PendingFile {
			private final OberonFile file;
			private final int version;
			private final boolean wasSymbolChange;

			PendingFile(OberonFile file, int version, boolean wasSymbolChange) {
				this.file = file;
				this.version = version;
				this.wasSymbolChange = wasSymbolChange;
			}
		}

		List<PendingFile> filesToUpdate = new ArrayList<>();
		filesToUpdate.add(new PendingFile(oberonFile, version, false));
		Set<String> fileUrisToUpdate = new HashSet<>(), moduleNames2Update = new HashSet<>();
		Map<String,String> fileUri2ModuleNames = new HashMap<String,String>();
		fileUrisToUpdate.add(oberonFile.getUri());
		String oberonFileModuleName = oberonFile.getCachedModuleName();
		if (oberonFileModuleName != null) {
			moduleNames2Update.add(oberonFileModuleName);
			fileUri2ModuleNames.put(oberonFile.getUri(), oberonFileModuleName);
		}
		int skipCount = 0;
		fileloop: while (!filesToUpdate.isEmpty()) {
			PendingFile pf = filesToUpdate.remove(0);
			OberonFile of = pf.file;
			if (skipCount > filesToUpdate.size()) {
				System.err.println("Cyclic module dependencies detected, switching to brute force compilation order!");
			} else if (of.getCachedModuleName() != null && of.getCachedDependencies() != null) {
				for (String dep : of.getCachedDependencies()) {
					if (!dep.equals(of.getCachedModuleName()) && moduleNames2Update.contains(dep)) {
						skipCount++;
						filesToUpdate.add(pf);
						continue fileloop;
					}
				}
			}
			skipCount = 0;
			fileUrisToUpdate.remove(of.getUri());
			if (fileUri2ModuleNames.containsKey(of.getUri())) {
				moduleNames2Update.remove(fileUri2ModuleNames.remove(of.getUri()));
			}
			String symbolsChangedModule = null;
			synchronized (of) {
				if (pf.version != of.getContentVersion())
					continue;
				if (of.isDirty()) {
					symbolsChangedModule = of.analyzeBy(bridge) || pf.wasSymbolChange ? of.getCachedModuleName() : null;
					of.setDirty(false);
				}
			}
			if (!of.isDirty()) {
				updateDiagnostics(of);
			}
			if (symbolsChangedModule != null) {
				final String symbolsChangedModule_ = symbolsChangedModule;
				for (OberonFile of2 : allFiles(symbolsChangedModule, true)) {
					synchronized (of2) {
						if (of2.isDirty())
							continue;
						of2.waitWhenDirty(backgroundExecutor, ar2 -> {
							if (of2 != of && ar2.getModuleDeps().containsKey(symbolsChangedModule_) && fileUrisToUpdate.add(of2.getUri())) {
								of2.setDirty(true);
								filesToUpdate.add(new PendingFile(of2, of2.getContentVersion(), true));
								String of2CachedModuleName = of2.getCachedModuleName();
								if (of2CachedModuleName != null) {
									moduleNames2Update.add(of2CachedModuleName);
									fileUri2ModuleNames.put(of2.getUri(), of2CachedModuleName);
								}
							}
							return null;
						}).get();
					}
				}
			}
		}
		client.refreshSemanticTokens();
		if (!fileUrisToUpdate.isEmpty() || !moduleNames2Update.isEmpty())
			throw new IllegalStateException("Not processed: Uris: " + fileUrisToUpdate + " / Module names: " + moduleNames2Update);
	}

	protected List<Diagnostic> getErrors(OberonFile of, boolean noblock) throws InterruptedException, ExecutionException {
		// be careful here and watch out for deadlocks locking two different
		// OberonFile instances; do not lock ANYTHING but return null when noblock is set
		Two<List<Diagnostic>, Range> pair = of.waitOrSkipWhenDirty(backgroundExecutor, ar -> {
			Range range = new Range(new Position(0, 0), new Position(0, 0));
			Identifier moduleDef = ar.getIdDefinitions().get(1);
			if (moduleDef != null && moduleDef.getKind() == SymbolKind.Module) {
				range = new Range(of.getPos(moduleDef.getStartPos()), of.getPos(moduleDef.getEndPos()));
			}
			return new Two<>(ar.getErrors(), range);
		}, noblock ? new Two<List<Diagnostic>,Range>(null, null) : null).get();
		if (pair.getSecond() == null)
			return null;
		List<Diagnostic> result = new ArrayList<>(pair.getFirst());
		if (of.getCachedModuleName() != null) {
			if (allFiles(of.getCachedModuleName(), false).stream().anyMatch(of2 -> of.getCachedModuleName().equals(of2.getCachedModuleName()) && !of.getNormalizedUri().equals(of2.getNormalizedUri()))) {
				result.add(new Diagnostic(pair.getSecond(), "Same module name used by multiple files"));
			}
		}
		return result;
	}

	@Override
	public void connect(LanguageClient client) {
		this.client = client;
	}

	protected static class OberonWorkspaceService implements WorkspaceService {
		@Override
		public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		}

		@Override
		public void didChangeConfiguration(DidChangeConfigurationParams params) {
		}

		@Override
		public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		}
	}
}
