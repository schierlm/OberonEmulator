package lspserver;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class OberonFile {

	public static final String UNUSED_PARAM = "Unused procedure parameter";
	public static final String UNUSED_DEFINITION = "Unused definition";
	public static final String UNUSED_EXPORT = "Unused export";
	public static final String UNUSED_COMMAND = "Unused command";

	private final String uri, normalizedUri;
	private boolean dirty, unusedExportsFound;
	private int contentVersion = 0;
	private String content;
	private String cachedModuleName;
	private List<String> cachedDependencies;
	private SortedMap<Integer,Integer> lineByOffset = new TreeMap<>();
	private final AtomicInteger pendingContentChanges = new AtomicInteger();

	private AnalysisResult analysisResult = new AnalysisResult();

	public static String toHex(byte[] digest) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < digest.length; i++) {
			sb.append(String.format("%02x", digest[i] & 0xff));
		}
		return sb.toString();
	}

	public static String hashText(String text) {
		try {
			return toHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8")));
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
			throw new RuntimeException(ex);
		}
	}

	public OberonFile(String uri, String content) {
		this.uri = uri;
		this.normalizedUri = normalizeURI(uri);
		this.content=content+" ";
		setContent(content);
	}

	public OberonFile(String uri, AnalysisResult cachedResult, String content) {
		this.uri = uri;
		this.normalizedUri = normalizeURI(uri);
		this.analysisResult = cachedResult;
		setContent(content);
		cachedModuleName = analysisResult.getModuleName();
		cachedDependencies = new ArrayList<>();
		cachedDependencies.addAll(analysisResult.getModuleDeps().keySet());
		setDirty(!cachedResult.getContentHash().equals(hashText(getContent())));
	}

	private String normalizeURI(String uriString) {
		try {
			URI uri = new URI(uriString);
			if (uri.getScheme().equalsIgnoreCase("file"))
				uri = new File(uri).getCanonicalFile().toURI();
			return uri.toString();
		} catch (URISyntaxException | IOException ex) {
			return uriString;
		}
	}

	public synchronized String getContent() {
		return content;
	}

	public void setContent(String content) {
		pendingContentChanges.incrementAndGet();
		synchronized(this) {
			 String newContent = content.replace("\r\n", "\n").replace('\r', '\n');
			 if (!newContent.equals(this.content))
				 setDirty(true);
			 this.content = newContent;
			 this.cachedModuleName = null;
			 this.cachedDependencies = null;
			 this.lineByOffset.clear();
			 contentVersion++;
		}
		if (pendingContentChanges.decrementAndGet() == 0) {
			synchronized(this) {
				notifyAll();
			}
		}
	}

	public synchronized String getCachedModuleName() {
		return cachedModuleName;
	}

	public synchronized List<String> getCachedDependencies() {
		return cachedDependencies;
	}

	public boolean isDirty() {
		return dirty;
	}

	public synchronized void setDirty(boolean dirty) {
		if (!dirty && pendingContentChanges.get() != 0)
			return;
		this.dirty = dirty;
		if (!dirty)
			notifyAll();
	}

	public synchronized int getContentVersion() {
		return contentVersion;
	}

	public String getUri() {
		return uri;
	}

	public String getNormalizedUri() {
		return normalizedUri;
	}

	public AnalysisResult getRawAnalysisResult() {
		return analysisResult;
	}

	public boolean isUnusedExportsFound() {
		return unusedExportsFound;
	}

	public void setUnusedExportsFound(boolean unusedExportsFound) {
		this.unusedExportsFound = unusedExportsFound;
	}

	public synchronized Position getPos(int pos) {
		if (pos < 0 || pos > content.length()) {
			System.err.println("pos out of range: "+pos);
			pos = pos < 0 ? 0 : content.length();
		}
		int line = 0, character = 0, startOffset = 0;
		SortedMap<Integer, Integer> headMap = lineByOffset.headMap(pos);
		if (!headMap.isEmpty()) {
			int lastKey = headMap.lastKey();
			line = lineByOffset.get(lastKey);
			startOffset = lastKey;
		}
		for(int i = startOffset; i<pos; i++) {
			character++;
			if(content.charAt(i) == '\n') {
				line++;
				character = 0;
				if (line % 10 == 0) {
					lineByOffset.put(i+1, line);
				}
			}
		}
		return new Position(line, character);
	}

	public synchronized int getRawPos(Position position) {
		String doc = getContent();
		int line = position.getLine(), character = position.getCharacter();
		for(int i = 0; i < doc.length(); i++) {
			if (line == 0 && character == 0) {
				return i;
			} else if (line == 0) {
				if (doc.charAt(i) == '\n')
					return -1;
				character--;
			} else if (doc.charAt(i) == '\n') {
				line--;
			}
		}
		return -1;
	}

	public <T> CompletableFuture<T> waitWhenDirty(ExecutorService exec, Function<AnalysisResult,T> lambda) {
		return waitOrSkipWhenDirty(exec, lambda, null);
	}

	public <T> CompletableFuture<T> waitOrSkipWhenDirty(ExecutorService exec, Function<AnalysisResult,T> lambda, T skipResult) {
		synchronized(this) {
			if (!isDirty() && pendingContentChanges.get() == 0) return CompletableFuture.completedFuture(lambda.apply(analysisResult));
		}
		if (skipResult != null)
			return CompletableFuture.completedFuture(skipResult);
		CompletableFuture<T> result = new CompletableFuture<>();
		exec.submit(() -> {
			try {
				T value;
				synchronized(this) {
					while (isDirty() || pendingContentChanges.get() != 0)
						wait();
					value = lambda.apply(analysisResult);
				}
				result.complete(value);
			} catch (Exception ex) {
				result.completeExceptionally(ex);
			}
		});
		return result;
	}

	public <T> void waitToAddWhenDirty(List<T> result, ExecutorService exec, Function<AnalysisResult,List<T>> lambda) {
		waitOrSkipToAddWhenDirty(result, exec, lambda, false);
	}

	public <T> void waitOrSkipToAddWhenDirty(List<T> result, ExecutorService exec, Function<AnalysisResult,List<T>> lambda, boolean skip) {
		try {
			List<T> elems = waitOrSkipWhenDirty(exec, lambda, skip ? new ArrayList<T>() : null).get();
			result.addAll(elems);
		} catch (InterruptedException | ExecutionException ex) {
		}
	}


	/** Return this module's name in case its symbols changed, else {@code null}. */
	public synchronized boolean analyzeBy(Bridge bridge) throws IOException {
		boolean result =  bridge.analyze(this, analysisResult);
		cachedModuleName = analysisResult.getModuleName();
		cachedDependencies = new ArrayList<>();
		cachedDependencies.addAll(analysisResult.getModuleDeps().keySet());
		return result;
	}

	public static enum ParamTag { PROC_START, CALL_START, NEXT, END, END_LAST }

	public static enum ModuleDepType { DEFINITION, OVERRIDING }

	public static class AnalysisResult implements Serializable {
		private boolean cached = false;
		private String moduleName, contentHash;
		private int[] semanticTokens = new int[0];
		private final List<Diagnostic> errors = new ArrayList<>();
		private final Map<String, Map<ModuleDepType,Map<Integer,List<Integer>>>> moduleDeps = new HashMap<>();
		private final List<Either<SymbolInformation, DocumentSymbol>> outline = new ArrayList<>();
		private final SortedMap<Integer, Identifier> idDefinitions = new TreeMap<>(), idReferences = new TreeMap<>();
		private final SortedMap<Integer, Identifier> functionDefinitions = new TreeMap<>();
		private final SortedMap<Integer, int[]> functionRanges = new TreeMap<>();
		private final SortedMap<Integer,ParamTag> paramTags = new TreeMap<>();
		private final Map<Integer,IdentifierReference> exportedSymbolRefs = new HashMap<>();
		private final SortedMap<Integer,Integer> declarationBlocks = new TreeMap<>();
		private final SortedMap<Integer,int[]> definitionLists = new TreeMap<>();

		private final List<SerializableDiagnostic> serializableDiagnostics = new ArrayList<>();
		private final List<SerializableDocumentSymbol> serializableDocumentSymbols = new ArrayList<>();

		public void prepareSerialize() {
			for(Either<SymbolInformation,DocumentSymbol> e : outline) {
				DocumentSymbol ds = e.getRight();
				SerializableDocumentSymbol sds = new SerializableDocumentSymbol(ds);
				if (!ds.equals(sds.toDocumentSymbol()))
					throw new IllegalStateException("Missing fields?");
				serializableDocumentSymbols.add(sds);
			}
			outline.clear();
			for(Diagnostic error : errors) {
				SerializableDiagnostic sd = new SerializableDiagnostic(error);
				if (!error.equals(sd.toDiagnostic()))
					throw new IllegalStateException("Missing fields?");
				serializableDiagnostics.add(sd);
			}
			errors.clear();
		}

		public void afterDeserialize() {
			for(SerializableDocumentSymbol sds : serializableDocumentSymbols) {
				outline.add(Either.forRight(sds.toDocumentSymbol()));
			}
			serializableDocumentSymbols.clear();
			for(SerializableDiagnostic sd  : serializableDiagnostics) {
				errors.add(sd.toDiagnostic());
			}
			serializableDiagnostics.clear();
			setCached(true);
		}

		public String getModuleName() {
			return moduleName;
		}

		public void setModuleName(String moduleName) {
			this.moduleName = moduleName;
		}

		public String getContentHash() {
			return contentHash;
		}

		public void setContentHash(String contentHash) {
			this.cached = false;
			this.contentHash = contentHash;
		}

		public boolean isCached() {
			return cached;
		}

		public void setCached(boolean cached) {
			this.cached = cached;
		}

		public List<Diagnostic> getErrors() {
			return errors;
		}

		public Map<String, Map<ModuleDepType,Map<Integer,List<Integer>>>> getModuleDeps() {
			return moduleDeps;
		}

		public int[] getSemanticTokens() {
			return semanticTokens;
		}

		public void setSemanticTokens(int[] semanticTokens) {
			this.semanticTokens = semanticTokens;
		}

		public List<Either<SymbolInformation, DocumentSymbol>> getOutline() {
			return outline;
		}

		public SortedMap<Integer, Identifier> getIdDefinitions() {
			return idDefinitions;
		}

		public SortedMap<Integer, Identifier> getIdReferences() {
			return idReferences;
		}

		public SortedMap<Integer, Identifier> getFunctionDefinitions() {
			return functionDefinitions;
		}

		/**
		 * Get function ranges. Maps start of {@code PROCEDURE} keyword to array of size 2 or 3.
		 * <li>{@code value[0]}: End of function identifier.
		 * <li>{@code value[1]}: End of function body.
		 * <li>{@code value[2]}: End of function keyword of outer nested function.
		 */
		public SortedMap<Integer, int[]> getFunctionRanges() {
			return functionRanges;
		}

		/**
		 * Get declaration blocks: Maps start of list to end of list.
		 * If there are no definitions in the declaration block any more, the whole list can be removed.
		 */
		public SortedMap<Integer, Integer> getDeclarationBlocks() {
			return declarationBlocks;
		}

		/**
		 * Get definition lists. Maps start of list to array of size 3.
		 * <li>{@code value[0]}: Start of list
		 * <li>{@code value[1]}: Start of the list's value (if any)
		 * <li>{@code value[2]}: End of the list
		 */
		public SortedMap<Integer, int[]> getDefinitionLists() {
			return definitionLists;
		}

		public SortedMap<Integer, ParamTag> getParamTags() {
			return paramTags;
		}

		public Map<Integer, IdentifierReference> getExportedSymbolRefs() {
			return exportedSymbolRefs;
		}
	}

	private static class SerializableDiagnostic implements Serializable {
		private final int startLine, startChar, endLine, endChar;
		private final boolean unneccessary;
		private final String message;
		private final DiagnosticSeverity severity;

		public SerializableDiagnostic(Diagnostic diag) {
			this.startLine = diag.getRange().getStart().getLine();
			this.startChar = diag.getRange().getStart().getCharacter();
			this.endLine = diag.getRange().getEnd().getLine();
			this.endChar = diag.getRange().getEnd().getCharacter();
			this.message = diag.getMessage();
			this.severity = diag.getSeverity();
			this.unneccessary = diag.getTags() != null && diag.getTags().contains(DiagnosticTag.Unnecessary);
		}

		public Diagnostic toDiagnostic() {
			Diagnostic diagnostic = new Diagnostic(new Range(new Position(startLine, startChar), new Position(endLine, endChar)), message);
			diagnostic.setSeverity(this.severity);
			if (unneccessary) {
				diagnostic.setTags(Arrays.asList(DiagnosticTag.Unnecessary));
			}
			return diagnostic;
		}
	}

	public static class SerializableDocumentSymbol implements Serializable {

		private final String name;
		private final SymbolKind kind;
		private final int startLine, startChar, endLine, endChar;
		private final int selStartLine, selStartChar, selEndLine, selEndChar;
		private final List<SerializableDocumentSymbol> children;

		public SerializableDocumentSymbol(DocumentSymbol ds) {
			this.name = ds.getName();
			this.kind = ds.getKind();
			this.startLine = ds.getRange().getStart().getLine();
			this.startChar = ds.getRange().getStart().getCharacter();
			this.endLine = ds.getRange().getEnd().getLine();
			this.endChar = ds.getRange().getEnd().getCharacter();
			this.selStartLine = ds.getSelectionRange().getStart().getLine();
			this.selStartChar = ds.getSelectionRange().getStart().getCharacter();
			this.selEndLine = ds.getSelectionRange().getEnd().getLine();
			this.selEndChar = ds.getSelectionRange().getEnd().getCharacter();
			this.children = ds.getChildren() == null ? null : ds.getChildren().stream().map(SerializableDocumentSymbol::new).collect(Collectors.toList());
		}

		public DocumentSymbol toDocumentSymbol() {
			DocumentSymbol ds = new DocumentSymbol(name, kind,
					new Range(new Position(startLine, startChar), new Position(endLine, endChar)),
					new Range(new Position(selStartLine, selStartChar), new Position(selEndLine, selEndChar)));
			if (children != null) {
				ds.setChildren(new ArrayList<>());
				for(SerializableDocumentSymbol child : children) {
					ds.getChildren().add(child.toDocumentSymbol());
				}
			}
			return ds;
		}
	}

	public static class IdentifierReference implements Serializable {
		private final String module;
		private final int endPos;

		public IdentifierReference(String module, int endPos) {
			this.module = module;
			this.endPos = endPos;
		}

		public String getModule() {
			return module;
		}

		public int getEndPos() {
			return endPos;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + endPos;
			result = prime * result + ((module == null) ? 0 : module.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			IdentifierReference other = (IdentifierReference) obj;
			if (endPos != other.endPos)
				return false;
			if (module == null) {
				if (other.module != null)
					return false;
			} else if (!module.equals(other.module))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return module + "@" + endPos;
		}
	}
	public static class Identifier implements Serializable {
		private final int startPos, endPos;
		private int exportedPos;
		private IdentifierReference definition;
		private final SymbolKind kind;
		private boolean writtenTo, used, procedureParameter, definitionRepeat, overriding, command;

		public Identifier(int startPos, int endPos, SymbolKind kind, IdentifierReference definition) {
			this.startPos = startPos;
			this.endPos = endPos;
			this.kind = kind;
			this.definition = definition;
			this.exportedPos = -1;
		}

		public int getStartPos() {
			return startPos;
		}

		public int getEndPos() {
			return endPos;
		}

		public SymbolKind getKind() {
			return kind;
		}

		public IdentifierReference getDefinition() {
			return definition;
		}

		/** For updating pointer references */
		public void setDefinition(IdentifierReference definition) {
			this.definition = definition;
		}

		public int getExportedPos() {
			return exportedPos;
		}

		public void setExportedPos(int exportedPos) {
			this.exportedPos = exportedPos;
		}

		public boolean isExported() {
			return exportedPos != -1;
		}

		public boolean isWrittenTo() {
			return writtenTo;
		}

		public void setWrittenTo(boolean writtenTo) {
			this.writtenTo = writtenTo;
		}

		public boolean isDefinitionRepeat() {
			return definitionRepeat;
		}

		public void setDefinitionRepeat(boolean definitionRepeat) {
			this.definitionRepeat = definitionRepeat;
		}

		public boolean isUsed() {
			return used;
		}

		public void setUsed(boolean used) {
			this.used = used;
		}

		public boolean isProcedureParameter() {
			return procedureParameter;
		}

		public void setProcedureParameter(boolean procedureParameter) {
			this.procedureParameter = procedureParameter;
		}

		public boolean isOverriding() {
			return overriding;
		}

		public void setOverriding(boolean overriding) {
			this.overriding = overriding;
		}

		public boolean isCommand() {
			return command;
		}

		public void setCommand(boolean command) {
			this.command = command;
		}
	}
}
